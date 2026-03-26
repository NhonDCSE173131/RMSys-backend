package com.rmsys.backend.domain.service.impl;

import com.rmsys.backend.api.response.DowntimeHistoryPointResponse;
import com.rmsys.backend.common.response.PageResponse;
import com.rmsys.backend.domain.entity.DowntimeEventEntity;
import com.rmsys.backend.domain.repository.DowntimeEventRepository;
import com.rmsys.backend.domain.service.DowntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DowntimeServiceImpl implements DowntimeService {

    private final DowntimeEventRepository downtimeRepo;

    @Override
    public PageResponse<DowntimeHistoryPointResponse> getMachineDowntimeHistory(
            UUID machineId, Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null) {
            var page = downtimeRepo
                    .findByMachineIdAndStartedAtBetweenOrderByStartedAtDesc(machineId, from, to, pageable)
                    .map(this::toResponse);
            return PageResponse.of(page);
        }
        var page = downtimeRepo.findByMachineIdOrderByStartedAtDesc(machineId, pageable)
                .map(this::toResponse);
        return PageResponse.of(page);
    }

    private DowntimeHistoryPointResponse toResponse(DowntimeEventEntity e) {
        return DowntimeHistoryPointResponse.builder()
                .id(e.getId())
                .machineId(e.getMachineId())
                .reasonCode(e.getReasonCode())
                .reasonGroup(e.getReasonGroup())
                .startedAt(e.getStartedAt())
                .endedAt(e.getEndedAt())
                .durationSec(e.getDurationSec())
                .plannedStop(Boolean.TRUE.equals(e.getPlannedStop()))
                .abnormalStop(Boolean.TRUE.equals(e.getAbnormalStop()))
                .notes(e.getNotes())
                .build();
    }
}

