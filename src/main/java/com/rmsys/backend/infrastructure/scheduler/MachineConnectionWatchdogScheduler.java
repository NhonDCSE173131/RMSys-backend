package com.rmsys.backend.infrastructure.scheduler;

import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.MachineConnectionStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class MachineConnectionWatchdogScheduler {

    private final MachineRepository machineRepo;
    private final MachineConnectionStateService connectionStateService;

    @Scheduled(fixedDelayString = "${app.connection.watchdog-interval-ms:3000}")
    public void evaluateConnections() {
        var now = Instant.now();
        machineRepo.findAll().stream()
                .filter(machine -> Boolean.TRUE.equals(machine.getIsEnabled()))
                .forEach(machine -> {
                    try {
                        connectionStateService.evaluateByWatchdog(machine, now);
                    } catch (Exception ex) {
                        log.error("Connection watchdog failed for machine {}", machine.getId(), ex);
                    }
                });
    }
}

