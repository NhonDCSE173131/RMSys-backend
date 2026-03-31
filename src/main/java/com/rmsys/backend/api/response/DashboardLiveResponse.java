package com.rmsys.backend.api.response;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record DashboardLiveResponse(
        double totalPowerKw,
        double totalEnergyTodayKwh,
        Double avgOeeRolling,
        int runningMachines,
        int idleMachines,
        int faultMachines,
        int offlineMachines,
        long criticalAlarms,
        long warningAlarms,
        long abnormalStops,
        long totalProduction,
        long totalGood,
        long totalReject,
        List<RiskSummaryItem> maintenanceRiskSummary,
        Instant lastUpdatedAt
) {
    @Builder
    public record RiskSummaryItem(UUID machineId, String machineCode, String riskLevel, Double healthScore) {}
}

