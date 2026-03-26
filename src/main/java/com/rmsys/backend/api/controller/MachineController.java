package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.MachineDetailResponse;
import com.rmsys.backend.api.response.MachineSnapshotResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.MachineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Machines")
@RestController
@RequestMapping("/api/v1/machines")
@RequiredArgsConstructor
public class MachineController {

    private final MachineService machineService;

    @GetMapping
    @Operation(summary = "List all machines")
    public ApiResponse<List<MachineDetailResponse>> list() {
        return ApiResponse.ok(machineService.getAllMachines());
    }

    @GetMapping("/{machineId}")
    @Operation(summary = "Get machine detail")
    public ApiResponse<MachineDetailResponse> detail(@PathVariable UUID machineId) {
        return ApiResponse.ok(machineService.getMachineDetail(machineId));
    }

    @GetMapping("/{machineId}/latest")
    @Operation(summary = "Get latest telemetry snapshot")
    public ApiResponse<MachineSnapshotResponse> latest(@PathVariable UUID machineId) {
        return ApiResponse.ok(machineService.getLatestSnapshot(machineId));
    }

    @GetMapping("/snapshots")
    @Operation(summary = "Get latest snapshots for all machines")
    public ApiResponse<List<MachineSnapshotResponse>> allSnapshots() {
        return ApiResponse.ok(machineService.getAllLatestSnapshots());
    }
}

