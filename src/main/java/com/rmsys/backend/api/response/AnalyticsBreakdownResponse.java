package com.rmsys.backend.api.response;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record AnalyticsBreakdownResponse(
        String dimension,
        Instant asOf,
        List<BreakdownItem> items
) {
    @Builder
    public record BreakdownItem(
            UUID machineId,
            String machineCode,
            String name,
            String group,
            Map<String, Double> metrics
    ) {}
}

