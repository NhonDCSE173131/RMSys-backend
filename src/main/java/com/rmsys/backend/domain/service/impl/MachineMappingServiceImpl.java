package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;
import com.rmsys.backend.domain.service.MachineMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Converts raw PLC values (already decoded by adapter) into NormalizedTelemetryDto.
 * The raw values are keyed by logical_key (e.g., "temperature", "powerKw", "running").
 * Scale factor is applied here if not already done by the adapter.
 */
@Slf4j
@Service
public class MachineMappingServiceImpl implements MachineMappingService {

    private static final String[] ALIAS_RUNNING = {"running"};
    private static final String[] ALIAS_TEMPERATURE = {"temperature", "temperaturec", "tempc", "temp"};
    private static final String[] ALIAS_POWER_KW = {"powerkw", "power_kw"};
    private static final String[] ALIAS_SPINDLE_SPEED_RPM = {"spindlespeedrpm", "spindle_speed_rpm"};
    private static final String[] ALIAS_ALARM_CODE = {"alarmcode", "alarm_code"};
    private static final String[] ALIAS_OUTPUT_COUNT = {"outputcount", "output_count"};
    private static final String[] ALIAS_GOOD_COUNT = {"goodcount", "good_count"};
    private static final String[] ALIAS_REJECT_COUNT = {"rejectcount", "reject_count"};
    private static final String[] ALIAS_RUNTIME_HOURS = {"runtimehours", "runtime_hours"};
    private static final String[] ALIAS_CYCLE_TIME_SEC = {"cycletimesec", "cycle_time_sec"};
    private static final String[] ALIAS_VIBRATION_MM_S = {"vibrationmms", "vibration_mm_s"};
    private static final String[] ALIAS_VOLTAGE_V = {"voltagev", "voltage_v"};
    private static final String[] ALIAS_CURRENT_A = {"currenta", "current_a"};

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

        Boolean running = getBoolean(scaled, "running", ALIAS_RUNNING);

        // Build NormalizedTelemetryDto from scaled values
        return NormalizedTelemetryDto.builder()
                .machineId(machine.getId())
                .machineCode(machine.getCode())
                .ts(Instant.now())
                .connectionStatus("ONLINE")
                .machineState(Boolean.TRUE.equals(running) ? "RUNNING" : "IDLE")
                .powerKw(getDouble(scaled, "powerKw", ALIAS_POWER_KW))
                .temperatureC(getDouble(scaled, "temperature", ALIAS_TEMPERATURE))
                .vibrationMmS(getDouble(scaled, "vibrationMmS", ALIAS_VIBRATION_MM_S))
                .runtimeHours(getDouble(scaled, "runtimeHours", ALIAS_RUNTIME_HOURS))
                .cycleTimeSec(getDouble(scaled, "cycleTimeSec", ALIAS_CYCLE_TIME_SEC))
                .outputCount(getInteger(scaled, "outputCount", ALIAS_OUTPUT_COUNT))
                .goodCount(getInteger(scaled, "goodCount", ALIAS_GOOD_COUNT))
                .rejectCount(getInteger(scaled, "rejectCount", ALIAS_REJECT_COUNT))
                .spindleSpeedRpm(getDouble(scaled, "spindleSpeedRpm", ALIAS_SPINDLE_SPEED_RPM))
                .alarmCode(getInteger(scaled, "alarmCode", ALIAS_ALARM_CODE))
                .feedRateMmMin(getDouble(scaled, "feedRateMmMin"))
                .spindleLoadPct(getDouble(scaled, "spindleLoadPct"))
                .servoLoadPct(getDouble(scaled, "servoLoadPct"))
                .voltageV(getDouble(scaled, "voltageV", ALIAS_VOLTAGE_V))
                .currentA(getDouble(scaled, "currentA", ALIAS_CURRENT_A))
                .powerFactor(getDouble(scaled, "powerFactor"))
                .frequencyHz(getDouble(scaled, "frequencyHz"))
                .energyKwhShift(getDouble(scaled, "energyKwhShift"))
                .energyKwhDay(getDouble(scaled, "energyKwhDay"))
                .motorTemperatureC(getDouble(scaled, "motorTemperatureC"))
                .bearingTemperatureC(getDouble(scaled, "bearingTemperatureC"))
                .cabinetTemperatureC(getDouble(scaled, "cabinetTemperatureC"))
                .cycleRunning(running)
                .metadata(scaled)
                .build();
    }

    private Double getDouble(Map<String, Object> map, String key, String... aliases) {
        Object val = resolveValue(map, key, aliases);
        if (val instanceof Number num) return num.doubleValue();
        if (val instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer getInteger(Map<String, Object> map, String key, String... aliases) {
        Object val = resolveValue(map, key, aliases);
        if (val instanceof Number num) return num.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean getBoolean(Map<String, Object> map, String key, String... aliases) {
        Object val = resolveValue(map, key, aliases);
        if (val instanceof Boolean b) return b;
        if (val instanceof Number num) return num.intValue() != 0;
        if (val instanceof String s) {
            String normalized = s.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "1".equals(normalized) || "on".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "0".equals(normalized) || "off".equals(normalized)) {
                return false;
            }
        }
        return null;
    }

    private Object resolveValue(Map<String, Object> map, String key, String... aliases) {
        List<String> candidates = new ArrayList<>();
        candidates.add(key);
        if (aliases != null) {
            candidates.addAll(List.of(aliases));
        }

        for (String candidate : candidates) {
            if (map.containsKey(candidate)) {
                return map.get(candidate);
            }
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String normalizedEntryKey = normalizeKey(entry.getKey());
            for (String candidate : candidates) {
                if (normalizedEntryKey.equals(normalizeKey(candidate))) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }
}

