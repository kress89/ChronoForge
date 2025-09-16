package io.chronoforge.core;
import java.time.Instant;
import java.util.Map;

public record TemporalEvent(
        TemporalId entityId,
        String type,
        Instant observedAt,
        VectorClock clock,
        Map<String,Object> payload
){
    public static TemporalEvent of(TemporalId id, String type, Map<String,Object> payload, VectorClock vc){
        return new TemporalEvent(id, type, Instant.now(Determinism.clock()), vc, Map.copyOf(payload));
    }
}
