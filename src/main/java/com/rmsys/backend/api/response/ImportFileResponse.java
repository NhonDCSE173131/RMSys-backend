package com.rmsys.backend.api.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportFileResponse {
    private UUID fileId;
    private String fileName;
    private String importType;
    private UUID batchId;
    private Instant uploadedAt;
    private String uploadedBy;
    private String status;
    private String profileCode;
    private UUID profileId;
    private Integer totalRows;
    private Integer successRows;
    private Integer failedRows;
}

