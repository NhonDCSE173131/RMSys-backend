package com.rmsys.backend.api.response;

import lombok.Builder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record TelemetrySeriesResponse(
        UUID machineId,
        String machineCode,
        String machineName,
        Instant from,
        Instant to,
        String interval,
        String aggregation,
        List<String> requestedMetrics,
        int totalPoints,
        List<TelemetryHistoryPointResponse> points
) {}

