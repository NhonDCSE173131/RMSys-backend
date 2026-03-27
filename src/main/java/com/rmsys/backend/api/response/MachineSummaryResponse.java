package com.rmsys.backend.api.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MachineSummaryResponse(
        UUID machineId,
        String machineCode,
        String machineName,
        MachineSnapshotResponse latestSnapshot,
        long activeAlarms,
        long alarmHistoryCount,
        long activeDowntimes,
        long abnormalStopsToday,
        Double availability,
        Double performance,
        Double quality,
        Double latestOee,
        Double healthScore,
        String riskLevel,
        String riskReason,
        Double remainingHoursToService,
        Instant nextMaintenanceDate,
        String toolCode,
        Double remainingToolLifePct,
        Instant lastUpdatedAt
) {}

