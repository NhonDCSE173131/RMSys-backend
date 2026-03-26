package com.rmsys.backend.infrastructure.realtime;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record SseEventEnvelope(
        String type,
        Instant ts,
        UUID machineId,
        Object payload
) {
}

