package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.common.enumtype.ConnectionStatus;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.MachineConnectionStateService;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MachineConnectionStateServiceImpl implements MachineConnectionStateService {

    private final MachineRepository machineRepo;
    private final SseEmitterRegistry sseRegistry;

    @Value("${app.connection.stale-threshold-sec:10}")
    private long staleThresholdSec;

    @Value("${app.connection.offline-threshold-sec:30}")
    private long offlineThresholdSec;

    @Value("${app.connection.flap-window-sec:60}")
    private long flapWindowSec;

    @Value("${app.connection.unstable-flap-threshold:3}")
    private int unstableFlapThreshold;

    @Override
    public void markTelemetryReceived(MachineEntity machine, Instant sourceTs, Instant receivedAt, String payloadFingerprint, boolean acceptedForCurrentState) {
        var previousState = normalizeState(machine.getConnectionState());

        machine.setLastSeenAt(receivedAt);
        machine.setLastTelemetryReceivedAt(receivedAt);
        machine.setLastPayloadFingerprint(payloadFingerprint);
        machine.setLastTelemetrySourceTs(max(machine.getLastTelemetrySourceTs(), sourceTs));

        if (acceptedForCurrentState) {
            machine.setLatestAcceptedSourceTs(max(machine.getLatestAcceptedSourceTs(), sourceTs));
        }

        if (!ConnectionStatus.ONLINE.name().equals(previousState)) {
            machine.setConnectionState(ConnectionStatus.ONLINE.name());
            registerFlapTransition(machine, receivedAt);
            emitConnectionChanged(machine, previousState, ConnectionStatus.ONLINE.name(), receivedAt);
        }

        machineRepo.save(machine);
    }

    @Override
    public void evaluateByWatchdog(MachineEntity machine, Instant now) {
        var previousState = normalizeState(machine.getConnectionState());
        var targetState = decideState(machine, now);

        if (targetState.equals(previousState)) {
            clearUnstableIfSettled(machine, now);
            machineRepo.save(machine);
            return;
        }

        machine.setConnectionState(targetState);
        // When going OFFLINE, clear stale operational status so dashboard/UI
        // never shows RUNNING for a disconnected machine.
        if (ConnectionStatus.OFFLINE.name().equals(targetState)) {
            machine.setStatus("OFFLINE");
            log.info("Machine {} ({}) went OFFLINE – operational status reset.", machine.getCode(), machine.getName());
        }
        registerFlapTransition(machine, now);
        emitConnectionChanged(machine, previousState, targetState, now);
        machineRepo.save(machine);
    }

    @Override
    public void markConnectionReported(MachineEntity machine, String reportedState, Instant at, Map<String, Object> metadata) {
        var previousState = normalizeState(machine.getConnectionState());
        var targetState = normalizeReportedState(reportedState);

        if (ConnectionStatus.ONLINE.name().equals(targetState) || ConnectionStatus.STALE.name().equals(targetState)) {
            machine.setLastSeenAt(at);
        }

        if (!targetState.equals(previousState)) {
            machine.setConnectionState(targetState);
            registerFlapTransition(machine, at);
            emitConnectionChanged(machine, previousState, targetState, at);
        }

        if (metadata != null && !metadata.isEmpty()) {
            sseRegistry.broadcast("machine-connection-reported", Map.of(
                    "machineId", machine.getId(),
                    "machineCode", machine.getCode(),
                    "state", targetState,
                    "metadata", metadata,
                    "ts", at
            ));
        }

        machineRepo.save(machine);
    }

    private String decideState(MachineEntity machine, Instant now) {
        if (machine.getLastSeenAt() == null) {
            return ConnectionStatus.OFFLINE.name();
        }

        long silenceSec = Duration.between(machine.getLastSeenAt(), now).toSeconds();
        if (silenceSec >= offlineThresholdSec) {
            return ConnectionStatus.OFFLINE.name();
        }
        if (silenceSec >= staleThresholdSec) {
            return ConnectionStatus.STALE.name();
        }
        return ConnectionStatus.ONLINE.name();
    }

    private void registerFlapTransition(MachineEntity machine, Instant at) {
        Integer flapCount = machine.getConnectionFlapCount() == null ? 0 : machine.getConnectionFlapCount();
        Instant lastChanged = machine.getLastConnectionChangedAt();

        if (lastChanged != null && Duration.between(lastChanged, at).toSeconds() <= flapWindowSec) {
            flapCount++;
        } else {
            flapCount = 1;
        }

        machine.setConnectionFlapCount(flapCount);
        machine.setLastConnectionChangedAt(at);

        if (flapCount >= unstableFlapThreshold && !Boolean.TRUE.equals(machine.getConnectionUnstable())) {
            machine.setConnectionUnstable(true);
            sseRegistry.broadcast("machine-connection-unstable", Map.of(
                    "machineId", machine.getId(),
                    "flapCount", flapCount,
                    "windowSec", flapWindowSec,
                    "ts", at
            ));
        }
    }

    private void clearUnstableIfSettled(MachineEntity machine, Instant now) {
        if (!Boolean.TRUE.equals(machine.getConnectionUnstable()) || machine.getLastConnectionChangedAt() == null) {
            return;
        }

        if (Duration.between(machine.getLastConnectionChangedAt(), now).toSeconds() > flapWindowSec) {
            machine.setConnectionUnstable(false);
            machine.setConnectionFlapCount(0);
        }
    }

    private void emitConnectionChanged(MachineEntity machine, String fromState, String toState, Instant at) {
        String eventName = switch (toState) {
            case "ONLINE" -> "machine-connection-online";
            case "STALE" -> "machine-connection-stale";
            default -> "machine-connection-offline";
        };

        long freshnessSec = machine.getLastSeenAt() == null ? -1 : Duration.between(machine.getLastSeenAt(), at).toSeconds();
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("machineId", machine.getId());
        payload.put("machineCode", machine.getCode());
        payload.put("from", fromState);
        payload.put("to", toState);
        payload.put("lastSeenAt", machine.getLastSeenAt());
        payload.put("freshnessSec", freshnessSec);
        payload.put("connectionUnstable", Boolean.TRUE.equals(machine.getConnectionUnstable()));
        payload.put("ts", at);
        sseRegistry.broadcast("machine-connection-changed", payload);
        sseRegistry.broadcast(eventName, payload);

        if ("ONLINE".equals(toState) && !"ONLINE".equals(fromState)) {
            log.info("Da nhan du lieu ingest cho may {} ({}) - connection {} -> {}",
                    machine.getCode(), machine.getName(), fromState, toState);
            return;
        }

        log.info("Machine {} ({}) connection {} -> {}", machine.getCode(), machine.getName(), fromState, toState);
    }

    private Instant max(Instant current, Instant candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return candidate.isAfter(current) ? candidate : current;
    }

    private String normalizeState(String state) {
        return state == null ? ConnectionStatus.OFFLINE.name() : state;
    }

    private String normalizeReportedState(String reportedState) {
        if (reportedState == null || reportedState.isBlank()) {
            throw new AppException("VALIDATION_ERROR", "connectionStatus is required");
        }

        String normalized = reportedState.trim().toUpperCase();
        return switch (normalized) {
            case "ONLINE", "ON" -> ConnectionStatus.ONLINE.name();
            case "STALE", "DEGRADED", "LAGGING" -> ConnectionStatus.STALE.name();
            case "OFFLINE", "DISCONNECTED" -> ConnectionStatus.OFFLINE.name();
            case "RECOVERING" -> ConnectionStatus.ONLINE.name();
            default -> throw new AppException("VALIDATION_ERROR", "Unsupported connectionStatus: " + reportedState);
        };
    }
}


