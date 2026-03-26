package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.ThresholdResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Settings")
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/thresholds")
    @Operation(summary = "Get all thresholds")
    public ApiResponse<ThresholdResponse> thresholds() {
        return ApiResponse.ok(settingsService.getAllThresholds());
    }
}

