package io.chronoforge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.chronoforge.core.TemporalEvent;
import io.chronoforge.core.VectorClock;
import io.chronoforge.store.EventStore;
import io.chronoforge.store.pg.DocSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.Flow;

@Component
public class DocProjector {
    private static final Logger log = LoggerFactory.getLogger(DocProjector.class);

    private final DocSnapshotRepository repo;

    public DocProjector(EventStore store, DocSnapshotRepository repo) {
        this.repo = repo;
        store.subscribe().subscribe(new Flow.Subscriber<>() {
            Flow.Subscription s;
            @Override public void onSubscribe(Flow.Subscription s) { (this.s = s).request(Long.MAX_VALUE); }
            @Override public void onNext(TemporalEvent e) {
                try { project(e); } catch (Throwable t) { log.warn("Project error", t); }
            }
            @Override public void onError(Throwable t) { log.error("Bus error", t); }
            @Override public void onComplete() { }
        });
    }

    private void project(TemporalEvent e) {
        if (!("DOC_SET".equals(e.type()) || "DOC_DEL".equals(e.type()))) return;

        var id = e.entityId().value();
        var snap = repo.get(id).orElse(null);

        Map<String,Object> doc = snap == null ? new LinkedHashMap<>() : new LinkedHashMap<>(snap.doc());
        var snapClock = VectorClock.from(snap == null ? Map.of() : snap.clock());
        var evClock = e.clock();

        int cmp = snapClock.compare(evClock);
        if (cmp > 0) { // snapshot ahead
            return;
        }
        if (cmp == 0) {
            // concurrent or equal; tie-break by node, higher wins
            // If equal clocks and same node means duplicate—ignore (idempotent)
            if (e.node() == null) return;
            // For equal clocks same node → ignore; for concurrent we pick highest node
            // We don't track last-writer node in snapshot; so we just accept the event by convention:
            // accept only if node is lexicographically highest among known participants in evClock
            var maxNode = evClock.snapshot().keySet().stream().max(String::compareTo).orElse(e.node());
            if (!e.node().equals(maxNode)) return;
        }

        // apply mutation
        if ("DOC_SET".equals(e.type())) {
            e.payload().forEach(doc::put);
        } else { // DOC_DEL
            var keys = e.payload().get("keys");
            if (keys instanceof Collection<?> ks) {
                ks.forEach(k -> doc.remove(String.valueOf(k)));
            }
        }

        // advance snapshot clock = element-wise max
        var next = new HashMap<>(snapClock.snapshot());
        e.clock().snapshot().forEach((n,c) -> next.merge(n, c, Math::max));

        repo.upsert(id, doc, next);
    }
}
