package com.rmsys.backend.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineConfigImportRequest {
    @JsonProperty("machines")
    private List<MachineRecord> machines;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MachineRecord {
        @JsonProperty("machine_code")
        private String machineCode;

        @JsonProperty("machine_name")
        private String machineName;

        @JsonProperty("protocol")
        private String protocol;

        @JsonProperty("host")
        private String host;

        @JsonProperty("port")
        private Integer port;

        @JsonProperty("unit_id")
        private Integer unitId;

        @JsonProperty("profile_code")
        private String profileCode;

        @JsonProperty("poll_interval_ms")
        private Integer pollIntervalMs;

        @JsonProperty("auto_connect")
        private Boolean autoConnect;

        @JsonProperty("type")
        private String type;

        @JsonProperty("vendor")
        private String vendor;

        @JsonProperty("model")
        private String model;

        @JsonProperty("line_id")
        private String lineId;

        @JsonProperty("plant_id")
        private String plantId;

        @JsonProperty("description")
        private String description;
    }
}

