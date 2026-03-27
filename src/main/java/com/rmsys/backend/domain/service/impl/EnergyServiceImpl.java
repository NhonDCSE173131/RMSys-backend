package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.AnalyticsBreakdownResponse;
import com.rmsys.backend.api.response.AnalyticsTrendPointResponse;
import com.rmsys.backend.api.response.AnalyticsTrendResponse;
import com.rmsys.backend.api.response.EnergyCostResponse;
import com.rmsys.backend.api.response.EnergyOverviewResponse;
import com.rmsys.backend.api.response.EnergyOverviewResponse.MachineEnergyItem;
import com.rmsys.backend.domain.repository.EnergyTelemetryRepository;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.EnergyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EnergyServiceImpl implements EnergyService {

    private static final double DEFAULT_RATE_PER_KWH = 3000.0;

    private final MachineRepository machineRepo;
    private final EnergyTelemetryRepository energyRepo;

    @Override
    public EnergyOverviewResponse getOverview() {
        var machines = machineRepo.findAll();
        var items = new ArrayList<MachineEnergyItem>();
        double totalPower = 0, totalEnergy = 0, totalEnergyMonth = 0, pfSum = 0;
        int pfCount = 0;

        for (var m : machines) {
            var latest = energyRepo.findFirstByMachineIdOrderByTsDesc(m.getId());
            if (latest.isPresent()) {
                var e = latest.get();
                var power = e.getPowerKw() != null ? e.getPowerKw() : 0;
                totalPower += power;
                totalEnergy += e.getEnergyKwhDay() != null ? e.getEnergyKwhDay() : 0;
                totalEnergyMonth += e.getEnergyKwhMonth() != null ? e.getEnergyKwhMonth() : 0;
                if (e.getPowerFactor() != null) { pfSum += e.getPowerFactor(); pfCount++; }
                items.add(MachineEnergyItem.builder()
                        .machineId(m.getId()).machineCode(m.getCode()).machineName(m.getName()).areaCode(m.getLineId())
                        .powerKw(e.getPowerKw()).voltageV(e.getVoltageV())
                        .currentA(e.getCurrentA()).powerFactor(e.getPowerFactor())
                        .energyKwhDay(e.getEnergyKwhDay()).energyKwhMonth(e.getEnergyKwhMonth()).build());
            }
        }

        double costToday = totalEnergy * DEFAULT_RATE_PER_KWH;
        double costMonth = totalEnergyMonth * DEFAULT_RATE_PER_KWH;

        return EnergyOverviewResponse.builder()
                .totalPowerKw(totalPower).totalEnergyTodayKwh(totalEnergy)
                .totalEnergyMonthKwh(totalEnergyMonth)
                .avgPowerFactor(pfCount > 0 ? pfSum / pfCount : 0)
                .costToday(costToday)
                .costMonth(costMonth)
                .lastUpdatedAt(Instant.now())
                .machines(items).build();
    }

    @Override
    public AnalyticsTrendResponse getTrend(Instant from, Instant to, String interval) {
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minus(24, ChronoUnit.HOURS);

        var points = energyRepo.findByTsBetweenOrderByTsAsc(resolvedFrom, resolvedTo).stream()
                .map(item -> {
                    var metrics = Map.<String, Double>of(
                            "powerKw", item.getPowerKw() != null ? item.getPowerKw() : 0,
                            "energyKwhDay", item.getEnergyKwhDay() != null ? item.getEnergyKwhDay() : 0,
                            "energyKwhMonth", item.getEnergyKwhMonth() != null ? item.getEnergyKwhMonth() : 0
                    );
                    return AnalyticsTrendPointResponse.builder()
                            .timestamp(item.getTs())
                            .bucketEnd(item.getTs())
                            .sampleCount(1)
                            .missing(false)
                            .metrics(metrics)
                            .build();
                })
                .toList();

        return AnalyticsTrendResponse.builder()
                .from(resolvedFrom)
                .to(resolvedTo)
                .interval(interval != null ? interval : "raw")
                .points(points)
                .build();
    }

    @Override
    public AnalyticsBreakdownResponse getByArea() {
        var grouped = new HashMap<String, double[]>();
        for (var machine : machineRepo.findAll()) {
            String key = machine.getLineId() != null && !machine.getLineId().isBlank() ? machine.getLineId() : "UNASSIGNED";
            var metrics = grouped.computeIfAbsent(key, ignored -> new double[3]);
            energyRepo.findFirstByMachineIdOrderByTsDesc(machine.getId()).ifPresent(e -> {
                metrics[0] += e.getPowerKw() != null ? e.getPowerKw() : 0;
                metrics[1] += e.getEnergyKwhDay() != null ? e.getEnergyKwhDay() : 0;
                metrics[2] += e.getEnergyKwhMonth() != null ? e.getEnergyKwhMonth() : 0;
            });
        }

        var items = grouped.entrySet().stream()
                .map(entry -> AnalyticsBreakdownResponse.BreakdownItem.builder()
                        .name(entry.getKey())
                        .group("area")
                        .metrics(Map.of(
                                "powerKw", entry.getValue()[0],
                                "energyKwhDay", entry.getValue()[1],
                                "energyKwhMonth", entry.getValue()[2]
                        ))
                        .build())
                .toList();

        return AnalyticsBreakdownResponse.builder().dimension("area").asOf(Instant.now()).items(items).build();
    }

    @Override
    public AnalyticsBreakdownResponse getByMachine() {
        var items = machineRepo.findAll().stream()
                .map(machine -> {
                    var latest = energyRepo.findFirstByMachineIdOrderByTsDesc(machine.getId());
                    Map<String, Double> metrics = new HashMap<>();
                    metrics.put("powerKw", latest.map(e -> e.getPowerKw() != null ? e.getPowerKw() : 0).orElse(0.0));
                    metrics.put("energyKwhDay", latest.map(e -> e.getEnergyKwhDay() != null ? e.getEnergyKwhDay() : 0).orElse(0.0));
                    metrics.put("energyKwhMonth", latest.map(e -> e.getEnergyKwhMonth() != null ? e.getEnergyKwhMonth() : 0).orElse(0.0));
                    return AnalyticsBreakdownResponse.BreakdownItem.builder()
                            .machineId(machine.getId())
                            .machineCode(machine.getCode())
                            .name(machine.getName())
                            .group(machine.getLineId())
                            .metrics(metrics)
                            .build();
                })
                .toList();

        return AnalyticsBreakdownResponse.builder().dimension("machine").asOf(Instant.now()).items(items).build();
    }

    @Override
    public EnergyCostResponse getCost() {
        var overview = getOverview();
        return EnergyCostResponse.builder()
                .ratePerKwh(DEFAULT_RATE_PER_KWH)
                .totalEnergyTodayKwh(overview.totalEnergyTodayKwh())
                .totalEnergyMonthKwh(overview.totalEnergyMonthKwh())
                .costToday(overview.costToday())
                .costMonth(overview.costMonth())
                .currency("VND")
                .asOf(Instant.now())
                .build();
    }
}

