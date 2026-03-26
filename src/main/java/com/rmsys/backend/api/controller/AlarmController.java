package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.request.AckAlarmRequest;
import com.rmsys.backend.api.response.AlarmResponse;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.common.response.PageResponse;
import com.rmsys.backend.domain.service.AlarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Alarms")
@RestController
@RequestMapping("/api/v1/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    @GetMapping("/active")
    @Operation(summary = "Get active alarms")
    public ApiResponse<List<AlarmResponse>> active() {
        return ApiResponse.ok(alarmService.getActiveAlarms());
    }

    @GetMapping("/history")
    @Operation(summary = "Get alarm history")
    public ApiResponse<PageResponse<AlarmResponse>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(alarmService.getAlarmHistory(PageRequest.of(page, size)));
    }

    @PostMapping("/{alarmId}/acknowledge")
    @Operation(summary = "Acknowledge an alarm")
    public ApiResponse<Void> acknowledge(@PathVariable UUID alarmId, @Valid @RequestBody AckAlarmRequest request) {
        alarmService.acknowledgeAlarm(alarmId, request);
        return ApiResponse.ok(null, "Alarm acknowledged");
    }
}

