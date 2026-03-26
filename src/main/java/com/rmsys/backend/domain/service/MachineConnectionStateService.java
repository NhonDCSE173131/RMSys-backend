package com.rmsys.backend.domain.service;

import com.rmsys.backend.domain.entity.MachineEntity;

import java.time.Instant;

public interface MachineConnectionStateService {
    void markTelemetryReceived(MachineEntity machine, Instant sourceTs, Instant receivedAt, String payloadFingerprint, boolean acceptedForCurrentState);

    void evaluateByWatchdog(MachineEntity machine, Instant now);
}

