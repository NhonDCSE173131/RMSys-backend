package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.request.AckAlarmRequest;
import com.rmsys.backend.api.response.AlarmResponse;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.common.response.PageResponse;
import com.rmsys.backend.domain.entity.AlarmEventEntity;
import com.rmsys.backend.domain.repository.AlarmEventRepository;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.AlarmService;
import com.rmsys.backend.domain.service.AlarmLifecycleService;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlarmServiceImpl implements AlarmService, AlarmLifecycleService {

    private final AlarmEventRepository alarmRepo;
    private final MachineRepository machineRepo;
    private final SseEmitterRegistry sseRegistry;

    @Override
    public List<AlarmResponse> getActiveAlarms() {
        return alarmRepo.findByIsActiveTrueOrderByStartedAtDesc().stream().map(this::toResponse).toList();
    }

    @Override
    public AlarmResponse getAlarmById(UUID alarmId) {
        return alarmRepo.findById(alarmId)
                .map(this::toResponse)
                .orElseThrow(() -> AppException.notFound("Alarm", alarmId));
    }

    @Override
    @Transactional
    public void closeAlarmByCode(UUID machineId, String alarmCode) {
        alarmRepo.findTopByMachineIdAndAlarmCodeAndIsActiveTrue(machineId, alarmCode)
                .ifPresent(alarm -> {
                    alarm.setIsActive(false);
                    alarm.setEndedAt(Instant.now());
                    alarmRepo.save(alarm);
                    sseRegistry.broadcast("alarm-resolved", toResponse(alarm));
                });
    }

    @Override
    @Transactional
    public void autoCloseStaleAlarms() {
        var staleThreshold = Instant.now().minusSeconds(3600);
        alarmRepo.findByIsActiveTrueOrderByStartedAtDesc().stream()
                .filter(a -> a.getStartedAt().isBefore(staleThreshold))
                .filter(a -> "NOISE".equals(a.getAlarmType()))
                .forEach(alarm -> {
                    alarm.setIsActive(false);
                    alarm.setEndedAt(Instant.now());
                    alarmRepo.save(alarm);
                });
    }

    @Override
    public PageResponse<AlarmResponse> getAlarmHistory(Pageable pageable) {
        var page = alarmRepo.findAllByOrderByStartedAtDesc(pageable).map(this::toResponse);
        return PageResponse.of(page);
    }

    @Override
    public PageResponse<AlarmResponse> getMachineAlarmHistory(UUID machineId, Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null) {
            var page = alarmRepo.findByMachineIdAndStartedAtBetweenOrderByStartedAtDesc(machineId, from, to, pageable)
                    .map(this::toResponse);
            return PageResponse.of(page);
        }
        var page = alarmRepo.findByMachineIdOrderByStartedAtDesc(machineId, pageable).map(this::toResponse);
        return PageResponse.of(page);
    }

    @Override
    @Transactional
    public void acknowledgeAlarm(UUID alarmId, AckAlarmRequest request) {
        var alarm = alarmRepo.findById(alarmId)
                .orElseThrow(() -> AppException.notFound("Alarm", alarmId));
        alarm.setAcknowledged(true);
        alarm.setAcknowledgedBy(request.acknowledgedBy());
        alarm.setAcknowledgedAt(Instant.now());
        alarm.setIsActive(false);
        alarm.setEndedAt(Instant.now());
        alarmRepo.save(alarm);
        sseRegistry.broadcast("alarm-acknowledged", toResponse(alarm));
    }

    private AlarmResponse toResponse(AlarmEventEntity e) {
        String machineCode = machineRepo.findById(e.getMachineId()).map(m -> m.getCode()).orElse(null);
        return AlarmResponse.builder()
                .id(e.getId()).machineId(e.getMachineId())
                .machineCode(machineCode)
                .alarmCode(e.getAlarmCode()).alarmType(e.getAlarmType())
                .severity(e.getSeverity()).message(e.getMessage())
                .startedAt(e.getStartedAt()).endedAt(e.getEndedAt())
                .isActive(e.getIsActive()).acknowledged(e.getAcknowledged())
                .acknowledgedBy(e.getAcknowledgedBy()).acknowledgedAt(e.getAcknowledgedAt())
                .build();
    }
}

