package com.rmsys.backend.domain.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rmsys.backend.common.enumtype.ConnectionStatus;
import com.rmsys.backend.common.enumtype.MachineState;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.dto.NormalizedAlarmDto;
import com.rmsys.backend.domain.dto.NormalizedDowntimeDto;
import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.entity.AlarmEventEntity;
import com.rmsys.backend.domain.entity.DowntimeEventEntity;
import com.rmsys.backend.domain.entity.EnergyTelemetryEntity;
import com.rmsys.backend.domain.entity.MachineTelemetryEntity;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MaintenanceTelemetryEntity;
import com.rmsys.backend.domain.entity.ToolUsageTelemetryEntity;
import com.rmsys.backend.domain.repository.AlarmEventRepository;
import com.rmsys.backend.domain.repository.DowntimeEventRepository;
import com.rmsys.backend.domain.repository.EnergyTelemetryRepository;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.repository.MachineTelemetryRepository;
import com.rmsys.backend.domain.repository.MaintenanceTelemetryRepository;
import com.rmsys.backend.domain.repository.ToolUsageTelemetryRepository;
import com.rmsys.backend.domain.service.IngestService;
import com.rmsys.backend.domain.service.MachineConnectionStateService;
import com.rmsys.backend.domain.service.TelemetryQualityService;
import com.rmsys.backend.domain.service.MachineRealtimeSnapshotService;
import com.rmsys.backend.domain.service.RuleEngineService;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestServiceImpl implements IngestService {

    private final MachineTelemetryRepository telemetryRepo;
    private final EnergyTelemetryRepository energyRepo;
    private final MaintenanceTelemetryRepository maintRepo;
    private final ToolUsageTelemetryRepository toolRepo;
    private final AlarmEventRepository alarmRepo;
    private final DowntimeEventRepository downtimeRepo;
    private final MachineRepository machineRepo;
    private final RuleEngineService ruleEngine;
    private final SseEmitterRegistry sseRegistry;
    private final MachineConnectionStateService connectionStateService;
    private final ObjectMapper objectMapper;
    private final TelemetryQualityService qualityService;
    private final MachineRealtimeSnapshotService snapshotService;
    private static final Set<String> CANONICAL_OPERATING_STATES = Set.of(
            MachineState.RUNNING.name(),
            MachineState.IDLE.name(),
            MachineState.WARMUP.name(),
            MachineState.STOPPED.name(),
            MachineState.EMERGENCY_STOP.name(),
            MachineState.MAINTENANCE.name());

    @Override
    @Transactional
    public void ingestTelemetry(NormalizedTelemetryDto dto) {
        var machine = machineRepo.findById(dto.machineId())
                .orElseThrow(() -> AppException.notFound("Machine", dto.machineId()));

        var normalizedDto = enrichMachineIdentity(dto, machine);

        var ts = resolveTimestamp(normalizedDto);
        var receivedAt = Instant.now();
        var fingerprint = fingerprint(normalizedDto, ts);
        var qualityScore = qualityService.scoreQuality(normalizedDto);
        boolean duplicate = isDuplicate(machine, ts, fingerprint);
        boolean outOfOrder = isOutOfOrder(machine, ts);
        boolean suspicious = qualityService.isSuspicious(normalizedDto);

        if (duplicate) {
            connectionStateService.markTelemetryReceived(machine, ts, receivedAt, fingerprint, false);
            log.debug("Skipped duplicate telemetry for machine {} at {}", dto.machineId(), ts);
            return;
        }

        saveMachineTelemetry(normalizedDto, ts, receivedAt, outOfOrder, qualityScore);
        saveEnergyTelemetryIfPresent(normalizedDto, ts);
        saveMaintenanceTelemetryIfPresent(normalizedDto, ts);
        saveToolUsageIfPresent(normalizedDto, ts);
        connectionStateService.markTelemetryReceived(machine, ts, receivedAt, fingerprint, !outOfOrder);
        if (normalizedDto.connectionStatus() != null && !normalizedDto.connectionStatus().isBlank()) {
            connectionStateService.markConnectionReported(
                    machine,
                    normalizedDto.connectionStatus(),
                    ts,
                    normalizedDto.metadata() != null ? normalizedDto.metadata() : Collections.emptyMap());
        }

        if (!outOfOrder) {
            updateMachineStatus(machine, normalizedDto);
            ruleEngine.evaluate(normalizedDto);
            publishTelemetryEvent(machine, normalizedDto, ts, receivedAt);
        } else {
            log.debug("Out-of-order telemetry kept for history only. machine={}, sourceTs={}, latestAccepted={}",
                    machine.getId(), ts, machine.getLatestAcceptedSourceTs());
        }

        if (suspicious && qualityScore < 50) {
            log.warn("Suspicious telemetry for machine {}: quality={}", machine.getId(), qualityScore);
        }

        log.debug("Ingested telemetry for machine {}", dto.machineId());
    }

    @Override
    @Transactional
    public void ingestAlarm(NormalizedAlarmDto dto) {
        var alarm = AlarmEventEntity.builder()
                .machineId(dto.machineId())
                .alarmCode(dto.alarmCode())
                .alarmType(dto.alarmType())
                .severity(dto.severity())
                .message(dto.message())
                .startedAt(dto.startedAt() != null ? dto.startedAt() : Instant.now())
                .isActive(true)
                .acknowledged(false)
                .build();

        alarmRepo.save(alarm);
        sseRegistry.broadcast("alarm-created", alarm);
        log.info("Alarm created: {} for machine {}", dto.alarmCode(), dto.machineId());
    }

    @Override
    @Transactional
    public void ingestDowntime(NormalizedDowntimeDto dto) {
        var event = DowntimeEventEntity.builder()
                .machineId(dto.machineId())
                .reasonCode(dto.reasonCode())
                .reasonGroup(dto.reasonGroup())
                .startedAt(dto.startedAt() != null ? dto.startedAt() : Instant.now())
                .plannedStop(dto.plannedStop())
                .abnormalStop(dto.abnormalStop())
                .notes(dto.notes())
                .build();

        downtimeRepo.save(event);
        sseRegistry.broadcast("downtime-created", event);
        log.info("Downtime created for machine {}: {}", dto.machineId(), dto.reasonCode());
    }

    private Instant resolveTimestamp(NormalizedTelemetryDto dto) {
        return dto.ts() != null ? dto.ts() : Instant.now();
    }

    private void saveMachineTelemetry(NormalizedTelemetryDto dto, Instant ts, Instant receivedAt, boolean outOfOrder, double qualityScore) {
        var metadata = new HashMap<String, Object>();
        if (dto.metadata() != null) {
            metadata.putAll(dto.metadata());
        }
        metadata.put("sourceTs", ts);
        metadata.put("receivedAt", receivedAt);
        metadata.put("lateArrival", outOfOrder);
        metadata.put("qualityScore", qualityScore);

        var telemetry = MachineTelemetryEntity.builder()
                .machineId(dto.machineId())
                .ts(ts)
                .connectionStatus(dto.connectionStatus())
                .machineState(dto.machineState())
                .operationMode(dto.operationMode())
                .programName(dto.programName())
                .cycleRunning(dto.cycleRunning())
                .powerKw(dto.powerKw())
                .temperatureC(dto.temperatureC())
                .vibrationMmS(dto.vibrationMmS())
                .runtimeHours(dto.runtimeHours())
                .cycleTimeSec(dto.cycleTimeSec())
                .outputCount(dto.outputCount())
                .goodCount(dto.goodCount())
                .rejectCount(dto.rejectCount())
                .spindleSpeedRpm(dto.spindleSpeedRpm())
                .feedRateMmMin(dto.feedRateMmMin())
                .idealCycleTimeSec(dto.idealCycleTimeSec())
                .spindleLoadPct(dto.spindleLoadPct())
                .servoLoadPct(dto.servoLoadPct())
                .cuttingSpeedMMin(dto.cuttingSpeedMMin())
                .depthOfCutMm(dto.depthOfCutMm())
                .feedPerToothMm(dto.feedPerToothMm())
                .widthOfCutMm(dto.widthOfCutMm())
                .materialRemovalRateCm3Min(dto.materialRemovalRateCm3Min())
                .weldingCurrentA(dto.weldingCurrentA())
                .metadataJson(toJson(metadata))
                .qualityScore(qualityScore)
                .isLateArrival(outOfOrder)
                .build();

        telemetryRepo.save(telemetry);
    }

    private void saveEnergyTelemetryIfPresent(NormalizedTelemetryDto dto, Instant ts) {
        if (dto.voltageV() == null && dto.powerKw() == null) {
            return;
        }

        var energy = EnergyTelemetryEntity.builder()
                .machineId(dto.machineId())
                .ts(ts)
                .voltageV(dto.voltageV())
                .currentA(dto.currentA())
                .powerKw(dto.powerKw())
                .powerFactor(dto.powerFactor())
                .frequencyHz(dto.frequencyHz())
                .energyKwhShift(dto.energyKwhShift())
                .energyKwhDay(dto.energyKwhDay())
                .build();

        energyRepo.save(energy);
    }

    private void saveMaintenanceTelemetryIfPresent(NormalizedTelemetryDto dto, Instant ts) {
        if (dto.motorTemperatureC() == null && dto.bearingTemperatureC() == null) {
            return;
        }

        var maintenance = MaintenanceTelemetryEntity.builder()
                .machineId(dto.machineId())
                .ts(ts)
                .motorTemperatureC(dto.motorTemperatureC())
                .bearingTemperatureC(dto.bearingTemperatureC())
                .cabinetTemperatureC(dto.cabinetTemperatureC())
                .vibrationMmS(dto.vibrationMmS())
                .runtimeHours(dto.runtimeHours())
                .servoOnHours(dto.servoOnHours())
                .startStopCount(dto.startStopCount())
                .lubricationLevelPct(dto.lubricationLevelPct())
                .batteryLow(dto.batteryLow())
                .build();

        maintRepo.save(maintenance);
    }

    private void saveToolUsageIfPresent(NormalizedTelemetryDto dto, Instant ts) {
        if (dto.toolCode() == null) {
            return;
        }

        var toolUsage = ToolUsageTelemetryEntity.builder()
                .machineId(dto.machineId())
                .ts(ts)
                .toolCode(dto.toolCode())
                .remainingLifePct(dto.remainingToolLifePct())
                .build();

        toolRepo.save(toolUsage);
    }

    private void updateMachineStatus(MachineEntity machine, NormalizedTelemetryDto dto) {
        if (dto.machineState() != null) {
            machine.setStatus(normalizeOperationalState(dto.machineState()));
            machineRepo.save(machine);
        }
    }

    private void publishTelemetryEvent(MachineEntity machine, NormalizedTelemetryDto dto, Instant sourceTs, Instant receivedAt) {
        // Build canonical snapshot with full OEE/health/maintenance/tool data
        var canonicalSnapshot = snapshotService.buildSnapshot(dto.machineId());

        if (canonicalSnapshot != null) {
            // Primary canonical event — UI only needs this one event
            sseRegistry.broadcast("machine-snapshot-updated", canonicalSnapshot);
        }

        // Keep backward-compatible telemetry event with raw fields for existing subscribers
        var payload = new LinkedHashMap<String, Object>();
        payload.put("machineId", dto.machineId());
        payload.put("machineCode", machine.getCode());
        payload.put("sourceTs", sourceTs);
        payload.put("receivedAt", receivedAt);
        String operationalState = normalizeOperationalState(dto.machineState() != null ? dto.machineState() : machine.getStatus());
        String connectionState = normalizeConnectionState(machine.getConnectionState(), machine.getConnectionUnstable());
        String displayState = resolveDisplayState(operationalState, connectionState);
        payload.put("operationalState", operationalState);
        payload.put("displayState", displayState);
        payload.put("connectionState", connectionState);
        payload.put("connectionUnstable", Boolean.TRUE.equals(machine.getConnectionUnstable()));
        payload.put("lastSeenAt", machine.getLastSeenAt());
        payload.put("dataFreshnessSec", machine.getLastSeenAt() == null ? null : Duration.between(machine.getLastSeenAt(), receivedAt).toSeconds());
        payload.put("liveDataAvailable", true);
        payload.put("operationMode", dto.operationMode());
        payload.put("programName", dto.programName());
        payload.put("cycleRunning", dto.cycleRunning());
        payload.put("powerKw", dto.powerKw());
        payload.put("temperatureC", dto.temperatureC());
        payload.put("vibrationMmS", dto.vibrationMmS());
        payload.put("runtimeHours", dto.runtimeHours());
        payload.put("cycleTimeSec", dto.cycleTimeSec());
        payload.put("outputCount", dto.outputCount());
        payload.put("goodCount", dto.goodCount());
        payload.put("rejectCount", dto.rejectCount());
        payload.put("spindleSpeedRpm", dto.spindleSpeedRpm());
        payload.put("feedRateMmMin", dto.feedRateMmMin());
        payload.put("idealCycleTimeSec", dto.idealCycleTimeSec());
        payload.put("spindleLoadPct", dto.spindleLoadPct());
        payload.put("servoLoadPct", dto.servoLoadPct());
        payload.put("cuttingSpeedMMin", dto.cuttingSpeedMMin());
        payload.put("depthOfCutMm", dto.depthOfCutMm());
        payload.put("feedPerToothMm", dto.feedPerToothMm());
        payload.put("widthOfCutMm", dto.widthOfCutMm());
        payload.put("materialRemovalRateCm3Min", dto.materialRemovalRateCm3Min());
        payload.put("weldingCurrentA", dto.weldingCurrentA());
        payload.put("voltageV", dto.voltageV());
        payload.put("currentA", dto.currentA());
        payload.put("powerFactor", dto.powerFactor());
        payload.put("frequencyHz", dto.frequencyHz());
        payload.put("energyKwhShift", dto.energyKwhShift());
        payload.put("energyKwhDay", dto.energyKwhDay());
        payload.put("remainingToolLifePct", dto.remainingToolLifePct());

        sseRegistry.broadcast("machine-telemetry-updated", payload);
    }

    private String normalizeConnectionState(String connectionState, Boolean unstable) {
        String normalized = connectionState == null ? ConnectionStatus.OFFLINE.name() : connectionState.trim().toUpperCase();
        if (ConnectionStatus.ONLINE.name().equals(normalized) && Boolean.TRUE.equals(unstable)) {
            return ConnectionStatus.UNSTABLE.name();
        }
        return switch (normalized) {
            case "ONLINE", "STALE", "OFFLINE", "UNSTABLE" -> normalized;
            default -> ConnectionStatus.OFFLINE.name();
        };
    }

    private String normalizeOperationalState(String rawState) {
        if (rawState == null || rawState.isBlank()) {
            return MachineState.IDLE.name();
        }
        String normalized = rawState.trim().toUpperCase();
        if (CANONICAL_OPERATING_STATES.contains(normalized)) {
            return normalized;
        }
        return switch (normalized) {
            case "STOP" -> MachineState.STOPPED.name();
            case "FAULT", "ERROR", "ALARM", "ESTOP", "E_STOP" -> MachineState.EMERGENCY_STOP.name();
            default -> MachineState.IDLE.name();
        };
    }

    private String resolveDisplayState(String operationalState, String connectionState) {
        if (!ConnectionStatus.ONLINE.name().equals(connectionState)) {
            return connectionState;
        }
        return operationalState;
    }

    private boolean isOutOfOrder(MachineEntity machine, Instant sourceTs) {
        return machine.getLatestAcceptedSourceTs() != null && sourceTs.isBefore(machine.getLatestAcceptedSourceTs());
    }

    private boolean isDuplicate(MachineEntity machine, Instant sourceTs, String fingerprint) {
        return sourceTs.equals(machine.getLastTelemetrySourceTs())
                && fingerprint.equals(machine.getLastPayloadFingerprint());
    }

    private String fingerprint(NormalizedTelemetryDto dto, Instant sourceTs) {
        return "%s|%s|%s|%s|%s|%s|%s|%s".formatted(
                dto.machineId(),
                sourceTs,
                dto.machineState(),
                dto.operationMode(),
                dto.powerKw(),
                dto.temperatureC(),
                dto.vibrationMmS(),
                dto.outputCount());
    }

    private NormalizedTelemetryDto enrichMachineIdentity(NormalizedTelemetryDto dto, MachineEntity machine) {
        if (dto.machineCode() != null && dto.machineCode().equalsIgnoreCase(machine.getCode())) {
            return dto;
        }

        return NormalizedTelemetryDto.builder()
                .machineId(dto.machineId())
                .machineCode(machine.getCode())
                .ts(dto.ts())
                .connectionStatus(dto.connectionStatus())
                .machineState(dto.machineState())
                .operationMode(dto.operationMode())
                .programName(dto.programName())
                .cycleRunning(dto.cycleRunning())
                .powerKw(dto.powerKw())
                .temperatureC(dto.temperatureC())
                .vibrationMmS(dto.vibrationMmS())
                .runtimeHours(dto.runtimeHours())
                .cycleTimeSec(dto.cycleTimeSec())
                .outputCount(dto.outputCount())
                .goodCount(dto.goodCount())
                .rejectCount(dto.rejectCount())
                .spindleSpeedRpm(dto.spindleSpeedRpm())
                .feedRateMmMin(dto.feedRateMmMin())
                .idealCycleTimeSec(dto.idealCycleTimeSec())
                .spindleLoadPct(dto.spindleLoadPct())
                .servoLoadPct(dto.servoLoadPct())
                .cuttingSpeedMMin(dto.cuttingSpeedMMin())
                .depthOfCutMm(dto.depthOfCutMm())
                .feedPerToothMm(dto.feedPerToothMm())
                .widthOfCutMm(dto.widthOfCutMm())
                .materialRemovalRateCm3Min(dto.materialRemovalRateCm3Min())
                .weldingCurrentA(dto.weldingCurrentA())
                .toolCode(dto.toolCode())
                .remainingToolLifePct(dto.remainingToolLifePct())
                .voltageV(dto.voltageV())
                .currentA(dto.currentA())
                .powerFactor(dto.powerFactor())
                .frequencyHz(dto.frequencyHz())
                .energyKwhShift(dto.energyKwhShift())
                .energyKwhDay(dto.energyKwhDay())
                .motorTemperatureC(dto.motorTemperatureC())
                .bearingTemperatureC(dto.bearingTemperatureC())
                .cabinetTemperatureC(dto.cabinetTemperatureC())
                .servoOnHours(dto.servoOnHours())
                .startStopCount(dto.startStopCount())
                .lubricationLevelPct(dto.lubricationLevelPct())
                .batteryLow(dto.batteryLow())
                .metadata(dto.metadata())
                .build();
    }

    private String toJson(HashMap<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize telemetry metadata", ex);
            return "{}";
        }
    }
}
