package com.rmsys.backend.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rmsys.backend.common.exception.GlobalExceptionHandler;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.IngestService;
import com.rmsys.backend.domain.service.MachineConnectionStateService;
import com.rmsys.backend.domain.service.MachineIdentityResolverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IngestController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "app.ingest.api-key=secret-key")
class IngestControllerApiKeyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IngestService ingestService;

    @MockitoBean
    private MachineRepository machineRepository;

    @MockitoBean
    private MachineIdentityResolverService machineIdentityResolverService;

    @MockitoBean
    private MachineConnectionStateService connectionStateService;

    @Test
    void ingestTelemetry_missingApiKey_returnsUnauthorized() throws Exception {
        var payload = Map.of(
                "machineId", UUID.randomUUID().toString(),
                "temperatureC", 44.0
        );

        mockMvc.perform(post("/api/v1/ingest/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(ingestService, never()).ingestTelemetry(any());
    }
}

