package com.rmsys.backend.api.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineConnectionStatusResponse {
    private UUID machineId;
    private String machineCode;
    private String status;
    private Instant lastConnectedAt;
    private Instant lastDisconnectedAt;
    private Instant lastDataAt;
    private String lastError;
}

