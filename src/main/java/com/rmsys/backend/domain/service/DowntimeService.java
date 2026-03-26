package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.response.DowntimeHistoryPointResponse;
import com.rmsys.backend.common.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface DowntimeService {
    PageResponse<DowntimeHistoryPointResponse> getMachineDowntimeHistory(UUID machineId, Instant from, Instant to, Pageable pageable);
}

