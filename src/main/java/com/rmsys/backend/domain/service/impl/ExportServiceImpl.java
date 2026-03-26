package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.request.ExportRequestDto;
import com.rmsys.backend.api.response.ExportJobResponse;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.entity.MachineTelemetryEntity;
import com.rmsys.backend.domain.repository.MachineTelemetryRepository;
import com.rmsys.backend.domain.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final MachineTelemetryRepository telemetryRepo;
    private final Map<String, ExportJobState> jobs = new ConcurrentHashMap<>();

    @Override
    public ExportJobResponse createTelemetryExport(ExportRequestDto request) {
        String jobId = UUID.randomUUID().toString();
        ExportJobState state = new ExportJobState(
                jobId,
                request.machineId(),
                request.from(),
                request.to(),
                request.format() != null ? request.format().toLowerCase() : "csv"
        );
        jobs.put(jobId, state);

        CompletableFuture.runAsync(() -> processExport(jobId, request));

        log.info("Export job created: {} for machine={} range=[{},{}]",
                jobId, request.machineId(), request.from(), request.to());
        return toResponse(state);
    }

    @Override
    public ExportJobResponse getJobStatus(String jobId) {
        return toResponse(requireJob(jobId));
    }

    @Override
    public byte[] downloadResult(String jobId) {
        ExportJobState state = requireJob(jobId);
        if (!"COMPLETED".equals(state.status)) {
            throw new AppException("EXPORT_NOT_READY",
                    "Export job '" + jobId + "' is not yet completed (status=" + state.status + ")");
        }
        return state.resultBytes;
    }

    // --- internal ---

    private void processExport(String jobId, ExportRequestDto request) {
        ExportJobState state = jobs.get(jobId);
        state.status = "PROCESSING";
        try {
            List<MachineTelemetryEntity> points = telemetryRepo.findByMachineIdAndTsBetweenOrderByTsDesc(
                    request.machineId(), request.from(), request.to());

            ZoneId zoneId = resolveZone(request.timezone());
            Set<String> metricsFilter = (request.metrics() != null && !request.metrics().isEmpty())
                    ? Set.copyOf(request.metrics()) : null;

            state.resultBytes = buildCsv(points, metricsFilter, zoneId).getBytes(StandardCharsets.UTF_8);
            state.totalRows = points.size();
            state.status = "COMPLETED";
            state.completedAt = Instant.now();
            log.info("Export job {} completed: {} rows", jobId, points.size());

        } catch (Exception ex) {
            state.status = "FAILED";
            state.errorMessage = ex.getMessage();
            state.completedAt = Instant.now();
            log.error("Export job {} failed: {}", jobId, ex.getMessage(), ex);
        }
    }

    private String buildCsv(List<MachineTelemetryEntity> points, Set<String> metricsFilter, ZoneId zoneId) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(zoneId);
        StringBuilder sb = new StringBuilder();

        // header
        sb.append("ts,machineId,machineState,connectionStatus");
        if (include("powerKw", metricsFilter))        sb.append(",powerKw");
        if (include("temperatureC", metricsFilter))    sb.append(",temperatureC");
        if (include("vibrationMmS", metricsFilter))    sb.append(",vibrationMmS");
        if (include("runtimeHours", metricsFilter))    sb.append(",runtimeHours");
        if (include("cycleTimeSec", metricsFilter))    sb.append(",cycleTimeSec");
        if (include("outputCount", metricsFilter))     sb.append(",outputCount");
        if (include("goodCount", metricsFilter))       sb.append(",goodCount");
        if (include("rejectCount", metricsFilter))     sb.append(",rejectCount");
        if (include("spindleSpeedRpm", metricsFilter)) sb.append(",spindleSpeedRpm");
        if (include("feedRateMmMin", metricsFilter))   sb.append(",feedRateMmMin");
        if (include("axisLoadPct", metricsFilter))     sb.append(",axisLoadPct");
        if (include("qualityScore", metricsFilter))    sb.append(",qualityScore");
        sb.append('\n');

        // rows
        for (MachineTelemetryEntity p : points) {
            sb.append(formatter.format(p.getTs())).append(',')
              .append(esc(p.getMachineId())).append(',')
              .append(esc(p.getMachineState())).append(',')
              .append(esc(p.getConnectionStatus()));
            if (include("powerKw", metricsFilter))        sb.append(',').append(val(p.getPowerKw()));
            if (include("temperatureC", metricsFilter))    sb.append(',').append(val(p.getTemperatureC()));
            if (include("vibrationMmS", metricsFilter))    sb.append(',').append(val(p.getVibrationMmS()));
            if (include("runtimeHours", metricsFilter))    sb.append(',').append(val(p.getRuntimeHours()));
            if (include("cycleTimeSec", metricsFilter))    sb.append(',').append(val(p.getCycleTimeSec()));
            if (include("outputCount", metricsFilter))     sb.append(',').append(val(p.getOutputCount()));
            if (include("goodCount", metricsFilter))       sb.append(',').append(val(p.getGoodCount()));
            if (include("rejectCount", metricsFilter))     sb.append(',').append(val(p.getRejectCount()));
            if (include("spindleSpeedRpm", metricsFilter)) sb.append(',').append(val(p.getSpindleSpeedRpm()));
            if (include("feedRateMmMin", metricsFilter))   sb.append(',').append(val(p.getFeedRateMmMin()));
            if (include("axisLoadPct", metricsFilter))     sb.append(',').append(val(p.getAxisLoadPct()));
            if (include("qualityScore", metricsFilter))    sb.append(',').append(val(p.getQualityScore()));
            sb.append('\n');
        }
        return sb.toString();
    }

    private boolean include(String metric, Set<String> filter) {
        return filter == null || filter.contains(metric);
    }

    private String val(Object v) {
        return v == null ? "" : v.toString();
    }

    private String esc(Object v) {
        if (v == null) return "";
        String s = v.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) return ZoneId.of("UTC");
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    private ExportJobState requireJob(String jobId) {
        ExportJobState state = jobs.get(jobId);
        if (state == null) throw AppException.notFound("ExportJob", jobId);
        return state;
    }

    private ExportJobResponse toResponse(ExportJobState s) {
        return ExportJobResponse.builder()
                .jobId(s.jobId)
                .status(s.status)
                .machineId(s.machineId)
                .from(s.from)
                .to(s.to)
                .format(s.format)
                .createdAt(s.createdAt)
                .completedAt(s.completedAt)
                .downloadUrl("COMPLETED".equals(s.status) ? "/api/v1/exports/" + s.jobId + "/download" : null)
                .errorMessage(s.errorMessage)
                .totalRows(s.totalRows)
                .build();
    }

    /** Mutable job state stored in-memory. Accessed only by the creating thread + one background thread. */
    private static final class ExportJobState {
        volatile String jobId;
        volatile String status;
        volatile String format;
        volatile String errorMessage;
        volatile UUID machineId;
        volatile Instant from;
        volatile Instant to;
        volatile Instant createdAt;
        volatile Instant completedAt;
        volatile byte[] resultBytes;
        volatile Integer totalRows;

        ExportJobState(String jobId, UUID machineId, Instant from, Instant to, String format) {
            this.jobId = jobId;
            this.machineId = machineId;
            this.from = from;
            this.to = to;
            this.format = format;
            this.status = "PENDING";
            this.createdAt = Instant.now();
        }
    }
}

