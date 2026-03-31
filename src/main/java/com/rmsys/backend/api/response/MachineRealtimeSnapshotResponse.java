package com.rmsys.backend.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical realtime snapshot for a single machine.
 * Used as the single shape for:
 * - REST overview/snapshot endpoints
 * - SSE machine-snapshot-updated event payload
 * - Chart seed data
 */
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MachineRealtimeSnapshotResponse(
        UUID machineId,
        String machineCode,
        String machineName,
        Instant ts,

        // Connection
        String connectionState,
        Boolean connectionUnstable,
        Instant lastSeenAt,
        Long dataFreshnessSec,
        Boolean liveDataAvailable,

        // Operational state
        String operationalState,
        String displayState,
        String operationMode,
        String programName,
        Boolean cycleRunning,

        // Core telemetry
        Double powerKw,
        Double temperatureC,
        Double vibrationMmS,
        Double runtimeHours,
        Double cycleTimeSec,
        Double idealCycleTimeSec,
        Integer outputCount,
        Integer goodCount,
        Integer rejectCount,
        Double spindleSpeedRpm,
        Double feedRateMmMin,
        Double spindleLoadPct,
        Double servoLoadPct,
        Double cuttingSpeedMMin,
        Double depthOfCutMm,
        Double feedPerToothMm,
        Double widthOfCutMm,
        Double materialRemovalRateCm3Min,
        Double weldingCurrentA,

        // Energy
        Double voltageV,
        Double currentA,
        Double powerFactor,
        Double frequencyHz,
        Double energyKwhShift,
        Double energyKwhDay,

        // Tool
        Double remainingToolLifePct,
        String wearLevel,

        // OEE rolling
        Double oee,
        Double availability,
        Double performance,
        Double quality,

        // Health & Maintenance
        Double machineHealth,
        Double anomalyScore,
        Integer maintenanceDueDays,
        Double remainingMaintenanceHours,
        Instant nextMaintenanceDate,
        String maintenanceRisk,
        String predictedFailureWindow,
        String recommendation
) {}

