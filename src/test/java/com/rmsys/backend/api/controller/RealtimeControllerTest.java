package com.rmsys.backend.api.controller;

import com.rmsys.backend.common.exception.GlobalExceptionHandler;
import com.rmsys.backend.common.exception.AppException;
import com.rmsys.backend.domain.service.MachineIdentityResolverService;
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
    private MachineIdentityResolverService machineIdentityResolverService;

    @Test
    void stream_withMachineCode_resolvesAndSubscribesToSpecificMachine() throws Exception {
        var machineId = UUID.randomUUID();
        when(machineIdentityResolverService.resolveRequiredId("CNC-01")).thenReturn(machineId);
        when(sseEmitterRegistry.createEmitter(eq(machineId), eq("telemetry"), eq("evt-1")))
                .thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/v1/realtime/stream")
                        .queryParam("machineId", "CNC-01")
                        .queryParam("topics", "telemetry")
                        .queryParam("sinceEventId", "evt-1")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());

        verify(machineIdentityResolverService, times(1)).resolveRequiredId("CNC-01");
        verify(sseEmitterRegistry, times(1)).createEmitter(eq(machineId), eq("telemetry"), eq("evt-1"));
    }

    @Test
    void stream_withUnknownMachineIdentifier_returnsBadRequest() throws Exception {
        when(machineIdentityResolverService.resolveRequiredId("M001"))
                .thenThrow(new AppException("MACHINE_NOT_FOUND", "Machine not found: M001"));

        mockMvc.perform(get("/api/v1/realtime/stream")
                        .queryParam("machineId", "M001")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());

        verify(sseEmitterRegistry, times(0)).createEmitter(isNull(), eq("all"), isNull());
    }

    @Test
    void health_returnsOk() throws Exception {
        when(sseEmitterRegistry.health()).thenReturn(Map.of("status", "ok"));

        mockMvc.perform(get("/api/v1/realtime/health"))
                .andExpect(status().isOk());

        verify(sseEmitterRegistry, times(1)).health();
    }
}


