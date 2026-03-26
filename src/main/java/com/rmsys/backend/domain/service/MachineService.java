package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.MachineDetailResponse;
import com.rmsys.backend.api.response.MachineSnapshotResponse;
import com.rmsys.backend.api.response.TelemetrySeriesResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MachineService {
    List<MachineDetailResponse> getAllMachines();
    MachineDetailResponse getMachineDetail(UUID machineId);
    MachineSnapshotResponse getLatestSnapshot(UUID machineId);
    List<MachineSnapshotResponse> getAllLatestSnapshots();
    TelemetrySeriesResponse getTelemetryHistory(UUID machineId, Instant from, Instant to, String interval, String aggregation);
}

