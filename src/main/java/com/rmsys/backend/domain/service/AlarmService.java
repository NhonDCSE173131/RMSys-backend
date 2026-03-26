package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.request.AckAlarmRequest;
import com.rmsys.backend.api.response.AlarmResponse;
import com.rmsys.backend.common.response.PageResponse;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlarmService {
    List<AlarmResponse> getActiveAlarms();
    PageResponse<AlarmResponse> getAlarmHistory(Pageable pageable);
    void acknowledgeAlarm(UUID alarmId, AckAlarmRequest request);
    PageResponse<AlarmResponse> getMachineAlarmHistory(UUID machineId, Instant from, Instant to, Pageable pageable);
}

