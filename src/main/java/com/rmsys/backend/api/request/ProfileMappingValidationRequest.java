package com.rmsys.backend.api.request;

import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProfileMappingValidationRequest {
    private UUID profileId;
    private UUID mappingFileId;
}

