package io.chronoforge.api;

import io.chronoforge.core.Determinism;
import io.chronoforge.core.TemporalEvent;
import io.chronoforge.core.TemporalId;
import io.chronoforge.core.VectorClock;
import io.chronoforge.store.EventStore;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/time")
public class TemporalController {

    private final EventStore store;

    public TemporalController(EventStore store) {
        this.store = store;
    }

    @PostMapping("/{id}/events")
    public ResponseEntity<?> append(@PathVariable("id") String id,
                                    @RequestBody AppendReq req,
                                    @RequestHeader(value = "X-CF-Seed", required = false) Long headerSeed,
                                    @RequestParam(value = "seed", required = false) Long querySeed) {

        var entityId = new TemporalId(UUID.fromString(id));
        var seed = headerSeed != null ? headerSeed : querySeed;

        Runnable writer = () -> {
            var effectiveNode = (req.node() == null || req.node().isBlank())
                    ? Determinism.node()
                    : req.node();

            var vc = new VectorClock().tick(effectiveNode);
            var payload = req.payload() == null ? Map.<String, Object>of() : req.payload();

            // Create the event with deterministic time
            var ev = new TemporalEvent(
                    entityId,
                    req.type(),
                    Determinism.now(),
                    vc,
                    payload
            );

            store.append(List.of(ev));
        };

        if (seed != null) {
            // Run this append under a deterministic scope if a seed is provided
            Determinism.withDeterminism(req.node(), seed, () -> {
                writer.run();
                return null;
            });
        } else {
            writer.run();
        }

        return ResponseEntity.accepted().body(Map.of("status", "queued"));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<EventView>> read(@PathVariable("id") String id,
                                                @RequestParam(name = "asOf", required = false) String asOf) {
        var entityId = new TemporalId(UUID.fromString(id));
        var cutoff = (asOf == null || asOf.isBlank()) ? null : Instant.parse(asOf);

        var list = store.read(entityId).stream()
                .filter(e -> cutoff == null || !e.observedAt().isAfter(cutoff))
                .sorted(Comparator.comparing(TemporalEvent::observedAt))
                .map(e -> new EventView(
                        e.entityId().toString(),
                        e.type(),
                        e.observedAt(),
                        e.clock().snapshot(),
                        e.payload()
                ))
                .toList();

        return ResponseEntity.ok(list);
    }

    @GetMapping("/search")
    public ResponseEntity<List<TemporalEvent>> search(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String jsonPath,
            @RequestParam(required = false) String jsonValue
    ) {
        return ResponseEntity.ok(store.search(type, from, to, limit, jsonPath, jsonValue));
    }

    /**
     * Request body for appending an event.
     */
    public record AppendReq(String type, Map<String, Object> payload, String node) {
    }
}
