package com.rmsys.backend.api.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record IngestTelemetryByCodeRequest(
        @NotBlank(message = "machineCode is required") String machineCode,
        @JsonAlias("sourceTimestamp")
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
        @PositiveOrZero Double voltageV,
        @PositiveOrZero Double currentA,
        @DecimalMin("0.0") @DecimalMax("1.0") Double powerFactor,
        @PositiveOrZero Double frequencyHz,
        @PositiveOrZero Double energyKwhShift,
        @PositiveOrZero Double energyKwhDay,
        @PositiveOrZero Double energyKwhTotal,
        @PositiveOrZero Integer cycleCount,
        @PositiveOrZero Integer partCount,
        @DecimalMin("0.0") @DecimalMax("100.0") Double toolWearPercent,
        @DecimalMin("0.0") @DecimalMax("100.0") Double maintenanceHealthScore,
        List<String> alarmHints,
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

