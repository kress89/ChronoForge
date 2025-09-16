package io.chronoforge.api;

import io.chronoforge.core.TemporalEvent;
import io.chronoforge.core.TemporalId;
import io.chronoforge.core.VectorClock;
import io.chronoforge.store.EventStore;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/time")
public class TemporalController {
    private final EventStore store;
    public TemporalController(EventStore store){ this.store = store; }

    record AppendReq(String type, Map<String,Object> payload, String node){}

    @PostMapping("/{id}/events")
    public ResponseEntity<?> append(@PathVariable("id") String id, @RequestBody AppendReq req){
        var entityId = new TemporalId(java.util.UUID.fromString(id));
        var vc = new VectorClock().tick(req.node()==null ? "api" : req.node());
        var ev = TemporalEvent.of(entityId, req.type(), req.payload(), vc);
        store.append(List.of(ev));
        return ResponseEntity.accepted().body(Map.of("status","queued","clock", vc.snapshot()));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<EventView>> read(@PathVariable("id") String id,
                                                @RequestParam(name = "asOf", required = false) String asOf) {
        var entityId = new TemporalId(java.util.UUID.fromString(id));
        var list = store.read(entityId).stream()
                .filter(e -> asOf == null || !e.observedAt().isAfter(java.time.Instant.parse(asOf)))
                .sorted(java.util.Comparator.comparing(TemporalEvent::observedAt))
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
        return ResponseEntity.ok(
                store.search(type, from, to, limit, jsonPath, jsonValue)
        );
    }

}
