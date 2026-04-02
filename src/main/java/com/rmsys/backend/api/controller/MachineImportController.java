package com.rmsys.backend.api.controller;

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

@Tag(name = "Machine Import", description = "Import machine config, profiles, and mappings from CSV")
@RestController
@RequestMapping("/api/v1/machine-imports")
@RequiredArgsConstructor
public class MachineImportController {

    private final MachineImportService importService;

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
    @Operation(summary = "Validate file without committing (dry run)")
    public ApiResponse<MachineImportResultResponse> validate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String importType) {
        return ApiResponse.ok(importService.validateFile(file, importType), "Validation completed");
    }
}

