package com.rmsys.backend.infrastructure.adapter.simulator;

import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import com.rmsys.backend.infrastructure.adapter.DeviceAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulator adapter that generates fake telemetry data.
 * Used for testing and development when no real PLC is available.
 */
@Slf4j
public class SimulatorAdapter implements DeviceAdapter {

    private boolean connected = false;

    @Override
    public boolean connect(MachineEntity machine) {
        log.info("SimulatorAdapter connected for machine {}", machine.getCode());
        connected = true;
        return true;
    }

    @Override
    public void disconnect() {
        connected = false;
        log.info("SimulatorAdapter disconnected");
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean testConnection(MachineEntity machine) {
        return true; // Simulator always succeeds
    }

    @Override
    public Map<String, Object> readData(List<MachineProfileMappingEntity> mappings) {
        Map<String, Object> data = new HashMap<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (MachineProfileMappingEntity mapping : mappings) {
            String key = mapping.getLogicalKey();
            switch (mapping.getDataType().toLowerCase()) {
                case "bool":
                    data.put(key, rng.nextBoolean());
                    break;
                case "uint16":
                case "int16":
                    data.put(key, rng.nextInt(0, 1000));
                    break;
                case "uint32":
                case "int32":
                    data.put(key, rng.nextInt(0, 100000));
                    break;
                case "float32":
                    data.put(key, Math.round(rng.nextDouble(0, 100) * 100.0) / 100.0);
                    break;
                default:
                    data.put(key, rng.nextInt(0, 500));
            }
        }
        return data;
    }

    @Override
    public String getProtocol() {
        return "simulator";
    }
}

