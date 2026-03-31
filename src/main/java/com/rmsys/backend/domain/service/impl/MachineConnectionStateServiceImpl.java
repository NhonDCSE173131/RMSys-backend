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

        machine.setConnectionScope(null);
        machine.setConnectionReason(null);

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

        // Clear unstable flag first if connection has settled, so decideState sees current state
        clearUnstableIfSettled(machine, now);

        var targetState = decideState(machine, now);

        if (targetState.equals(previousState)) {
            machineRepo.save(machine);
            return;
        }

        machine.setConnectionState(targetState);
        if (!ConnectionStatus.ONLINE.name().equals(targetState)) {
            machine.setConnectionScope("BE_WATCHDOG");
            machine.setConnectionReason("NO_TELEMETRY");
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

        machine.setConnectionScope(resolveConnectionScope(metadata, targetState));
        machine.setConnectionReason(resolveConnectionReason(metadata, targetState));

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
        if (Boolean.TRUE.equals(machine.getConnectionUnstable())) {
            return ConnectionStatus.UNSTABLE.name();
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
            if (ConnectionStatus.ONLINE.name().equals(normalizeState(machine.getConnectionState()))) {
                machine.setConnectionState(ConnectionStatus.UNSTABLE.name());
            }
            var unstablePayload = new java.util.LinkedHashMap<String, Object>();
            unstablePayload.put("machineId", machine.getId());
            unstablePayload.put("machineCode", machine.getCode());
            unstablePayload.put("connectionState", ConnectionStatus.UNSTABLE.name());
            unstablePayload.put("displayState", ConnectionStatus.UNSTABLE.name());
            unstablePayload.put("lastSeenAt", machine.getLastSeenAt());
            unstablePayload.put("dataFreshnessSec", dataFreshnessSec(machine.getLastSeenAt(), at));
            unstablePayload.put("flapCount", flapCount);
            unstablePayload.put("windowSec", flapWindowSec);
            unstablePayload.put("ts", at);
            sseRegistry.broadcast("machine-connection-unstable", unstablePayload);
        }
    }

    private void clearUnstableIfSettled(MachineEntity machine, Instant now) {
        if (!Boolean.TRUE.equals(machine.getConnectionUnstable()) || machine.getLastConnectionChangedAt() == null) {
            return;
        }

        if (Duration.between(machine.getLastConnectionChangedAt(), now).toSeconds() > flapWindowSec) {
            machine.setConnectionUnstable(false);
            machine.setConnectionFlapCount(0);
            if (ConnectionStatus.UNSTABLE.name().equals(normalizeState(machine.getConnectionState()))) {
                machine.setConnectionState(ConnectionStatus.ONLINE.name());
            }
        }
    }

    private void emitConnectionChanged(MachineEntity machine, String fromState, String toState, Instant at) {
        String normalizedTo = normalizeState(toState);
        String eventName = switch (normalizedTo) {
            case "ONLINE" -> "machine-connection-online";
            case "STALE" -> "machine-connection-stale";
            case "UNSTABLE" -> "machine-connection-unstable";
            default -> "machine-connection-offline";
        };

        long freshnessSec = dataFreshnessSec(machine.getLastSeenAt(), at);
        String displayState = resolveDisplayState(normalizedTo);
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("machineId", machine.getId());
        payload.put("machineCode", machine.getCode());
        payload.put("from", fromState);
        payload.put("to", normalizedTo);
        payload.put("connectionState", normalizedTo);
        payload.put("displayState", displayState);
        payload.put("lastSeenAt", machine.getLastSeenAt());
        payload.put("dataFreshnessSec", freshnessSec);
        payload.put("connectionUnstable", Boolean.TRUE.equals(machine.getConnectionUnstable()));
        payload.put("connectionReason", machine.getConnectionReason());
        payload.put("connectionScope", machine.getConnectionScope());
        payload.put("ts", at);
        sseRegistry.broadcast("machine-connection-changed", payload);
        sseRegistry.broadcast(eventName, payload);

        if (ConnectionStatus.ONLINE.name().equals(normalizedTo) && !ConnectionStatus.ONLINE.name().equals(normalizeState(fromState))) {
            log.info("Da nhan du lieu ingest cho may {} ({}) - connection {} -> {}",
                    machine.getCode(), machine.getName(), fromState, normalizedTo);
            return;
        }

        log.info("Machine {} ({}) connection {} -> {}", machine.getCode(), machine.getName(), fromState, normalizedTo);
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
        if (state == null || state.isBlank()) {
            return ConnectionStatus.OFFLINE.name();
        }
        String normalized = state.trim().toUpperCase();
        return switch (normalized) {
            case "ONLINE", "ON" -> ConnectionStatus.ONLINE.name();
            case "STALE", "DEGRADED", "LAGGING" -> ConnectionStatus.STALE.name();
            case "UNSTABLE", "FLAPPING" -> ConnectionStatus.UNSTABLE.name();
            default -> ConnectionStatus.OFFLINE.name();
        };
    }

    private String normalizeReportedState(String reportedState) {
        if (reportedState == null || reportedState.isBlank()) {
            throw new AppException("VALIDATION_ERROR", "connectionStatus is required");
        }

        String normalized = reportedState.trim().toUpperCase();
        return switch (normalized) {
            case "ONLINE", "ON" -> ConnectionStatus.ONLINE.name();
            case "STALE", "DEGRADED", "LAGGING" -> ConnectionStatus.STALE.name();
            case "UNSTABLE", "FLAPPING" -> ConnectionStatus.UNSTABLE.name();
            case "OFFLINE", "DISCONNECTED" -> ConnectionStatus.OFFLINE.name();
            case "RECOVERING" -> ConnectionStatus.ONLINE.name();
            default -> throw new AppException("VALIDATION_ERROR", "Unsupported connectionStatus: " + reportedState);
        };
    }

    private long dataFreshnessSec(Instant lastSeenAt, Instant at) {
        return lastSeenAt == null ? -1 : Duration.between(lastSeenAt, at).toSeconds();
    }

    private String resolveDisplayState(String connectionState) {
        return switch (connectionState) {
            case "OFFLINE" -> "OFFLINE";
            case "STALE" -> "STALE";
            case "UNSTABLE" -> "UNSTABLE";
            default -> "ONLINE";
        };
    }

    private String resolveConnectionScope(Map<String, Object> metadata, String targetState) {
        if (ConnectionStatus.ONLINE.name().equals(targetState)) {
            return null;
        }
        String value = pickMetadataString(metadata, "connectionScope", "disconnectScope", "scope");
        return value != null ? value : "COLLECTOR";
    }

    private String resolveConnectionReason(Map<String, Object> metadata, String targetState) {
        if (ConnectionStatus.ONLINE.name().equals(targetState)) {
            return null;
        }
        String value = pickMetadataString(metadata, "connectionReason", "reason", "disconnectReason");
        return value != null ? value : "NETWORK_LOSS";
    }

    private String pickMetadataString(Map<String, Object> metadata, String... keys) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null) {
                String normalized = value.toString().trim();
                if (!normalized.isBlank()) {
                    return normalized;
                }
            }
        }
        return null;
    }
}


