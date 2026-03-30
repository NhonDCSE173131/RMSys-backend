package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.MachineDetailResponse;
import com.rmsys.backend.api.response.MachineOverviewAnalytics;
import com.rmsys.backend.api.response.MachineOverviewMaintenance;
import com.rmsys.backend.api.response.MachineOverviewResponse;
import com.rmsys.backend.api.response.MachineOverviewTelemetry;
import com.rmsys.backend.api.response.MachineOverviewTool;
import com.rmsys.backend.api.response.MachineSnapshotResponse;
import com.rmsys.backend.api.response.MachineSummaryResponse;
import com.rmsys.backend.api.response.TelemetryHistoryPointResponse;
import com.rmsys.backend.api.response.TelemetrySeriesResponse;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineTelemetryEntity;
import com.rmsys.backend.domain.repository.AlarmEventRepository;
import com.rmsys.backend.domain.repository.DowntimeEventRepository;
import com.rmsys.backend.domain.repository.EnergyTelemetryRepository;
import com.rmsys.backend.domain.repository.MachineHealthSnapshotRepository;
import com.rmsys.backend.domain.repository.MaintenanceTelemetryRepository;
import com.rmsys.backend.domain.repository.MaintenancePredictionRepository;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.repository.MachineTelemetryRepository;
import com.rmsys.backend.domain.repository.OeeSnapshotRepository;
import com.rmsys.backend.domain.repository.ToolUsageTelemetryRepository;
import com.rmsys.backend.domain.service.MachineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MachineServiceImpl implements MachineService {

    private final MachineRepository machineRepo;
    private final MachineTelemetryRepository telemetryRepo;
    private final AlarmEventRepository alarmRepo;
    private final DowntimeEventRepository downtimeRepo;
    private final OeeSnapshotRepository oeeRepo;
    private final MachineHealthSnapshotRepository healthRepo;
    private final MaintenancePredictionRepository maintenancePredictionRepo;
    private final EnergyTelemetryRepository energyTelemetryRepo;
    private final MaintenanceTelemetryRepository maintenanceTelemetryRepo;
    private final ToolUsageTelemetryRepository toolUsageRepo;

    @Override
    public List<MachineDetailResponse> getAllMachines() {
        return machineRepo.findAll().stream().map(this::toDetail).toList();
    }

    @Override
    public MachineDetailResponse getMachineDetail(UUID machineId) {
        return toDetail(findMachine(machineId));
    }

    @Override
    public List<MachineOverviewResponse> getMachineOverviews() {
        var machines = machineRepo.findAll();
        var latestTelemetryMap = telemetryRepo.findLatestForAllMachines().stream()
                .collect(Collectors.toMap(MachineTelemetryEntity::getMachineId, t -> t, (left, right) -> left));

        return machines.stream()
                .map(machine -> toOverview(machine, latestTelemetryMap.get(machine.getId())))
                .toList();
    }

    @Override
    public MachineSnapshotResponse getLatestSnapshot(UUID machineId) {
        var machine = findMachine(machineId);
        return telemetryRepo.findFirstByMachineIdOrderByTsDesc(machineId)
                .map(t -> toSnapshot(t, machine))
                .orElse(emptySnapshot(machine));
    }

    @Override
    public MachineSummaryResponse getMachineSummary(UUID machineId) {
        var machine = findMachine(machineId);
        var latestSnapshot = getLatestSnapshot(machineId);

        long activeAlarms = alarmRepo.findByMachineIdAndIsActiveTrueOrderByStartedAtDesc(machineId).size();
        long alarmHistoryCount = alarmRepo.findByMachineIdAndStartedAtBetween(
                machineId,
                Instant.now().minus(Duration.ofDays(30)),
                Instant.now()).size();

        var downtimes = downtimeRepo.findByMachineIdOrderByStartedAtDesc(machineId);
        long activeDowntimes = downtimes.stream().filter(d -> d.getEndedAt() == null).count();
        long abnormalStopsToday = downtimes.stream()
                .filter(d -> Boolean.TRUE.equals(d.getAbnormalStop()))
                .filter(d -> d.getStartedAt() != null && d.getStartedAt().isAfter(Instant.now().minus(Duration.ofDays(1))))
                .count();

        var latestOee = oeeRepo.findFirstByMachineIdAndBucketTypeOrderByBucketStartDesc(machineId, "HOUR");
        var latestHealth = healthRepo.findFirstByMachineIdOrderByBucketStartDesc(machineId);
        var prediction = maintenancePredictionRepo.findFirstByMachineIdOrderByTsDesc(machineId);
        var latestTool = toolUsageRepo.findByMachineIdOrderByTsDesc(machineId).stream().findFirst();

        return MachineSummaryResponse.builder()
                .machineId(machine.getId())
                .machineCode(machine.getCode())
                .machineName(machine.getName())
                .latestSnapshot(latestSnapshot)
                .activeAlarms(activeAlarms)
                .alarmHistoryCount(alarmHistoryCount)
                .activeDowntimes(activeDowntimes)
                .abnormalStopsToday(abnormalStopsToday)
                .availability(latestOee.map(o -> o.getAvailability()).orElse(null))
                .performance(latestOee.map(o -> o.getPerformance()).orElse(null))
                .quality(latestOee.map(o -> o.getQuality()).orElse(null))
                .latestOee(latestOee.map(o -> o.getOee()).orElse(null))
                .healthScore(latestHealth.map(h -> h.getHealthScore()).orElse(null))
                .riskLevel(latestHealth.map(h -> h.getRiskLevel()).orElse(null))
                .riskReason(latestHealth.map(h -> h.getMainReason()).orElse(null))
                .remainingHoursToService(prediction.map(p -> p.getRemainingHoursToService()).orElse(null))
                .nextMaintenanceDate(prediction.map(p -> p.getNextMaintenanceDate()).orElse(null))
                .toolCode(latestTool.map(t -> t.getToolCode()).orElse(null))
                .remainingToolLifePct(latestTool.map(t -> t.getRemainingLifePct()).orElse(null))
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @Override
    public List<MachineSnapshotResponse> getAllLatestSnapshots() {
        var machines = machineRepo.findAll().stream()
                .collect(Collectors.toMap(MachineEntity::getId, m -> m));
        return telemetryRepo.findLatestForAllMachines().stream()
                .map(t -> toSnapshot(t, machines.get(t.getMachineId())))
                .toList();
    }

    @Override
    public TelemetrySeriesResponse getTelemetryHistory(UUID machineId, Instant from, Instant to, String interval, String aggregation, List<String> metrics) {
        var machine = findMachine(machineId);
        var rawDesc = telemetryRepo.findByMachineIdAndTsBetweenOrderByTsDesc(machineId, from, to);
        var ascending = new ArrayList<>(rawDesc);
        Collections.reverse(ascending);

        String resolvedAgg = (aggregation != null && !aggregation.isBlank()) ? aggregation : "avg";
        var requestedMetrics = normalizeMetrics(metrics);

        if (interval == null || interval.isBlank() || "raw".equalsIgnoreCase(interval)) {
            var points = ascending.stream()
                    .map(this::toHistoryPoint)
                    .map(point -> filterMetrics(point, requestedMetrics))
                    .toList();
            return TelemetrySeriesResponse.builder()
                    .machineId(machineId)
                    .machineCode(machine.getCode())
                    .machineName(machine.getName())
                    .from(from)
                    .to(to)
                    .interval("raw")
                    .aggregation("none")
                    .requestedMetrics(requestedMetrics)
                    .totalPoints(points.size())
                    .points(points)
                    .build();
        }

        Duration bucketDuration = parseInterval(interval);
        var points = bucket(ascending, from, bucketDuration, resolvedAgg).stream()
                .map(point -> filterMetrics(point, requestedMetrics))
                .toList();
        return TelemetrySeriesResponse.builder()
                .machineId(machineId)
                .machineCode(machine.getCode())
                .machineName(machine.getName())
                .from(from)
                .to(to)
                .interval(interval)
                .aggregation(resolvedAgg)
                .requestedMetrics(requestedMetrics)
                .totalPoints(points.size())
                .points(points)
                .build();
    }

    private MachineEntity findMachine(UUID id) {
        return machineRepo.findById(id).orElseThrow(() -> AppException.notFound("Machine", id));
    }

    private TelemetryHistoryPointResponse toHistoryPoint(MachineTelemetryEntity t) {
        return TelemetryHistoryPointResponse.builder()
                .ts(t.getTs())
                .bucketEnd(t.getTs())
                .machineState(t.getMachineState())
                .connectionStatus(t.getConnectionStatus())
                .powerKw(t.getPowerKw())
                .temperatureC(t.getTemperatureC())
                .vibrationMmS(t.getVibrationMmS())
                .runtimeHours(t.getRuntimeHours())
                .cycleTimeSec(t.getCycleTimeSec())
                .outputCount(t.getOutputCount())
                .goodCount(t.getGoodCount())
                .rejectCount(t.getRejectCount())
                .spindleSpeedRpm(t.getSpindleSpeedRpm())
                .feedRateMmMin(t.getFeedRateMmMin())
                .idealCycleTimeSec(t.getIdealCycleTimeSec())
                .spindleLoadPct(t.getSpindleLoadPct())
                .servoLoadPct(t.getServoLoadPct())
                .cuttingSpeedMMin(t.getCuttingSpeedMMin())
                .depthOfCutMm(t.getDepthOfCutMm())
                .feedPerToothMm(t.getFeedPerToothMm())
                .widthOfCutMm(t.getWidthOfCutMm())
                .materialRemovalRateCm3Min(t.getMaterialRemovalRateCm3Min())
                .weldingCurrentA(t.getWeldingCurrentA())
                .axisLoadPct(t.getAxisLoadPct())
                .qualityScore(t.getQualityScore())
                .sampleCount(1)
                .build();
    }

    private List<TelemetryHistoryPointResponse> bucket(
            List<MachineTelemetryEntity> asc,
            Instant windowStart,
            Duration bucketDuration,
            String aggregation
    ) {
        long durationSec = bucketDuration.getSeconds();
        long windowEpoch = windowStart.getEpochSecond();

        var bucketed = new LinkedHashMap<Instant, List<MachineTelemetryEntity>>();
        for (var t : asc) {
            long offset = t.getTs().getEpochSecond() - windowEpoch;
            long idx = (durationSec > 0) ? (offset / durationSec) : 0;
            Instant bucketTs = windowStart.plusSeconds(idx * durationSec);
            bucketed.computeIfAbsent(bucketTs, k -> new ArrayList<>()).add(t);
        }

        return bucketed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> aggregate(e.getKey(), e.getValue(), aggregation))
                .toList();
    }

    private TelemetryHistoryPointResponse aggregate(Instant bucketTs, List<MachineTelemetryEntity> points, String agg) {
        if (points.isEmpty()) {
            return TelemetryHistoryPointResponse.builder()
                    .ts(bucketTs)
                    .bucketEnd(bucketTs)
                    .sampleCount(0)
                    .missing(true)
                    .gapDetected(true)
                    .build();
        }
        var last = points.get(points.size() - 1);
        if ("last".equalsIgnoreCase(agg)) {
            return TelemetryHistoryPointResponse.builder()
                    .ts(bucketTs)
                    .bucketEnd(last.getTs())
                    .machineState(last.getMachineState())
                    .connectionStatus(last.getConnectionStatus())
                    .powerKw(last.getPowerKw())
                    .temperatureC(last.getTemperatureC())
                    .vibrationMmS(last.getVibrationMmS())
                    .runtimeHours(last.getRuntimeHours())
                    .cycleTimeSec(last.getCycleTimeSec())
                    .outputCount(last.getOutputCount())
                    .goodCount(last.getGoodCount())
                    .rejectCount(last.getRejectCount())
                    .spindleSpeedRpm(last.getSpindleSpeedRpm())
                    .feedRateMmMin(last.getFeedRateMmMin())
                    .idealCycleTimeSec(last.getIdealCycleTimeSec())
                    .spindleLoadPct(last.getSpindleLoadPct())
                    .servoLoadPct(last.getServoLoadPct())
                    .cuttingSpeedMMin(last.getCuttingSpeedMMin())
                    .depthOfCutMm(last.getDepthOfCutMm())
                    .feedPerToothMm(last.getFeedPerToothMm())
                    .widthOfCutMm(last.getWidthOfCutMm())
                    .materialRemovalRateCm3Min(last.getMaterialRemovalRateCm3Min())
                    .weldingCurrentA(last.getWeldingCurrentA())
                    .axisLoadPct(last.getAxisLoadPct())
                    .qualityScore(last.getQualityScore())
                    .sampleCount(points.size())
                    .missing(false)
                    .gapDetected(false)
                    .build();
        }

        return TelemetryHistoryPointResponse.builder()
                .ts(bucketTs)
                .bucketEnd(last.getTs())
                .machineState(last.getMachineState())
                .connectionStatus(last.getConnectionStatus())
                .powerKw(numericAgg(points, MachineTelemetryEntity::getPowerKw, agg))
                .temperatureC(numericAgg(points, MachineTelemetryEntity::getTemperatureC, agg))
                .vibrationMmS(numericAgg(points, MachineTelemetryEntity::getVibrationMmS, agg))
                .runtimeHours(numericAgg(points, MachineTelemetryEntity::getRuntimeHours, agg))
                .cycleTimeSec(numericAgg(points, MachineTelemetryEntity::getCycleTimeSec, agg))
                .outputCount(intAgg(points, MachineTelemetryEntity::getOutputCount, agg))
                .goodCount(intAgg(points, MachineTelemetryEntity::getGoodCount, agg))
                .rejectCount(intAgg(points, MachineTelemetryEntity::getRejectCount, agg))
                .spindleSpeedRpm(numericAgg(points, MachineTelemetryEntity::getSpindleSpeedRpm, agg))
                .feedRateMmMin(numericAgg(points, MachineTelemetryEntity::getFeedRateMmMin, agg))
                .idealCycleTimeSec(numericAgg(points, MachineTelemetryEntity::getIdealCycleTimeSec, agg))
                .spindleLoadPct(numericAgg(points, MachineTelemetryEntity::getSpindleLoadPct, agg))
                .servoLoadPct(numericAgg(points, MachineTelemetryEntity::getServoLoadPct, agg))
                .cuttingSpeedMMin(numericAgg(points, MachineTelemetryEntity::getCuttingSpeedMMin, agg))
                .depthOfCutMm(numericAgg(points, MachineTelemetryEntity::getDepthOfCutMm, agg))
                .feedPerToothMm(numericAgg(points, MachineTelemetryEntity::getFeedPerToothMm, agg))
                .widthOfCutMm(numericAgg(points, MachineTelemetryEntity::getWidthOfCutMm, agg))
                .materialRemovalRateCm3Min(numericAgg(points, MachineTelemetryEntity::getMaterialRemovalRateCm3Min, agg))
                .weldingCurrentA(numericAgg(points, MachineTelemetryEntity::getWeldingCurrentA, agg))
                .axisLoadPct(numericAgg(points, MachineTelemetryEntity::getAxisLoadPct, agg))
                .qualityScore(numericAgg(points, MachineTelemetryEntity::getQualityScore, agg))
                .sampleCount(points.size())
                .missing(false)
                .gapDetected(false)
                .build();
    }

    private Double numericAgg(List<MachineTelemetryEntity> points, Function<MachineTelemetryEntity, Double> getter, String agg) {
        var values = points.stream().map(getter).filter(Objects::nonNull).mapToDouble(Double::doubleValue);
        OptionalDouble result = switch (agg.toLowerCase()) {
            case "max" -> values.max();
            case "min" -> values.min();
            default -> values.average();
        };
        return result.isPresent() ? result.getAsDouble() : null;
    }

    private Integer intAgg(List<MachineTelemetryEntity> points, Function<MachineTelemetryEntity, Integer> getter, String agg) {
        var values = points.stream().map(getter).filter(Objects::nonNull).mapToInt(Integer::intValue);
        OptionalInt result = switch (agg.toLowerCase()) {
            case "max" -> values.max();
            case "min" -> values.min();
            default -> values.max();
        };
        return result.isPresent() ? result.getAsInt() : null;
    }

    private Duration parseInterval(String interval) {
        if (interval == null) {
            return Duration.ofMinutes(1);
        }
        return switch (interval.toLowerCase()) {
            case "1m" -> Duration.ofMinutes(1);
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "30m" -> Duration.ofMinutes(30);
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "12h" -> Duration.ofHours(12);
            case "1d" -> Duration.ofDays(1);
            default -> Duration.ofMinutes(1);
        };
    }

    private List<String> normalizeMetrics(List<String> metrics) {
        if (metrics == null) {
            return List.of();
        }
        return metrics.stream()
                .filter(Objects::nonNull)
                .flatMap(metric -> java.util.Arrays.stream(metric.split(",")))
                .map(String::trim)
                .filter(metric -> !metric.isBlank())
                .distinct()
                .toList();
    }

    private TelemetryHistoryPointResponse filterMetrics(TelemetryHistoryPointResponse point, List<String> requestedMetrics) {
        if (requestedMetrics == null || requestedMetrics.isEmpty()) {
            return point;
        }

        var selected = requestedMetrics.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        return TelemetryHistoryPointResponse.builder()
                .ts(point.ts())
                .bucketEnd(point.bucketEnd())
                .machineState(point.machineState())
                .connectionStatus(point.connectionStatus())
                .powerKw(selected.contains("powerkw") ? point.powerKw() : null)
                .temperatureC(selected.contains("temperaturec") ? point.temperatureC() : null)
                .vibrationMmS(selected.contains("vibrationmms") ? point.vibrationMmS() : null)
                .runtimeHours(selected.contains("runtimehours") ? point.runtimeHours() : null)
                .cycleTimeSec(selected.contains("cycletimesec") ? point.cycleTimeSec() : null)
                .outputCount(selected.contains("outputcount") ? point.outputCount() : null)
                .goodCount(selected.contains("goodcount") ? point.goodCount() : null)
                .rejectCount(selected.contains("rejectcount") ? point.rejectCount() : null)
                .spindleSpeedRpm(selected.contains("spindlespeedrpm") ? point.spindleSpeedRpm() : null)
                .feedRateMmMin(selected.contains("feedratemmmin") ? point.feedRateMmMin() : null)
                .idealCycleTimeSec(selected.contains("idealcycletimesec") ? point.idealCycleTimeSec() : null)
                .spindleLoadPct(selected.contains("spindleloadpct") ? point.spindleLoadPct() : null)
                .servoLoadPct(selected.contains("servoloadpct") ? point.servoLoadPct() : null)
                .cuttingSpeedMMin(selected.contains("cuttingspeedmmin") ? point.cuttingSpeedMMin() : null)
                .depthOfCutMm(selected.contains("depthofcutmm") ? point.depthOfCutMm() : null)
                .feedPerToothMm(selected.contains("feedpertoothmm") ? point.feedPerToothMm() : null)
                .widthOfCutMm(selected.contains("widthofcutmm") ? point.widthOfCutMm() : null)
                .materialRemovalRateCm3Min(selected.contains("materialremovalratecm3min") ? point.materialRemovalRateCm3Min() : null)
                .weldingCurrentA(selected.contains("weldingcurrenta") ? point.weldingCurrentA() : null)
                .axisLoadPct(selected.contains("axisloadpct") ? point.axisLoadPct() : null)
                .qualityScore(point.qualityScore())
                .sampleCount(point.sampleCount())
                .missing(point.missing())
                .gapDetected(point.gapDetected())
                .build();
    }

    private MachineDetailResponse toDetail(MachineEntity m) {
        String operationalState = resolveOperationalState(m, null);
        String displayState = resolveDisplayState(operationalState, m.getConnectionState());
        return MachineDetailResponse.builder()
                .id(m.getId())
                .code(m.getCode())
                .name(m.getName())
                .type(m.getType())
                .vendor(m.getVendor())
                .model(m.getModel())
                .lineId(m.getLineId())
                .plantId(m.getPlantId())
                .status(displayState)
                .operationalState(operationalState)
                .displayState(displayState)
                .connectionState(m.getConnectionState() != null ? m.getConnectionState() : "OFFLINE")
                .connectionUnstable(Boolean.TRUE.equals(m.getConnectionUnstable()))
                .lastSeenAt(m.getLastSeenAt())
                .dataFreshnessSec(dataFreshnessSec(m))
                .connectionReason(m.getConnectionReason())
                .connectionScope(m.getConnectionScope())
                .isEnabled(m.getIsEnabled())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private MachineSnapshotResponse toSnapshot(MachineTelemetryEntity t, MachineEntity m) {
        Long freshnessSec = dataFreshnessSec(m);

        return MachineSnapshotResponse.builder()
                .machineId(t.getMachineId())
                .machineCode(m.getCode())
                .machineName(m.getName())
                .ts(t.getTs())
                .connectionStatus(t.getConnectionStatus())
                .connectionState(m.getConnectionState())
                .connectionUnstable(Boolean.TRUE.equals(m.getConnectionUnstable()))
                .connectionReason(m.getConnectionReason())
                .connectionScope(m.getConnectionScope())
                .lastSeenAt(m.getLastSeenAt())
                .dataFreshnessSec(freshnessSec)
                .machineState(t.getMachineState())
                .operationMode(t.getOperationMode())
                .programName(t.getProgramName())
                .cycleRunning(t.getCycleRunning())
                .powerKw(t.getPowerKw())
                .temperatureC(t.getTemperatureC())
                .vibrationMmS(t.getVibrationMmS())
                .runtimeHours(t.getRuntimeHours())
                .cycleTimeSec(t.getCycleTimeSec())
                .outputCount(t.getOutputCount())
                .goodCount(t.getGoodCount())
                .rejectCount(t.getRejectCount())
                .spindleSpeedRpm(t.getSpindleSpeedRpm())
                .feedRateMmMin(t.getFeedRateMmMin())
                .idealCycleTimeSec(t.getIdealCycleTimeSec())
                .spindleLoadPct(t.getSpindleLoadPct())
                .servoLoadPct(t.getServoLoadPct())
                .cuttingSpeedMMin(t.getCuttingSpeedMMin())
                .depthOfCutMm(t.getDepthOfCutMm())
                .feedPerToothMm(t.getFeedPerToothMm())
                .widthOfCutMm(t.getWidthOfCutMm())
                .materialRemovalRateCm3Min(t.getMaterialRemovalRateCm3Min())
                .weldingCurrentA(t.getWeldingCurrentA())
                .build();
    }

    private MachineSnapshotResponse emptySnapshot(MachineEntity m) {
        Long freshnessSec = dataFreshnessSec(m);

        return MachineSnapshotResponse.builder()
                .machineId(m.getId())
                .machineCode(m.getCode())
                .machineName(m.getName())
                .connectionStatus("OFFLINE")
                .connectionState(m.getConnectionState() != null ? m.getConnectionState() : "OFFLINE")
                .connectionUnstable(Boolean.TRUE.equals(m.getConnectionUnstable()))
                .connectionReason(m.getConnectionReason())
                .connectionScope(m.getConnectionScope())
                .lastSeenAt(m.getLastSeenAt())
                .dataFreshnessSec(freshnessSec)
                .machineState(resolveDisplayState(resolveOperationalState(m, null), m.getConnectionState()))
                .build();
    }

    private MachineOverviewResponse toOverview(MachineEntity machine, MachineTelemetryEntity latestTelemetry) {
        String connectionState = machine.getConnectionState() != null ? machine.getConnectionState() : "OFFLINE";
        String operationalState = resolveOperationalState(machine, latestTelemetry);
        String displayState = resolveDisplayState(operationalState, connectionState);

        var latestOee = oeeRepo.findFirstByMachineIdAndBucketTypeOrderByBucketStartDesc(machine.getId(), "HOUR");
        var latestHealth = healthRepo.findFirstByMachineIdOrderByBucketStartDesc(machine.getId());
        var prediction = maintenancePredictionRepo.findFirstByMachineIdOrderByTsDesc(machine.getId());
        var latestToolUsage = toolUsageRepo.findByMachineIdOrderByTsDesc(machine.getId()).stream().findFirst();
        var latestEnergy = energyTelemetryRepo.findFirstByMachineIdOrderByTsDesc(machine.getId()).orElse(null);
        var latestMaintenance = maintenanceTelemetryRepo.findFirstByMachineIdOrderByTsDesc(machine.getId()).orElse(null);

        return MachineOverviewResponse.builder()
                .machineId(machine.getId())
                .machineCode(machine.getCode())
                .machineName(machine.getName())
                .type(machine.getType())
                .category(machine.getType() != null ? machine.getType().toLowerCase() : null)
                .vendor(machine.getVendor())
                .model(machine.getModel())
                .lineId(machine.getLineId())
                .plantId(machine.getPlantId())
                .operationalState(operationalState)
                .displayState(displayState)
                .operationMode(latestTelemetry != null ? latestTelemetry.getOperationMode() : null)
                .programName(latestTelemetry != null ? latestTelemetry.getProgramName() : null)
                .cycleRunning(latestTelemetry != null ? latestTelemetry.getCycleRunning() : null)
                .connectionState(connectionState)
                .connectionUnstable(Boolean.TRUE.equals(machine.getConnectionUnstable()))
                .lastSeenAt(machine.getLastSeenAt())
                .dataFreshnessSec(dataFreshnessSec(machine))
                .connectionReason(machine.getConnectionReason())
                .connectionScope(machine.getConnectionScope())
                .telemetry(toOverviewTelemetry(latestTelemetry, latestEnergy, latestMaintenance))
                .analytics(MachineOverviewAnalytics.builder()
                        .oee(latestOee.map(o -> o.getOee()).orElse(null))
                        .availability(latestOee.map(o -> o.getAvailability()).orElse(null))
                        .performance(latestOee.map(o -> o.getPerformance()).orElse(null))
                        .quality(latestOee.map(o -> o.getQuality()).orElse(null))
                        .machineHealth(latestHealth.map(h -> h.getHealthScore()).orElse(null))
                        .anomalyScore(null)
                        .build())
                .maintenance(toOverviewMaintenance(prediction.orElse(null)))
                .tool(toOverviewTool(latestToolUsage.orElse(null)))
                .build();
    }

    private MachineOverviewTelemetry toOverviewTelemetry(
            MachineTelemetryEntity telemetry,
            com.rmsys.backend.domain.entity.EnergyTelemetryEntity energy,
            com.rmsys.backend.domain.entity.MaintenanceTelemetryEntity maintenance) {
        if (telemetry == null && energy == null && maintenance == null) {
            return null;
        }
        return MachineOverviewTelemetry.builder()
                .powerKw(firstNonNull(telemetry != null ? telemetry.getPowerKw() : null, energy != null ? energy.getPowerKw() : null))
                .temperatureC(telemetry != null ? telemetry.getTemperatureC() : null)
                .vibrationMmS(firstNonNull(telemetry != null ? telemetry.getVibrationMmS() : null, maintenance != null ? maintenance.getVibrationMmS() : null))
                .runtimeHours(firstNonNull(telemetry != null ? telemetry.getRuntimeHours() : null, maintenance != null ? maintenance.getRuntimeHours() : null))
                .cycleTimeSec(telemetry != null ? telemetry.getCycleTimeSec() : null)
                .outputCount(telemetry != null ? telemetry.getOutputCount() : null)
                .goodCount(telemetry != null ? telemetry.getGoodCount() : null)
                .rejectCount(telemetry != null ? telemetry.getRejectCount() : null)
                .spindleSpeedRpm(telemetry != null ? telemetry.getSpindleSpeedRpm() : null)
                .feedRateMmMin(telemetry != null ? telemetry.getFeedRateMmMin() : null)
                .voltageV(energy != null ? energy.getVoltageV() : null)
                .currentA(energy != null ? energy.getCurrentA() : null)
                .powerFactor(energy != null ? energy.getPowerFactor() : null)
                .frequencyHz(energy != null ? energy.getFrequencyHz() : null)
                .energyKwhShift(energy != null ? energy.getEnergyKwhShift() : null)
                .energyKwhDay(energy != null ? energy.getEnergyKwhDay() : null)
                .motorTemperatureC(maintenance != null ? maintenance.getMotorTemperatureC() : null)
                .bearingTemperatureC(maintenance != null ? maintenance.getBearingTemperatureC() : null)
                .cabinetTemperatureC(maintenance != null ? maintenance.getCabinetTemperatureC() : null)
                .servoOnHours(maintenance != null ? maintenance.getServoOnHours() : null)
                .startStopCount(maintenance != null ? maintenance.getStartStopCount() : null)
                .lubricationLevelPct(maintenance != null ? maintenance.getLubricationLevelPct() : null)
                .batteryLow(maintenance != null ? maintenance.getBatteryLow() : null)
                .idealCycleTimeSec(telemetry != null ? telemetry.getIdealCycleTimeSec() : null)
                .spindleLoadPct(telemetry != null ? telemetry.getSpindleLoadPct() : null)
                .servoLoadPct(telemetry != null ? telemetry.getServoLoadPct() : null)
                .cuttingSpeedMMin(telemetry != null ? telemetry.getCuttingSpeedMMin() : null)
                .depthOfCutMm(telemetry != null ? telemetry.getDepthOfCutMm() : null)
                .feedPerToothMm(telemetry != null ? telemetry.getFeedPerToothMm() : null)
                .widthOfCutMm(telemetry != null ? telemetry.getWidthOfCutMm() : null)
                .materialRemovalRateCm3Min(telemetry != null ? telemetry.getMaterialRemovalRateCm3Min() : null)
                .weldingCurrentA(telemetry != null ? telemetry.getWeldingCurrentA() : null)
                .build();
    }

    private <T> T firstNonNull(T left, T right) {
        return left != null ? left : right;
    }

    private MachineOverviewMaintenance toOverviewMaintenance(com.rmsys.backend.domain.entity.MaintenancePredictionEntity prediction) {
        if (prediction == null) {
            return null;
        }
        Integer dueDays = null;
        if (prediction.getNextMaintenanceDate() != null) {
            dueDays = Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(Instant.now(), prediction.getNextMaintenanceDate())));
        }
        return MachineOverviewMaintenance.builder()
                .maintenanceDueDays(dueDays)
                .remainingMaintenanceHours(prediction.getRemainingHoursToService())
                .nextMaintenanceDate(prediction.getNextMaintenanceDate())
                .riskLevel(prediction.getRiskLevel())
                .predictedFailureWindow(null)
                .recommendation(prediction.getRecommendedAction())
                .build();
    }

    private MachineOverviewTool toOverviewTool(com.rmsys.backend.domain.entity.ToolUsageTelemetryEntity toolUsage) {
        if (toolUsage == null) {
            return null;
        }
        return MachineOverviewTool.builder()
                .toolCode(toolUsage.getToolCode())
                .remainingToolLifePct(toolUsage.getRemainingLifePct())
                .wearLevel(toolUsage.getWearLevel())
                .build();
    }

    private String resolveOperationalState(MachineEntity machine, MachineTelemetryEntity latestTelemetry) {
        if (latestTelemetry != null && latestTelemetry.getMachineState() != null && !latestTelemetry.getMachineState().isBlank()) {
            return latestTelemetry.getMachineState();
        }
        return machine.getStatus();
    }

    private String resolveDisplayState(String operationalState, String connectionState) {
        String normalizedConnection = connectionState == null ? "OFFLINE" : connectionState;
        if (!"ONLINE".equalsIgnoreCase(normalizedConnection)) {
            return normalizedConnection;
        }
        return operationalState;
    }

    private Long dataFreshnessSec(MachineEntity machine) {
        if (machine.getLastSeenAt() == null) {
            return null;
        }
        return Duration.between(machine.getLastSeenAt(), Instant.now()).toSeconds();
    }
}

