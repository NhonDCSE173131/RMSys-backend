package com.rmsys.backend.infrastructure.scheduler;

import com.rmsys.backend.domain.service.PlcConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for ApplicationReadyEvent and starts auto-connect
 * for all machines with autoConnect=true if startup-auto-connect is enabled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcAutoConnectStartup {

    private final PlcConnectionManager plcConnectionManager;

    @Value("${app.plc.startup-auto-connect:false}")
    private boolean startupAutoConnect;

    @Value("${app.plc.enabled:true}")
    private boolean plcEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!plcEnabled) {
            log.info("PLC module is disabled (app.plc.enabled=false), skipping auto-connect");
            return;
        }
        if (!startupAutoConnect) {
            log.info("PLC startup auto-connect is disabled (app.plc.startup-auto-connect=false)");
            return;
        }

        log.info("Starting PLC auto-connect for all eligible machines...");
        try {
            plcConnectionManager.startAutoConnectAll();
            log.info("PLC auto-connect startup completed");
        } catch (Exception e) {
            log.error("PLC auto-connect startup failed: {}", e.getMessage(), e);
        }
    }
}

