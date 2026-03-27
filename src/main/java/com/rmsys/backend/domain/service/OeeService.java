package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.AnalyticsBreakdownResponse;
import com.rmsys.backend.api.response.AnalyticsTrendResponse;
import com.rmsys.backend.api.response.OeeLossesResponse;
import com.rmsys.backend.api.response.OeeOverviewResponse;

import java.time.Instant;

public interface OeeService {
    OeeOverviewResponse getOverview();
    AnalyticsTrendResponse getTrend(Instant from, Instant to, String interval);
    AnalyticsBreakdownResponse getByMachine();
    OeeLossesResponse getLosses(Instant from);
}

