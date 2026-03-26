package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.MachineDetailResponse;
import com.rmsys.backend.api.response.MachineSnapshotResponse;
import java.util.List;
import java.util.UUID;

public interface MachineService {
    List<MachineDetailResponse> getAllMachines();
    MachineDetailResponse getMachineDetail(UUID machineId);
    MachineSnapshotResponse getLatestSnapshot(UUID machineId);
    List<MachineSnapshotResponse> getAllLatestSnapshots();
}

