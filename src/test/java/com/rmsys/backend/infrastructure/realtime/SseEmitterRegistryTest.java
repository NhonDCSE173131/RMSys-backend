package com.rmsys.backend.infrastructure.realtime;

import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseEmitterRegistryTest {

    @Test
    void buildEnvelope_withMachinePayload_extractsMachineId() {
        var registry = new SseEmitterRegistry(10_000);
        var machineId = UUID.randomUUID();

        var payload = NormalizedTelemetryDto.builder()
                .machineId(machineId)
                .temperatureC(40.0)
                .build();

        var envelope = registry.buildEnvelope("machine-telemetry-updated", payload);

        assertEquals("telemetry.updated", envelope.type());
        assertEquals(machineId, envelope.machineId());
        assertEquals(payload, envelope.payload());
        assertNotNull(envelope.ts());
    }

    @Test
    void buildEnvelope_heartbeatPayload_hasNoMachineId() {
        var registry = new SseEmitterRegistry(10_000);
        var payload = Map.of("activeSubscribers", 1);

        var envelope = registry.buildEnvelope("ping", payload);

        assertEquals("ping", envelope.type());
        assertNull(envelope.machineId());
        assertEquals(payload, envelope.payload());
        assertNotNull(envelope.ts());
    }

    @Test
    @SuppressWarnings("unchecked")
    void broadcast_clientAbortException_isHandledAndSubscriptionRemoved() throws Exception {
        var registry = new SseEmitterRegistry(10_000);

        SseEmitter failingEmitter = new SseEmitter(10_000L) {
            @Override
            public synchronized void send(@NonNull SseEventBuilder builder) throws IOException {
                throw new IOException("An established connection was aborted by the software in your host machine");
            }
        };

        Field subscriptionsField = SseEmitterRegistry.class.getDeclaredField("subscriptions");
        subscriptionsField.setAccessible(true);
        var subscriptions = (ConcurrentHashMap<String, RealtimeSubscription>) subscriptionsField.get(registry);
        subscriptions.put("sub-1", new RealtimeSubscription("sub-1", null, Set.of("all"), failingEmitter));

        assertDoesNotThrow(() -> registry.broadcast("machine-telemetry-updated", Map.of("temperatureC", 50.0)));
        assertTrue(subscriptions.isEmpty());
    }
}

