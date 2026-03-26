package com.rmsys.backend.api.response;

import lombok.Builder;
import java.time.Instant;

@Builder
public record TelemetryHistoryPointResponse(
        Instant ts,
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
        Double axisLoadPct,
        Double qualityScore
) {}

