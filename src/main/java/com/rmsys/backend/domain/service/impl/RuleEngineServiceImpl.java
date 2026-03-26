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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineServiceImpl implements RuleEngineService {

    private final MachineThresholdRepository thresholdRepo;
    private final AlarmEventRepository alarmRepo;
    private final SseEmitterRegistry sseRegistry;

    @Override
    @Transactional
    public void evaluate(NormalizedTelemetryDto dto) {
        var thresholds = thresholdRepo.findByMachineId(dto.machineId());

        for (var threshold : thresholds) {
            evaluateThreshold(dto, threshold);
        }

        evaluateToolLife(dto);
    }

    private void evaluateThreshold(NormalizedTelemetryDto dto, MachineThresholdEntity threshold) {
        Double value = getMetricValue(dto, threshold.getMetricCode());
        if (value == null) {
            return;
        }

        var criticalCode = threshold.getMetricCode() + "_CRITICAL";
        var warningCode = threshold.getMetricCode() + "_WARNING";

        if (threshold.getCriticalValue() != null && value >= threshold.getCriticalValue()) {
            closeAlarmIfActive(dto.machineId(), warningCode);
            openAlarmIfAbsent(
                    dto.machineId(),
                    criticalCode,
                    "THRESHOLD",
                    "CRITICAL",
                    "%s critical: %.2f %s (limit: %.2f)".formatted(
                            threshold.getMetricCode(),
                            value,
                            threshold.getUnit(),
                            threshold.getCriticalValue()));
            return;
        }

        if (threshold.getWarningValue() != null && value >= threshold.getWarningValue()) {
            closeAlarmIfActive(dto.machineId(), criticalCode);
            openAlarmIfAbsent(
                    dto.machineId(),
                    warningCode,
                    "THRESHOLD",
                    "WARNING",
                    "%s warning: %.2f %s (limit: %.2f)".formatted(
                            threshold.getMetricCode(),
                            value,
                            threshold.getUnit(),
                            threshold.getWarningValue()));
            return;
        }

        // Recover both severities when metric returns below warning threshold.
        closeAlarmIfActive(dto.machineId(), warningCode);
        closeAlarmIfActive(dto.machineId(), criticalCode);
    }

    private void evaluateToolLife(NormalizedTelemetryDto dto) {
        var value = dto.remainingToolLifePct();
        var criticalCode = "TOOL_LIFE_CRITICAL";
        var warningCode = "TOOL_LIFE_WARNING";

        if (value == null) {
            return;
        }

        var toolCode = dto.toolCode() != null ? dto.toolCode() : "N/A";

        if (value < 10) {
            closeAlarmIfActive(dto.machineId(), warningCode);
            openAlarmIfAbsent(dto.machineId(), criticalCode, "TOOL", "CRITICAL",
                    "Tool %s life critical: %.1f%% remaining".formatted(toolCode, value));
            return;
        }

        if (value < 20) {
            closeAlarmIfActive(dto.machineId(), criticalCode);
            openAlarmIfAbsent(dto.machineId(), warningCode, "TOOL", "WARNING",
                    "Tool %s life low: %.1f%% remaining".formatted(toolCode, value));
            return;
        }

        closeAlarmIfActive(dto.machineId(), warningCode);
        closeAlarmIfActive(dto.machineId(), criticalCode);
    }

    private void openAlarmIfAbsent(UUID machineId, String code, String type, String severity, String message) {
        var existing = alarmRepo.findTopByMachineIdAndAlarmCodeAndIsActiveTrue(machineId, code);
        if (existing.isPresent()) {
            return;
        }

        var alarm = AlarmEventEntity.builder()
                .machineId(machineId)
                .alarmCode(code)
                .alarmType(type)
                .severity(severity)
                .message(message)
                .startedAt(Instant.now())
                .isActive(true)
                .acknowledged(false)
                .build();

        alarmRepo.save(alarm);
        sseRegistry.broadcast("alarm-created", alarm);
        log.info("Alarm [{}] {} for machine {}", severity, code, machineId);
    }

    private void closeAlarmIfActive(UUID machineId, String code) {
        var existing = alarmRepo.findTopByMachineIdAndAlarmCodeAndIsActiveTrue(machineId, code);
        if (existing.isEmpty()) {
            return;
        }

        var activeAlarm = existing.get();
        activeAlarm.setIsActive(false);
        activeAlarm.setEndedAt(Instant.now());
        alarmRepo.save(activeAlarm);
        sseRegistry.broadcast("alarm-resolved", activeAlarm);
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
