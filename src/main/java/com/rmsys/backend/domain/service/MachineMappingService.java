package com.rmsys.backend.domain.service;

import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.entity.MachineProfileMappingEntity;

import java.util.List;
import java.util.Map;

/**
 * Maps raw PLC register/tag values to normalized telemetry DTO.
 */
public interface MachineMappingService {
    /**
     * Convert raw values read from PLC into a NormalizedTelemetryDto.
     *
     * @param machine  the machine entity
     * @param mappings the profile mappings
     * @param rawValues raw values keyed by logical_key
     * @return normalized telemetry DTO
     */
    NormalizedTelemetryDto mapToTelemetry(MachineEntity machine,
                                          List<MachineProfileMappingEntity> mappings,
                                          Map<String, Object> rawValues);
}

