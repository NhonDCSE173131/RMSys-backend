package com.rmsys.backend.infrastructure.adapter.modbus;

import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility for decoding Modbus register values into Java types
 * based on mapping configuration (data_type, byte_order, word_order).
 */
@Slf4j
public class ModbusValueDecoder {

    /**
     * Decode a value from Modbus registers based on mapping config.
     *
     * @param registers raw registers read from Modbus
     * @param mapping   mapping config that describes the data type and endianness
     * @return decoded value
     */
    public static Object decode(InputRegister[] registers, MachineProfileMappingEntity mapping) {
        if (registers == null || registers.length == 0) return null;

        String dataType = mapping.getDataType().toLowerCase();
        ByteOrder byteOrder = "LITTLE".equalsIgnoreCase(mapping.getByteOrder())
                ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        boolean wordSwap = "LITTLE".equalsIgnoreCase(mapping.getWordOrder());

        switch (dataType) {
            case "bool":
                return registers[0].getValue() != 0;
            case "uint16":
                return registers[0].getValue() & 0xFFFF;
            case "int16":
                return (short) registers[0].getValue();
            case "uint32":
                return decodeUint32(registers, wordSwap);
            case "int32":
                return decodeInt32(registers, wordSwap);
            case "float32":
                return decodeFloat32(registers, wordSwap);
            default:
                log.warn("Unknown data type '{}', returning raw int", dataType);
                return registers[0].getValue();
        }
    }

    private static long decodeUint32(InputRegister[] regs, boolean wordSwap) {
        if (regs.length < 2) return regs[0].getValue() & 0xFFFFL;
        int hi = wordSwap ? regs[1].getValue() : regs[0].getValue();
        int lo = wordSwap ? regs[0].getValue() : regs[1].getValue();
        return ((long) (hi & 0xFFFF) << 16) | (lo & 0xFFFFL);
    }

    private static int decodeInt32(InputRegister[] regs, boolean wordSwap) {
        return (int) decodeUint32(regs, wordSwap);
    }

    private static float decodeFloat32(InputRegister[] regs, boolean wordSwap) {
        if (regs.length < 2) return 0f;
        int hi = wordSwap ? regs[1].getValue() : regs[0].getValue();
        int lo = wordSwap ? regs[0].getValue() : regs[1].getValue();
        int bits = ((hi & 0xFFFF) << 16) | (lo & 0xFFFF);
        return Float.intBitsToFloat(bits);
    }

    /**
     * Calculate number of registers needed for a given data type.
     */
    public static int registerCount(String dataType) {
        switch (dataType.toLowerCase()) {
            case "bool":
            case "uint16":
            case "int16":
                return 1;
            case "uint32":
            case "int32":
            case "float32":
                return 2;
            default:
                return 1;
        }
    }
}

