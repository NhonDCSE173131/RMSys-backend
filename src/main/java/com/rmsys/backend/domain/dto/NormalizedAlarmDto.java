package com.rmsys.backend.domain.dto;

import lombok.Builder;
import java.time.Instant;
import java.util.UUID;

@Builder
public record NormalizedAlarmDto(
        UUID machineId,
        String alarmCode,
        String alarmType,
        String severity,
        String message,
        Instant startedAt
) {}

