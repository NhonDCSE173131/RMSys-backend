package com.rmsys.backend.api.response;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineProfileResponse {
    private UUID id;
    private String profileCode;
    private String profileName;
    private String protocol;
    private String vendor;
    private String model;
    private String description;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    private List<MachineMappingResponse> mappings;
}

