package com.rmsys.backend.api.response;

import lombok.Builder;
import java.time.Instant;
import java.util.UUID;

@Builder
public record MachineSnapshotResponse(
        UUID machineId,
        String machineCode,
        String machineName,
        Instant ts,
        String connectionStatus,
        String connectionState,
        Boolean connectionUnstable,
        Instant lastSeenAt,
        Long dataFreshnessSec,
        String machineState,
        String operationMode,
        String programName,
        Boolean cycleRunning,
        Double powerKw,
        Double temperatureC,
        Double vibrationMmS,
        Double runtimeHours,
        Double cycleTimeSec,
        Integer outputCount,
        Integer goodCount,
        Integer rejectCount,
        Double spindleSpeedRpm,
        Double feedRateMmMin
) {}

