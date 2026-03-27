package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.AnalyticsBreakdownResponse;
import com.rmsys.backend.api.response.AnalyticsTrendResponse;
import com.rmsys.backend.api.response.EnergyCostResponse;
import com.rmsys.backend.api.response.EnergyOverviewResponse;

import java.time.Instant;

public interface EnergyService {
    EnergyOverviewResponse getOverview();
    AnalyticsTrendResponse getTrend(Instant from, Instant to, String interval);
    AnalyticsBreakdownResponse getByArea();
    AnalyticsBreakdownResponse getByMachine();
    EnergyCostResponse getCost();
}

