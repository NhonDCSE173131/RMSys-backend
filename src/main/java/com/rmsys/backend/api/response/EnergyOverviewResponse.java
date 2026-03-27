package com.rmsys.backend.api.response;

import lombok.Builder;
import java.time.Instant;
import java.util.UUID;

@Builder
public record EnergyOverviewResponse(
        double totalPowerKw,
        double totalEnergyTodayKwh,
        double totalEnergyMonthKwh,
        double avgPowerFactor,
        double costToday,
        double costMonth,
        Instant lastUpdatedAt,
        java.util.List<MachineEnergyItem> machines
) {
    @Builder
    public record MachineEnergyItem(
            UUID machineId, String machineCode, String machineName, String areaCode,
            Double powerKw, Double voltageV, Double currentA,
            Double powerFactor, Double energyKwhDay, Double energyKwhMonth
    ) {}
}

