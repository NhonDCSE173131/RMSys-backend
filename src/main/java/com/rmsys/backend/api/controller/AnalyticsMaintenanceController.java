package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.AnalyticsTrendPointResponse;
import com.rmsys.backend.api.response.AnalyticsTrendResponse;
import com.rmsys.backend.api.response.MaintenanceOverviewResponse;
import com.rmsys.backend.api.response.ToolOverviewResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.repository.MachineHealthSnapshotRepository;
import com.rmsys.backend.domain.service.MaintenanceService;
import com.rmsys.backend.domain.service.ToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Tag(name = "Analytics Maintenance")
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsMaintenanceController {

    private final MaintenanceService maintenanceService;
    private final ToolService toolService;
    private final MachineHealthSnapshotRepository machineHealthSnapshotRepository;

    @GetMapping("/maintenance/risk")
    @Operation(summary = "Get maintenance risk overview")
    public ApiResponse<MaintenanceOverviewResponse> risk() {
        return ApiResponse.ok(maintenanceService.getOverview());
    }

    @GetMapping("/maintenance/health-trend")
    @Operation(summary = "Get maintenance health trend")
    public ApiResponse<AnalyticsTrendResponse> healthTrend(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "1h") String interval) {
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minus(24, ChronoUnit.HOURS);

        var points = machineHealthSnapshotRepository.findByBucketStartBetweenOrderByBucketStartAsc(resolvedFrom, resolvedTo)
                .stream()
                .map(snapshot -> AnalyticsTrendPointResponse.builder()
                        .timestamp(snapshot.getBucketStart())
                        .bucketEnd(snapshot.getBucketStart())
                        .sampleCount(1)
                        .missing(false)
                        .metrics(Map.of(
                                "healthScore", snapshot.getHealthScore() != null ? snapshot.getHealthScore() : 0,
                                "temperatureScore", snapshot.getTemperatureScore() != null ? snapshot.getTemperatureScore() : 0,
                                "vibrationScore", snapshot.getVibrationScore() != null ? snapshot.getVibrationScore() : 0,
                                "alarmScore", snapshot.getAlarmScore() != null ? snapshot.getAlarmScore() : 0
                        ))
                        .build())
                .toList();

        return ApiResponse.ok(AnalyticsTrendResponse.builder()
                .from(resolvedFrom)
                .to(resolvedTo)
                .interval(interval)
                .points(points)
                .build());
    }

    @GetMapping("/tool-life/overview")
    @Operation(summary = "Get tool life overview")
    public ApiResponse<ToolOverviewResponse> toolLifeOverview() {
        return ApiResponse.ok(toolService.getOverview());
    }
}

