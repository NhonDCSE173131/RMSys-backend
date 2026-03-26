package com.rmsys.backend.infrastructure.scheduler;

import com.rmsys.backend.infrastructure.adapter.simulator.SimulatorAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.simulator.enabled", havingValue = "true")
public class SimulationScheduler {

    private final SimulatorAdapter simulator;

    @Scheduled(fixedDelayString = "${app.simulator.interval-ms:2000}")
    public void simulate() {
        try {
            simulator.simulateAll();
        } catch (Exception e) {
            log.error("Simulation error", e);
        }
    }
}

