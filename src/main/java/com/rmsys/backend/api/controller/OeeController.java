package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.OeeOverviewResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.OeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OEE")
@RestController
@RequestMapping("/api/v1/oee")
@RequiredArgsConstructor
public class OeeController {

    private final OeeService oeeService;

    @GetMapping("/overview")
    @Operation(summary = "Get OEE overview")
    public ApiResponse<OeeOverviewResponse> overview() {
        return ApiResponse.ok(oeeService.getOverview());
    }
}

