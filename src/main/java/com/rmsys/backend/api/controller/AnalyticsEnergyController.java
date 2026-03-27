package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.AnalyticsBreakdownResponse;
import com.rmsys.backend.api.response.AnalyticsTrendResponse;
import com.rmsys.backend.api.response.EnergyCostResponse;
import com.rmsys.backend.api.response.EnergyOverviewResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.EnergyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Analytics Energy")
@RestController
@RequestMapping("/api/v1/analytics/energy")
@RequiredArgsConstructor
public class AnalyticsEnergyController {

    private final EnergyService energyService;

    @GetMapping("/overview")
    @Operation(summary = "Get energy analytics overview")
    public ApiResponse<EnergyOverviewResponse> overview() {
        return ApiResponse.ok(energyService.getOverview());
    }

    @GetMapping("/trend")
    @Operation(summary = "Get energy trend")
    public ApiResponse<AnalyticsTrendResponse> trend(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "1h") String interval) {
        return ApiResponse.ok(energyService.getTrend(from, to, interval));
    }

    @GetMapping("/by-area")
    @Operation(summary = "Get energy breakdown by area")
    public ApiResponse<AnalyticsBreakdownResponse> byArea() {
        return ApiResponse.ok(energyService.getByArea());
    }

    @GetMapping("/by-machine")
    @Operation(summary = "Get energy breakdown by machine")
    public ApiResponse<AnalyticsBreakdownResponse> byMachine() {
        return ApiResponse.ok(energyService.getByMachine());
    }

    @GetMapping("/cost")
    @Operation(summary = "Get energy cost summary")
    public ApiResponse<EnergyCostResponse> cost() {
        return ApiResponse.ok(energyService.getCost());
    }
}

