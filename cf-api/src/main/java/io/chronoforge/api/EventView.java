package io.chronoforge.api;

import java.time.Instant;
import java.util.Map;

public record EventView(
        String entityId,
        String type,
        Instant observedAt,
        Map<String, Long> clock,
        Map<String, Object> payload
) {}
