package com.rmsys.backend.api.controller;

import com.rmsys.backend.api.response.MachineDetailResponse;
import com.rmsys.backend.api.response.MachineOverviewResponse;
import com.rmsys.backend.common.exception.GlobalExceptionHandler;
import com.rmsys.backend.domain.service.MachineIdentityResolverService;
import com.rmsys.backend.domain.service.MachineRealtimeSnapshotService;
import com.rmsys.backend.domain.service.MachineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MachineController.class)
@Import(GlobalExceptionHandler.class)
class MachineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MachineService machineService;

    @MockitoBean
    private MachineIdentityResolverService machineIdentityResolverService;

    @MockitoBean
    private MachineRealtimeSnapshotService snapshotService;

    @Test
    void overview_returnsBulkPayload() throws Exception {
        var machineId = UUID.randomUUID();
        when(machineService.getMachineOverviews()).thenReturn(List.of(
                MachineOverviewResponse.builder()
                        .machineId(machineId)
                        .machineCode("CNC-01")
                        .machineName("Machine 01")
                        .operationalState("RUNNING")
                        .displayState("RUNNING")
                        .connectionState("ONLINE")
                        .dataFreshnessSec(1L)
                        .lastSeenAt(Instant.parse("2026-03-29T10:15:00Z"))
                        .build()
        ));

        mockMvc.perform(get("/api/v1/machines/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].machineCode").value("CNC-01"))
                .andExpect(jsonPath("$.data[0].connectionState").value("ONLINE"));

        verify(machineService, times(1)).getMachineOverviews();
    }

    @Test
    void list_returnsDisplayAndOperationalStates() throws Exception {
        var machineId = UUID.randomUUID();
        when(machineService.getAllMachines()).thenReturn(List.of(
                MachineDetailResponse.builder()
                        .id(machineId)
                        .code("CNC-02")
                        .name("Machine 02")
                        .status("OFFLINE")
                        .operationalState("RUNNING")
                        .displayState("OFFLINE")
                        .connectionState("OFFLINE")
                        .build()
        ));

        mockMvc.perform(get("/api/v1/machines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].operationalState").value("RUNNING"))
                .andExpect(jsonPath("$.data[0].displayState").value("OFFLINE"))
                .andExpect(jsonPath("$.data[0].status").value("OFFLINE"));

        verify(machineService, times(1)).getAllMachines();
    }
}


