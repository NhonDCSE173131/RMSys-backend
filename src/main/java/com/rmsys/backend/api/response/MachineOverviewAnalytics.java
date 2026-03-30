package com.rmsys.backend.api.response;

import lombok.Builder;

@Builder
public record MachineOverviewAnalytics(
        Double oee,
        Double availability,
        Double performance,
        Double quality,
        Double machineHealth,
        Double anomalyScore
) {}

