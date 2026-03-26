package com.rmsys.backend.domain.service;

import com.rmsys.backend.domain.dto.NormalizedAlarmDto;
import com.rmsys.backend.domain.dto.NormalizedDowntimeDto;
import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;

public interface IngestService {
    void ingestTelemetry(NormalizedTelemetryDto dto);
    void ingestAlarm(NormalizedAlarmDto dto);
    void ingestDowntime(NormalizedDowntimeDto dto);
}

