package com.rmsys.backend.infrastructure.realtime;

import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}

