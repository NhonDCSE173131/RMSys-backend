package com.rmsys.backend.api.response;

import lombok.Builder;
import java.util.List;

@Builder
public record DashboardOverviewResponse(
        int totalMachines,
        int onlineMachines,
        int runningMachines,
        long criticalAlarms,
        double plantPowerKw,
        double todayEnergyKwh,
        double todayOee,
        long abnormalStops,
        List<RiskMachineItem> topRiskMachines
) {
    @Builder
    public record RiskMachineItem(String machineId, String machineName, String riskLevel, String reason) {}
}

