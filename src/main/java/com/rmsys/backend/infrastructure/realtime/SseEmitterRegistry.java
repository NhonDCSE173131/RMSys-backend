package com.rmsys.backend.infrastructure.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitterRegistry {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final long timeout;

    public SseEmitterRegistry(@Value("${app.realtime.sse-timeout-ms:300000}") long timeout) {
        this.timeout = timeout;
    }

    public SseEmitter createEmitter() {
        var id = UUID.randomUUID().toString();
        var emitter = new SseEmitter(timeout);

        emitter.onCompletion(() -> {
            emitters.remove(id);
            log.debug("SSE completed: {}", id);
        });
        emitter.onTimeout(() -> {
            emitters.remove(id);
            log.debug("SSE timeout: {}", id);
        });
        emitter.onError(e -> {
            emitters.remove(id);
            log.debug("SSE error: {}", id);
        });

        emitters.put(id, emitter);
        log.info("SSE subscriber connected: {} (total: {})", id, emitters.size());
        return emitter;
    }

    public void broadcast(String eventName, Object data) {
        var envelope = buildEnvelope(eventName, data);

        var deadIds = new java.util.ArrayList<String>();
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(envelope));
            } catch (IOException e) {
                deadIds.add(id);
            }
        });

        deadIds.forEach(emitters::remove);
    }

    public void sendHeartbeat() {
        broadcast("ping", Map.of("activeSubscribers", getActiveCount()));
    }

    SseEventEnvelope buildEnvelope(String eventName, Object data) {
        return SseEventEnvelope.builder()
                .type(eventName)
                .ts(Instant.now())
                .machineId(extractMachineId(data))
                .payload(data)
                .build();
    }

    public int getActiveCount() {
        return emitters.size();
    }

    private UUID extractMachineId(Object data) {
        if (data == null) {
            return null;
        }

        if (data instanceof UUID uuid) {
            return uuid;
        }

        try {
            var method = data.getClass().getMethod("machineId");
            var result = method.invoke(data);
            if (result instanceof UUID uuid) {
                return uuid;
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through to getter naming style
        }

        try {
            var method = data.getClass().getMethod("getMachineId");
            var result = method.invoke(data);
            if (result instanceof UUID uuid) {
                return uuid;
            }
        } catch (ReflectiveOperationException ignored) {
            // no machine id on this event payload
        }

        return null;
    }
}
