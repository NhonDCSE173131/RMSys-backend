package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import com.rmsys.backend.domain.service.MachineMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts raw PLC values (already decoded by adapter) into NormalizedTelemetryDto.
 * The raw values are keyed by logical_key (e.g., "temperature", "powerKw", "running").
 * Scale factor is applied here if not already done by the adapter.
 */
@Slf4j
@Service
public class MachineMappingServiceImpl implements MachineMappingService {

    @Override
    public NormalizedTelemetryDto mapToTelemetry(MachineEntity machine,
                                                  List<MachineProfileMappingEntity> mappings,
                                                  Map<String, Object> rawValues) {
        Map<String, Object> scaled = new HashMap<>();
        for (MachineProfileMappingEntity mapping : mappings) {
            String key = mapping.getLogicalKey();
            Object raw = rawValues.get(key);
            if (raw == null) {
                if (Boolean.TRUE.equals(mapping.getIsRequired())) {
                    log.warn("Required mapping key '{}' missing from raw values for machine {}", key, machine.getCode());
                }
                continue;
            }
            // Apply scale factor
            if (raw instanceof Number num && mapping.getScaleFactor() != null && mapping.getScaleFactor() != 1.0) {
                scaled.put(key, num.doubleValue() * mapping.getScaleFactor());
            } else {
                scaled.put(key, raw);
            }
        }

        // Build NormalizedTelemetryDto from scaled values
        return NormalizedTelemetryDto.builder()
                .machineId(machine.getId())
                .machineCode(machine.getCode())
                .ts(Instant.now())
                .connectionStatus("ONLINE")
                .machineState(getBoolean(scaled, "running") != null && getBoolean(scaled, "running") ? "RUNNING" : "IDLE")
                .powerKw(getDouble(scaled, "powerKw"))
                .temperatureC(getDouble(scaled, "temperature"))
                .vibrationMmS(getDouble(scaled, "vibrationMmS"))
                .runtimeHours(getDouble(scaled, "runtimeHours"))
                .cycleTimeSec(getDouble(scaled, "cycleTimeSec"))
                .outputCount(getInteger(scaled, "outputCount"))
                .goodCount(getInteger(scaled, "goodCount"))
                .rejectCount(getInteger(scaled, "rejectCount"))
                .spindleSpeedRpm(getDouble(scaled, "spindleSpeedRpm"))
                .feedRateMmMin(getDouble(scaled, "feedRateMmMin"))
                .spindleLoadPct(getDouble(scaled, "spindleLoadPct"))
                .servoLoadPct(getDouble(scaled, "servoLoadPct"))
                .voltageV(getDouble(scaled, "voltageV"))
                .currentA(getDouble(scaled, "currentA"))
                .powerFactor(getDouble(scaled, "powerFactor"))
                .frequencyHz(getDouble(scaled, "frequencyHz"))
                .energyKwhShift(getDouble(scaled, "energyKwhShift"))
                .energyKwhDay(getDouble(scaled, "energyKwhDay"))
                .motorTemperatureC(getDouble(scaled, "motorTemperatureC"))
                .bearingTemperatureC(getDouble(scaled, "bearingTemperatureC"))
                .cabinetTemperatureC(getDouble(scaled, "cabinetTemperatureC"))
                .cycleRunning(getBoolean(scaled, "running"))
                .metadata(scaled)
                .build();
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number num) return num.doubleValue();
        return null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number num) return num.intValue();
        return null;
    }

    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof Number num) return num.intValue() != 0;
        return null;
    }
}

