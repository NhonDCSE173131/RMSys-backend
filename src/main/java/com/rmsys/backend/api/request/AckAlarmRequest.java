package com.rmsys.backend.api.request;

import jakarta.validation.constraints.NotBlank;

public record AckAlarmRequest(
        @NotBlank String acknowledgedBy
) {}

