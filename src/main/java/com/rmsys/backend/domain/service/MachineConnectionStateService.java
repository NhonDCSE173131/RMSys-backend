package com.rmsys.backend.domain.service;

import com.rmsys.backend.domain.entity.MachineEntity;

import java.time.Instant;
import java.util.Map;

public interface MachineConnectionStateService {
    void markTelemetryReceived(MachineEntity machine, Instant sourceTs, Instant receivedAt, String payloadFingerprint, boolean acceptedForCurrentState);

    void evaluateByWatchdog(MachineEntity machine, Instant now);

    void markConnectionReported(MachineEntity machine, String reportedState, Instant at, Map<String, Object> metadata);
}

