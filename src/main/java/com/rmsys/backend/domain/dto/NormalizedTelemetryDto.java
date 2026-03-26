package com.rmsys.backend.domain.dto;

import lombok.Builder;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record NormalizedTelemetryDto(
        UUID machineId,
        Instant ts,
        String connectionStatus,
        String machineState,
        String operationMode,
        String programName,
        Boolean cycleRunning,
        Double powerKw,
        Double temperatureC,
        Double vibrationMmS,
        Double runtimeHours,
        Double cycleTimeSec,
        Integer outputCount,
        Integer goodCount,
        Integer rejectCount,
        Double spindleSpeedRpm,
        Double feedRateMmMin,
        String toolCode,
        Double remainingToolLifePct,
        // Energy fields
        Double voltageV,
        Double currentA,
        Double powerFactor,
        Double frequencyHz,
        Double energyKwhShift,
        Double energyKwhDay,
        // Maintenance fields
        Double motorTemperatureC,
        Double bearingTemperatureC,
        Double cabinetTemperatureC,
        Double servoOnHours,
        Integer startStopCount,
        Double lubricationLevelPct,
        Boolean batteryLow,
        Map<String, Object> metadata
) {}

