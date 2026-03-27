package com.rmsys.backend.api.response;

import lombok.Builder;

import java.util.List;

@Builder
public record OeeLossesResponse(
        int runtimeSec,
        int stopSec,
        int goodCount,
        int rejectCount,
        List<LossItem> losses
) {
    @Builder
    public record LossItem(String code, String label, double value) {}
}

