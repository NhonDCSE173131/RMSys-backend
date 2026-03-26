package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.ToolOverviewResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.domain.service.ToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Tools")
@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    @GetMapping("/overview")
    @Operation(summary = "Get tools overview")
    public ApiResponse<ToolOverviewResponse> overview() {
        return ApiResponse.ok(toolService.getOverview());
    }

    @GetMapping("/machines/{machineId}")
    @Operation(summary = "Get tools for a machine")
    public ApiResponse<ToolOverviewResponse> byMachine(@PathVariable UUID machineId) {
        return ApiResponse.ok(toolService.getByMachine(machineId));
    }
}

