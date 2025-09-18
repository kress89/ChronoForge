package io.chronoforge.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record TemporalEvent(
        TemporalId entityId,
        String type,
        Instant observedAt,
        VectorClock clock,
        Map<String, Object> payload,
        String node // <- NEW
) {
    public TemporalEvent {
        Objects.requireNonNull(entityId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(observedAt);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(payload);
        if (node == null || node.isBlank()) node = "api";
    }

    /** Back-compat factory—uses Determinism for time & node. */
    public static TemporalEvent of(TemporalId id, String type, Map<String,Object> payload, VectorClock clock) {
        return new TemporalEvent(id, type, Determinism.now(), clock,
                payload == null ? Map.of() : payload,
                Determinism.node());
    }
}
