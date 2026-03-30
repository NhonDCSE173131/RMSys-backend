package com.rmsys.backend.api.response;

import lombok.Builder;
import java.time.Instant;

@Builder
public record TelemetryHistoryPointResponse(
        Instant ts,
        Instant bucketEnd,
        String machineState,
        String connectionStatus,
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
        Double idealCycleTimeSec,
        Double spindleLoadPct,
        Double servoLoadPct,
        Double cuttingSpeedMMin,
        Double depthOfCutMm,
        Double feedPerToothMm,
        Double widthOfCutMm,
        Double materialRemovalRateCm3Min,
        Double weldingCurrentA,
        Double axisLoadPct,
        Double qualityScore,
        Integer sampleCount,
        boolean missing,
        boolean gapDetected
) {}

