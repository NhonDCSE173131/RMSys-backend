package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.MachineConnectionStatusResponse;

import java.util.UUID;

public interface MachineConnectionService {
    MachineConnectionStatusResponse testConnection(UUID machineId);
    MachineConnectionStatusResponse connect(UUID machineId);
    MachineConnectionStatusResponse disconnect(UUID machineId);
    MachineConnectionStatusResponse reconnect(UUID machineId);
    MachineConnectionStatusResponse getStatus(UUID machineId);
    void startAll();
}

