package com.rmsys.backend.api.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record IngestTelemetryRequest(
        UUID machineId,
        String machineCode,
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
        Map<String, Object> metadata,
        @PositiveOrZero Double energyKwhTotal,
        @PositiveOrZero Integer cycleCount,
        @PositiveOrZero Integer partCount,
        @DecimalMin("0.0") @DecimalMax("100.0") Double toolWearPercent,
        @DecimalMin("0.0") @DecimalMax("100.0") Double maintenanceHealthScore,
        List<String> alarmHints
) {
    @AssertTrue(message = "machineId or machineCode is required")
    public boolean hasMachineIdentifier() {
        return machineId != null || (machineCode != null && !machineCode.isBlank());
    }
}

