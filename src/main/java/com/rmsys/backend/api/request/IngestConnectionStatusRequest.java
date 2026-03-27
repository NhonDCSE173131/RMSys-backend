package com.rmsys.backend.api.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record IngestConnectionStatusRequest(
        UUID machineId,
        String machineCode,
        @NotBlank(message = "connectionStatus is required") String connectionStatus,
        @JsonAlias("sourceTimestamp")
        Instant ts,
        Map<String, Object> metadata
) {

    @AssertTrue(message = "machineId or machineCode is required")
    public boolean hasMachineIdentifier() {
        return machineId != null || (machineCode != null && !machineCode.isBlank());
    }
}

