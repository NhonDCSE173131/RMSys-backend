package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.MachineRealtimeSnapshotResponse;
import com.rmsys.backend.common.enumtype.ConnectionStatus;
import com.rmsys.backend.common.enumtype.MachineState;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineTelemetryEntity;
import com.rmsys.backend.domain.repository.*;
import com.rmsys.backend.domain.service.MachineRealtimeSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MachineRealtimeSnapshotServiceImpl implements MachineRealtimeSnapshotService {

    private final MachineRepository machineRepo;
    private final MachineTelemetryRepository telemetryRepo;
    private final EnergyTelemetryRepository energyRepo;
    private final OeeSnapshotRepository oeeRepo;
    private final MachineHealthSnapshotRepository healthRepo;
    private final MaintenancePredictionRepository predictionRepo;
    private final ToolUsageTelemetryRepository toolUsageRepo;
    private final AlarmEventRepository alarmRepo;

    private static final Set<String> CANONICAL_OPERATING_STATES = Set.of(
            MachineState.RUNNING.name(), MachineState.IDLE.name(),
            MachineState.WARMUP.name(), MachineState.STOPPED.name(),
            MachineState.EMERGENCY_STOP.name(), MachineState.MAINTENANCE.name());

    @Override
    public MachineRealtimeSnapshotResponse buildSnapshot(UUID machineId) {
        var machine = machineRepo.findById(machineId).orElse(null);
        if (machine == null) return null;
        var latestTelemetry = telemetryRepo.findFirstByMachineIdOrderByTsDesc(machineId).orElse(null);
        return buildSnapshotForMachine(machine, latestTelemetry);
    }

    @Override
    public List<MachineRealtimeSnapshotResponse> buildAllSnapshots() {
        var machines = machineRepo.findAll();
        var latestTelemetryMap = telemetryRepo.findLatestForAllMachines().stream()
                .collect(Collectors.toMap(MachineTelemetryEntity::getMachineId, t -> t, (l, r) -> l));

        return machines.stream()
                .map(m -> buildSnapshotForMachine(m, latestTelemetryMap.get(m.getId())))
                .filter(Objects::nonNull)
                .toList();
    }

    private MachineRealtimeSnapshotResponse buildSnapshotForMachine(MachineEntity machine, MachineTelemetryEntity latestTelemetry) {
        Instant now = Instant.now();
        UUID mid = machine.getId();

        // Connection
        String connectionState = normalizeConnectionState(machine.getConnectionState(), machine.getConnectionUnstable());
        boolean liveDataAvailable = ConnectionStatus.ONLINE.name().equals(connectionState)
                || ConnectionStatus.UNSTABLE.name().equals(connectionState);
        Long freshnessSec = machine.getLastSeenAt() != null
                ? Duration.between(machine.getLastSeenAt(), now).toSeconds() : null;

        // Operational state
        String operationalState = resolveOperationalState(machine, latestTelemetry);
        String displayState = resolveDisplayState(operationalState, connectionState);

        // OEE rolling — prefer ROLLING_60S, fallback to HOUR
        var rollingOee = oeeRepo.findFirstByMachineIdAndBucketTypeOrderByBucketStartDesc(mid, "ROLLING_60S");
        if (rollingOee.isEmpty()) {
            rollingOee = oeeRepo.findFirstByMachineIdAndBucketTypeOrderByBucketStartDesc(mid, "HOUR");
        }

        // Health
        var latestHealth = healthRepo.findFirstByMachineIdOrderByBucketStartDesc(mid);

        // Maintenance prediction
        var prediction = predictionRepo.findFirstByMachineIdOrderByTsDesc(mid);
        Integer maintenanceDueDays = null;
        if (prediction.isPresent() && prediction.get().getNextMaintenanceDate() != null) {
            maintenanceDueDays = Math.toIntExact(Math.max(0,
                    ChronoUnit.DAYS.between(now, prediction.get().getNextMaintenanceDate())));
        }

        // Tool usage
        var latestTool = toolUsageRepo.findByMachineIdOrderByTsDesc(mid).stream().findFirst();

        // Energy
        var latestEnergy = energyRepo.findFirstByMachineIdOrderByTsDesc(mid).orElse(null);

        // Anomaly score from recent telemetry
        var recentTelemetry = telemetryRepo.findRecentByMachineId(mid, now.minus(5, ChronoUnit.MINUTES));
        Double anomalyScore = computeAnomalyScore(recentTelemetry, latestTelemetry);

        // Predicted failure window
        String predictedFailureWindow = prediction.isPresent()
                ? resolvePredictedFailureWindow(prediction.get(), latestHealth.map(h -> h.getHealthScore()).orElse(null))
                : null;

        return MachineRealtimeSnapshotResponse.builder()
                .machineId(mid)
                .machineCode(machine.getCode())
                .machineName(machine.getName())
                .ts(latestTelemetry != null ? latestTelemetry.getTs() : now)

                // Connection
                .connectionState(connectionState)
                .connectionUnstable(Boolean.TRUE.equals(machine.getConnectionUnstable()))
                .lastSeenAt(machine.getLastSeenAt())
                .dataFreshnessSec(freshnessSec)
                .liveDataAvailable(liveDataAvailable)

                // Operational state
                .operationalState(operationalState)
                .displayState(displayState)
                .operationMode(latestTelemetry != null ? latestTelemetry.getOperationMode() : null)
                .programName(latestTelemetry != null ? latestTelemetry.getProgramName() : null)
                .cycleRunning(latestTelemetry != null ? latestTelemetry.getCycleRunning() : null)

                // Core telemetry
                .powerKw(latestTelemetry != null ? latestTelemetry.getPowerKw() : null)
                .temperatureC(latestTelemetry != null ? latestTelemetry.getTemperatureC() : null)
                .vibrationMmS(latestTelemetry != null ? latestTelemetry.getVibrationMmS() : null)
                .runtimeHours(latestTelemetry != null ? latestTelemetry.getRuntimeHours() : null)
                .cycleTimeSec(latestTelemetry != null ? latestTelemetry.getCycleTimeSec() : null)
                .idealCycleTimeSec(latestTelemetry != null ? latestTelemetry.getIdealCycleTimeSec() : null)
                .outputCount(latestTelemetry != null ? latestTelemetry.getOutputCount() : null)
                .goodCount(latestTelemetry != null ? latestTelemetry.getGoodCount() : null)
                .rejectCount(latestTelemetry != null ? latestTelemetry.getRejectCount() : null)
                .spindleSpeedRpm(latestTelemetry != null ? latestTelemetry.getSpindleSpeedRpm() : null)
                .feedRateMmMin(latestTelemetry != null ? latestTelemetry.getFeedRateMmMin() : null)
                .spindleLoadPct(latestTelemetry != null ? latestTelemetry.getSpindleLoadPct() : null)
                .servoLoadPct(latestTelemetry != null ? latestTelemetry.getServoLoadPct() : null)
                .cuttingSpeedMMin(latestTelemetry != null ? latestTelemetry.getCuttingSpeedMMin() : null)
                .depthOfCutMm(latestTelemetry != null ? latestTelemetry.getDepthOfCutMm() : null)
                .feedPerToothMm(latestTelemetry != null ? latestTelemetry.getFeedPerToothMm() : null)
                .widthOfCutMm(latestTelemetry != null ? latestTelemetry.getWidthOfCutMm() : null)
                .materialRemovalRateCm3Min(latestTelemetry != null ? latestTelemetry.getMaterialRemovalRateCm3Min() : null)
                .weldingCurrentA(latestTelemetry != null ? latestTelemetry.getWeldingCurrentA() : null)

                // Energy
                .voltageV(latestEnergy != null ? latestEnergy.getVoltageV() : null)
                .currentA(latestEnergy != null ? latestEnergy.getCurrentA() : null)
                .powerFactor(latestEnergy != null ? latestEnergy.getPowerFactor() : null)
                .frequencyHz(latestEnergy != null ? latestEnergy.getFrequencyHz() : null)
                .energyKwhShift(latestEnergy != null ? latestEnergy.getEnergyKwhShift() : null)
                .energyKwhDay(latestEnergy != null ? latestEnergy.getEnergyKwhDay() : null)

                // Tool
                .remainingToolLifePct(latestTool.map(t -> t.getRemainingLifePct()).orElse(null))
                .wearLevel(latestTool.map(t -> t.getWearLevel()).orElse(null))

                // OEE rolling
                .oee(rollingOee.map(o -> o.getOee()).orElse(null))
                .availability(rollingOee.map(o -> o.getAvailability()).orElse(null))
                .performance(rollingOee.map(o -> o.getPerformance()).orElse(null))
                .quality(rollingOee.map(o -> o.getQuality()).orElse(null))

                // Health & maintenance
                .machineHealth(latestHealth.map(h -> h.getHealthScore()).orElse(null))
                .anomalyScore(anomalyScore)
                .maintenanceDueDays(maintenanceDueDays)
                .remainingMaintenanceHours(prediction.map(p -> p.getRemainingHoursToService()).orElse(null))
                .nextMaintenanceDate(prediction.map(p -> p.getNextMaintenanceDate()).orElse(null))
                .maintenanceRisk(prediction.map(p -> p.getRiskLevel()).orElse(null))
                .predictedFailureWindow(predictedFailureWindow)
                .recommendation(prediction.map(p -> p.getRecommendedAction()).orElse(null))
                .build();
    }

    // ── State resolution helpers ──

    private String normalizeConnectionState(String connectionState, Boolean unstable) {
        String normalized = connectionState == null ? ConnectionStatus.OFFLINE.name() : connectionState.trim().toUpperCase();
        if (ConnectionStatus.ONLINE.name().equals(normalized) && Boolean.TRUE.equals(unstable)) {
            return ConnectionStatus.UNSTABLE.name();
        }
        return switch (normalized) {
            case "ONLINE", "STALE", "OFFLINE", "UNSTABLE" -> normalized;
            default -> ConnectionStatus.OFFLINE.name();
        };
    }

    private String resolveOperationalState(MachineEntity machine, MachineTelemetryEntity latestTelemetry) {
        if (latestTelemetry != null && latestTelemetry.getMachineState() != null && !latestTelemetry.getMachineState().isBlank()) {
            return normalizeOperationalState(latestTelemetry.getMachineState());
        }
        return normalizeOperationalState(machine.getStatus());
    }

    private String normalizeOperationalState(String rawState) {
        if (rawState == null || rawState.isBlank()) return MachineState.IDLE.name();
        String normalized = rawState.trim().toUpperCase();
        if (CANONICAL_OPERATING_STATES.contains(normalized)) return normalized;
        return switch (normalized) {
            case "STOP" -> MachineState.STOPPED.name();
            case "FAULT", "ERROR", "ALARM", "ESTOP", "E_STOP" -> MachineState.EMERGENCY_STOP.name();
            default -> MachineState.IDLE.name();
        };
    }

    private String resolveDisplayState(String operationalState, String connectionState) {
        if (!ConnectionStatus.ONLINE.name().equals(connectionState)) {
            return connectionState;
        }
        return operationalState != null ? operationalState : MachineState.IDLE.name();
    }

    // ── Anomaly scoring ──

    private Double computeAnomalyScore(List<MachineTelemetryEntity> recent, MachineTelemetryEntity latest) {
        if (latest == null || recent == null || recent.size() < 3) return null;
        Double baseTmp = avg(recent, MachineTelemetryEntity::getTemperatureC);
        Double baseVib = avg(recent, MachineTelemetryEntity::getVibrationMmS);
        Double baseCyc = avg(recent, MachineTelemetryEntity::getCycleTimeSec);
        Double basePwr = avg(recent, MachineTelemetryEntity::getPowerKw);
        double score = 0;
        score += deviation(latest.getTemperatureC(), baseTmp, 35);
        score += deviation(latest.getVibrationMmS(), baseVib, 30);
        score += deviation(latest.getCycleTimeSec(), baseCyc, 20);
        score += deviation(latest.getPowerKw(), basePwr, 15);
        if (latest.getRejectCount() != null && latest.getOutputCount() != null && latest.getOutputCount() > 0) {
            double pct = (latest.getRejectCount() * 100.0) / latest.getOutputCount();
            if (pct >= 10) score += 15;
            else if (pct >= 5) score += 8;
        }
        return Math.min(100.0, Math.round(score * 100.0) / 100.0);
    }

    private Double avg(List<MachineTelemetryEntity> items, Function<MachineTelemetryEntity, Double> getter) {
        var opt = items.stream().map(getter).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return opt.isPresent() ? opt.getAsDouble() : null;
    }

    private double deviation(Double current, Double baseline, double weight) {
        if (current == null || baseline == null || baseline <= 0) return 0;
        double ratio = Math.abs(current - baseline) / baseline;
        if (ratio < 0.1) return 0;
        if (ratio < 0.25) return weight * 0.4;
        if (ratio < 0.5) return weight * 0.75;
        return weight;
    }

    // ── Predicted failure window ──

    private String resolvePredictedFailureWindow(
            com.rmsys.backend.domain.entity.MaintenancePredictionEntity prediction, Double healthScore) {
        if (prediction.getNextMaintenanceDate() != null) {
            long days = ChronoUnit.DAYS.between(Instant.now(), prediction.getNextMaintenanceDate());
            if (days <= 1) return "< 24h";
            if (days <= 3) return "1-3 days";
            if (days <= 7) return "3-7 days";
            return "> 7 days";
        }
        Double rem = prediction.getRemainingHoursToService();
        if (rem != null) {
            if (rem <= 24) return "< 24h";
            if (rem <= 72) return "1-3 days";
            if (rem <= 168) return "3-7 days";
            return "> 7 days";
        }
        if (healthScore == null) return null;
        if (healthScore < 35) return "< 24h";
        if (healthScore < 55) return "1-3 days";
        if (healthScore < 75) return "3-7 days";
        return "> 7 days";
    }
}

