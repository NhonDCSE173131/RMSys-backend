package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.MachineDetailResponse;
import com.rmsys.backend.api.response.MachineSnapshotResponse;
import com.rmsys.backend.api.response.TelemetryHistoryPointResponse;
import com.rmsys.backend.api.response.TelemetrySeriesResponse;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineTelemetryEntity;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.repository.MachineTelemetryRepository;
import com.rmsys.backend.domain.service.MachineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
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

    @Override
    public List<MachineDetailResponse> getAllMachines() {
        return machineRepo.findAll().stream().map(this::toDetail).toList();
    }

    @Override
    public MachineDetailResponse getMachineDetail(UUID machineId) {
        return toDetail(findMachine(machineId));
    }

    @Override
    public MachineSnapshotResponse getLatestSnapshot(UUID machineId) {
        var machine = findMachine(machineId);
        return telemetryRepo.findFirstByMachineIdOrderByTsDesc(machineId)
                .map(t -> toSnapshot(t, machine))
                .orElse(emptySnapshot(machine));
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
    public TelemetrySeriesResponse getTelemetryHistory(UUID machineId, Instant from, Instant to, String interval, String aggregation) {
        var machine = findMachine(machineId);
        var rawDesc = telemetryRepo.findByMachineIdAndTsBetweenOrderByTsDesc(machineId, from, to);
        var ascending = new ArrayList<>(rawDesc);
        Collections.reverse(ascending);

        String resolvedAgg = (aggregation != null && !aggregation.isBlank()) ? aggregation : "avg";

        if (interval == null || interval.isBlank() || "raw".equalsIgnoreCase(interval)) {
            var points = ascending.stream().map(this::toHistoryPoint).toList();
            return TelemetrySeriesResponse.builder()
                    .machineId(machineId)
                    .machineCode(machine.getCode())
                    .machineName(machine.getName())
                    .from(from)
                    .to(to)
                    .interval("raw")
                    .aggregation("none")
                    .totalPoints(points.size())
                    .points(points)
                    .build();
        }

        Duration bucketDuration = parseInterval(interval);
        var points = bucket(ascending, from, bucketDuration, resolvedAgg);
        return TelemetrySeriesResponse.builder()
                .machineId(machineId)
                .machineCode(machine.getCode())
                .machineName(machine.getName())
                .from(from)
                .to(to)
                .interval(interval)
                .aggregation(resolvedAgg)
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
                .axisLoadPct(t.getAxisLoadPct())
                .qualityScore(t.getQualityScore())
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
            return TelemetryHistoryPointResponse.builder().ts(bucketTs).build();
        }
        var last = points.get(points.size() - 1);
        if ("last".equalsIgnoreCase(agg)) {
            return TelemetryHistoryPointResponse.builder()
                    .ts(bucketTs)
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
                    .axisLoadPct(last.getAxisLoadPct())
                    .qualityScore(last.getQualityScore())
                    .build();
        }

        return TelemetryHistoryPointResponse.builder()
                .ts(bucketTs)
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
                .axisLoadPct(numericAgg(points, MachineTelemetryEntity::getAxisLoadPct, agg))
                .qualityScore(numericAgg(points, MachineTelemetryEntity::getQualityScore, agg))
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

    private MachineDetailResponse toDetail(MachineEntity m) {
        return MachineDetailResponse.builder()
                .id(m.getId())
                .code(m.getCode())
                .name(m.getName())
                .type(m.getType())
                .vendor(m.getVendor())
                .model(m.getModel())
                .lineId(m.getLineId())
                .plantId(m.getPlantId())
                .status(m.getStatus())
                .isEnabled(m.getIsEnabled())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private MachineSnapshotResponse toSnapshot(MachineTelemetryEntity t, MachineEntity m) {
        var now = Instant.now();
        Long freshnessSec = m.getLastSeenAt() == null ? null : Duration.between(m.getLastSeenAt(), now).toSeconds();

        return MachineSnapshotResponse.builder()
                .machineId(t.getMachineId())
                .machineCode(m.getCode())
                .machineName(m.getName())
                .ts(t.getTs())
                .connectionStatus(t.getConnectionStatus())
                .connectionState(m.getConnectionState())
                .connectionUnstable(Boolean.TRUE.equals(m.getConnectionUnstable()))
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
                .build();
    }

    private MachineSnapshotResponse emptySnapshot(MachineEntity m) {
        var now = Instant.now();
        Long freshnessSec = m.getLastSeenAt() == null ? null : Duration.between(m.getLastSeenAt(), now).toSeconds();

        return MachineSnapshotResponse.builder()
                .machineId(m.getId())
                .machineCode(m.getCode())
                .machineName(m.getName())
                .connectionStatus("OFFLINE")
                .connectionState(m.getConnectionState() != null ? m.getConnectionState() : "OFFLINE")
                .connectionUnstable(Boolean.TRUE.equals(m.getConnectionUnstable()))
                .lastSeenAt(m.getLastSeenAt())
                .dataFreshnessSec(freshnessSec)
                .machineState("OFFLINE")
                .build();
    }
}

