package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.EnergyOverviewResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.EnergyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Energy")
@RestController
@RequestMapping("/api/v1/energy")
@RequiredArgsConstructor
public class EnergyController {

    private final EnergyService energyService;

    @GetMapping("/overview")
    @Operation(summary = "Get energy overview")
    public ApiResponse<EnergyOverviewResponse> overview() {
        return ApiResponse.ok(energyService.getOverview());
    }
}

