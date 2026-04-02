package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.request.MachineCreateRequest;
import com.rmsys.backend.api.request.MachineUpdateRequest;
import com.rmsys.backend.api.response.MachineConfigResponse;

import java.util.List;
import java.util.UUID;

public interface MachineConfigService {
    MachineConfigResponse createMachine(MachineCreateRequest request);
    MachineConfigResponse updateMachine(UUID machineId, MachineUpdateRequest request);
    MachineConfigResponse getMachineConfig(UUID machineId);
    List<MachineConfigResponse> getAllMachineConfigs();
    void disableMachine(UUID machineId);
    void enableMachine(UUID machineId);
}

