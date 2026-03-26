package com.rmsys.backend.api.response;

import lombok.Builder;
import java.time.Instant;
import java.util.UUID;

@Builder
public record MaintenanceOverviewResponse(
        int totalMachines,
        int dueSoonCount,
        double avgHealthScore,
        java.util.List<MachineMaintenanceItem> machines
) {
    @Builder
    public record MachineMaintenanceItem(
            UUID machineId, String machineName,
            Double runtimeHours, Double healthScore, String riskLevel,
            Double remainingHoursToService, Instant nextMaintenanceDate, String recommendedAction
    ) {}
}

