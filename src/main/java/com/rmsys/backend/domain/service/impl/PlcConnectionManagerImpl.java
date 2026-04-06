package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.common.enumtype.PlcConnectionStatus;
import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineImportBatchEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import com.rmsys.backend.domain.repository.MachineImportBatchRepository;
import com.rmsys.backend.domain.repository.MachineProfileMappingRepository;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.IngestService;
import com.rmsys.backend.domain.service.MachineMappingService;
import com.rmsys.backend.domain.service.PlcConnectionManager;
import com.rmsys.backend.infrastructure.adapter.DeviceAdapter;
import com.rmsys.backend.infrastructure.adapter.factory.DeviceAdapterFactory;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages runtime PLC connections for all machines.
 * Holds active sessions in memory keyed by machineId.
 * Each session runs a periodic poll task that reads PLC data and feeds IngestService.
 */
@Slf4j
@Component
public class PlcConnectionManagerImpl implements PlcConnectionManager {

    private final MachineRepository machineRepo;
    private final MachineProfileMappingRepository mappingRepo;
    private final MachineImportBatchRepository batchRepo;
    private final DeviceAdapterFactory adapterFactory;
    private final MachineMappingService mappingService;
    private final IngestService ingestService;

    @Value("${app.plc.reconnect-delay-ms:3000}")
    private long reconnectDelayMs;

    @Value("${app.plc.default-poll-interval-ms:1000}")
    private int defaultPollIntervalMs;

    @Value("${app.plc.default-timeout-ms:2000}")
    private int defaultTimeoutMs;

