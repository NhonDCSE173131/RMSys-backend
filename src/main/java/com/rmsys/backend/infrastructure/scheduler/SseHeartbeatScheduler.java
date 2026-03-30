package com.rmsys.backend.infrastructure.scheduler;

import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.realtime.heartbeat-enabled", havingValue = "true", matchIfMissing = true)
public class SseHeartbeatScheduler {

    private final SseEmitterRegistry sseRegistry;

    @Scheduled(fixedDelayString = "${app.realtime.heartbeat-interval-ms:1000}")
    public void publishHeartbeat() {
        sseRegistry.flushTelemetryUpdates();
        if (sseRegistry.getActiveCount() > 0) {
            sseRegistry.sendHeartbeat();
            log.debug("SSE heartbeat sent to {} subscribers", sseRegistry.getActiveCount());
        }
    }
}

