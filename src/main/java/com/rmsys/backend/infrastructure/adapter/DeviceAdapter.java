package com.rmsys.backend.infrastructure.adapter;

import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;

import java.util.List;
import java.util.Map;

/**
 * Interface for device adapters that connect to PLC/devices and read data.
 */
public interface DeviceAdapter {

    /**
     * Connect to the device.
     * @return true if connection successful
     */
    boolean connect(MachineEntity machine);

    /**
     * Disconnect from the device.
     */
    void disconnect();

    /**
     * Check if currently connected.
     */
    boolean isConnected();

    /**
     * Test connectivity without maintaining connection.
     * @return true if the device is reachable
     */
    boolean testConnection(MachineEntity machine);

    /**
     * Read data from the device according to mappings.
     * @param mappings the profile mappings defining what to read
     * @return map of logical_key -> decoded value
     */
    Map<String, Object> readData(List<MachineProfileMappingEntity> mappings);

    /**
     * Get the protocol this adapter handles.
     */
    String getProtocol();
}

