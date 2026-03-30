package com.rmsys.backend.api.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MachineOverviewResponse(
        UUID machineId,
        String machineCode,
        String machineName,
        String type,
        String category,
        String vendor,
        String model,
        String lineId,
        String plantId,
        String operationalState,
        String displayState,
        String operationMode,
        String programName,
        Boolean cycleRunning,
        String connectionState,
        Boolean connectionUnstable,
        Instant lastSeenAt,
        Long dataFreshnessSec,
        String connectionReason,
        String connectionScope,
        MachineOverviewTelemetry telemetry,
        MachineOverviewAnalytics analytics,
        MachineOverviewMaintenance maintenance,
        MachineOverviewTool tool
) {}

