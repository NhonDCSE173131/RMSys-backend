package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.MaintenanceOverviewResponse;
import java.util.UUID;

public interface MaintenanceService {
    MaintenanceOverviewResponse getOverview();
}

