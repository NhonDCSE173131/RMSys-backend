package com.rmsys.backend.api.response;

import lombok.Builder;
import java.time.Instant;
import java.util.UUID;

@Builder
public record AlarmHistoryPointResponse(
        UUID id,
        UUID machineId,
        String alarmCode,
        String alarmType,
        String severity,
        String message,
        Instant startedAt,
        Instant endedAt,
        boolean isActive,
        boolean acknowledged,
        String acknowledgedBy,
        Instant acknowledgedAt
) {}

