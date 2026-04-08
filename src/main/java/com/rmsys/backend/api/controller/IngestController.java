package com.rmsys.backend.api.controller;

import com.rmsys.backend.common.response.ApiResponse;
import com.rmsys.backend.api.request.IngestConnectionStatusRequest;
import com.rmsys.backend.api.request.IngestTelemetryByCodeRequest;
import com.rmsys.backend.api.request.IngestTelemetryRequest;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.dto.NormalizedAlarmDto;
import com.rmsys.backend.domain.dto.NormalizedDowntimeDto;
import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.service.IngestService;
import com.rmsys.backend.domain.service.MachineIdentityResolverService;
import com.rmsys.backend.domain.service.MachineConnectionStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;

@Tag(name = "Ingest")
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final IngestService ingestService;
    private final MachineIdentityResolverService machineIdentityResolverService;
    private final MachineConnectionStateService connectionStateService;

    @Value("${app.ingest.api-key:}")
    private String ingestApiKey;

    @Value("${app.ingest.http-enabled:false}")
    private boolean ingestHttpEnabled;

    @PostMapping("/telemetry")
    @Operation(summary = "Ingest normalized telemetry data")
    public ApiResponse<Void> ingestTelemetry(
            @RequestHeader(value = "X-Ingest-Key", required = false) String ingestKey,
            @Valid @RequestBody IngestTelemetryRequest request) {
        ensureIngestHttpEnabled();
        validateIngestKey(ingestKey);
        ingestService.ingestTelemetry(toTelemetryDto(request));
        return ApiResponse.ok(null, "Telemetry ingested");
    }

    @PostMapping("/telemetry/by-code")
    @Operation(summary = "Ingest normalized telemetry data by machine code")
    public ApiResponse<Void> ingestTelemetryByCode(
            @RequestHeader(value = "X-Ingest-Key", required = false) String ingestKey,
            @Valid @RequestBody IngestTelemetryByCodeRequest request) {
        ensureIngestHttpEnabled();
        validateIngestKey(ingestKey);

        var machine = machineIdentityResolverService.resolveRequired((java.util.UUID) null, request.machineCode());

        ingestService.ingestTelemetry(toTelemetryDto(machine, request));
        return ApiResponse.ok(null, "Telemetry ingested");
    }

    @PostMapping("/alarm")
    @Operation(summary = "Ingest normalized alarm event")
    public ApiResponse<Void> ingestAlarm(
            @RequestHeader(value = "X-Ingest-Key", required = false) String ingestKey,
            @Valid @RequestBody NormalizedAlarmDto dto) {
        ensureIngestHttpEnabled();
        validateIngestKey(ingestKey);
        ingestService.ingestAlarm(dto);
        return ApiResponse.ok(null, "Alarm ingested");
    }

    @PostMapping("/downtime")
    @Operation(summary = "Ingest normalized downtime event")
    public ApiResponse<Void> ingestDowntime(
            @RequestHeader(value = "X-Ingest-Key", required = false) String ingestKey,
            @Valid @RequestBody NormalizedDowntimeDto dto) {
        ensureIngestHttpEnabled();
        validateIngestKey(ingestKey);
        ingestService.ingestDowntime(dto);
        return ApiResponse.ok(null, "Downtime ingested");
    }

    @PostMapping("/connection-status")
    @Operation(summary = "Ingest machine connection status from collector")
    public ApiResponse<Void> ingestConnectionStatus(
            @RequestHeader(value = "X-Ingest-Key", required = false) String ingestKey,
            @Valid @RequestBody IngestConnectionStatusRequest request) {
        ensureIngestHttpEnabled();
        validateIngestKey(ingestKey);

        var machine = machineIdentityResolverService.resolveRequired(request.machineId(), request.machineCode());

        connectionStateService.markConnectionReported(
                machine,
                request.connectionStatus(),
                request.ts() != null ? request.ts() : Instant.now(),
                request.metadata());

        return ApiResponse.ok(null, "Connection status ingested");
    }

    private void validateIngestKey(String ingestKey) {
        if (ingestApiKey == null || ingestApiKey.isBlank()) {
            return;
        }
        if (!ingestApiKey.equals(ingestKey)) {
            throw new AppException("UNAUTHORIZED", "Invalid ingest API key");
        }
    }

    private void ensureIngestHttpEnabled() {
        if (!ingestHttpEnabled) {
            throw new AppException("INGEST_HTTP_DISABLED", "External ingest endpoints are disabled");
        }
    }

    private NormalizedTelemetryDto toTelemetryDto(IngestTelemetryRequest request) {
        return toTelemetryDto(machineIdentityResolverService.resolveRequired(request.machineId(), request.machineCode()), request);
    }

    private NormalizedTelemetryDto toTelemetryDto(com.rmsys.backend.domain.entity.MachineEntity machine, IngestTelemetryRequest request) {
        Integer outputCount = firstNonNull(request.outputCount(), request.partCount(), request.cycleCount());
        Integer goodCount = firstNonNull(request.goodCount(), request.partCount());
        Boolean cycleRunning = request.cycleRunning() != null
                ? request.cycleRunning()
                : request.machineState() != null && "RUNNING".equalsIgnoreCase(request.machineState());

        var metadata = new LinkedHashMap<String, Object>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        if (request.cycleCount() != null) {
            metadata.putIfAbsent("cycleCount", request.cycleCount());
        }
        if (request.partCount() != null) {
            metadata.putIfAbsent("partCount", request.partCount());
        }
        if (request.energyKwhTotal() != null) {
            metadata.putIfAbsent("energyKwhTotal", request.energyKwhTotal());
        }
        if (request.maintenanceHealthScore() != null) {
            metadata.putIfAbsent("maintenanceHealthScore", request.maintenanceHealthScore());
        }
        if (request.toolWearPercent() != null) {
            metadata.putIfAbsent("toolWearPercent", request.toolWearPercent());
        }
        if (request.alarmHints() != null && !request.alarmHints().isEmpty()) {
            metadata.putIfAbsent("alarmHints", request.alarmHints());
        }

        return NormalizedTelemetryDto.builder()
                .machineId(machine.getId())
                .machineCode(machine.getCode())
                .ts(request.ts())
                .connectionStatus(request.connectionStatus())
                .machineState(request.machineState())
                .operationMode(request.operationMode())
                .programName(request.programName())
                .cycleRunning(cycleRunning)
                .powerKw(request.powerKw())
                .temperatureC(request.temperatureC())
                .vibrationMmS(request.vibrationMmS())
                .runtimeHours(request.runtimeHours())
                .cycleTimeSec(request.cycleTimeSec())
                .outputCount(outputCount)
                .goodCount(goodCount)
                .rejectCount(request.rejectCount())
                .spindleSpeedRpm(request.spindleSpeedRpm())
                .feedRateMmMin(request.feedRateMmMin())
                .idealCycleTimeSec(request.idealCycleTimeSec())
                .spindleLoadPct(request.spindleLoadPct())
                .servoLoadPct(request.servoLoadPct())
                .cuttingSpeedMMin(request.cuttingSpeedMMin())
                .depthOfCutMm(request.depthOfCutMm())
                .feedPerToothMm(request.feedPerToothMm())
                .widthOfCutMm(request.widthOfCutMm())
                .materialRemovalRateCm3Min(request.materialRemovalRateCm3Min())
                .weldingCurrentA(request.weldingCurrentA())
                .toolCode(request.toolCode())
                .remainingToolLifePct(resolveRemainingToolLifePct(request))
                .voltageV(request.voltageV())
                .currentA(request.currentA())
                .powerFactor(request.powerFactor())
                .frequencyHz(request.frequencyHz())
                .energyKwhShift(request.energyKwhShift())
                .energyKwhDay(firstNonNull(request.energyKwhDay(), request.energyKwhTotal()))
                .motorTemperatureC(request.motorTemperatureC())
                .bearingTemperatureC(request.bearingTemperatureC())
                .cabinetTemperatureC(request.cabinetTemperatureC())
                .servoOnHours(request.servoOnHours())
                .startStopCount(request.startStopCount())
                .lubricationLevelPct(request.lubricationLevelPct())
                .batteryLow(request.batteryLow())
                .metadata(metadata.isEmpty() ? null : metadata)
                .build();
    }

    private NormalizedTelemetryDto toTelemetryDto(com.rmsys.backend.domain.entity.MachineEntity machine, IngestTelemetryByCodeRequest request) {
        return toTelemetryDto(machine, IngestTelemetryRequest.builder()
                .machineId(machine.getId())
                .machineCode(machine.getCode())
                .ts(request.ts())
                .connectionStatus(request.connectionStatus())
                .machineState(request.machineState())
                .operationMode(request.operationMode())
                .programName(request.programName())
                .cycleRunning(request.cycleRunning())
                .powerKw(request.powerKw())
                .temperatureC(request.temperatureC())
                .vibrationMmS(request.vibrationMmS())
                .runtimeHours(request.runtimeHours())
                .cycleTimeSec(request.cycleTimeSec())
                .outputCount(request.outputCount())
                .goodCount(request.goodCount())
                .rejectCount(request.rejectCount())
                .spindleSpeedRpm(request.spindleSpeedRpm())
                .feedRateMmMin(request.feedRateMmMin())
                .idealCycleTimeSec(request.idealCycleTimeSec())
                .spindleLoadPct(request.spindleLoadPct())
                .servoLoadPct(request.servoLoadPct())
                .cuttingSpeedMMin(request.cuttingSpeedMMin())
                .depthOfCutMm(request.depthOfCutMm())
                .feedPerToothMm(request.feedPerToothMm())
                .widthOfCutMm(request.widthOfCutMm())
                .materialRemovalRateCm3Min(request.materialRemovalRateCm3Min())
                .weldingCurrentA(request.weldingCurrentA())
                .toolCode(request.toolCode())
                .remainingToolLifePct(request.remainingToolLifePct())
                .voltageV(request.voltageV())
                .currentA(request.currentA())
                .powerFactor(request.powerFactor())
                .frequencyHz(request.frequencyHz())
                .energyKwhShift(request.energyKwhShift())
                .energyKwhDay(request.energyKwhDay())
                .energyKwhTotal(request.energyKwhTotal())
                .cycleCount(request.cycleCount())
                .partCount(request.partCount())
                .toolWearPercent(request.toolWearPercent())
                .maintenanceHealthScore(request.maintenanceHealthScore())
                .alarmHints(request.alarmHints())
                .motorTemperatureC(request.motorTemperatureC())
                .bearingTemperatureC(request.bearingTemperatureC())
                .cabinetTemperatureC(request.cabinetTemperatureC())
                .servoOnHours(request.servoOnHours())
                .startStopCount(request.startStopCount())
                .lubricationLevelPct(request.lubricationLevelPct())
                .batteryLow(request.batteryLow())
                .metadata(request.metadata())
                .build());
    }

    private Double resolveRemainingToolLifePct(IngestTelemetryRequest request) {
        if (request.remainingToolLifePct() != null) {
            return request.remainingToolLifePct();
        }
        if (request.toolWearPercent() == null) {
            return null;
        }
        return Math.max(0.0, 100.0 - request.toolWearPercent());
    }

    private <T> T firstNonNull(T... candidates) {
        for (T candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
