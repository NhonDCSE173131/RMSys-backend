package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.domain.dto.NormalizedAlarmDto;
import com.rmsys.backend.domain.dto.NormalizedDowntimeDto;
import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.entity.*;
import com.rmsys.backend.domain.repository.*;
import com.rmsys.backend.domain.service.IngestService;
import com.rmsys.backend.domain.service.RuleEngineService;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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

    @Override
    @Transactional
    public void ingestTelemetry(NormalizedTelemetryDto dto) {
        var ts = dto.ts() != null ? dto.ts() : Instant.now();

        // Save machine telemetry
        var telemetry = MachineTelemetryEntity.builder()
                .machineId(dto.machineId()).ts(ts)
                .connectionStatus(dto.connectionStatus()).machineState(dto.machineState())
                .operationMode(dto.operationMode()).programName(dto.programName())
                .cycleRunning(dto.cycleRunning()).powerKw(dto.powerKw())
                .temperatureC(dto.temperatureC()).vibrationMmS(dto.vibrationMmS())
                .runtimeHours(dto.runtimeHours()).cycleTimeSec(dto.cycleTimeSec())
                .outputCount(dto.outputCount()).goodCount(dto.goodCount())
                .rejectCount(dto.rejectCount()).spindleSpeedRpm(dto.spindleSpeedRpm())
                .feedRateMmMin(dto.feedRateMmMin())
                .build();
        telemetryRepo.save(telemetry);

        // Save energy telemetry if available
        if (dto.voltageV() != null || dto.powerKw() != null) {
            var energy = EnergyTelemetryEntity.builder()
                    .machineId(dto.machineId()).ts(ts)
                    .voltageV(dto.voltageV()).currentA(dto.currentA())
                    .powerKw(dto.powerKw()).powerFactor(dto.powerFactor())
                    .frequencyHz(dto.frequencyHz())
                    .energyKwhShift(dto.energyKwhShift()).energyKwhDay(dto.energyKwhDay())
                    .build();
            energyRepo.save(energy);
        }

        // Save maintenance telemetry if available
        if (dto.motorTemperatureC() != null || dto.bearingTemperatureC() != null) {
            var maint = MaintenanceTelemetryEntity.builder()
                    .machineId(dto.machineId()).ts(ts)
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
            maintRepo.save(maint);
        }

        // Save tool usage if available
        if (dto.toolCode() != null) {
            var toolUsage = ToolUsageTelemetryEntity.builder()
                    .machineId(dto.machineId()).ts(ts)
                    .toolCode(dto.toolCode())
                    .remainingLifePct(dto.remainingToolLifePct())
                    .build();
            toolRepo.save(toolUsage);
        }

        // Update machine status
        machineRepo.findById(dto.machineId()).ifPresent(m -> {
            m.setStatus(dto.machineState() != null ? dto.machineState() : m.getStatus());
            machineRepo.save(m);
        });

        // Evaluate rules (alarm generation)
        ruleEngine.evaluate(dto);

        // Push realtime event
        sseRegistry.broadcast("machine-telemetry-updated", dto);
        log.debug("Ingested telemetry for machine {}", dto.machineId());
    }

    @Override
    @Transactional
    public void ingestAlarm(NormalizedAlarmDto dto) {
        var alarm = AlarmEventEntity.builder()
                .machineId(dto.machineId())
                .alarmCode(dto.alarmCode()).alarmType(dto.alarmType())
                .severity(dto.severity()).message(dto.message())
                .startedAt(dto.startedAt() != null ? dto.startedAt() : Instant.now())
                .isActive(true).acknowledged(false)
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
                .reasonCode(dto.reasonCode()).reasonGroup(dto.reasonGroup())
                .startedAt(dto.startedAt() != null ? dto.startedAt() : Instant.now())
                .plannedStop(dto.plannedStop()).abnormalStop(dto.abnormalStop())
                .notes(dto.notes())
                .build();
        downtimeRepo.save(event);
        sseRegistry.broadcast("downtime-created", event);
        log.info("Downtime created for machine {}: {}", dto.machineId(), dto.reasonCode());
    }
}

