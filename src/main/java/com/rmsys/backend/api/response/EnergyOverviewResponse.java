package com.rmsys.backend.api.response;

import lombok.Builder;
import java.time.Instant;
import java.util.UUID;

@Builder
public record EnergyOverviewResponse(
        double totalPowerKw,
        double totalEnergyTodayKwh,
        double avgPowerFactor,
        java.util.List<MachineEnergyItem> machines
) {
    @Builder
    public record MachineEnergyItem(
            UUID machineId, String machineName,
            Double powerKw, Double voltageV, Double currentA,
            Double powerFactor, Double energyKwhDay
    ) {}
}

