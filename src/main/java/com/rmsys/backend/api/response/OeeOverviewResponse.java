package com.rmsys.backend.api.response;

import lombok.Builder;
import java.util.UUID;

@Builder
public record OeeOverviewResponse(
        double avgAvailability,
        double avgPerformance,
        double avgQuality,
        double avgOee,
        java.util.List<MachineOeeItem> machines
) {
    @Builder
    public record MachineOeeItem(
            UUID machineId, String machineName,
            Double availability, Double performance, Double quality, Double oee
    ) {}
}

