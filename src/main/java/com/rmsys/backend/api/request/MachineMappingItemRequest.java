package com.rmsys.backend.api.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineMappingItemRequest {

    @NotBlank(message = "Logical key is required")
    @Size(max = 100)
    private String logicalKey;

    @NotBlank(message = "Area is required")
    @Size(max = 50)
    private String area;

    @NotNull(message = "Address start is required")
    private Integer addressStart;

    private Integer addressEnd;

    @NotBlank(message = "Data type is required")
    @Size(max = 30)
    private String dataType;

    private Double scaleFactor;

    @Size(max = 20)
    private String unit;

    @Size(max = 10)
    private String byteOrder;

    @Size(max = 10)
    private String wordOrder;

    private Boolean isRequired;

    @Size(max = 500)
    private String description;
}

