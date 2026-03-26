package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.request.AckAlarmRequest;
import com.rmsys.backend.api.response.AlarmResponse;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.common.response.PageResponse;
import com.rmsys.backend.domain.entity.AlarmEventEntity;
import com.rmsys.backend.domain.repository.AlarmEventRepository;
import com.rmsys.backend.domain.service.AlarmService;
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
public class AlarmServiceImpl implements AlarmService {

    private final AlarmEventRepository alarmRepo;
    private final SseEmitterRegistry sseRegistry;

    @Override
    public List<AlarmResponse> getActiveAlarms() {
        return alarmRepo.findByIsActiveTrueOrderByStartedAtDesc().stream().map(this::toResponse).toList();
    }

    @Override
    public PageResponse<AlarmResponse> getAlarmHistory(Pageable pageable) {
        var page = alarmRepo.findAllByOrderByStartedAtDesc(pageable).map(this::toResponse);
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
        return AlarmResponse.builder()
                .id(e.getId()).machineId(e.getMachineId())
                .alarmCode(e.getAlarmCode()).alarmType(e.getAlarmType())
                .severity(e.getSeverity()).message(e.getMessage())
                .startedAt(e.getStartedAt()).endedAt(e.getEndedAt())
                .isActive(e.getIsActive()).acknowledged(e.getAcknowledged())
                .acknowledgedBy(e.getAcknowledgedBy()).acknowledgedAt(e.getAcknowledgedAt())
                .build();
    }
}

