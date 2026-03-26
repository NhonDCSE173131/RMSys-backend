package com.rmsys.backend.domain.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.rmsys.backend.domain.service.AlarmLifecycleService;
import com.rmsys.backend.domain.service.RuleEngineService;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;

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

    @Override
    @Transactional
    public void ingestTelemetry(NormalizedTelemetryDto dto) {
        var machine = machineRepo.findById(dto.machineId())
                .orElseThrow(() -> AppException.notFound("Machine", dto.machineId()));

        var ts = resolveTimestamp(dto);
        var receivedAt = Instant.now();
        var fingerprint = fingerprint(dto, ts);
        var qualityScore = qualityService.scoreQuality(dto);
        boolean duplicate = isDuplicate(machine, ts, fingerprint);
        boolean outOfOrder = isOutOfOrder(machine, ts);
        boolean suspicious = qualityService.isSuspicious(dto);

        if (duplicate) {
            connectionStateService.markTelemetryReceived(machine, ts, receivedAt, fingerprint, false);
            log.debug("Skipped duplicate telemetry for machine {} at {}", dto.machineId(), ts);
            return;
        }

        saveMachineTelemetry(dto, ts, receivedAt, outOfOrder, qualityScore);
        saveEnergyTelemetryIfPresent(dto, ts);
        saveMaintenanceTelemetryIfPresent(dto, ts);
        saveToolUsageIfPresent(dto, ts);
        connectionStateService.markTelemetryReceived(machine, ts, receivedAt, fingerprint, !outOfOrder);

        if (!outOfOrder) {
            updateMachineStatus(machine, dto);
            ruleEngine.evaluate(dto);
            publishTelemetryEvent(dto);
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
            machine.setStatus(dto.machineState());
            machineRepo.save(machine);
        }
    }

    private void publishTelemetryEvent(NormalizedTelemetryDto dto) {
        sseRegistry.broadcast("machine-telemetry-updated", dto);
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

    private String toJson(HashMap<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize telemetry metadata", ex);
            return "{}";
        }
    }
}
