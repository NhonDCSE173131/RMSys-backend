package com.rmsys.backend.api.response;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder
public record AnalyticsTrendPointResponse(
        Instant timestamp,
        Instant bucketEnd,
        Integer sampleCount,
        boolean missing,
        Map<String, Double> metrics
) {}

