package com.rmsys.backend.api.response;

import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineMappingResponse {
    private UUID id;
    private UUID profileId;
    private UUID mappingFileId;
    private String logicalKey;
    private String area;
    private Integer addressStart;
    private Integer addressEnd;
    private String dataType;
    private Double scaleFactor;
    private String unit;
    private String byteOrder;
    private String wordOrder;
    private Boolean isRequired;
    private String description;
}

