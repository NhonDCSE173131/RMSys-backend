package com.rmsys.backend.api.controller;

import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.dto.NormalizedAlarmDto;
import com.rmsys.backend.domain.dto.NormalizedDowntimeDto;
import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.service.IngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    public ApiResponse<Void> ingestTelemetry(@Valid @RequestBody NormalizedTelemetryDto dto) {
        ingestService.ingestTelemetry(dto);
        return ApiResponse.ok(null, "Telemetry ingested");
    }

    @PostMapping("/alarm")
    @Operation(summary = "Ingest normalized alarm event")
    public ApiResponse<Void> ingestAlarm(@Valid @RequestBody NormalizedAlarmDto dto) {
        ingestService.ingestAlarm(dto);
        return ApiResponse.ok(null, "Alarm ingested");
    }

    @PostMapping("/downtime")
    @Operation(summary = "Ingest normalized downtime event")
    public ApiResponse<Void> ingestDowntime(@Valid @RequestBody NormalizedDowntimeDto dto) {
        ingestService.ingestDowntime(dto);
        return ApiResponse.ok(null, "Downtime ingested");
    }
}
