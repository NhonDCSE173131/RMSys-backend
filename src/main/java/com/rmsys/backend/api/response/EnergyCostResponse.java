package com.rmsys.backend.api.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record EnergyCostResponse(
        double ratePerKwh,
        double totalEnergyTodayKwh,
        double totalEnergyMonthKwh,
        double costToday,
        double costMonth,
        String currency,
        Instant asOf
) {}

