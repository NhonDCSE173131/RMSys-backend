package com.rmsys.backend.api.response;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineImportResultResponse {
    private UUID batchId;
    private String importType;
    private int totalRows;
    private int successRows;
    private int failedRows;
    private List<ImportRowError> errors;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ImportRowError {
        private int row;
        private String field;
        private String message;
    }
}

