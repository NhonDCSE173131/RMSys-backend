package com.rmsys.backend.domain.dto;

import lombok.Builder;
import java.time.Instant;
import java.util.UUID;

@Builder
public record NormalizedDowntimeDto(
        UUID machineId,
        String reasonCode,
        String reasonGroup,
        Instant startedAt,
        boolean plannedStop,
        boolean abnormalStop,
        String notes
) {}

