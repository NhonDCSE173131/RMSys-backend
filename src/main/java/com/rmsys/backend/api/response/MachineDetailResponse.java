package com.rmsys.backend.api.response;

import lombok.Builder;
import java.time.Instant;
import java.util.UUID;

@Builder
public record MachineDetailResponse(
        UUID id,
        String code,
        String name,
        String type,
        String vendor,
        String model,
        String lineId,
        String plantId,
        String status,
        String connectionState,
        Boolean connectionUnstable,
        Instant lastSeenAt,
        boolean isEnabled,
        Instant createdAt
) {}

