package com.rmsys.backend.api.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineConfigResponse {
    private UUID id;
    private String code;
    private String name;
    private String type;
    private String vendor;
    private String model;
    private String protocol;
    private String host;
    private Integer port;
    private Integer unitId;
    private Integer pollIntervalMs;
    private String connectionMode;
    private Boolean autoConnect;
    private UUID profileId;
    private UUID mappingFileId;
    private String profileCode;
    private String lineId;
    private String plantId;
    private String status;
    private Boolean isEnabled;
    private String lastConnectionStatus;
    private Instant lastConnectedAt;
    private Instant lastDisconnectedAt;
    private Instant lastDataAt;
    private Instant createdAt;
    private Instant updatedAt;
}

