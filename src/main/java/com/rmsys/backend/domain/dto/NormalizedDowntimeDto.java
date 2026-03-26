package com.rmsys.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record NormalizedDowntimeDto(
        @NotNull UUID machineId,
        @NotBlank String reasonCode,
        @NotBlank String reasonGroup,
        Instant startedAt,
        boolean plannedStop,
        boolean abnormalStop,
        String notes
) {}

