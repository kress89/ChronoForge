package io.chronoforge.api;

import io.chronoforge.core.TemporalEvent;
import io.chronoforge.store.EventStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Flow;

@RestController
@RequestMapping("/api/time")
public class TimeStreamController {

    private final EventStore store;

    public TimeStreamController(EventStore store) {
        this.store = store;
    }

    /** Stream all events (entityId optional via query). */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(name = "entityId", required = false) String entityId) {
        return subscribeFiltering(entityId);
    }

    /** Convenience path variant: /api/time/{id}/stream */
    @GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamById(@PathVariable("id") String id) {
        return subscribeFiltering(id);
    }

    private SseEmitter subscribeFiltering(String entityIdOrNull) {
        final String entityFilter = (entityIdOrNull == null || entityIdOrNull.isBlank()) ? null : entityIdOrNull;
        final SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());

        Flow.Subscriber<TemporalEvent> sub = new Flow.Subscriber<>() {
            Flow.Subscription s;

            @Override public void onSubscribe(Flow.Subscription s) { (this.s = s).request(Long.MAX_VALUE); }

            @Override public void onNext(TemporalEvent e) {
                try {
                    if (entityFilter == null || e.entityId().value().equals(UUID.fromString(entityFilter))) {
                        emitter.send(SseEmitter.event().name("event").data(e));
                    }
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                    if (s != null) s.cancel();
                }
            }

            @Override public void onError(Throwable t) { emitter.completeWithError(t); }
            @Override public void onComplete() { emitter.complete(); }
        };

        store.subscribe().subscribe(sub);
        return emitter;
    }
}
