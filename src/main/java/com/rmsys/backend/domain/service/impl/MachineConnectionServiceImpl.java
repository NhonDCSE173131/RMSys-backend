package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.MachineConnectionStatusResponse;
import com.rmsys.backend.common.enumtype.PlcConnectionStatus;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.MachineConnectionService;
import com.rmsys.backend.domain.service.PlcConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MachineConnectionServiceImpl implements MachineConnectionService {

    private final MachineRepository machineRepo;
    private final PlcConnectionManager plcConnectionManager;

    @Override
    @Transactional
    public MachineConnectionStatusResponse testConnection(UUID machineId) {
        MachineEntity machine = findMachine(machineId);

        if (machine.getHost() == null || machine.getHost().isBlank()) {
            throw new AppException("MISSING_HOST", "Machine host is not configured");
        }

        boolean success = plcConnectionManager.testConnection(machine);

        return MachineConnectionStatusResponse.builder()
                .machineId(machineId)
                .machineCode(machine.getCode())
                .status(success ? "REACHABLE" : "UNREACHABLE")
                .lastError(success ? null : "Test connection failed")
                .build();
    }

    @Override
    @Transactional
    public MachineConnectionStatusResponse connect(UUID machineId) {
        MachineEntity machine = findMachine(machineId);
        validateConnectionConfig(machine);

        plcConnectionManager.startConnection(machineId);

        String status = plcConnectionManager.getConnectionStatus(machineId);
        return buildStatusResponse(machineId, machine.getCode(), status);
    }

    @Override
    @Transactional
    public MachineConnectionStatusResponse disconnect(UUID machineId) {
        MachineEntity machine = findMachine(machineId);

        plcConnectionManager.stopConnection(machineId);

        return MachineConnectionStatusResponse.builder()
                .machineId(machineId)
                .machineCode(machine.getCode())
                .status(PlcConnectionStatus.DISCONNECTED.name())
                .lastDisconnectedAt(Instant.now())
                .build();
    }

    @Override
    @Transactional
    public MachineConnectionStatusResponse reconnect(UUID machineId) {
        MachineEntity machine = findMachine(machineId);
        validateConnectionConfig(machine);

        // Stop then start
        plcConnectionManager.stopConnection(machineId);
        plcConnectionManager.startConnection(machineId);

        String status = plcConnectionManager.getConnectionStatus(machineId);
        return buildStatusResponse(machineId, machine.getCode(), status);
    }

    @Override
    @Transactional(readOnly = true)
    public MachineConnectionStatusResponse getStatus(UUID machineId) {
        MachineEntity machine = findMachine(machineId);
        String status = plcConnectionManager.getConnectionStatus(machineId);

        return MachineConnectionStatusResponse.builder()
                .machineId(machineId)
                .machineCode(machine.getCode())
                .status(status)
                .lastConnectedAt(machine.getLastConnectedAt())
                .lastDisconnectedAt(machine.getLastDisconnectedAt())
                .lastDataAt(machine.getLastDataAt())
                .lastError(machine.getLastConnectionReasonDetail())
                .build();
    }

    @Override
    public void startAll() {
        plcConnectionManager.startAutoConnectAll();
    }

    // ---- helpers ----

    private MachineEntity findMachine(UUID machineId) {
        return machineRepo.findById(machineId)
                .orElseThrow(() -> AppException.notFound("Machine", machineId));
    }

    private void validateConnectionConfig(MachineEntity machine) {
        if (machine.getProtocol() == null || machine.getProtocol().isBlank()) {
            throw new AppException("MISSING_PROTOCOL", "Machine protocol is not configured");
        }
        if (!"simulator".equalsIgnoreCase(machine.getProtocol())) {
            if (machine.getHost() == null || machine.getHost().isBlank()) {
                throw new AppException("MISSING_HOST", "Machine host is not configured");
            }
        }
    }

    private MachineConnectionStatusResponse buildStatusResponse(UUID machineId, String code, String status) {
        MachineEntity machine = machineRepo.findById(machineId).orElse(null);
        return MachineConnectionStatusResponse.builder()
                .machineId(machineId)
                .machineCode(code)
                .status(status)
                .lastConnectedAt(machine != null ? machine.getLastConnectedAt() : null)
                .lastDisconnectedAt(machine != null ? machine.getLastDisconnectedAt() : null)
                .lastDataAt(machine != null ? machine.getLastDataAt() : null)
                .lastError(machine != null ? machine.getLastConnectionReasonDetail() : null)
                .build();
    }
}

