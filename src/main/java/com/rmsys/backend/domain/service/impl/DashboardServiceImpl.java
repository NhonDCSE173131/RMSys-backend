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
        int online = (int) machines.stream().filter(m -> !"OFFLINE".equals(m.getStatus())).count();
        int running = (int) machines.stream().filter(m -> "RUNNING".equals(m.getStatus())).count();

        long criticalAlarms = alarmRepo.countByIsActiveTrueAndSeverity("CRITICAL");

        var todayStart = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
        long abnormalStops = downtimeRepo.countByAbnormalStopTrueAndStartedAtAfter(todayStart);

        // Aggregate power from latest telemetry
        var latestAll = telemetryRepo.findLatestForAllMachines();
        double plantPower = latestAll.stream()
                .mapToDouble(t -> t.getPowerKw() != null ? t.getPowerKw() : 0).sum();

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

        // Top risk machines
        List<RiskMachineItem> topRisk = new ArrayList<>();
        for (var m : machines) {
            healthRepo.findFirstByMachineIdOrderByBucketStartDesc(m.getId()).ifPresent(h -> {
                if (h.getHealthScore() != null && h.getHealthScore() < 60) {
                    topRisk.add(RiskMachineItem.builder()
                            .machineId(m.getCode()).machineName(m.getName())
                            .riskLevel(h.getRiskLevel()).reason(h.getMainReason()).build());
                }
            });
        }

        return DashboardOverviewResponse.builder()
                .totalMachines(total).onlineMachines(online).runningMachines(running)
                .criticalAlarms(criticalAlarms).plantPowerKw(plantPower)
                .todayEnergyKwh(todayEnergy).todayOee(todayOee)
                .abnormalStops(abnormalStops).topRiskMachines(topRisk).build();
    }
}

