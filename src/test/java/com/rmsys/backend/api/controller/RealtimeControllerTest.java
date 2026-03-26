package com.rmsys.backend.api.controller;

import com.rmsys.backend.common.exception.GlobalExceptionHandler;
import com.rmsys.backend.domain.entity.MachineEntity;
import com.rmsys.backend.domain.repository.MachineRepository;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RealtimeController.class)
@Import(GlobalExceptionHandler.class)
class RealtimeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SseEmitterRegistry sseEmitterRegistry;

    @MockitoBean
    private MachineRepository machineRepository;

    @Test
    void stream_withMachineCode_resolvesAndSubscribesToSpecificMachine() throws Exception {
        var machineId = UUID.randomUUID();
        var machine = MachineEntity.builder()
                .id(machineId)
                .code("CNC-01")
                .name("Machine 01")
                .type("CNC")
                .vendor("TEST")
                .status("RUNNING")
                .build();

        when(machineRepository.findByCode("CNC-01")).thenReturn(Optional.of(machine));
        when(sseEmitterRegistry.createEmitter(eq(machineId), eq("telemetry"), eq("evt-1")))
                .thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/v1/realtime/stream")
                        .queryParam("machineId", "CNC-01")
                        .queryParam("topics", "telemetry")
                        .queryParam("sinceEventId", "evt-1")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());

        verify(machineRepository, times(1)).findByCode("CNC-01");
        verify(sseEmitterRegistry, times(1)).createEmitter(eq(machineId), eq("telemetry"), eq("evt-1"));
    }

    @Test
    void stream_withUnknownMachineIdentifier_fallsBackToAllMachinesWithoutError() throws Exception {
        when(machineRepository.findByCode("M001")).thenReturn(Optional.empty());
        when(sseEmitterRegistry.createEmitter(isNull(), eq("all"), isNull())).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/v1/realtime/stream")
                        .queryParam("machineId", "M001")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());

        verify(sseEmitterRegistry, times(1)).createEmitter(isNull(), eq("all"), isNull());
    }

    @Test
    void health_returnsOk() throws Exception {
        when(sseEmitterRegistry.health()).thenReturn(Map.of("status", "ok"));

        mockMvc.perform(get("/api/v1/realtime/health"))
                .andExpect(status().isOk());

        verify(sseEmitterRegistry, times(1)).health();
    }
}


