package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.common.enumtype.ConnectionStatus;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MachineConnectionStateServiceImplTest {

    @Mock
    private MachineRepository machineRepo;

    @Mock
    private SseEmitterRegistry sseRegistry;

    @InjectMocks
    private MachineConnectionStateServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "staleThresholdSec", 10L);
        ReflectionTestUtils.setField(service, "offlineThresholdSec", 30L);
        ReflectionTestUtils.setField(service, "flapWindowSec", 60L);
        ReflectionTestUtils.setField(service, "unstableFlapThreshold", 3);
    }

    @Test
    void evaluateByWatchdog_transitionsToStaleAndOffline() {
        var now = Instant.now();
        var machine = baseMachine();
        machine.setConnectionState(ConnectionStatus.ONLINE.name());
        machine.setLastSeenAt(now.minusSeconds(20));

        service.evaluateByWatchdog(machine, now);

        assertEquals(ConnectionStatus.STALE.name(), machine.getConnectionState());
        verify(sseRegistry).broadcast(eq("machine-connection-stale"), any());

        service.evaluateByWatchdog(machine, now.plusSeconds(20));

        assertEquals(ConnectionStatus.OFFLINE.name(), machine.getConnectionState());
        verify(sseRegistry).broadcast(eq("machine-connection-offline"), any());
    }

    @Test
    void markTelemetryReceived_fromOffline_toOnlineEmitsOnlineEvent() {
        var machine = baseMachine();
        machine.setConnectionState(ConnectionStatus.OFFLINE.name());
        machine.setLastSeenAt(Instant.now().minusSeconds(100));

        service.markTelemetryReceived(machine, Instant.now(), Instant.now(), "fingerprint", true);

        assertEquals(ConnectionStatus.ONLINE.name(), machine.getConnectionState());
        verify(sseRegistry).broadcast(eq("machine-connection-online"), any());
        verify(machineRepo, atLeast(1)).save(machine);
    }

    @Test
    void markTelemetryReceived_samePacket_skipsTransitionEvent() {
        var now = Instant.now();
        var machine = baseMachine();
        machine.setConnectionState(ConnectionStatus.ONLINE.name());
        machine.setLastTelemetrySourceTs(now);
        machine.setLastPayloadFingerprint("same");

        service.markTelemetryReceived(machine, now, now, "same", true);

        verify(sseRegistry, never()).broadcast(eq("machine-connection-online"), any());
    }

    @Test
    void evaluateByWatchdog_flappingMarksUnstable() {
        var now = Instant.now();
        var machine = baseMachine();
        machine.setConnectionState(ConnectionStatus.ONLINE.name());
        machine.setLastSeenAt(now.minusSeconds(40));

        service.evaluateByWatchdog(machine, now);
        service.markTelemetryReceived(machine, now.plusSeconds(1), now.plusSeconds(1), "f-1", true);
        service.evaluateByWatchdog(machine, now.plusSeconds(35));

        assertTrue(Boolean.TRUE.equals(machine.getConnectionUnstable()));
        verify(sseRegistry, times(1)).broadcast(eq("machine-connection-unstable"), any());
    }

    @Test
    void evaluateByWatchdog_settledConnectionClearsUnstable() {
        var now = Instant.now();
        var machine = baseMachine();
        machine.setConnectionState(ConnectionStatus.ONLINE.name());
        machine.setLastSeenAt(now);
        machine.setConnectionUnstable(true);
        machine.setConnectionFlapCount(4);
        machine.setLastConnectionChangedAt(now.minusSeconds(120));

        service.evaluateByWatchdog(machine, now);

        assertFalse(Boolean.TRUE.equals(machine.getConnectionUnstable()));
        assertEquals(0, machine.getConnectionFlapCount());
    }

    private MachineEntity baseMachine() {
        return MachineEntity.builder()
                .id(UUID.randomUUID())
                .code("M-001")
                .name("Machine")
                .type("CNC")
                .vendor("TEST")
                .status("RUNNING")
                .isEnabled(true)
                .build();
    }
}

