package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.EnergyOverviewResponse;
import java.util.UUID;

public interface EnergyService {
    EnergyOverviewResponse getOverview();
}

