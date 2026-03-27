package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.AnalyticsBreakdownResponse;
import com.rmsys.backend.api.response.AnalyticsTrendResponse;
import com.rmsys.backend.api.response.OeeLossesResponse;
import com.rmsys.backend.api.response.OeeOverviewResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.OeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Analytics OEE")
@RestController
@RequestMapping("/api/v1/analytics/oee")
@RequiredArgsConstructor
public class AnalyticsOeeController {

    private final OeeService oeeService;

    @GetMapping("/overview")
    @Operation(summary = "Get OEE analytics overview")
    public ApiResponse<OeeOverviewResponse> overview() {
        return ApiResponse.ok(oeeService.getOverview());
    }

    @GetMapping("/trend")
    @Operation(summary = "Get OEE trend")
    public ApiResponse<AnalyticsTrendResponse> trend(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "1h") String interval) {
        return ApiResponse.ok(oeeService.getTrend(from, to, interval));
    }

    @GetMapping("/by-machine")
    @Operation(summary = "Get OEE breakdown by machine")
    public ApiResponse<AnalyticsBreakdownResponse> byMachine() {
        return ApiResponse.ok(oeeService.getByMachine());
    }

    @GetMapping("/losses")
    @Operation(summary = "Get OEE loss summary")
    public ApiResponse<OeeLossesResponse> losses(@RequestParam(required = false) Instant from) {
        return ApiResponse.ok(oeeService.getLosses(from));
    }
}

