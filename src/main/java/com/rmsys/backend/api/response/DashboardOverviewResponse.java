package com.rmsys.backend.api.response;

import lombok.Builder;
import java.util.List;

@Builder
public record DashboardOverviewResponse(
        int totalMachines,
        int onlineMachines,
        int offlineMachines,
        int runningMachines,
        int idleMachines,
        int faultMachines,
        long criticalAlarms,
        long warningAlarms,
        double plantPowerKw,
        double todayEnergyKwh,
        double todayOee,
        long abnormalStops,
        long totalProduction,
        long totalGood,
        long totalReject,
        List<RiskMachineItem> topRiskMachines,
        List<AreaSummaryItem> areaSummaries,
        java.time.Instant lastUpdatedAt
) {
    @Builder
    public record RiskMachineItem(java.util.UUID machineId, String machineCode, String machineName, String riskLevel, String reason) {}

    @Builder
    public record AreaSummaryItem(
            String areaCode,
            int totalMachines,
            int onlineMachines,
            double powerKw,
            double energyTodayKwh,
            double avgOee
    ) {}
}

