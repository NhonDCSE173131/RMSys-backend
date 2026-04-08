package com.rmsys.backend.infrastructure.adapter.easy;

import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import com.rmsys.backend.infrastructure.adapter.DeviceAdapter;
import com.rmsys.backend.infrastructure.adapter.modbus.ModbusPayloadReader;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Adapter for Easy521 PLC using Modbus TCP protocol.
 * Uses j2mod library for Modbus communication.
 */
@Slf4j
public class Easy521ModbusAdapter implements DeviceAdapter {

    private ModbusTCPMaster master;
    private ModbusPayloadReader payloadReader;
    private boolean connected = false;
    private String host;
    private int port;
    private int unitId;

    @Override
    public boolean connect(MachineEntity machine) {
        this.host = machine.getHost();
        this.port = machine.getPort() != null ? machine.getPort() : 502;
        this.unitId = machine.getUnitId() != null ? machine.getUnitId() : 1;

        try {
            master = new ModbusTCPMaster(host, port);
            master.setRetries(3);
            master.setTimeout(2000);
            master.connect();
            payloadReader = new ModbusPayloadReader(master, unitId);
            connected = true;
            log.info("Easy521ModbusAdapter connected to {}:{} (unit={})", host, port, unitId);
            return true;
        } catch (Exception e) {
            log.error("Failed to connect Easy521 at {}:{}: {}", host, port, e.getMessage());
            connected = false;
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (master != null) {
            try {
                master.disconnect();
            } catch (Exception e) {
                log.warn("Error disconnecting Easy521: {}", e.getMessage());
            }
        }
        connected = false;
        payloadReader = null;
        log.info("Easy521ModbusAdapter disconnected from {}:{}", host, port);
    }

    @Override
    public boolean isConnected() {
        return connected && master != null;
    }

    @Override
    public boolean testConnection(MachineEntity machine) {
        return testConnection(machine, List.of());
    }

    /**
     * Mapping-aware test connection.
     * Priority:
     *  1) required=true mapping with smallest address
     *  2) smallest mapping address
     *  3) fallback holding register 0
     */
    public boolean testConnection(MachineEntity machine, List<MachineProfileMappingEntity> mappings) {
        String testHost = machine.getHost();
        int testPort = machine.getPort() != null ? machine.getPort() : 502;
        int testUnitId = machine.getUnitId() != null ? machine.getUnitId() : 1;

        ModbusTCPMaster testMaster = null;
        try {
            testMaster = new ModbusTCPMaster(testHost, testPort);
            testMaster.setTimeout(3000);
            testMaster.connect();

            MachineProfileMappingEntity probe = selectProbeMapping(mappings);
            if (probe == null) {
                // Fallback for machines without mappings yet
                testMaster.readMultipleRegisters(testUnitId, 0, 1);
                log.info("Test connection to {}:{} succeeded (fallback HOLDING 0)", testHost, testPort);
                return true;
            }

            String area = probe.getArea() != null ? probe.getArea().toUpperCase() : "HOLDING";
            int address = probe.getAddressStart() != null ? probe.getAddressStart() : 0;
            int quantity = registerCountForType(probe.getDataType());

            if ("COIL".equals(area)) {
                testMaster.readCoils(testUnitId, address, 1);
            } else {
                // Use holding read for INPUT/HOLDING as a pragmatic health probe.
                testMaster.readMultipleRegisters(testUnitId, address, quantity);
            }

            log.info("Test connection to {}:{} succeeded (unit={}, area={}, address={}, qty={})",
                    testHost, testPort, testUnitId, area, address, quantity);
            return true;
        } catch (Exception e) {
            log.warn("Test connection to {}:{} failed (unit={}): {}", testHost, testPort, testUnitId, e.getMessage());
            return false;
        } finally {
            if (testMaster != null) {
                try { testMaster.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private MachineProfileMappingEntity selectProbeMapping(List<MachineProfileMappingEntity> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return null;
        }
        return mappings.stream()
                .filter(m -> m.getAddressStart() != null)
                .sorted(Comparator
                        .comparing((MachineProfileMappingEntity m) -> !Boolean.TRUE.equals(m.getIsRequired()))
                        .thenComparing(MachineProfileMappingEntity::getAddressStart))
                .findFirst()
                .orElse(null);
    }

    private int registerCountForType(String dataType) {
        if (dataType == null) {
            return 1;
        }
        String dt = dataType.toUpperCase();
        if ("FLOAT64".equals(dt)) {
            return 4;
        }
        if ("FLOAT32".equals(dt) || "INT32".equals(dt) || "UINT32".equals(dt)) {
            return 2;
        }
        return 1;
    }

    @Override
    public Map<String, Object> readData(List<MachineProfileMappingEntity> mappings) {
        if (!isConnected() || payloadReader == null) {
            log.warn("Cannot read data: not connected");
            return Collections.emptyMap();
        }
        try {
            return payloadReader.readAll(mappings);
        } catch (Exception e) {
            log.error("Error reading data from Easy521 at {}:{}: {}", host, port, e.getMessage());
            connected = false;
            return Collections.emptyMap();
        }
    }

    @Override
    public String getProtocol() {
        return "modbus-tcp";
    }
}

