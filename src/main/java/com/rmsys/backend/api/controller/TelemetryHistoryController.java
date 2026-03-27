package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.AlarmResponse;
import com.rmsys.backend.api.response.DowntimeHistoryPointResponse;
import com.rmsys.backend.api.response.TelemetrySeriesResponse;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.common.response.PageResponse;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.AlarmService;
import com.rmsys.backend.domain.service.DowntimeService;
import com.rmsys.backend.domain.service.MachineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "History")
@RestController
@RequestMapping("/api/v1/machines")
@RequiredArgsConstructor
public class TelemetryHistoryController {

    private final MachineService machineService;
    private final AlarmService alarmService;
    private final DowntimeService downtimeService;
    private final MachineRepository machineRepository;

    /**
     * Telemetry chart history with optional bucket aggregation.
     *
     * <p>Query params:
     * <ul>
     *   <li>{@code from} / {@code to} – ISO-8601 instant, required</li>
     *   <li>{@code interval} – "raw"|"1m"|"5m"|"15m"|"30m"|"1h"|"6h"|"12h"|"1d" (default raw)</li>
     *   <li>{@code aggregation} – "avg"|"min"|"max"|"last" (default avg)</li>
     * </ul>
     */
    @GetMapping("/{machineId}/telemetry/history")
    @Operation(summary = "Get telemetry history for a machine (chart data)")
    public ApiResponse<TelemetrySeriesResponse> telemetryHistory(
            @PathVariable String machineId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false, defaultValue = "raw") String interval,
            @RequestParam(required = false, defaultValue = "avg") String aggregation) {
        return ApiResponse.ok(machineService.getTelemetryHistory(resolveMachineId(machineId), from, to, interval, aggregation));
    }

    /**
     * Paginated alarm history for a specific machine, optionally filtered by time range.
     */
    @GetMapping("/{machineId}/alarms/history")
    @Operation(summary = "Get alarm history for a specific machine")
    public ApiResponse<PageResponse<AlarmResponse>> alarmHistory(
            @PathVariable String machineId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(alarmService.getMachineAlarmHistory(resolveMachineId(machineId), from, to, PageRequest.of(page, size)));
    }

    /**
     * Paginated downtime history for a specific machine, optionally filtered by time range.
     */
    @GetMapping("/{machineId}/downtime/history")
    @Operation(summary = "Get downtime history for a specific machine")
    public ApiResponse<PageResponse<DowntimeHistoryPointResponse>> downtimeHistory(
            @PathVariable String machineId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(downtimeService.getMachineDowntimeHistory(resolveMachineId(machineId), from, to, PageRequest.of(page, size)));
    }

    private UUID resolveMachineId(String machineIdentifier) {
        try {
            return UUID.fromString(machineIdentifier);
        } catch (IllegalArgumentException ignored) {
            return machineRepository.findByCode(machineIdentifier)
                    .map(MachineEntity::getId)
                    .orElseThrow(() -> AppException.notFound("Machine", machineIdentifier));
        }
    }
}

