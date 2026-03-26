package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.MaintenanceOverviewResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.MaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Maintenance")
@RestController
@RequestMapping("/api/v1/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @GetMapping("/overview")
    @Operation(summary = "Get maintenance overview")
    public ApiResponse<MaintenanceOverviewResponse> overview() {
        return ApiResponse.ok(maintenanceService.getOverview());
    }
}

