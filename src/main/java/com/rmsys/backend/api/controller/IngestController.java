package com.rmsys.backend.api.controller;

import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.service.IngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ingest")
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final IngestService ingestService;

    @PostMapping("/telemetry")
    @Operation(summary = "Ingest normalized telemetry data")
    public ApiResponse<Void> ingestTelemetry(@RequestBody NormalizedTelemetryDto dto) {
        ingestService.ingestTelemetry(dto);
        return ApiResponse.ok(null, "Telemetry ingested");
    }
}

