package com.rmsys.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record NormalizedAlarmDto(
        @NotNull UUID machineId,
        @NotBlank String alarmCode,
        @NotBlank String alarmType,
        @NotBlank String severity,
        @NotBlank String message,
        Instant startedAt
) {}
