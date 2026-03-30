package com.rmsys.backend.api.response;

import lombok.Builder;

@Builder
public record MachineOverviewTelemetry(
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
        Double voltageV,
        Double currentA,
        Double powerFactor,
        Double frequencyHz,
        Double energyKwhShift,
        Double energyKwhDay,
        Double motorTemperatureC,
        Double bearingTemperatureC,
        Double cabinetTemperatureC,
        Double servoOnHours,
        Integer startStopCount,
        Double lubricationLevelPct,
        Boolean batteryLow,
        Double idealCycleTimeSec,
        Double spindleLoadPct,
        Double servoLoadPct,
        Double cuttingSpeedMMin,
        Double depthOfCutMm,
        Double feedPerToothMm,
        Double widthOfCutMm,
        Double materialRemovalRateCm3Min,
        Double weldingCurrentA
) {}

