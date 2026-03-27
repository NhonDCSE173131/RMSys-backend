package com.rmsys.backend.infrastructure.realtime;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record SseEventEnvelope(
        String eventId,
        String eventType,
        UUID machineId,
        String machineCode,
        Instant sourceTs,
        Instant receivedAt,
        Long sequence,
        Integer quality,
        Object payload
) {
    // Backward-compatible aliases used by existing tests.
    public String type() {
        return eventType;
    }

    public Instant ts() {
        return sourceTs;
    }
}

