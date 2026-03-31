package com.rmsys.backend.infrastructure.scheduler;

import com.rmsys.backend.domain.service.AggregationService;
import com.rmsys.backend.domain.service.MachineRealtimeSnapshotService;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationScheduler {

    private final AggregationService aggregationService;
    private final MachineRealtimeSnapshotService snapshotService;
    private final SseEmitterRegistry sseRegistry;

    /**
     * Runs every 1 second:
     * - Rolling OEE KPIs (ROLLING_60S)
     * - Health scoring
     * - Maintenance predictions
     * - Broadcast canonical snapshots via SSE
     */
    @Scheduled(fixedDelayString = "${app.aggregation.realtime-interval-ms:1000}")
    public void aggregateRealtime() {
        try {
            aggregationService.aggregateRealtimeRollingKpis();
            aggregationService.aggregateHealth();
            aggregationService.aggregateMaintenancePredictions();

            // Broadcast canonical snapshots for all machines
            var snapshots = snapshotService.buildAllSnapshots();
            for (var snapshot : snapshots) {
                sseRegistry.broadcast("machine-snapshot-updated", snapshot);
            }
        } catch (Exception e) {
            log.error("Realtime aggregation error", e);
        }
    }

    /**
     * Runs every minute: hourly OEE aggregate for analytics/reporting.
     */
    @Scheduled(fixedDelayString = "${app.aggregation.interval-ms:60000}")
    public void aggregate() {
        try {
            aggregationService.aggregateOee();
        } catch (Exception e) {
            log.error("Aggregation error", e);
        }
    }
}

