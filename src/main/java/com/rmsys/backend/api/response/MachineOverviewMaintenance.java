package com.rmsys.backend.api.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record MachineOverviewMaintenance(
        Integer maintenanceDueDays,
        Double remainingMaintenanceHours,
        Instant nextMaintenanceDate,
        String riskLevel,
        String predictedFailureWindow,
        String recommendation
) {}

