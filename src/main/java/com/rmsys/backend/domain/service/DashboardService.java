package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.DashboardLiveResponse;
import com.rmsys.backend.api.response.DashboardOverviewResponse;

public interface DashboardService {
    DashboardOverviewResponse getOverview();
    DashboardLiveResponse getLive();
}

