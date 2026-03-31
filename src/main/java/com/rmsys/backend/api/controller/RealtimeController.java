package com.rmsys.backend.api.controller;

import com.rmsys.backend.domain.service.MachineIdentityResolverService;
import com.rmsys.backend.infrastructure.realtime.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Realtime")
@RestController
@RequestMapping("/api/v1/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private final SseEmitterRegistry sseRegistry;
    private final MachineIdentityResolverService machineIdentityResolverService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to realtime SSE stream")
    public SseEmitter stream(
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false, defaultValue = "all") String topics,
            @RequestParam(required = false) String sinceEventId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        String replayFrom = sinceEventId != null ? sinceEventId : lastEventId;
        return sseRegistry.createEmitter(resolveMachineFilter(machineId), topics, replayFrom);
    }


    @GetMapping("/health")
    @Operation(summary = "Get realtime stream health")
    public Map<String, Object> health() {
        return sseRegistry.health();
    }

    private UUID resolveMachineFilter(String machineIdentifier) {
        if (machineIdentifier == null || machineIdentifier.isBlank()) {
            return null;
        }

        return machineIdentityResolverService.resolveRequiredId(machineIdentifier);
    }
}

