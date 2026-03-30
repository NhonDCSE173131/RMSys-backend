package com.rmsys.backend.api.response;

import lombok.Builder;

@Builder
public record MachineOverviewTool(
        String toolCode,
        Double remainingToolLifePct,
        String wearLevel
) {}

