package com.rmsys.backend.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder
public record IngestTelemetryByCodeRequest(
        @NotBlank(message = "machineCode is required") String machineCode,
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
        String toolCode,
        @DecimalMin("0.0") @DecimalMax("100.0") Double remainingToolLifePct,
        @PositiveOrZero Double voltageV,
        @PositiveOrZero Double currentA,
        @DecimalMin("0.0") @DecimalMax("1.0") Double powerFactor,
        @PositiveOrZero Double frequencyHz,
        @PositiveOrZero Double energyKwhShift,
        @PositiveOrZero Double energyKwhDay,
        @PositiveOrZero Double motorTemperatureC,
        @PositiveOrZero Double bearingTemperatureC,
        @PositiveOrZero Double cabinetTemperatureC,
        @PositiveOrZero Double servoOnHours,
        @PositiveOrZero Integer startStopCount,
        @DecimalMin("0.0") @DecimalMax("100.0") Double lubricationLevelPct,
        Boolean batteryLow,
        Map<String, Object> metadata
) {
}

