package com.rmsys.backend.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MachineMappingImportRequest {
    @JsonProperty("mappings")
    private List<MappingRecord> mappings;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MappingRecord {
        @JsonProperty("profile_code")
        private String profileCode;

        @JsonProperty("logical_key")
        private String logicalKey;

        @JsonProperty("area")
        private String area;

        @JsonProperty("address")
        private Integer address;

        @JsonProperty("address_start")
        private Integer addressStart;

        @JsonProperty("address_end")
        private Integer addressEnd;

        @JsonProperty("bit_index")
        private Integer bitIndex;

        @JsonProperty("data_type")
        private String dataType;

        @JsonProperty("scale")
        private Double scale;

        @JsonProperty("scale_factor")
        private Double scaleFactor;

        @JsonProperty("unit")
        private String unit;

        @JsonProperty("byte_order")
        private String byteOrder;

        @JsonProperty("word_order")
        private String wordOrder;

        @JsonProperty("required")
        private Boolean required;

        @JsonProperty("is_required")
        private Boolean isRequired;

        @JsonProperty("description")
        private String description;
    }
}

