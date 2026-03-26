package com.rmsys.backend.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rmsys.backend.common.exception.GlobalExceptionHandler;
import com.rmsys.backend.domain.dto.NormalizedTelemetryDto;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.domain.service.IngestService;
import com.rmsys.backend.domain.service.MachineConnectionStateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IngestController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "app.ingest.api-key=")
class IngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IngestService ingestService;

    @MockitoBean
    private MachineRepository machineRepository;

    @MockitoBean
    private MachineConnectionStateService connectionStateService;

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
    void ingestTelemetry_withSimulatorPayloadByMachineCode_returnsOkAndMapsCompatibilityFields() throws Exception {
        var machineId = UUID.randomUUID();
        var machine = MachineEntity.builder()
                .id(machineId)
                .code("CNC-01")
                .name("Machine 01")
                .type("CNC")
                .vendor("TEST")
                .status("IDLE")
                .build();
        when(machineRepository.findByCode("CNC-01")).thenReturn(Optional.of(machine));

        var payload = new LinkedHashMap<String, Object>();
        payload.put("machineCode", "CNC-01");
        payload.put("ts", "2026-03-27T00:10:05Z");
        payload.put("connectionStatus", "ONLINE");
        payload.put("machineState", "RUNNING");
        payload.put("temperatureC", 48.2);
        payload.put("spindleSpeedRpm", 3200.0);
        payload.put("feedRateMmMin", 860.0);
        payload.put("vibrationMmS", 1.9);
        payload.put("powerKw", 4.8);
        payload.put("energyKwhTotal", 1524.6);
        payload.put("cycleCount", 568);
        payload.put("partCount", 551);
        payload.put("toolWearPercent", 37.5);
        payload.put("maintenanceHealthScore", 82.0);
        payload.put("alarmHints", List.of("TOOL_NEAR_LIMIT"));
        payload.put("metadata", Map.of("scenario", "NORMAL", "simulatorNode", "SIM-LOCAL-01"));

        mockMvc.perform(post("/api/v1/ingest/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var captor = ArgumentCaptor.forClass(NormalizedTelemetryDto.class);
        verify(machineRepository, times(1)).findByCode("CNC-01");
        verify(ingestService, times(1)).ingestTelemetry(captor.capture());

        var dto = captor.getValue();
        assertEquals(machineId, dto.machineId());
        assertEquals(551, dto.outputCount());
        assertEquals(551, dto.goodCount());
        assertEquals(1524.6, dto.energyKwhDay());
        assertEquals(62.5, dto.remainingToolLifePct());
        assertEquals(true, dto.cycleRunning());
        assertFalse(dto.metadata().isEmpty());
        assertEquals(568, dto.metadata().get("cycleCount"));
        assertEquals(82.0, dto.metadata().get("maintenanceHealthScore"));
    }

    @Test
    void ingestTelemetry_missingMachineIdentifier_returnsValidationError() throws Exception {
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
                .andExpect(jsonPath("$.data.machineIdentifier").exists());
    }

    @Test
    void ingestTelemetryByCode_validPayload_resolvesMachineAndCallsService() throws Exception {
        var machineId = UUID.randomUUID();
        var machine = MachineEntity.builder()
                .id(machineId)
                .code("CNC-01")
                .name("Machine 01")
                .type("CNC")
                .vendor("TEST")
                .status("IDLE")
                .build();
        when(machineRepository.findByCode("CNC-01")).thenReturn(Optional.of(machine));

        var payload = Map.of(
                "machineCode", "CNC-01",
                "temperatureC", 45.0,
                "powerKw", 7.2
        );

        mockMvc.perform(post("/api/v1/ingest/telemetry/by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(machineRepository, times(1)).findByCode("CNC-01");
        verify(ingestService, times(1)).ingestTelemetry(any());
    }

    @Test
    void ingestConnectionStatus_byCode_callsConnectionService() throws Exception {
        var machineId = UUID.randomUUID();
        var machine = MachineEntity.builder()
                .id(machineId)
                .code("CNC-01")
                .name("Machine 01")
                .type("CNC")
                .vendor("TEST")
                .status("IDLE")
                .build();
        when(machineRepository.findByCode("CNC-01")).thenReturn(Optional.of(machine));

        var payload = Map.of(
                "machineCode", "CNC-01",
                "connectionStatus", "DEGRADED",
                "metadata", Map.of("readLatencyMs", 1800)
        );

        mockMvc.perform(post("/api/v1/ingest/connection-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(connectionStateService, times(1)).markConnectionReported(any(), any(), any(), any());
    }
}


