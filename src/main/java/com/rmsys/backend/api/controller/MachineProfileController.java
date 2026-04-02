package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.request.MachineMappingItemRequest;
import com.rmsys.backend.api.request.MachineProfileCreateRequest;
import com.rmsys.backend.api.response.MachineProfileResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.MachineProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Machine Profiles", description = "CRUD API for machine profiles and mappings")
@RestController
@RequestMapping("/api/v1/machine-profiles")
@RequiredArgsConstructor
public class MachineProfileController {

    private final MachineProfileService profileService;

    @PostMapping
    @Operation(summary = "Create a new machine profile")
    public ApiResponse<MachineProfileResponse> create(@Valid @RequestBody MachineProfileCreateRequest request) {
        return ApiResponse.ok(profileService.createProfile(request), "Profile created successfully");
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "Get profile with mappings")
    public ApiResponse<MachineProfileResponse> getProfile(@PathVariable UUID profileId) {
        return ApiResponse.ok(profileService.getProfile(profileId));
    }

    @GetMapping
    @Operation(summary = "List all profiles")
    public ApiResponse<List<MachineProfileResponse>> listProfiles() {
        return ApiResponse.ok(profileService.getAllProfiles());
    }

    @PutMapping("/{profileId}/mappings")
    @Operation(summary = "Replace all mappings for a profile")
    public ApiResponse<MachineProfileResponse> updateMappings(@PathVariable UUID profileId,
                                                               @Valid @RequestBody List<MachineMappingItemRequest> mappings) {
        return ApiResponse.ok(profileService.updateMappings(profileId, mappings), "Mappings updated successfully");
    }

    @DeleteMapping("/{profileId}")
    @Operation(summary = "Delete a profile and its mappings")
    public ApiResponse<Void> deleteProfile(@PathVariable UUID profileId) {
        profileService.deleteProfile(profileId);
        return ApiResponse.ok(null, "Profile deleted");
    }
}