    private final Map<UUID, MachineRuntimeSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "plc-poller");
                t.setDaemon(true);
                return t;
            }
    );

    public PlcConnectionManagerImpl(MachineRepository machineRepo,
                                    MachineProfileMappingRepository mappingRepo,
                                    MachineImportBatchRepository batchRepo,
                                    DeviceAdapterFactory adapterFactory,
                                    MachineMappingService mappingService,
                                    IngestService ingestService) {
        this.machineRepo = machineRepo;
        this.mappingRepo = mappingRepo;
        this.batchRepo = batchRepo;
        this.adapterFactory = adapterFactory;
        this.mappingService = mappingService;
        this.ingestService = ingestService;
    }

    @Override
    public void startConnection(UUID machineId) {
        MachineEntity machine = machineRepo.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));

        if (sessions.containsKey(machineId) && sessions.get(machineId).isRunning()) {
            log.info("Connection already active for machine {}", machine.getCode());
            return;
        }

        DeviceAdapter adapter = adapterFactory.createAdapter(machine);
        boolean connected = adapter.connect(machine);

        MachineRuntimeSession session = new MachineRuntimeSession();
        session.machineId = machineId;
        session.machineCode = machine.getCode();
        session.adapter = adapter;
        session.lastError = null;

        if (connected) {
            session.status = PlcConnectionStatus.ONLINE.name();
            session.lastConnectedAt = Instant.now();

            // Load mappings
            session.mappings = loadMappings(machine);

            // Start polling
            int interval = machine.getPollIntervalMs() != null ? machine.getPollIntervalMs() : defaultPollIntervalMs;
            session.pollFuture = scheduler.scheduleAtFixedRate(
                    () -> pollMachine(machineId),
                    0, interval, TimeUnit.MILLISECONDS
            );

            // Update DB
            machine.setLastConnectionStatus(PlcConnectionStatus.ONLINE.name());
            machine.setLastConnectedAt(Instant.now());
            machineRepo.save(machine);

            log.info("Started connection for machine {} ({})", machine.getCode(), machineId);
        } else {
            session.status = PlcConnectionStatus.ERROR.name();
            session.lastError = "Failed to connect";

            machine.setLastConnectionStatus(PlcConnectionStatus.ERROR.name());
            machine.setLastConnectionReasonDetail("Initial connect failed");
            machineRepo.save(machine);

            log.warn("Failed to start connection for machine {}", machine.getCode());
        }

        sessions.put(machineId, session);
    }

    @Override
    public void stopConnection(UUID machineId) {
        MachineRuntimeSession session = sessions.get(machineId);
        if (session == null) {
            log.debug("No active session for machine {}", machineId);
            return;
        }

        // Cancel poll task
        if (session.pollFuture != null && !session.pollFuture.isCancelled()) {
            session.pollFuture.cancel(false);
        }

        // Disconnect adapter
        if (session.adapter != null) {
            try {
                session.adapter.disconnect();
            } catch (Exception e) {
                log.warn("Error disconnecting adapter for machine {}: {}", machineId, e.getMessage());
            }
        }

        session.status = PlcConnectionStatus.DISCONNECTED.name();
        session.lastDisconnectedAt = Instant.now();

        // Update DB
        machineRepo.findById(machineId).ifPresent(m -> {
            m.setLastConnectionStatus(PlcConnectionStatus.DISCONNECTED.name());
            m.setLastDisconnectedAt(Instant.now());
            machineRepo.save(m);
        });

        sessions.remove(machineId);
        log.info("Stopped connection for machine {}", machineId);
    }

    @Override
    public boolean testConnection(MachineEntity machine) {
        DeviceAdapter adapter = adapterFactory.createAdapter(machine);
        return adapter.testConnection(machine);
    }

    @Override
    public String getConnectionStatus(UUID machineId) {
        MachineRuntimeSession session = sessions.get(machineId);
        if (session == null) return PlcConnectionStatus.DISCONNECTED.name();
        return session.status;
    }

    @Override
    public void startAutoConnectAll() {
        List<MachineEntity> machines = machineRepo.findByAutoConnectTrueAndIsEnabledTrue();
        log.info("Auto-connecting {} eligible machines (autoConnect=true, enabled=true)...", machines.size());
        for (MachineEntity machine : machines) {
            try {
                startConnection(machine.getId());
            } catch (Exception e) {
                log.error("Auto-connect failed for machine {}: {}", machine.getCode(), e.getMessage());
            }
        }
    }

    @Override
    @PreDestroy
    public void stopAll() {
        log.info("Stopping all PLC connections...");
        for (UUID machineId : new ArrayList<>(sessions.keySet())) {
            try {
                stopConnection(machineId);
            } catch (Exception e) {
                log.warn("Error stopping connection for {}: {}", machineId, e.getMessage());
            }
        }
        scheduler.shutdown();
    }

    /**
     * Get session info (for status reporting).
     */
    public MachineRuntimeSession getSession(UUID machineId) {
        return sessions.get(machineId);
    }

    // ---- Poll logic ----

    private void pollMachine(UUID machineId) {
        MachineRuntimeSession session = sessions.get(machineId);
        if (session == null || session.adapter == null) return;

        try {
            if (!session.adapter.isConnected()) {
                handleDisconnect(machineId, session, "Adapter reports not connected");
                return;
            }

            Map<String, Object> rawValues = session.adapter.readData(session.mappings);
            if (rawValues == null || rawValues.isEmpty()) {
                log.debug("Empty data from machine {}", session.machineCode);
                return;
            }

            // Get machine entity for mapping
            MachineEntity machine = machineRepo.findById(machineId).orElse(null);
            if (machine == null) {
                log.warn("Machine {} not found in DB during poll, stopping", machineId);
                stopConnection(machineId);
                return;
            }

            // Map raw -> normalized
            NormalizedTelemetryDto dto = mappingService.mapToTelemetry(machine, session.mappings, rawValues);

            // Feed into ingest pipeline
            ingestService.ingestTelemetry(dto);

            // Update session
            session.lastDataAt = Instant.now();
            session.status = PlcConnectionStatus.ONLINE.name();
            session.consecutiveErrors = 0;

            // Update DB lastDataAt
            machine.setLastDataAt(Instant.now());
            machine.setLastConnectionStatus(PlcConnectionStatus.ONLINE.name());
            machineRepo.save(machine);

        } catch (Exception e) {
            session.consecutiveErrors++;
            session.lastError = e.getMessage();
            log.error("Poll error for machine {} (errors={}): {}",
                    session.machineCode, session.consecutiveErrors, e.getMessage());

            if (session.consecutiveErrors >= 5) {
                handleDisconnect(machineId, session, "Too many consecutive errors: " + e.getMessage());
            }
        }
    }

    private void handleDisconnect(UUID machineId, MachineRuntimeSession session, String reason) {
        session.status = PlcConnectionStatus.STALE.name();
        session.lastError = reason;

        // Update DB
        machineRepo.findById(machineId).ifPresent(m -> {
            m.setLastConnectionStatus(PlcConnectionStatus.STALE.name());
            m.setLastConnectionReasonDetail(reason);
            machineRepo.save(m);
        });

        // Schedule reconnect
        scheduler.schedule(() -> attemptReconnect(machineId), reconnectDelayMs, TimeUnit.MILLISECONDS);
    }

    private void attemptReconnect(UUID machineId) {
        MachineRuntimeSession session = sessions.get(machineId);
        if (session == null) return;

        MachineEntity machine = machineRepo.findById(machineId).orElse(null);
        if (machine == null || !Boolean.TRUE.equals(machine.getIsEnabled())) {
            log.info("Machine {} disabled or deleted, skip reconnect", machineId);
            stopConnection(machineId);
            return;
        }

        log.info("Attempting reconnect for machine {}", session.machineCode);

        // Disconnect old adapter
        if (session.adapter != null) {
            try { session.adapter.disconnect(); } catch (Exception ignored) {}
        }

        // Create new adapter and connect
        DeviceAdapter adapter = adapterFactory.createAdapter(machine);
        boolean ok = adapter.connect(machine);
        session.adapter = adapter;

        if (ok) {
            session.status = PlcConnectionStatus.ONLINE.name();
            session.lastConnectedAt = Instant.now();
            session.consecutiveErrors = 0;
            session.mappings = loadMappings(machine);

            machine.setLastConnectionStatus(PlcConnectionStatus.ONLINE.name());
            machine.setLastConnectedAt(Instant.now());
            machineRepo.save(machine);

            log.info("Reconnected machine {}", session.machineCode);
        } else {
            session.status = PlcConnectionStatus.ERROR.name();
            session.lastError = "Reconnect failed";

            machine.setLastConnectionStatus(PlcConnectionStatus.ERROR.name());
            machine.setLastConnectionReasonDetail("Reconnect failed");
            machineRepo.save(machine);

            // Schedule another retry
            scheduler.schedule(() -> attemptReconnect(machineId), reconnectDelayMs * 2, TimeUnit.MILLISECONDS);
        }
    }

    private List<MachineProfileMappingEntity> loadMappings(MachineEntity machine) {
        if (machine.getProfileId() == null) {
            log.warn("Machine {} has no profile assigned, using empty mappings", machine.getCode());
            return List.of();
        }

        // 1) Explicit mapping file selected on machine
        if (machine.getMappingFileId() != null) {
            List<MachineProfileMappingEntity> selected = mappingRepo
                    .findByMappingFileIdOrderByAddressStartAsc(machine.getMappingFileId());
            if (!selected.isEmpty()) {
                return selected;
            }
            log.warn("Machine {} references mappingFileId {} but no mappings found, trying fallback",
                    machine.getCode(), machine.getMappingFileId());
        }

        // 2) Fallback to latest completed mapping batch for this profile
        List<MachineImportBatchEntity> latestBatches = batchRepo
                .findByProfileIdAndImportTypeAndStatusOrderByCreatedAtDesc(machine.getProfileId(), "MAPPINGS", "COMPLETED");
        if (!latestBatches.isEmpty()) {
            UUID latestFileId = latestBatches.get(0).getId();
            List<MachineProfileMappingEntity> latest = mappingRepo
                    .findByProfileIdAndMappingFileIdOrderByAddressStartAsc(machine.getProfileId(), latestFileId);
            if (!latest.isEmpty()) {
                return latest;
            }
        }

        // 3) Legacy fallback (before mapping_file_id existed)
        return mappingRepo.findByProfileIdOrderByAddressStartAsc(machine.getProfileId());
    }

    // ---- Session data ----

    @Getter
    public static class MachineRuntimeSession {
        UUID machineId;
        String machineCode;
        DeviceAdapter adapter;
        List<MachineProfileMappingEntity> mappings = List.of();
        String status = PlcConnectionStatus.DISCONNECTED.name();
        Instant lastConnectedAt;
        Instant lastDisconnectedAt;
        Instant lastDataAt;
        String lastError;
        int consecutiveErrors;
        ScheduledFuture<?> pollFuture;

        public boolean isRunning() {
            return pollFuture != null && !pollFuture.isCancelled()
                    && PlcConnectionStatus.ONLINE.name().equals(status);
        }
    }
}


