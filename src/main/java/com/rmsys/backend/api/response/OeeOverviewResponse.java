package com.rmsys.backend.api.response;

import lombok.Builder;
import java.util.UUID;

@Builder
public record OeeOverviewResponse(
        Double avgAvailability,
        Double avgPerformance,
        Double avgQuality,
        Double avgOee,
        java.time.Instant lastUpdatedAt,
        java.util.List<MachineOeeItem> machines
) {
    @Builder
    public record MachineOeeItem(
            UUID machineId, String machineCode, String machineName, String areaCode,
            Double availability, Double performance, Double quality, Double oee
    ) {}
}

