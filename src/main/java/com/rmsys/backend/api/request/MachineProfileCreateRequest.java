package com.rmsys.backend.api.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineProfileCreateRequest {

    @NotBlank(message = "Profile code is required")
    @Size(max = 100)
    private String profileCode;

    @NotBlank(message = "Profile name is required")
    @Size(max = 200)
    private String profileName;

    @NotBlank(message = "Protocol is required")
    @Size(max = 50)
    private String protocol;

    @Size(max = 50)
    private String vendor;

    @Size(max = 100)
    private String model;

    @Size(max = 500)
    private String description;
}

