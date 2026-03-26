package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.entity.AlarmEventEntity;
import com.rmsys.backend.domain.entity.MachineThresholdEntity;
import com.rmsys.backend.domain.repository.AlarmEventRepository;
import com.rmsys.backend.domain.repository.MachineThresholdRepository;
import com.rmsys.backend.domain.service.RuleEngineService;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineServiceImpl implements RuleEngineService {

    private final MachineThresholdRepository thresholdRepo;
    private final AlarmEventRepository alarmRepo;
    private final SseEmitterRegistry sseRegistry;

    @Override
    public void evaluate(NormalizedTelemetryDto dto) {
        var thresholds = thresholdRepo.findByMachineId(dto.machineId());

        for (var threshold : thresholds) {
            Double value = getMetricValue(dto, threshold.getMetricCode());
            if (value == null) continue;

            if (threshold.getCriticalValue() != null && value >= threshold.getCriticalValue()) {
                fireAlarm(dto, threshold, "CRITICAL", value);
            } else if (threshold.getWarningValue() != null && value >= threshold.getWarningValue()) {
                fireAlarm(dto, threshold, "WARNING", value);
            }
        }

        // Tool life check
        if (dto.remainingToolLifePct() != null && dto.remainingToolLifePct() < 10) {
            saveAlarm(dto.machineId(), "TOOL_LIFE_CRITICAL", "TOOL", "CRITICAL",
                    "Tool %s life critical: %.1f%% remaining".formatted(dto.toolCode(), dto.remainingToolLifePct()));
        } else if (dto.remainingToolLifePct() != null && dto.remainingToolLifePct() < 20) {
            saveAlarm(dto.machineId(), "TOOL_LIFE_WARNING", "TOOL", "WARNING",
                    "Tool %s life low: %.1f%% remaining".formatted(dto.toolCode(), dto.remainingToolLifePct()));
        }
    }

    private void fireAlarm(NormalizedTelemetryDto dto, MachineThresholdEntity threshold, String severity, double value) {
        saveAlarm(dto.machineId(), threshold.getMetricCode() + "_" + severity, "THRESHOLD", severity,
                "%s %s: %.2f %s (limit: %.2f)".formatted(
                        threshold.getMetricCode(), severity.toLowerCase(), value,
                        threshold.getUnit(), severity.equals("CRITICAL") ? threshold.getCriticalValue() : threshold.getWarningValue()));
    }

    private void saveAlarm(java.util.UUID machineId, String code, String type, String severity, String message) {
        var alarm = AlarmEventEntity.builder()
                .machineId(machineId).alarmCode(code).alarmType(type)
                .severity(severity).message(message).startedAt(Instant.now())
                .isActive(true).acknowledged(false).build();
        alarmRepo.save(alarm);
        sseRegistry.broadcast("alarm-created", alarm);
        log.info("Alarm [{}] {} for machine {}", severity, code, machineId);
    }

    private Double getMetricValue(NormalizedTelemetryDto dto, String metricCode) {
        return switch (metricCode) {
            case "TEMPERATURE" -> dto.temperatureC();
            case "VIBRATION" -> dto.vibrationMmS();
            case "POWER" -> dto.powerKw();
            default -> null;
        };
    }
}
