package com.rmsys.backend.infrastructure.scheduler;

import com.rmsys.backend.domain.service.AggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationScheduler {

    private final AggregationService aggregationService;

    @Scheduled(fixedDelayString = "${app.aggregation.interval-ms:60000}")
    public void aggregate() {
        try {
            aggregationService.aggregateOee();
            aggregationService.aggregateHealth();
        } catch (Exception e) {
            log.error("Aggregation error", e);
        }
    }
}

