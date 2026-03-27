package com.rmsys.backend.infrastructure.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class SseEmitterRegistry {

    private final Map<String, RealtimeSubscription> subscriptions = new ConcurrentHashMap<>();
    private final long timeout;
    private final int replayBufferSize;
    private final AtomicLong sequence = new AtomicLong(0);
    private final ArrayDeque<SseEventEnvelope> replayBuffer = new ArrayDeque<>();

    @Autowired
    public SseEmitterRegistry(
            @Value("${app.realtime.sse-timeout-ms:300000}") long timeout,
            @Value("${app.realtime.replay-buffer-size:500}") int replayBufferSize) {
        this.timeout = timeout;
        this.replayBufferSize = replayBufferSize;
    }

    // Backward-compatible overload for tests and direct construction.
    public SseEmitterRegistry(long timeout) {
        this(timeout, 500);
    }

    public SseEmitter createEmitter(UUID machineId, String topics, String sinceEventId) {
        var id = UUID.randomUUID().toString();
        var emitter = new SseEmitter(timeout);
        var parsedTopics = parseTopics(topics);
        var subscription = new RealtimeSubscription(id, machineId, parsedTopics, emitter);

        emitter.onCompletion(() -> {
            subscriptions.remove(id);
            log.debug("SSE completed: {}", id);
        });
        emitter.onTimeout(() -> {
            subscriptions.remove(id);
            log.debug("SSE timeout: {}", id);
        });
        emitter.onError(e -> {
            subscriptions.remove(id);
            log.debug("SSE error: {}", id);
        });

        subscriptions.put(id, subscription);
        replayMissedEvents(subscription, sinceEventId);
        log.info("SSE subscriber connected: {} machineId={} topics={} total={}", id, machineId, parsedTopics, subscriptions.size());
        return emitter;
    }

    public void broadcast(String eventName, Object data) {
        var envelope = buildEnvelope(eventName, data);
        appendReplayBuffer(envelope);

        var deadIds = new java.util.ArrayList<String>();
        subscriptions.forEach((id, subscription) -> {
            try {
                if (subscription.matches(envelope.eventType(), envelope.machineId())) {
                    subscription.emitter().send(SseEmitter.event()
                            .id(envelope.eventId())
                            .name(envelope.eventType())
                            .data(envelope));
                }
            } catch (Exception ex) {
                if (isClientAbort(ex)) {
                    log.debug("SSE client disconnected while sending event. subscriptionId={} event={}", id, eventName);
                } else {
                    log.warn("SSE send failed. subscriptionId={} event={} error={}", id, eventName, rootMessage(ex));
                }
                deadIds.add(id);
            }
        });

        deadIds.forEach(subscriptions::remove);
    }

    public void sendHeartbeat() {
        broadcast("heartbeat.ping", Map.of("activeSubscribers", getActiveCount()));
    }

    SseEventEnvelope buildEnvelope(String eventName, Object data) {
        var now = Instant.now();
        var nextSequence = sequence.incrementAndGet();
        return SseEventEnvelope.builder()
                .eventId("evt-" + nextSequence)
                .eventType(toEventType(eventName))
                .machineId(extractMachineId(data))
                .machineCode(extractMachineCode(data))
                .sourceTs(extractSourceTs(data, now))
                .receivedAt(now)
                .sequence(nextSequence)
                .quality(extractQuality(data))
                .payload(data)
                .build();
    }

    public int getActiveCount() {
        return subscriptions.size();
    }

    public Map<String, Object> health() {
        var result = new LinkedHashMap<String, Object>();
        result.put("activeSubscribers", getActiveCount());
        result.put("replayBufferSize", replayBuffer.size());
        result.put("latestSequence", sequence.get());
        return result;
    }

    private void replayMissedEvents(RealtimeSubscription subscription, String sinceEventId) {
        if (sinceEventId == null || sinceEventId.isBlank()) {
            return;
        }
        long since = parseEventId(sinceEventId);
        if (since <= 0) {
            return;
        }

        synchronized (replayBuffer) {
            for (SseEventEnvelope envelope : replayBuffer) {
                if (envelope.sequence() != null
                        && envelope.sequence() > since
                        && subscription.matches(envelope.eventType(), envelope.machineId())) {
                    try {
                        subscription.emitter().send(SseEmitter.event()
                                .id(envelope.eventId())
                                .name(envelope.eventType())
                                .data(envelope));
                    } catch (Exception ex) {
                        if (isClientAbort(ex)) {
                            log.debug("SSE replay skipped due to disconnected client. subscriptionId={}", subscription.id());
                        } else {
                            log.warn("SSE replay failed. subscriptionId={} error={}", subscription.id(), rootMessage(ex));
                        }
                        subscriptions.remove(subscription.id());
                        return;
                    }
                }
            }
        }
    }

    private void appendReplayBuffer(SseEventEnvelope envelope) {
        synchronized (replayBuffer) {
            replayBuffer.addLast(envelope);
            while (replayBuffer.size() > replayBufferSize) {
                replayBuffer.removeFirst();
            }
        }
    }

    private Set<String> parseTopics(String topics) {
        if (topics == null || topics.isBlank()) {
            return Set.of("all");
        }
        return Arrays.stream(topics.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .map(this::normalizeTopicAlias)
                .filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private String normalizeTopicAlias(String topic) {
        if (topic == null || topic.isBlank()) {
            return "";
        }
        String normalized = topic.toLowerCase().replace('_', '.').replace('-', '.');
        if ("machine.telemetry.updated".equals(normalized) || "telemetry.updated".equals(normalized)) {
            return "telemetry";
        }
        if (normalized.startsWith("alarm.")) {
            return "alarm";
        }
        if (normalized.startsWith("machine.connection.")) {
            return "machine.connection";
        }
        if (normalized.startsWith("downtime.")) {
            return "downtime";
        }
        if (normalized.startsWith("heartbeat")) {
            return "heartbeat";
        }
        return normalized;
    }

    private long parseEventId(String eventId) {
        try {
            if (eventId.startsWith("evt-")) {
                return Long.parseLong(eventId.substring(4));
            }
            return Long.parseLong(eventId);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String toEventType(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            return "unknown";
        }
        String normalized = eventName.toLowerCase().replace('_', '.').replace('-', '.');
        if (normalized.startsWith("machine.telemetry")) {
            return "telemetry.updated";
        }
        if (normalized.startsWith("alarm.")) {
            return normalized;
        }
        if (normalized.startsWith("machine.connection")) {
            return normalized;
        }
        if (normalized.startsWith("downtime.")) {
            return normalized;
        }
        if (normalized.startsWith("heartbeat")) {
            return "heartbeat";
        }
        return normalized;
    }

    private UUID extractMachineId(Object data) {
        if (data == null) {
            return null;
        }

        if (data instanceof Map<?, ?> map) {
            Object machineId = map.get("machineId");
            if (machineId instanceof UUID uuid) {
                return uuid;
            }
            if (machineId instanceof String value) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
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

    private String extractMachineCode(Object data) {
        if (data == null) {
            return null;
        }

        if (data instanceof Map<?, ?> map) {
            Object machineCode = map.get("machineCode");
            return machineCode == null ? null : machineCode.toString();
        }

        try {
            var method = data.getClass().getMethod("machineCode");
            var result = method.invoke(data);
            return result == null ? null : result.toString();
        } catch (ReflectiveOperationException ignored) {
            // fall through to getter naming style
        }

        try {
            var method = data.getClass().getMethod("getMachineCode");
            var result = method.invoke(data);
            return result == null ? null : result.toString();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Instant extractSourceTs(Object data, Instant fallback) {
        if (data == null) {
            return fallback;
        }

        try {
            var method = data.getClass().getMethod("ts");
            var result = method.invoke(data);
            if (result instanceof Instant instant) {
                return instant;
            }
        } catch (ReflectiveOperationException ignored) {
            // keep fallback
        }
        return fallback;
    }

    private Integer extractQuality(Object data) {
        if (data == null) {
            return 0;
        }

        if (data instanceof Map<?, ?> map) {
            return normalizeQuality(map.get("quality"));
        }

        try {
            var method = data.getClass().getMethod("quality");
            var result = method.invoke(data);
            return normalizeQuality(result);
        } catch (ReflectiveOperationException ignored) {
            // fallback below
        }
        return 100;
    }

    private Integer normalizeQuality(Object value) {
        if (value == null) {
            return 100;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }

        String normalized = value.toString().trim();
        if (normalized.isEmpty()) {
            return 100;
        }

        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            String mapped = normalized.toUpperCase();
            return switch (mapped) {
                case "GOOD" -> 100;
                case "DEGRADED", "WARN", "WARNING" -> 60;
                case "BAD", "POOR" -> 30;
                case "UNKNOWN" -> 0;
                default -> 100;
            };
        }
    }

    private boolean isClientAbort(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            if (className.contains("ClientAbortException")) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("broken pipe")
                        || lower.contains("connection reset")
                        || lower.contains("connection aborted")
                        || lower.contains("was aborted")
                        || lower.contains("forcibly closed")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        String last = throwable.getMessage();
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                last = current.getMessage();
            }
            current = current.getCause();
        }
        return last == null ? throwable.getClass().getSimpleName() : last;
    }
}
