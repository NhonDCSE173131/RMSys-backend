package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.request.ExportRequestDto;
import com.rmsys.backend.api.response.ExportJobResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Export")
@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    /**
     * Creates an async telemetry export job.
     * Poll {@code GET /api/v1/exports/{jobId}} until status is COMPLETED,
     * then download via {@code GET /api/v1/exports/{jobId}/download}.
     */
    @PostMapping("/telemetry")
    @Operation(summary = "Create a telemetry export job")
    public ApiResponse<ExportJobResponse> createExport(@Valid @RequestBody ExportRequestDto request) {
        return ApiResponse.ok(exportService.createTelemetryExport(request), "Export job created");
    }

    /** Returns the current status and metadata of an export job. */
    @GetMapping("/{jobId}")
    @Operation(summary = "Get export job status")
    public ApiResponse<ExportJobResponse> getJob(@PathVariable String jobId) {
        return ApiResponse.ok(exportService.getJobStatus(jobId));
    }

    /**
     * Downloads the result file once the job is COMPLETED.
     * Returns a CSV file as an attachment.
     */
    @GetMapping("/{jobId}/download")
    @Operation(summary = "Download completed export file")
    public ResponseEntity<byte[]> download(@PathVariable String jobId) {
        byte[] bytes = exportService.downloadResult(jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"telemetry-export-" + jobId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}

