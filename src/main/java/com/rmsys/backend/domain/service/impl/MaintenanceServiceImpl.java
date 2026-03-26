package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.MaintenanceOverviewResponse;
import com.rmsys.backend.api.response.MaintenanceOverviewResponse.MachineMaintenanceItem;
import com.rmsys.backend.domain.repository.*;
import com.rmsys.backend.domain.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MachineRepository machineRepo;
    private final MaintenanceTelemetryRepository maintRepo;
    private final MachineHealthSnapshotRepository healthRepo;
    private final MaintenancePredictionRepository predictionRepo;

    @Override
    public MaintenanceOverviewResponse getOverview() {
        var machines = machineRepo.findAll();
        var items = new ArrayList<MachineMaintenanceItem>();
        int dueSoon = 0;
        double healthSum = 0;
        int healthCount = 0;

        for (var m : machines) {
            var health = healthRepo.findFirstByMachineIdOrderByBucketStartDesc(m.getId());
            var prediction = predictionRepo.findFirstByMachineIdOrderByTsDesc(m.getId());
            var maint = maintRepo.findFirstByMachineIdOrderByTsDesc(m.getId());

            double score = health.map(h -> h.getHealthScore() != null ? h.getHealthScore() : 80.0).orElse(80.0);
            healthSum += score;
            healthCount++;

            Double remaining = prediction.map(p -> p.getRemainingHoursToService()).orElse(null);
            if (remaining != null && remaining < 100) dueSoon++;

            items.add(MachineMaintenanceItem.builder()
                    .machineId(m.getId()).machineName(m.getName())
                    .runtimeHours(maint.map(mt -> mt.getRuntimeHours()).orElse(null))
                    .healthScore(score)
                    .riskLevel(health.map(h -> h.getRiskLevel()).orElse("LOW"))
                    .remainingHoursToService(remaining)
                    .nextMaintenanceDate(prediction.map(p -> p.getNextMaintenanceDate()).orElse(null))
                    .recommendedAction(prediction.map(p -> p.getRecommendedAction()).orElse(null))
                    .build());
        }

        return MaintenanceOverviewResponse.builder()
                .totalMachines(machines.size()).dueSoonCount(dueSoon)
                .avgHealthScore(healthCount > 0 ? healthSum / healthCount : 0)
                .machines(items).build();
    }
}

