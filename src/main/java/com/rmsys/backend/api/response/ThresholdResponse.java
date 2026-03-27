package com.rmsys.backend.api.response;

import lombok.Builder;
import java.util.List;

@Builder
public record ThresholdResponse(
        List<ThresholdItem> thresholds
) {
    @Builder
    public record ThresholdItem(
            String machineId, String machineCode, String machineName, String metricCode,
            Double warningValue, Double criticalValue, String unit
    ) {}
}

