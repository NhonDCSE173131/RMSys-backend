package com.rmsys.backend.domain.service;

import com.rmsys.backend.domain.entity.MachineTelemetryEntity;
import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;

/**
 * Quality scoring service for telemetry data.
 * Evaluates data quality based on recency, completeness, and reasonableness.
 */
public interface TelemetryQualityService {
    /**
     * Score telemetry quality on scale 0-100.
     * 100 = perfect, 0 = completely invalid.
     */
    double scoreQuality(NormalizedTelemetryDto dto);

    /**
     * Determine if packet is late arrival (sourceTs older than latest accepted).
     */
    boolean isLateArrival(NormalizedTelemetryDto dto, Long latestAcceptedSeq);

    /**
     * Flag suspicious metric combinations (e.g., power too high for state).
     */
    boolean isSuspicious(NormalizedTelemetryDto dto);
}

