package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.request.MachineConfigImportRequest;
import com.rmsys.backend.api.request.MachineMappingImportRequest;
import com.rmsys.backend.api.request.MachineProfileImportRequest;
import com.rmsys.backend.api.response.ImportFileResponse;
import com.rmsys.backend.api.response.MachineImportResultResponse;
import com.rmsys.backend.common.enumtype.ImportMode;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.MachineImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Machine Import", description = "Import machine config, profiles, and mappings from CSV or JSON")
@RestController
@RequestMapping("/api/v1/machine-imports")
@RequiredArgsConstructor
public class MachineImportController {

    private final MachineImportService importService;

    // ========== CSV IMPORT ENDPOINTS ==========

    @PostMapping(value = "/machines", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import machine configurations from CSV file")
    public ApiResponse<MachineImportResultResponse> importMachines(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "CREATE_ONLY") ImportMode mode) {
        return ApiResponse.ok(importService.importMachines(file, mode), "Machine import completed");
    }

    @PostMapping(value = "/profiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import profiles from CSV file")
    public ApiResponse<MachineImportResultResponse> importProfiles(
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.importProfiles(file), "Profile import completed");
    }

    @PostMapping(value = "/mappings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import profile mappings from CSV file")
    public ApiResponse<MachineImportResultResponse> importMappings(
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.importMappings(file), "Mapping import completed");
    }

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Validate CSV file without committing (dry run)")
    public ApiResponse<MachineImportResultResponse> validate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String importType) {
        return ApiResponse.ok(importService.validateFile(file, importType), "Validation completed");
    }

    // ========== JSON IMPORT ENDPOINTS ==========

    @PostMapping(value = "/machines/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Import machine configurations from JSON")
    public ApiResponse<MachineImportResultResponse> importMachinesJson(
            @RequestBody MachineConfigImportRequest request,
            @RequestParam(value = "mode", defaultValue = "CREATE_ONLY") ImportMode mode) {
        return ApiResponse.ok(importService.importMachinesJson(request, mode), "Machine import (JSON) completed");
    }

    @PostMapping(value = "/profiles/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Import profiles from JSON")
    public ApiResponse<MachineImportResultResponse> importProfilesJson(
            @RequestBody MachineProfileImportRequest request) {
        return ApiResponse.ok(importService.importProfilesJson(request), "Profile import (JSON) completed");
    }

    @PostMapping(value = "/mappings/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Import profile mappings from JSON")
    public ApiResponse<MachineImportResultResponse> importMappingsJson(
            @RequestBody MachineMappingImportRequest request) {
        return ApiResponse.ok(importService.importMappingsJson(request), "Mapping import (JSON) completed");
    }

    // ========== FILE LISTING AND VALIDATION ENDPOINTS ==========

    @GetMapping("/files")
    @Operation(summary = "List imported files with optional filtering")
    public ApiResponse<List<ImportFileResponse>> listImportFiles(
            @RequestParam(value = "type", required = false) String importType,
            @RequestParam(value = "profileCode", required = false) String profileCode) {
        return ApiResponse.ok(importService.listImportFiles(importType, profileCode), "Import files retrieved");
    }

    @GetMapping("/files/{fileId}")
    @Operation(summary = "Get imported file detail")
    public ApiResponse<ImportFileResponse> getImportFileDetail(@PathVariable UUID fileId) {
        return ApiResponse.ok(importService.getImportFileDetail(fileId), "Import file detail retrieved");
    }
}
