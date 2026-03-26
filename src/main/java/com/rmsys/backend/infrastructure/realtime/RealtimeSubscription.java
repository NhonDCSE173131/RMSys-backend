package com.rmsys.backend.infrastructure.realtime;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.UUID;

public record RealtimeSubscription(
        String id,
        UUID machineId,
        Set<String> topics,
        SseEmitter emitter
) {
    public boolean matches(String eventType, UUID eventMachineId) {
        if (machineId != null && eventMachineId != null && !machineId.equals(eventMachineId)) {
            return false;
        }
        if (machineId != null && eventMachineId == null) {
            return false;
        }
        if (topics == null || topics.isEmpty() || topics.contains("all")) {
            return true;
        }

        String normalized = normalize(eventType);
        for (String topic : topics) {
            if ("heartbeat".equals(topic) && "heartbeat".equals(normalized)) {
                return true;
            }
            if (normalized.startsWith(topic + ".") || normalized.equals(topic)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "unknown";
        }
        return eventType.toLowerCase();
    }
}

