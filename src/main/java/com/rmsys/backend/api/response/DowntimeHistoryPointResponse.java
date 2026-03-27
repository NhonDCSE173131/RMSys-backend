package com.rmsys.backend.api.response;

import lombok.Builder;
import java.time.Instant;
import java.util.UUID;

@Builder
public record DowntimeHistoryPointResponse(
        UUID id,
        UUID machineId,
        String machineCode,
        String reasonCode,
        String reasonGroup,
        Instant startedAt,
        Instant endedAt,
        Integer durationSec,
        boolean plannedStop,
        boolean abnormalStop,
        String notes
) {}

