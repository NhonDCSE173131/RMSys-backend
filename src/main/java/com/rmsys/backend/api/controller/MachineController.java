package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.MachineDetailResponse;
import com.rmsys.backend.api.response.MachineOverviewResponse;
import com.rmsys.backend.api.response.MachineRealtimeSnapshotResponse;
import com.rmsys.backend.api.response.MachineSnapshotResponse;
import com.rmsys.backend.api.response.MachineSummaryResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.MachineIdentityResolverService;
import com.rmsys.backend.domain.service.MachineRealtimeSnapshotService;
import com.rmsys.backend.domain.service.MachineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Tag(name = "Machines")
@RestController
@RequestMapping("/api/v1/machines")
@RequiredArgsConstructor
public class MachineController {

    private final MachineService machineService;
    private final MachineIdentityResolverService machineIdentityResolverService;
    private final MachineRealtimeSnapshotService snapshotService;

    @GetMapping
    @Operation(summary = "List all machines")
    public ApiResponse<List<MachineDetailResponse>> list() {
        return ApiResponse.ok(machineService.getAllMachines());
    }

    @GetMapping("/overview")
    @Operation(summary = "Get machine overview payloads for all machines")
    public ApiResponse<List<MachineOverviewResponse>> overviews() {
        return ApiResponse.ok(machineService.getMachineOverviews());
    }

    @GetMapping("/{machineId}")
    @Operation(summary = "Get machine detail")
    public ApiResponse<MachineDetailResponse> detail(@PathVariable String machineId) {
        return ApiResponse.ok(machineService.getMachineDetail(machineIdentityResolverService.resolveRequiredId(machineId)));
    }

    @GetMapping("/{machineId}/latest")
    @Operation(summary = "Get latest telemetry snapshot")
    public ApiResponse<MachineSnapshotResponse> latest(@PathVariable String machineId) {
        return ApiResponse.ok(machineService.getLatestSnapshot(machineIdentityResolverService.resolveRequiredId(machineId)));
    }

    @GetMapping("/{machineId}/summary")
    @Operation(summary = "Get machine summary")
    public ApiResponse<MachineSummaryResponse> summary(@PathVariable String machineId) {
        return ApiResponse.ok(machineService.getMachineSummary(machineIdentityResolverService.resolveRequiredId(machineId)));
    }

    @GetMapping("/snapshots")
    @Operation(summary = "Get latest snapshots for all machines")
    public ApiResponse<List<MachineSnapshotResponse>> allSnapshots() {
        return ApiResponse.ok(machineService.getAllLatestSnapshots());
    }

    @GetMapping("/{machineId}/realtime-snapshot")
    @Operation(summary = "Get canonical realtime snapshot (same shape as SSE)")
    public ApiResponse<MachineRealtimeSnapshotResponse> realtimeSnapshot(@PathVariable String machineId) {
        return ApiResponse.ok(snapshotService.buildSnapshot(machineIdentityResolverService.resolveRequiredId(machineId)));
    }

    @GetMapping("/realtime-snapshots")
    @Operation(summary = "Get canonical realtime snapshots for all machines")
    public ApiResponse<List<MachineRealtimeSnapshotResponse>> allRealtimeSnapshots() {
        return ApiResponse.ok(snapshotService.buildAllSnapshots());
    }
}

