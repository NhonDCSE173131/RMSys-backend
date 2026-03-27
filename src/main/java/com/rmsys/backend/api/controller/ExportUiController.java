package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.request.ExportRequestDto;
import com.rmsys.backend.api.response.ExportJobResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.ExportService;
import com.rmsys.backend.domain.service.MachineIdentityResolverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Export UI")
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class ExportUiController {

    private final ExportService exportService;
    private final MachineIdentityResolverService machineIdentityResolverService;

    @PostMapping("/history")
    @Operation(summary = "Create history export job")
    public ApiResponse<ExportJobResponse> exportHistory(@Valid @RequestBody ExportRequestDto request) {
        return ApiResponse.ok(exportService.createTelemetryExport(request), "Export job created");
    }

    @PostMapping("/energy")
    @Operation(summary = "Create energy report export job")
    public ApiResponse<ExportJobResponse> exportEnergy(@Valid @RequestBody ExportRequestDto request) {
        return ApiResponse.ok(exportService.createTelemetryExport(request), "Export job created");
    }

    @PostMapping("/oee")
    @Operation(summary = "Create OEE report export job")
    public ApiResponse<ExportJobResponse> exportOee(@Valid @RequestBody ExportRequestDto request) {
        return ApiResponse.ok(exportService.createTelemetryExport(request), "Export job created");
    }

    @PostMapping("/machines/{machineId}/history")
    @Operation(summary = "Create machine history export job")
    public ApiResponse<ExportJobResponse> exportMachineHistory(
            @PathVariable String machineId,
            @Valid @RequestBody ExportRequestDto request) {
        UUID resolvedMachineId = machineIdentityResolverService.resolveRequiredId(machineId);
        return ApiResponse.ok(exportService.createTelemetryExport(withMachine(request, resolvedMachineId)), "Export job created");
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get export job status")
    public ApiResponse<ExportJobResponse> getJob(@PathVariable String jobId) {
        return ApiResponse.ok(exportService.getJobStatus(jobId));
    }

    @GetMapping("/jobs/{jobId}/download")
    @Operation(summary = "Download completed export file")
    public ResponseEntity<byte[]> download(@PathVariable String jobId) {
        byte[] bytes = exportService.downloadResult(jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"export-" + jobId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    private ExportRequestDto withMachine(ExportRequestDto request, UUID machineId) {
        return ExportRequestDto.builder()
                .machineId(machineId)
                .from(request.from())
                .to(request.to())
                .metrics(request.metrics())
                .interval(request.interval())
                .aggregation(request.aggregation())
                .format(request.format())
                .timezone(request.timezone())
                .build();
    }
}

