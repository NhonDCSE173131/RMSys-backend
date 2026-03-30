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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

    @Override
    @Transactional
    public void aggregateOee() {
        var now = Instant.now();
        var hourAgo = now.minus(1, ChronoUnit.HOURS);

        for (var machine : machineRepo.findAll()) {
            var records = telemetryRepo.findByMachineIdAndTsBetweenOrderByTsDesc(machine.getId(), hourAgo, now);
            if (records.isEmpty()) continue;

            int totalSec = 3600;
            long runningSec = records.stream().filter(t -> "RUNNING".equals(t.getMachineState())).count() * 2L; // approx by sampling
            int stopSec = totalSec - (int) runningSec;

            int totalOutput = records.stream().mapToInt(t -> t.getOutputCount() != null ? t.getOutputCount() : 0).max().orElse(0);
            int goodCount = records.stream().mapToInt(t -> t.getGoodCount() != null ? t.getGoodCount() : 0).max().orElse(0);
            int rejectCount = records.stream().mapToInt(t -> t.getRejectCount() != null ? t.getRejectCount() : 0).max().orElse(0);

            double availability = totalSec > 0 ? (double) runningSec / totalSec * 100 : 0;
            double idealCycleTime = 30.0; // seconds
            double performance = runningSec > 0 ? (totalOutput * idealCycleTime) / runningSec * 100 : 0;
            double quality = totalOutput > 0 ? (double) goodCount / totalOutput * 100 : 100;
            double oee = availability * performance * quality / 10000;

            oeeRepo.save(OeeSnapshotEntity.builder()
                    .machineId(machine.getId()).bucketStart(hourAgo).bucketType("HOUR")
                    .availability(Math.min(availability, 100)).performance(Math.min(performance, 100))
                    .quality(Math.min(quality, 100)).oee(Math.min(oee, 100))
                    .runtimeSec((int) runningSec).stopSec(stopSec)
                    .goodCount(goodCount).rejectCount(rejectCount)
                    .actualCycleTimeSec(runningSec > 0 ? (double) runningSec / Math.max(totalOutput, 1) : 0)
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
            long activeAlarms = alarmRepo.countByIsActiveTrue();
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

