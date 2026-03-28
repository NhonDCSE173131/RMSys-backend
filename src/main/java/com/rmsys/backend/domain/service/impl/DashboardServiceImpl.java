package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.DashboardOverviewResponse;
import com.rmsys.backend.api.response.DashboardOverviewResponse.RiskMachineItem;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.repository.*;
import com.rmsys.backend.domain.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final MachineRepository machineRepo;
    private final AlarmEventRepository alarmRepo;
    private final MachineTelemetryRepository telemetryRepo;
    private final EnergyTelemetryRepository energyRepo;
    private final DowntimeEventRepository downtimeRepo;
    private final OeeSnapshotRepository oeeRepo;
    private final MachineHealthSnapshotRepository healthRepo;

    @Override
    public DashboardOverviewResponse getOverview() {
        var machines = machineRepo.findAll();
        int total = machines.size();
        int online = (int) machines.stream().filter(m -> !"OFFLINE".equalsIgnoreCase(m.getConnectionState())).count();
        int offline = total - online;
        // Only count operational state for machines that are actually connected
        int running = (int) machines.stream()
                .filter(m -> "RUNNING".equals(m.getStatus()) && !"OFFLINE".equalsIgnoreCase(m.getConnectionState()))
                .count();
        int idle = (int) machines.stream()
                .filter(m -> "IDLE".equals(m.getStatus()) && !"OFFLINE".equalsIgnoreCase(m.getConnectionState()))
                .count();
        int fault = (int) machines.stream()
                .filter(m -> ("FAULT".equals(m.getStatus()) || "STOPPED".equals(m.getStatus()))
                        && !"OFFLINE".equalsIgnoreCase(m.getConnectionState()))
                .count();

        long criticalAlarms = alarmRepo.countByIsActiveTrueAndSeverity("CRITICAL");
        long warningAlarms = alarmRepo.countByIsActiveTrueAndSeverity("WARNING");

        var todayStart = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
        long abnormalStops = downtimeRepo.countByAbnormalStopTrueAndStartedAtAfter(todayStart);

        // Aggregate power from latest telemetry
        var latestAll = telemetryRepo.findLatestForAllMachines();
        double plantPower = latestAll.stream()
                .mapToDouble(t -> t.getPowerKw() != null ? t.getPowerKw() : 0).sum();
        long totalProduction = latestAll.stream().mapToLong(t -> t.getOutputCount() != null ? t.getOutputCount() : 0).sum();
        long totalGood = latestAll.stream().mapToLong(t -> t.getGoodCount() != null ? t.getGoodCount() : 0).sum();
        long totalReject = latestAll.stream().mapToLong(t -> t.getRejectCount() != null ? t.getRejectCount() : 0).sum();

        // Today energy from latest energy readings
        double todayEnergy = 0;
        for (var m : machines) {
            energyRepo.findFirstByMachineIdOrderByTsDesc(m.getId())
                    .ifPresent(e -> {});
            var latest = energyRepo.findFirstByMachineIdOrderByTsDesc(m.getId());
            if (latest.isPresent() && latest.get().getEnergyKwhDay() != null) {
                todayEnergy += latest.get().getEnergyKwhDay();
            }
        }

        // Today OEE - avg from latest snapshots
        double todayOee = machines.stream()
                .map(m -> oeeRepo.findFirstByMachineIdAndBucketTypeOrderByBucketStartDesc(m.getId(), "HOUR"))
                .filter(java.util.Optional::isPresent).map(java.util.Optional::get)
                .mapToDouble(s -> s.getOee() != null ? s.getOee() : 0).average().orElse(0);

        var areaStats = new HashMap<String, AreaAccumulator>();
        for (var machine : machines) {
            String areaCode = machine.getLineId() != null && !machine.getLineId().isBlank() ? machine.getLineId() : "UNASSIGNED";
            var area = areaStats.computeIfAbsent(areaCode, key -> new AreaAccumulator());
            area.totalMachines++;
            if (!"OFFLINE".equalsIgnoreCase(machine.getConnectionState())) {
                area.onlineMachines++;
            }
            telemetryRepo.findFirstByMachineIdOrderByTsDesc(machine.getId()).ifPresent(t -> {
                area.powerKw += t.getPowerKw() != null ? t.getPowerKw() : 0;
            });
            energyRepo.findFirstByMachineIdOrderByTsDesc(machine.getId()).ifPresent(e -> {
                area.energyKwh += e.getEnergyKwhDay() != null ? e.getEnergyKwhDay() : 0;
            });
            oeeRepo.findFirstByMachineIdAndBucketTypeOrderByBucketStartDesc(machine.getId(), "HOUR").ifPresent(o -> {
                if (o.getOee() != null) {
                    area.oeeSum += o.getOee();
                    area.oeeCount++;
                }
            });
        }

        // Top risk machines
        List<RiskMachineItem> topRisk = new ArrayList<>();
        for (var m : machines) {
            healthRepo.findFirstByMachineIdOrderByBucketStartDesc(m.getId()).ifPresent(h -> {
                if (h.getHealthScore() != null && h.getHealthScore() < 60) {
                    topRisk.add(RiskMachineItem.builder()
                            .machineId(m.getId()).machineCode(m.getCode()).machineName(m.getName())
                            .riskLevel(h.getRiskLevel()).reason(h.getMainReason()).build());
                }
            });
        }

        var areaSummaries = areaStats.entrySet().stream()
                .map(entry -> DashboardOverviewResponse.AreaSummaryItem.builder()
                        .areaCode(entry.getKey())
                        .totalMachines(entry.getValue().totalMachines)
                        .onlineMachines(entry.getValue().onlineMachines)
                        .powerKw(entry.getValue().powerKw)
                        .energyTodayKwh(entry.getValue().energyKwh)
                        .avgOee(entry.getValue().oeeCount > 0 ? entry.getValue().oeeSum / entry.getValue().oeeCount : 0)
                        .build())
                .toList();

        return DashboardOverviewResponse.builder()
                .totalMachines(total).onlineMachines(online).offlineMachines(offline)
                .runningMachines(running).idleMachines(idle).faultMachines(fault)
                .criticalAlarms(criticalAlarms).plantPowerKw(plantPower)
                .warningAlarms(warningAlarms)
                .todayEnergyKwh(todayEnergy).todayOee(todayOee)
                .abnormalStops(abnormalStops)
                .totalProduction(totalProduction).totalGood(totalGood).totalReject(totalReject)
                .topRiskMachines(topRisk)
                .areaSummaries(areaSummaries)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    private static final class AreaAccumulator {
        int totalMachines;
        int onlineMachines;
        double powerKw;
        double energyKwh;
        double oeeSum;
        int oeeCount;
    }
}

