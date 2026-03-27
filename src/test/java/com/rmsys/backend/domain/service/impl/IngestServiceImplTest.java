package com.rmsys.backend.domain.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.repository.EnergyTelemetryRepository;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.repository.MachineTelemetryRepository;
import com.rmsys.backend.domain.repository.MaintenanceTelemetryRepository;
import com.rmsys.backend.domain.repository.ToolUsageTelemetryRepository;
import com.rmsys.backend.domain.service.MachineConnectionStateService;
import com.rmsys.backend.domain.service.TelemetryQualityService;
import com.rmsys.backend.domain.service.RuleEngineService;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestServiceImplTest {

    @Mock
    private MachineTelemetryRepository telemetryRepo;
    @Mock
    private EnergyTelemetryRepository energyRepo;
    @Mock
    private MaintenanceTelemetryRepository maintRepo;
    @Mock
    private ToolUsageTelemetryRepository toolRepo;
    @Mock
    private MachineRepository machineRepo;
    @Mock
    private RuleEngineService ruleEngine;
    @Mock
    private SseEmitterRegistry sseRegistry;
    @Mock
    private MachineConnectionStateService connectionStateService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TelemetryQualityService qualityService;

    @InjectMocks
    private IngestServiceImpl service;

    @Test
    void ingestTelemetry_minimalPayload_savesCoreDataAndPublishesEvent() {
        var machineId = UUID.randomUUID();
        var machine = MachineEntity.builder()
                .id(machineId)
                .code("M-001")
                .name("Machine 001")
                .type("CNC")
                .vendor("TEST")
                .status("IDLE")
                .build();
        when(machineRepo.findById(machineId)).thenReturn(Optional.of(machine));
        when(qualityService.scoreQuality(any())).thenReturn(100.0);

        var dto = NormalizedTelemetryDto.builder()
                .machineId(machineId)
                .temperatureC(42.0)
                .build();

        service.ingestTelemetry(dto);

        verify(telemetryRepo, times(1)).save(any());
        verify(energyRepo, never()).save(any());
        verify(maintRepo, never()).save(any());
        verify(toolRepo, never()).save(any());
        verify(connectionStateService, times(1)).markTelemetryReceived(any(), any(), any(), any(), anyBoolean());
        verify(ruleEngine, times(1)).evaluate(dto);
        verify(sseRegistry, times(1)).broadcast("machine-telemetry-updated", dto);
    }

    @Test
    void ingestTelemetry_optionalDataPresent_savesAllSlicesAndUpdatesMachineStatus() {
        var machineId = UUID.randomUUID();
        var machine = MachineEntity.builder()
                .id(machineId)
                .code("M-001")
                .name("Machine 001")
                .type("CNC")
                .vendor("TEST")
                .status("IDLE")
                .build();

        when(machineRepo.findById(machineId)).thenReturn(Optional.of(machine));
        when(qualityService.scoreQuality(any())).thenReturn(95.0);

        var dto = NormalizedTelemetryDto.builder()
                .machineId(machineId)
                .machineState("RUNNING")
                .powerKw(10.0)
                .voltageV(380.0)
                .motorTemperatureC(58.0)
                .toolCode("T01")
                .remainingToolLifePct(75.0)
                .build();

        service.ingestTelemetry(dto);

        verify(telemetryRepo, times(1)).save(any());
        verify(energyRepo, times(1)).save(any());
        verify(maintRepo, times(1)).save(any());
        verify(toolRepo, times(1)).save(any());
        verify(connectionStateService, times(1)).markTelemetryReceived(any(), any(), any(), any(), anyBoolean());
        verify(machineRepo, times(1)).save(machine);
        verify(ruleEngine, times(1)).evaluate(dto);
        verify(sseRegistry, times(1)).broadcast("machine-telemetry-updated", dto);
    }
}

