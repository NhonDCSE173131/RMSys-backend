package com.rmsys.backend.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rmsys.backend.common.exception.GlobalExceptionHandler;
import com.rmsys.backend.domain.service.IngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IngestController.class)
@Import(GlobalExceptionHandler.class)
class IngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IngestService ingestService;

    @Test
    void ingestTelemetry_validPayload_returnsOk() throws Exception {
        var payload = Map.of(
                "machineId", UUID.randomUUID().toString(),
                "temperatureC", 45.0,
                "vibrationMmS", 2.1
        );

        mockMvc.perform(post("/api/v1/ingest/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(ingestService, times(1)).ingestTelemetry(any());
    }

    @Test
    void ingestTelemetry_missingMachineId_returnsValidationError() throws Exception {
        var payload = Map.of(
                "temperatureC", 45.0,
                "vibrationMmS", 2.1
        );

        mockMvc.perform(post("/api/v1/ingest/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.machineId").exists());
    }
}


