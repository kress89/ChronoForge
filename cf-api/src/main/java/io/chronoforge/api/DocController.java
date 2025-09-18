package io.chronoforge.api;

import io.chronoforge.core.Determinism;
import io.chronoforge.core.TemporalEvent;
import io.chronoforge.core.TemporalId;
import io.chronoforge.core.VectorClock;
import io.chronoforge.store.EventStore;
import io.chronoforge.store.pg.DocSnapshotRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/docs")
public class DocController {

    private final EventStore store;
    private final DocSnapshotRepository repo;

    public DocController(EventStore store, DocSnapshotRepository repo) {
        this.store = store;
        this.repo = repo;
    }

    @PostMapping("/{id}/set")
    public ResponseEntity<?> set(@PathVariable("id") String id, @RequestBody Map<String, Object> fields) {
        var entityId = new TemporalId(UUID.fromString(id));
        var node = Determinism.node();
        var vc = new VectorClock().tick(node);
        var ev = new TemporalEvent(entityId, "DOC_SET", Determinism.now(), vc, fields == null ? Map.of() : fields, node);
        store.append(List.of(ev));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/del")
    public ResponseEntity<?> del(@PathVariable("id") String id, @RequestBody List<String> keys) {
        var entityId = new TemporalId(UUID.fromString(id));
        var node = Determinism.node();
        var vc = new VectorClock().tick(node);

        Map<String, Object> body = Map.of("keys", keys == null ? List.of() : new java.util.ArrayList<>(keys));

        var ev = new TemporalEvent(entityId, "DOC_DEL", Determinism.now(), vc, body, node);
        store.append(List.of(ev));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable("id") String id,
                                                   @RequestParam(name = "at", required = false)
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {

        var uuid = UUID.fromString(id);

        if (at == null) {
            // current snapshot
            var snap = repo.get(uuid).map(DocSnapshotRepository.Snapshot::doc).orElse(Map.of());
            return ResponseEntity.ok(snap);
        }

        // time-travel: replay from events up to 'at' (no DB write)
        var entityId = new TemporalId(uuid);
        var events = store.read(entityId).stream()
                .filter(e -> !e.observedAt().isAfter(at))
                .sorted(Comparator.comparing(TemporalEvent::observedAt))
                .toList();

        var doc = new LinkedHashMap<String, Object>();
        var snapClock = new VectorClock();
        for (var e : events) {
            if ("DOC_SET".equals(e.type())) {
                e.payload().forEach(doc::put);
            } else if ("DOC_DEL".equals(e.type())) {
                var keys = e.payload().get("keys");
                if (keys instanceof Collection<?> ks) ks.forEach(k -> doc.remove(String.valueOf(k)));
            }
            // advance clock
            e.clock().snapshot().forEach((n, c) -> snapClock.tick(n)); // light advance; precise max not needed for read-only
        }
        return ResponseEntity.ok(doc);
    }

    record SetBody(Map<String, Object> fields) {
    }

    record DelBody(List<String> keys) {
    }
}
