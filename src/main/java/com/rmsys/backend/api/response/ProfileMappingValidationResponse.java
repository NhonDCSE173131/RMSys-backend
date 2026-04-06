package com.rmsys.backend.api.response;

import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProfileMappingValidationResponse {
    private Boolean valid;
    private UUID profileId;
    private String profileCode;
    private UUID mappingFileId;
    private String mappingProfileCode;
    private String message;
}

