package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.entity.AlarmEventEntity;
import com.rmsys.backend.domain.entity.MachineThresholdEntity;
import com.rmsys.backend.domain.repository.AlarmEventRepository;
import com.rmsys.backend.domain.repository.MachineThresholdRepository;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceImplTest {

    @Mock
    private MachineThresholdRepository thresholdRepo;

    @Mock
    private AlarmEventRepository alarmRepo;

    @Mock
    private SseEmitterRegistry sseRegistry;

    @InjectMocks
    private RuleEngineServiceImpl service;

    @Test
    void evaluate_warningThreshold_deduplicatesActiveAlarm() {
        var machineId = UUID.randomUUID();

        var threshold = MachineThresholdEntity.builder()
                .machineId(machineId)
                .metricCode("TEMPERATURE")
                .warningValue(80.0)
                .unit("C")
                .build();

        var existing = AlarmEventEntity.builder()
                .machineId(machineId)
                .alarmCode("TEMPERATURE_WARNING")
                .isActive(true)
                .build();

        when(thresholdRepo.findByMachineId(machineId)).thenReturn(List.of(threshold));
        var callCounter = new AtomicInteger(0);
        when(alarmRepo.findTopByMachineIdAndAlarmCodeAndIsActiveTrue(machineId, "TEMPERATURE_WARNING"))
                .thenAnswer(invocation -> callCounter.getAndIncrement() == 0 ? Optional.empty() : Optional.of(existing));
        when(alarmRepo.findTopByMachineIdAndAlarmCodeAndIsActiveTrue(machineId, "TEMPERATURE_CRITICAL"))
                .thenReturn(Optional.empty());

        var dto = NormalizedTelemetryDto.builder()
                .machineId(machineId)
                .temperatureC(85.0)
                .build();

        service.evaluate(dto);
        service.evaluate(dto);

        verify(alarmRepo, times(1)).save(any(AlarmEventEntity.class));
        verify(sseRegistry, times(1)).broadcast(eq("alarm-created"), any(AlarmEventEntity.class));
    }

    @Test
    void evaluate_metricRecovered_closesActiveAlarm() {
        var machineId = UUID.randomUUID();

        var threshold = MachineThresholdEntity.builder()
                .machineId(machineId)
                .metricCode("TEMPERATURE")
                .warningValue(80.0)
                .unit("C")
                .build();

        var activeWarning = AlarmEventEntity.builder()
                .machineId(machineId)
                .alarmCode("TEMPERATURE_WARNING")
                .isActive(true)
                .build();

        when(thresholdRepo.findByMachineId(machineId)).thenReturn(List.of(threshold));
        when(alarmRepo.findTopByMachineIdAndAlarmCodeAndIsActiveTrue(machineId, "TEMPERATURE_WARNING"))
                .thenReturn(Optional.of(activeWarning));
        when(alarmRepo.findTopByMachineIdAndAlarmCodeAndIsActiveTrue(machineId, "TEMPERATURE_CRITICAL"))
                .thenReturn(Optional.empty());

        var dto = NormalizedTelemetryDto.builder()
                .machineId(machineId)
                .temperatureC(60.0)
                .build();

        service.evaluate(dto);

        verify(alarmRepo, times(1)).save(activeWarning);
        verify(sseRegistry, times(1)).broadcast("alarm-resolved", activeWarning);
    }
}


