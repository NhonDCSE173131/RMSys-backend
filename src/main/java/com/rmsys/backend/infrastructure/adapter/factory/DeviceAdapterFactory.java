package com.rmsys.backend.infrastructure.adapter.factory;

import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.infrastructure.adapter.DeviceAdapter;
import com.rmsys.backend.infrastructure.adapter.easy.Easy521ModbusAdapter;
import com.rmsys.backend.infrastructure.adapter.simulator.SimulatorAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Factory that creates the appropriate DeviceAdapter based on machine config.
 * Selection rules:
 *  - protocol=simulator-internal (or legacy simulator) -> SimulatorAdapter
 *  - protocol=simulator-http -> no polling adapter (ingest-only mode)
 *  - protocol=modbus-tcp + vendor=Easy + model=521 -> Easy521ModbusAdapter
 *  - protocol=modbus-tcp (generic) -> Easy521ModbusAdapter (default Modbus)
 */
@Slf4j
@Component
public class DeviceAdapterFactory {

    public DeviceAdapter createAdapter(MachineEntity machine) {
        String protocol = machine.getProtocol();
        if (protocol == null) {
            log.warn("No protocol configured for machine {}, defaulting to simulator", machine.getCode());
            return new SimulatorAdapter();
        }

        switch (protocol.toLowerCase()) {
            case "simulator":
            case "simulator-internal":
                return new SimulatorAdapter();
            case "simulator-http":
                log.warn("protocol=simulator-http is ingest-only, falling back to SimulatorAdapter for safety on machine {}",
                        machine.getCode());
                return new SimulatorAdapter();
            case "modbus-tcp":
                return createModbusAdapter(machine);
            default:
                log.warn("Unknown protocol '{}' for machine {}, defaulting to simulator",
                        protocol, machine.getCode());
                return new SimulatorAdapter();
        }
    }

    private DeviceAdapter createModbusAdapter(MachineEntity machine) {
        String vendor = machine.getVendor();
        String model = machine.getModel();

        // For Easy521 or generic modbus, use Easy521ModbusAdapter
        if ("Easy".equalsIgnoreCase(vendor) && "521".equals(model)) {
            log.info("Creating Easy521ModbusAdapter for machine {}", machine.getCode());
            return new Easy521ModbusAdapter();
        }

        // Default: generic Modbus adapter (using Easy521 as base)
        log.info("Creating generic ModbusAdapter for machine {} (vendor={}, model={})",
                machine.getCode(), vendor, model);
        return new Easy521ModbusAdapter();
    }
}

