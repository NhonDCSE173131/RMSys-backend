package com.rmsys.backend.api.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineCreateRequest {

    @NotBlank(message = "Machine code is required")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Machine name is required")
    @Size(max = 200)
    private String name;

    @Size(max = 50)
    private String type;

    @Size(max = 50)
    private String vendor;

    @Size(max = 200)
    private String model;

    @Size(max = 50)
    private String protocol;

    @Size(max = 255)
    private String host;

    private Integer port;

    private Integer unitId;

    @Positive(message = "Poll interval must be positive")
    private Integer pollIntervalMs;

    private Boolean autoConnect;

    private UUID profileId;

    @Size(max = 50)
    private String lineId;

    @Size(max = 50)
    private String plantId;
}

