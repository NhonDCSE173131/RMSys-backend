package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.OeeOverviewResponse;
import java.util.UUID;

public interface OeeService {
    OeeOverviewResponse getOverview();
}

