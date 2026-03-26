package com.rmsys.backend.infrastructure.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitterRegistry {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final long timeout;

    public SseEmitterRegistry(ObjectMapper objectMapper,
                              @Value("${app.realtime.sse-timeout-ms:300000}") long timeout) {
        this.objectMapper = objectMapper;
        this.timeout = timeout;
    }

    public SseEmitter createEmitter() {
        var id = UUID.randomUUID().toString();
        var emitter = new SseEmitter(timeout);

        emitter.onCompletion(() -> { emitters.remove(id); log.debug("SSE completed: {}", id); });
        emitter.onTimeout(() -> { emitters.remove(id); log.debug("SSE timeout: {}", id); });
        emitter.onError(e -> { emitters.remove(id); log.debug("SSE error: {}", id); });

        emitters.put(id, emitter);
        log.info("SSE subscriber connected: {} (total: {})", id, emitters.size());
        return emitter;
    }

    public void broadcast(String eventName, Object data) {
        var deadIds = new java.util.ArrayList<String>();

        emitters.forEach((id, emitter) -> {
            try {
                var json = objectMapper.writeValueAsString(data);
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (IOException e) {
                deadIds.add(id);
            }
        });

        deadIds.forEach(emitters::remove);
    }

    public int getActiveCount() {
        return emitters.size();
    }
}

