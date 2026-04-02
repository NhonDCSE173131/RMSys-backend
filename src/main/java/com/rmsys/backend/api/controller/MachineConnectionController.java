package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.MachineConnectionStatusResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.MachineConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Machine Connections", description = "Runtime PLC connection management")
@RestController
@RequestMapping("/api/v1/machine-connections")
@RequiredArgsConstructor
public class MachineConnectionController {

    private final MachineConnectionService connectionService;

    @PostMapping("/{machineId}/test")
    @Operation(summary = "Test connection to a machine's PLC")
    public ApiResponse<MachineConnectionStatusResponse> testConnection(@PathVariable UUID machineId) {
        return ApiResponse.ok(connectionService.testConnection(machineId));
    }

    @PostMapping("/{machineId}/connect")
    @Operation(summary = "Connect to a machine's PLC and start polling")
    public ApiResponse<MachineConnectionStatusResponse> connect(@PathVariable UUID machineId) {
        return ApiResponse.ok(connectionService.connect(machineId), "Connection started");
    }

    @PostMapping("/{machineId}/disconnect")
    @Operation(summary = "Disconnect from a machine's PLC")
    public ApiResponse<MachineConnectionStatusResponse> disconnect(@PathVariable UUID machineId) {
        return ApiResponse.ok(connectionService.disconnect(machineId), "Disconnected");
    }

    @PostMapping("/{machineId}/reconnect")
    @Operation(summary = "Reconnect to a machine's PLC")
    public ApiResponse<MachineConnectionStatusResponse> reconnect(@PathVariable UUID machineId) {
        return ApiResponse.ok(connectionService.reconnect(machineId), "Reconnected");
    }

    @GetMapping("/{machineId}/status")
    @Operation(summary = "Get current runtime connection status")
    public ApiResponse<MachineConnectionStatusResponse> status(@PathVariable UUID machineId) {
        return ApiResponse.ok(connectionService.getStatus(machineId));
    }

    @PostMapping("/start-all")
    @Operation(summary = "Start auto-connect for all machines with autoConnect=true")
    public ApiResponse<Void> startAll() {
        connectionService.startAll();
        return ApiResponse.ok(null, "Auto-connect started for all eligible machines");
    }
}

