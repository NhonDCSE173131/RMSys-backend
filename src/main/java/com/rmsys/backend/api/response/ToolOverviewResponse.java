package com.rmsys.backend.api.response;

import lombok.Builder;
import java.util.UUID;

@Builder
public record ToolOverviewResponse(
        int totalTools,
        int criticalTools,
        int warningTools,
        java.util.List<ToolItem> tools
) {
    @Builder
    public record ToolItem(
            UUID machineId, String machineCode, String machineName, String toolCode, String toolName,
            Double usageMinutes, Integer usageCycles, Double remainingLifePct,
            String wearLevel, String riskLevel
    ) {}
}

