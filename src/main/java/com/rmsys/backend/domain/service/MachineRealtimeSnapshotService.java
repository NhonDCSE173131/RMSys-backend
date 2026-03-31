package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.MachineRealtimeSnapshotResponse;

import java.util.List;
import java.util.UUID;

/**
 * Builds canonical realtime snapshots combining telemetry, OEE rolling,
 * health, maintenance prediction, and tool usage into a single shape.
 */
public interface MachineRealtimeSnapshotService {

    /**
     * Build a canonical snapshot for a specific machine, using in-memory
     * rolling KPIs and the latest persisted data.
     */
    MachineRealtimeSnapshotResponse buildSnapshot(UUID machineId);

    /**
     * Build canonical snapshots for all machines.
     */
    List<MachineRealtimeSnapshotResponse> buildAllSnapshots();
}

