package com.rmsys.backend.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineProfileImportRequest {
    @JsonProperty("profiles")
    private List<ProfileRecord> profiles;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ProfileRecord {
        @JsonProperty("profile_code")
        private String profileCode;

        @JsonProperty("profile_name")
        private String profileName;

        @JsonProperty("protocol")
        private String protocol;

        @JsonProperty("vendor")
        private String vendor;

        @JsonProperty("model")
        private String model;

        @JsonProperty("description")
        private String description;
    }
}

