package com.rmsys.backend.domain.service;

import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;

public interface RuleEngineService {
    void evaluate(NormalizedTelemetryDto dto);
}

