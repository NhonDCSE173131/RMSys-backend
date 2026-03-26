package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.service.TelemetryQualityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TelemetryQualityServiceImpl implements TelemetryQualityService {

    @Override
    public double scoreQuality(NormalizedTelemetryDto dto) {
        double score = 100.0;

        // Deduct for missing key fields
        if (dto.machineId() == null) score -= 50;
        if (dto.ts() == null) score -= 20;
        if (dto.machineState() == null) score -= 10;

        // Deduct for suspicious values
        if (isSuspicious(dto)) score -= 15;

        // Deduct for incomplete optional fields
        int fieldsPresent = countNonNullFields(dto);
        int totalFields = 25; // approximate
        double completeness = (double) fieldsPresent / totalFields;
        score -= (1.0 - completeness) * 10;

        return Math.max(0, Math.min(100, score));
    }

    @Override
    public boolean isLateArrival(NormalizedTelemetryDto dto, Long latestAcceptedSeq) {
        if (latestAcceptedSeq == null || dto.metadata() == null) {
            return false;
        }
        Object seq = dto.metadata().get("sourceSequence");
        if (seq instanceof Number) {
            return ((Number) seq).longValue() < latestAcceptedSeq;
        }
        return false;
    }

    @Override
    public boolean isSuspicious(NormalizedTelemetryDto dto) {
        // Power too high
        if (dto.powerKw() != null && dto.powerKw() > 100) return true;

        // Negative temperature
        if (dto.temperatureC() != null && dto.temperatureC() < -50) return true;

        // Abnormal vibration
        if (dto.vibrationMmS() != null && dto.vibrationMmS() > 100) return true;

        // Impossible cycle time
        if (dto.cycleTimeSec() != null && dto.cycleTimeSec() < 0) return true;

        // Reject count > output count
        if (dto.rejectCount() != null && dto.outputCount() != null && dto.rejectCount() > dto.outputCount()) {
            return true;
        }

        return false;
    }

    private int countNonNullFields(NormalizedTelemetryDto dto) {
        int count = 0;
        if (dto.machineId() != null) count++;
        if (dto.ts() != null) count++;
        if (dto.connectionStatus() != null) count++;
        if (dto.machineState() != null) count++;
        if (dto.operationMode() != null) count++;
        if (dto.powerKw() != null) count++;
        if (dto.temperatureC() != null) count++;
        if (dto.vibrationMmS() != null) count++;
        if (dto.runtimeHours() != null) count++;
        if (dto.outputCount() != null) count++;
        if (dto.goodCount() != null) count++;
        if (dto.rejectCount() != null) count++;
        if (dto.voltageV() != null) count++;
        if (dto.currentA() != null) count++;
        if (dto.motorTemperatureC() != null) count++;
        if (dto.bearingTemperatureC() != null) count++;
        if (dto.toolCode() != null) count++;
        return count;
    }
}

