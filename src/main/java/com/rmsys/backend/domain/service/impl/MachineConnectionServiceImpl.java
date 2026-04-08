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

        if (isBadConfig(machine)) {
            return MachineConnectionStatusResponse.builder()
                    .machineId(machineId)
                    .machineCode(machine.getCode())
                    .status("BAD_CONFIG")
                    .connected(false)
                    .message("Machine configuration is incomplete")
                    .lastError("Missing protocol/host/port/unitId")
                    .build();
        }

        boolean success = plcConnectionManager.testConnection(machine);

        return MachineConnectionStatusResponse.builder()
                .machineId(machineId)
                .machineCode(machine.getCode())
                .status(success ? "REACHABLE" : "UNREACHABLE")
                .connected(success)
                .message(success ? "Connection test succeeded" : "Connection test failed")
                .lastError(success ? null : "Test connection failed")
                .build();
    }

    @Override
    @Transactional
    public MachineConnectionStatusResponse connect(UUID machineId) {
        MachineEntity machine = findMachine(machineId);
        if (isBadConfig(machine)) {
            return MachineConnectionStatusResponse.builder()
                    .machineId(machineId)
                    .machineCode(machine.getCode())
                    .status("BAD_CONFIG")
                    .connected(false)
                    .message("Machine configuration is incomplete")
                    .lastError("Missing protocol/host/port/unitId")
                    .build();
        }

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
                .status("OFFLINE")
                .connected(false)
                .message("Disconnected")
                .lastDisconnectedAt(Instant.now())
                .build();
    }

    @Override
    @Transactional
    public MachineConnectionStatusResponse reconnect(UUID machineId) {
        MachineEntity machine = findMachine(machineId);
        if (isBadConfig(machine)) {
            return MachineConnectionStatusResponse.builder()
                    .machineId(machineId)
                    .machineCode(machine.getCode())
                    .status("BAD_CONFIG")
                    .connected(false)
                    .message("Machine configuration is incomplete")
                    .lastError("Missing protocol/host/port/unitId")
                    .build();
        }

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
                .status(normalizeRuntimeStatus(status))
                .connected(isConnectedStatus(status))
                .message(buildMessage(status))
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

    private boolean isBadConfig(MachineEntity machine) {
        if (machine.getProtocol() == null || machine.getProtocol().isBlank()) {
            return true;
        }
        if ("simulator".equalsIgnoreCase(machine.getProtocol())) {
            return false;
        }
        if (machine.getHost() == null || machine.getHost().isBlank()) {
            return true;
        }
        if (machine.getPort() == null || machine.getPort() <= 0) {
            return true;
        }
        return machine.getUnitId() == null || machine.getUnitId() <= 0;
    }

    private MachineConnectionStatusResponse buildStatusResponse(UUID machineId, String code, String status) {
        MachineEntity machine = machineRepo.findById(machineId).orElse(null);
        return MachineConnectionStatusResponse.builder()
                .machineId(machineId)
                .machineCode(code)
                .status(normalizeRuntimeStatus(status))
                .connected(isConnectedStatus(status))
                .message(buildMessage(status))
                .lastConnectedAt(machine != null ? machine.getLastConnectedAt() : null)
                .lastDisconnectedAt(machine != null ? machine.getLastDisconnectedAt() : null)
                .lastDataAt(machine != null ? machine.getLastDataAt() : null)
                .lastError(machine != null ? machine.getLastConnectionReasonDetail() : null)
                .build();
    }

    private String normalizeRuntimeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "OFFLINE";
        }
        String s = status.toUpperCase();
        if (PlcConnectionStatus.DISCONNECTED.name().equals(s) || PlcConnectionStatus.DISABLED.name().equals(s)) {
            return "OFFLINE";
        }
        return s;
    }

    private boolean isConnectedStatus(String status) {
        return PlcConnectionStatus.ONLINE.name().equalsIgnoreCase(status);
    }

    private String buildMessage(String status) {
        String normalized = normalizeRuntimeStatus(status);
        return switch (normalized) {
            case "ONLINE" -> "Machine is connected";
            case "STALE" -> "Machine connected but data is stale";
            case "ERROR" -> "Machine connection error";
            case "BAD_CONFIG" -> "Machine configuration is invalid";
            default -> "Machine is offline";
        };
    }
}

