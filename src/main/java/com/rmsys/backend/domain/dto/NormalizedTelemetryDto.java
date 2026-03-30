package com.rmsys.backend.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record NormalizedTelemetryDto(
        @NotNull UUID machineId,
        String machineCode,
        Instant ts,
        String connectionStatus,
        String machineState,
        String operationMode,
        String programName,
        Boolean cycleRunning,
        @PositiveOrZero Double powerKw,
        @PositiveOrZero Double temperatureC,
        @PositiveOrZero Double vibrationMmS,
        @PositiveOrZero Double runtimeHours,
        @PositiveOrZero Double cycleTimeSec,
        @PositiveOrZero Integer outputCount,
        @PositiveOrZero Integer goodCount,
        @PositiveOrZero Integer rejectCount,
        @PositiveOrZero Double spindleSpeedRpm,
        @PositiveOrZero Double feedRateMmMin,
        @PositiveOrZero Double idealCycleTimeSec,
        @DecimalMin("0.0") @DecimalMax("100.0") Double spindleLoadPct,
        @DecimalMin("0.0") @DecimalMax("100.0") Double servoLoadPct,
        @PositiveOrZero Double cuttingSpeedMMin,
        @PositiveOrZero Double depthOfCutMm,
        @PositiveOrZero Double feedPerToothMm,
        @PositiveOrZero Double widthOfCutMm,
        @PositiveOrZero Double materialRemovalRateCm3Min,
        @PositiveOrZero Double weldingCurrentA,
        String toolCode,
        @DecimalMin("0.0") @DecimalMax("100.0") Double remainingToolLifePct,
        // Energy fields
        @PositiveOrZero Double voltageV,
        @PositiveOrZero Double currentA,
        @DecimalMin("0.0") @DecimalMax("1.0") Double powerFactor,
        @PositiveOrZero Double frequencyHz,
        @PositiveOrZero Double energyKwhShift,
        @PositiveOrZero Double energyKwhDay,
        // Maintenance fields
        @PositiveOrZero Double motorTemperatureC,
        @PositiveOrZero Double bearingTemperatureC,
        @PositiveOrZero Double cabinetTemperatureC,
        @PositiveOrZero Double servoOnHours,
        @PositiveOrZero Integer startStopCount,
        @DecimalMin("0.0") @DecimalMax("100.0") Double lubricationLevelPct,
        Boolean batteryLow,
        Map<String, Object> metadata
) {}
