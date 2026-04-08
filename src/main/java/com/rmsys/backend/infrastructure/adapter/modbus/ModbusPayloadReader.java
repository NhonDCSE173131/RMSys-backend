package com.rmsys.backend.infrastructure.adapter.modbus;

import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.util.BitVector;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Modbus PLC registers/coils based on mapping configuration.
 * Handles different areas: holding_register, input_register, coil, discrete_input.
 */
@Slf4j
public class ModbusPayloadReader {

    private final ModbusTCPMaster master;
    private final int unitId;

    public ModbusPayloadReader(ModbusTCPMaster master, int unitId) {
        this.master = master;
        this.unitId = unitId;
    }

    /**
     * Read all mappings from the PLC and return decoded values.
     */
    public Map<String, Object> readAll(List<MachineProfileMappingEntity> mappings) {
        Map<String, Object> result = new HashMap<>();

        for (MachineProfileMappingEntity mapping : mappings) {
            try {
                Object value = readMapping(mapping);
                result.put(mapping.getLogicalKey(), value);
            } catch (Exception e) {
                log.warn("Failed to read mapping '{}' (area={}, addr={}): {}",
                        mapping.getLogicalKey(), mapping.getArea(), mapping.getAddressStart(), e.getMessage());
            }
        }
        return result;
    }

    private Object readMapping(MachineProfileMappingEntity mapping) throws Exception {
        String area = normalizeArea(mapping.getArea());
        int address = mapping.getAddressStart();

        switch (area) {
            case "holding_register": {
                int count = ModbusValueDecoder.registerCount(mapping.getDataType());
                InputRegister[] regs = master.readMultipleRegisters(unitId, address, count);
                return ModbusValueDecoder.decode(regs, mapping);
            }
            case "input_register": {
                int count = ModbusValueDecoder.registerCount(mapping.getDataType());
                InputRegister[] regs = master.readInputRegisters(unitId, address, count);
                return ModbusValueDecoder.decode(regs, mapping);
            }
            case "coil": {
                BitVector bits = master.readCoils(unitId, address, 1);
                return bits.getBit(0);
            }
            case "discrete_input": {
                BitVector bits = master.readInputDiscretes(unitId, address, 1);
                return bits.getBit(0);
            }
            default:
                log.warn("Unknown area type '{}' for mapping '{}'", area, mapping.getLogicalKey());
                return null;
        }
    }

    private String normalizeArea(String rawArea) {
        if (rawArea == null) {
            return "";
        }
        return switch (rawArea.trim().toLowerCase()) {
            case "holding", "holding_register", "hr" -> "holding_register";
            case "input", "input_register", "ir" -> "input_register";
            case "coil", "coils" -> "coil";
            case "discrete_input", "discrete", "di" -> "discrete_input";
            default -> rawArea.trim().toLowerCase();
        };
    }
}

