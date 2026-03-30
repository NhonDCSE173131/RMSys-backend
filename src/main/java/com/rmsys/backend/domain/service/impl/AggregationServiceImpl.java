package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.domain.entity.MachineHealthSnapshotEntity;
import com.rmsys.backend.domain.entity.MaintenancePredictionEntity;
import com.rmsys.backend.domain.entity.OeeSnapshotEntity;
import com.rmsys.backend.domain.repository.*;
import com.rmsys.backend.domain.service.AggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregationServiceImpl implements AggregationService {

    private final MachineRepository machineRepo;
    private final MachineTelemetryRepository telemetryRepo;
    private final AlarmEventRepository alarmRepo;
    private final OeeSnapshotRepository oeeRepo;
    private final MachineHealthSnapshotRepository healthRepo;
    private final MaintenanceTelemetryRepository maintenanceTelemetryRepo;
    private final MaintenancePredictionRepository maintenancePredictionRepo;

    @Value("${app.aggregation.default-sample-sec:1}")
    private long defaultSampleSec;

    @Value("${app.aggregation.realtime-window-sec:60}")
    private long realtimeWindowSec;

    @Override
    @Transactional
    public void aggregateRealtimeRollingKpis() {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(Math.max(5, realtimeWindowSec));

        for (var machine : machineRepo.findAll()) {
            var records = telemetryRepo.findByMachineIdAndTsBetweenOrderByTsDesc(machine.getId(), windowStart, now);
            if (records.isEmpty()) {
                continue;
            }

            var asc = new ArrayList<>(records);
            asc.sort(Comparator.comparing(r -> r.getTs()));

            int totalSec = Math.max(1, Math.toIntExact(Duration.between(windowStart, now).toSeconds()));
            long runningSec = estimateRunningSeconds(asc, now);
            runningSec = Math.min(runningSec, totalSec);

            int totalOutput = asc.stream().mapToInt(t -> t.getOutputCount() != null ? t.getOutputCount() : 0).max().orElse(0);
            int goodCount = asc.stream().mapToInt(t -> t.getGoodCount() != null ? t.getGoodCount() : 0).max().orElse(0);
            int rejectCount = asc.stream().mapToInt(t -> t.getRejectCount() != null ? t.getRejectCount() : 0).max().orElse(0);

            Double idealCycleTime = resolveIdealCycleTimeSec(asc);
            double availability = (double) runningSec / totalSec * 100;
            Double performance = (idealCycleTime != null && runningSec > 0)
                    ? (totalOutput * idealCycleTime) / runningSec * 100
                    : null;
            Double quality = totalOutput > 0 ? (double) goodCount / totalOutput * 100 : null;
            Double oee = (performance != null && quality != null)
                    ? availability * performance * quality / 10000
                    : null;

            oeeRepo.save(OeeSnapshotEntity.builder()
                    .machineId(machine.getId())
                    .bucketStart(windowStart)
                    .bucketType("ROLLING_60S")
                    .availability(Math.min(availability, 100))
                    .performance(performance != null ? Math.min(performance, 100) : null)
                    .quality(quality != null ? Math.min(quality, 100) : null)
                    .oee(oee != null ? Math.min(oee, 100) : null)
                    .runtimeSec((int) runningSec)
                    .stopSec(totalSec - (int) runningSec)
                    .goodCount(goodCount)
                    .rejectCount(rejectCount)
                    .actualCycleTimeSec(totalOutput > 0 && runningSec > 0 ? (double) runningSec / totalOutput : null)
                    .idealCycleTimeSec(idealCycleTime)
                    .build());
        }
    }

    @Override
    @Transactional
    public void aggregateOee() {
        var now = Instant.now();
        var hourAgo = now.minus(1, ChronoUnit.HOURS);

        for (var machine : machineRepo.findAll()) {
            var records = telemetryRepo.findByMachineIdAndTsBetweenOrderByTsDesc(machine.getId(), hourAgo, now);
            if (records.isEmpty()) continue;
            var asc = new ArrayList<>(records);
            asc.sort(Comparator.comparing(r -> r.getTs()));

            int totalSec = Math.max(1, Math.toIntExact(Duration.between(hourAgo, now).toSeconds()));
            long runningSec = estimateRunningSeconds(asc, now);
            runningSec = Math.min(runningSec, totalSec);
            int stopSec = totalSec - (int) runningSec;

            int totalOutput = asc.stream().mapToInt(t -> t.getOutputCount() != null ? t.getOutputCount() : 0).max().orElse(0);
            int goodCount = asc.stream().mapToInt(t -> t.getGoodCount() != null ? t.getGoodCount() : 0).max().orElse(0);
            int rejectCount = asc.stream().mapToInt(t -> t.getRejectCount() != null ? t.getRejectCount() : 0).max().orElse(0);

            Double idealCycleTime = resolveIdealCycleTimeSec(asc);
            double availability = (double) runningSec / totalSec * 100;
            Double performance = (idealCycleTime != null && runningSec > 0)
                    ? (totalOutput * idealCycleTime) / runningSec * 100
                    : null;
            Double quality = totalOutput > 0 ? (double) goodCount / totalOutput * 100 : null;
            Double oee = (performance != null && quality != null)
                    ? availability * performance * quality / 10000
                    : null;

            oeeRepo.save(OeeSnapshotEntity.builder()
                    .machineId(machine.getId()).bucketStart(hourAgo).bucketType("HOUR")
                    .availability(Math.min(availability, 100))
                    .performance(performance != null ? Math.min(performance, 100) : null)
                    .quality(quality != null ? Math.min(quality, 100) : null)
                    .oee(oee != null ? Math.min(oee, 100) : null)
                    .runtimeSec((int) runningSec).stopSec(stopSec)
                    .goodCount(goodCount).rejectCount(rejectCount)
                    .actualCycleTimeSec(totalOutput > 0 && runningSec > 0 ? (double) runningSec / totalOutput : null)
                    .idealCycleTimeSec(idealCycleTime).build());
        }
        log.info("OEE aggregation completed");
    }

    @Override
    @Transactional
    public void aggregateHealth() {
        var now = Instant.now();

        for (var machine : machineRepo.findAll()) {
            var latest = telemetryRepo.findFirstByMachineIdOrderByTsDesc(machine.getId());
            if (latest.isEmpty()) continue;

            var t = latest.get();
            double tempScore = scoreInverse(t.getTemperatureC(), 40, 80);
            double vibScore = scoreInverse(t.getVibrationMmS(), 2, 7);
            long activeAlarms = alarmRepo.countByMachineIdAndIsActiveTrue(machine.getId());
            double alarmScore = Math.max(0, 100 - activeAlarms * 15);
            double runtimeScore = 80; // simplified

            double health = (tempScore * 0.3 + vibScore * 0.3 + alarmScore * 0.2 + runtimeScore * 0.2);
            String risk = health > 75 ? "LOW" : health > 50 ? "MEDIUM" : health > 25 ? "HIGH" : "CRITICAL";
            String reason = health < 50 ? determineMainReason(tempScore, vibScore, alarmScore) : null;

            healthRepo.save(MachineHealthSnapshotEntity.builder()
                    .machineId(machine.getId()).bucketStart(now)
                    .healthScore(health).riskLevel(risk).mainReason(reason)
                    .temperatureScore(tempScore).vibrationScore(vibScore)
                    .alarmScore(alarmScore).runtimeScore(runtimeScore).build());
        }
        log.info("Health aggregation completed");
    }

    private double scoreInverse(Double value, double good, double bad) {
        if (value == null) return 80;
        if (value <= good) return 100;
        if (value >= bad) return 0;
        return 100 * (bad - value) / (bad - good);
    }

    private String determineMainReason(double temp, double vib, double alarm) {
        if (temp <= vib && temp <= alarm) return "High temperature";
        if (vib <= alarm) return "High vibration";
        return "Active alarms";
    }

    private long estimateRunningSeconds(java.util.List<com.rmsys.backend.domain.entity.MachineTelemetryEntity> asc, Instant now) {
        if (asc.isEmpty()) {
            return 0;
        }

        long runningSec = 0;
        for (int i = 0; i < asc.size(); i++) {
            var current = asc.get(i);
            Instant nextTs = (i + 1 < asc.size()) ? asc.get(i + 1).getTs() : now;
            long deltaSec = Math.max(1, Duration.between(current.getTs(), nextTs).toSeconds());
            if ("RUNNING".equalsIgnoreCase(current.getMachineState())) {
                runningSec += Math.min(deltaSec, Math.max(1, defaultSampleSec * 10));
            }
        }
        return runningSec;
    }

    private Double resolveIdealCycleTimeSec(java.util.List<com.rmsys.backend.domain.entity.MachineTelemetryEntity> asc) {
        var idealAvg = asc.stream()
                .map(com.rmsys.backend.domain.entity.MachineTelemetryEntity::getIdealCycleTimeSec)
                .filter(v -> v != null && v > 0)
                .mapToDouble(Double::doubleValue)
                .average();
        if (idealAvg.isPresent()) {
            return idealAvg.getAsDouble();
        }

        var cycleAvg = asc.stream()
                .map(com.rmsys.backend.domain.entity.MachineTelemetryEntity::getCycleTimeSec)
                .filter(v -> v != null && v > 0)
                .mapToDouble(Double::doubleValue)
                .average();
        return cycleAvg.isPresent() ? cycleAvg.getAsDouble() : null;
    }

    @Override
    @Transactional
    public void aggregateMaintenancePredictions() {
        Instant now = Instant.now();

        for (var machine : machineRepo.findAll()) {
            var latestMaintenance = maintenanceTelemetryRepo.findFirstByMachineIdOrderByTsDesc(machine.getId());
            var latestHealth = healthRepo.findFirstByMachineIdOrderByBucketStartDesc(machine.getId());
            if (latestMaintenance.isEmpty() && latestHealth.isEmpty()) {
                continue;
            }

            Double remainingHours = estimateRemainingHours(latestMaintenance.orElse(null), latestHealth.orElse(null));
            String riskLevel = estimateRiskLevel(remainingHours, latestHealth.map(MachineHealthSnapshotEntity::getHealthScore).orElse(null));
            Instant nextMaintenanceDate = remainingHours == null ? null : now.plus(Math.max(1, Math.round(remainingHours)), ChronoUnit.HOURS);
            Double predictedFailureRisk = estimateFailureRisk(remainingHours, latestHealth.map(MachineHealthSnapshotEntity::getHealthScore).orElse(null));

            maintenancePredictionRepo.save(MaintenancePredictionEntity.builder()
                    .machineId(machine.getId())
                    .ts(now)
                    .remainingHoursToService(remainingHours)
                    .predictedFailureRisk(predictedFailureRisk)
                    .riskLevel(riskLevel)
                    .recommendedAction(recommendedAction(riskLevel))
                    .nextMaintenanceDate(nextMaintenanceDate)
                    .build());
        }

        log.info("Maintenance prediction aggregation completed");
    }

    private Double estimateRemainingHours(com.rmsys.backend.domain.entity.MaintenanceTelemetryEntity maintenance,
                                          MachineHealthSnapshotEntity health) {
        if (maintenance == null && health == null) {
            return null;
        }

        double base = 240;
        if (maintenance != null) {
            if (maintenance.getLubricationLevelPct() != null) {
                base -= (100 - maintenance.getLubricationLevelPct()) * 1.5;
            }
            if (Boolean.TRUE.equals(maintenance.getBatteryLow())) {
                base -= 80;
            }
            if (maintenance.getBearingTemperatureC() != null && maintenance.getBearingTemperatureC() > 75) {
                base -= 60;
            }
            if (maintenance.getVibrationMmS() != null && maintenance.getVibrationMmS() > 6) {
                base -= 40;
            }
        }
        if (health != null && health.getHealthScore() != null) {
            base -= Math.max(0, 80 - health.getHealthScore()) * 1.2;
        }
        return Math.max(4, Math.round(base * 100.0) / 100.0);
    }

    private String estimateRiskLevel(Double remainingHours, Double healthScore) {
        if (remainingHours != null) {
            if (remainingHours < 24) return "CRITICAL";
            if (remainingHours < 72) return "HIGH";
            if (remainingHours < 168) return "MEDIUM";
        }
        if (healthScore != null) {
            if (healthScore < 35) return "CRITICAL";
            if (healthScore < 55) return "HIGH";
            if (healthScore < 75) return "MEDIUM";
        }
        return "LOW";
    }

    private Double estimateFailureRisk(Double remainingHours, Double healthScore) {
        double risk = 0;
        if (remainingHours != null) {
            risk += Math.max(0, 100 - Math.min(100, remainingHours / 2.4));
        }
        if (healthScore != null) {
            risk += Math.max(0, 100 - healthScore);
        }
        if (risk == 0) {
            return null;
        }
        return Math.min(100, Math.round((risk / 2.0) * 100.0) / 100.0);
    }

    private String recommendedAction(String riskLevel) {
        return switch (riskLevel) {
            case "CRITICAL" -> "Stop machine and perform urgent maintenance inspection";
            case "HIGH" -> "Schedule maintenance within 24h";
            case "MEDIUM" -> "Prepare maintenance materials and monitor closely";
            default -> "Continue operation and follow standard PM plan";
        };
    }
}

