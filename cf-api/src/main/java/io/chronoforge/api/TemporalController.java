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
    public TemporalController(EventStore store){ this.store = store; }

    record AppendReq(String type, Map<String,Object> payload, String node){}

    @PostMapping("/{id}/events")
    public ResponseEntity<?> append(@PathVariable("id") String id, @RequestBody AppendReq req){
        var entityId = new TemporalId(UUID.fromString(id));
        var node = (req.node() == null || req.node().isBlank()) ? Determinism.node() : req.node();
        var vc = new VectorClock().tick(node);
        var ev = new TemporalEvent(
                entityId,
                req.type(),
                Determinism.now(),
                vc,
                req.payload() == null ? Map.of() : req.payload(),
                node
        );
        store.append(List.of(ev));
        return ResponseEntity.accepted().body(Map.of("status","queued"));
    }

    public record EventView(String entityId, String type, Instant observedAt,
                            Map<String,Long> clock, Map<String,Object> payload, String node) {}

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
                        e.payload(),
                        e.node()
                ))
                .toList();

        return ResponseEntity.ok(list);
    }

    @GetMapping("/search")
    public ResponseEntity<List<TemporalEvent>> search(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "jsonPath", required = false) String jsonPath,
            @RequestParam(name = "jsonValue", required = false) String jsonValue
    ) {
        return ResponseEntity.ok(
                store.search(type, from, to, limit, jsonPath, jsonValue)
        );
    }
}
