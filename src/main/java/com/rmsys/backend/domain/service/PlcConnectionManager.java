package com.rmsys.backend.domain.service;

import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineProfileEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;

import java.util.List;
import java.util.UUID;

/**
 * Manages runtime PLC connections.
 * Holds active connections in memory keyed by machineId.
 */
public interface PlcConnectionManager {

    void startConnection(UUID machineId);

    void stopConnection(UUID machineId);

    boolean testConnection(MachineEntity machine);

    String getConnectionStatus(UUID machineId);

    void startAutoConnectAll();

    void stopAll();
}

