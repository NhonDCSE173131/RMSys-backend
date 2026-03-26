package com.rmsys.backend.domain.service;

import com.rmsys.backend.api.request.AckAlarmRequest;
import com.rmsys.backend.api.response.AlarmResponse;
import java.util.List;
import java.util.UUID;

public interface AlarmLifecycleService {
    List<AlarmResponse> getActiveAlarms();

    AlarmResponse getAlarmById(UUID alarmId);

    void acknowledgeAlarm(UUID alarmId, AckAlarmRequest request);

    void closeAlarmByCode(UUID machineId, String alarmCode);

    void autoCloseStaleAlarms();
}

