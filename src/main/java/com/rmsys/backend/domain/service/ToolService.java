package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.ToolOverviewResponse;
import java.util.UUID;

public interface ToolService {
    ToolOverviewResponse getOverview();
    ToolOverviewResponse getByMachine(UUID machineId);
}

