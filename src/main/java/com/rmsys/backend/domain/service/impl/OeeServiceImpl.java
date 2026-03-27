package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.AnalyticsBreakdownResponse;
import com.rmsys.backend.api.response.AnalyticsTrendPointResponse;
import com.rmsys.backend.api.response.AnalyticsTrendResponse;
import com.rmsys.backend.api.response.OeeLossesResponse;
import com.rmsys.backend.api.response.OeeOverviewResponse;
import com.rmsys.backend.api.response.OeeOverviewResponse.MachineOeeItem;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.repository.OeeSnapshotRepository;
import com.rmsys.backend.domain.service.OeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OeeServiceImpl implements OeeService {

    private final MachineRepository machineRepo;
    private final OeeSnapshotRepository oeeRepo;

    @Override
    public OeeOverviewResponse getOverview() {
        var machines = machineRepo.findAll();
        var items = new ArrayList<MachineOeeItem>();
        double sumA = 0, sumP = 0, sumQ = 0, sumO = 0;
        int count = 0;

        for (var m : machines) {
            var latest = oeeRepo.findFirstByMachineIdAndBucketTypeOrderByBucketStartDesc(m.getId(), "HOUR");
            if (latest.isPresent()) {
                var s = latest.get();
                sumA += s.getAvailability() != null ? s.getAvailability() : 0;
                sumP += s.getPerformance() != null ? s.getPerformance() : 0;
                sumQ += s.getQuality() != null ? s.getQuality() : 0;
                sumO += s.getOee() != null ? s.getOee() : 0;
                count++;
                items.add(MachineOeeItem.builder()
                        .machineId(m.getId()).machineCode(m.getCode()).machineName(m.getName()).areaCode(m.getLineId())
                        .availability(s.getAvailability()).performance(s.getPerformance())
                        .quality(s.getQuality()).oee(s.getOee()).build());
            }
        }

        return OeeOverviewResponse.builder()
                .avgAvailability(count > 0 ? sumA / count : 0)
                .avgPerformance(count > 0 ? sumP / count : 0)
                .avgQuality(count > 0 ? sumQ / count : 0)
                .avgOee(count > 0 ? sumO / count : 0)
                .lastUpdatedAt(Instant.now())
                .machines(items).build();
    }

    @Override
    public AnalyticsTrendResponse getTrend(Instant from, Instant to, String interval) {
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minus(24, ChronoUnit.HOURS);
        var snapshots = oeeRepo.findByBucketTypeAndBucketStartAfterOrderByBucketStartDesc("HOUR", resolvedFrom).stream()
                .filter(s -> !s.getBucketStart().isAfter(resolvedTo))
                .sorted(java.util.Comparator.comparing(s -> s.getBucketStart()))
                .toList();

        var points = snapshots.stream().map(snapshot -> AnalyticsTrendPointResponse.builder()
                .timestamp(snapshot.getBucketStart())
                .bucketEnd(snapshot.getBucketStart())
                .sampleCount(1)
                .missing(false)
                .metrics(Map.of(
                        "oee", snapshot.getOee() != null ? snapshot.getOee() : 0,
                        "availability", snapshot.getAvailability() != null ? snapshot.getAvailability() : 0,
                        "performance", snapshot.getPerformance() != null ? snapshot.getPerformance() : 0,
                        "quality", snapshot.getQuality() != null ? snapshot.getQuality() : 0
                ))
                .build()).toList();

        return AnalyticsTrendResponse.builder()
                .from(resolvedFrom)
                .to(resolvedTo)
                .interval(interval != null ? interval : "1h")
                .points(points)
                .build();
    }

    @Override
    public AnalyticsBreakdownResponse getByMachine() {
        var items = machineRepo.findAll().stream().map(machine -> {
            var latest = oeeRepo.findFirstByMachineIdAndBucketTypeOrderByBucketStartDesc(machine.getId(), "HOUR");
            return AnalyticsBreakdownResponse.BreakdownItem.builder()
                    .machineId(machine.getId())
                    .machineCode(machine.getCode())
                    .name(machine.getName())
                    .group(machine.getLineId())
                    .metrics(Map.of(
                            "oee", latest.map(s -> s.getOee() != null ? s.getOee() : 0).orElse(0.0),
                            "availability", latest.map(s -> s.getAvailability() != null ? s.getAvailability() : 0).orElse(0.0),
                            "performance", latest.map(s -> s.getPerformance() != null ? s.getPerformance() : 0).orElse(0.0),
                            "quality", latest.map(s -> s.getQuality() != null ? s.getQuality() : 0).orElse(0.0)
                    ))
                    .build();
        }).toList();

        return AnalyticsBreakdownResponse.builder()
                .dimension("machine")
                .asOf(Instant.now())
                .items(items)
                .build();
    }

    @Override
    public OeeLossesResponse getLosses(Instant from) {
        Instant since = from != null ? from : Instant.now().minus(24, ChronoUnit.HOURS);
        var snapshots = oeeRepo.findByBucketTypeAndBucketStartAfterOrderByBucketStartDesc("HOUR", since);

        int runtime = snapshots.stream().mapToInt(s -> s.getRuntimeSec() != null ? s.getRuntimeSec() : 0).sum();
        int stop = snapshots.stream().mapToInt(s -> s.getStopSec() != null ? s.getStopSec() : 0).sum();
        int good = snapshots.stream().mapToInt(s -> s.getGoodCount() != null ? s.getGoodCount() : 0).sum();
        int reject = snapshots.stream().mapToInt(s -> s.getRejectCount() != null ? s.getRejectCount() : 0).sum();

        var losses = java.util.List.of(
                OeeLossesResponse.LossItem.builder().code("availability-loss").label("Availability Loss").value(stop).build(),
                OeeLossesResponse.LossItem.builder().code("quality-loss").label("Quality Loss").value(reject).build(),
                OeeLossesResponse.LossItem.builder().code("performance-loss").label("Performance Loss").value(Math.max(0, runtime - good)).build()
        );

        return OeeLossesResponse.builder()
                .runtimeSec(runtime)
                .stopSec(stop)
                .goodCount(good)
                .rejectCount(reject)
                .losses(losses)
                .build();
    }
}

