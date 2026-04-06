package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.request.MachineCreateRequest;
import com.rmsys.backend.api.request.MachineUpdateRequest;
import com.rmsys.backend.api.request.ProfileMappingValidationRequest;
import com.rmsys.backend.api.response.MachineConfigResponse;
import com.rmsys.backend.api.response.ProfileMappingValidationResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.MachineConfigService;
import com.rmsys.backend.domain.service.MachineImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Machine Config", description = "CRUD API for machine configuration")
@RestController
@RequestMapping("/api/v1/machine-configs")
@RequiredArgsConstructor
public class MachineConfigController {

    private final MachineConfigService machineConfigService;
    private final MachineImportService machineImportService;

    @PostMapping
    @Operation(summary = "Create a new machine from form")
    public ApiResponse<MachineConfigResponse> create(@Valid @RequestBody MachineCreateRequest request) {
        return ApiResponse.ok(machineConfigService.createMachine(request), "Machine created successfully");
    }

    @PutMapping("/{machineId}")
    @Operation(summary = "Update machine configuration")
    public ApiResponse<MachineConfigResponse> update(@PathVariable UUID machineId,
                                                     @Valid @RequestBody MachineUpdateRequest request) {
        return ApiResponse.ok(machineConfigService.updateMachine(machineId, request), "Machine updated successfully");
    }

    @GetMapping("/{machineId}")
    @Operation(summary = "Get machine configuration detail")
    public ApiResponse<MachineConfigResponse> getConfig(@PathVariable UUID machineId) {
        return ApiResponse.ok(machineConfigService.getMachineConfig(machineId));
    }

    @GetMapping
    @Operation(summary = "List all machine configurations")
    public ApiResponse<List<MachineConfigResponse>> listConfigs() {
        return ApiResponse.ok(machineConfigService.getAllMachineConfigs());
    }

    @PatchMapping("/{machineId}/disable")
    @Operation(summary = "Disable a machine (stops PLC connection)")
    public ApiResponse<Void> disable(@PathVariable UUID machineId) {
        machineConfigService.disableMachine(machineId);
        return ApiResponse.ok(null, "Machine disabled");
    }

    @PatchMapping("/{machineId}/enable")
    @Operation(summary = "Enable a machine")
    public ApiResponse<Void> enable(@PathVariable UUID machineId) {
        machineConfigService.enableMachine(machineId);
        return ApiResponse.ok(null, "Machine enabled");
    }

    @PostMapping("/validate-profile-mapping")
    @Operation(summary = "Validate profile and mapping file combination")
    public ApiResponse<ProfileMappingValidationResponse> validateProfileMapping(
            @RequestBody ProfileMappingValidationRequest request) {
        return ApiResponse.ok(machineImportService.validateProfileMapping(request.getProfileId(), request.getMappingFileId()),
                "Profile-mapping validation completed");
    }
}
