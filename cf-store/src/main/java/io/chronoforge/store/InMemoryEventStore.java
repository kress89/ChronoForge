package io.chronoforge.store;

import io.chronoforge.core.TemporalEvent;
import io.chronoforge.core.TemporalId;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.stream.Stream;

public final class InMemoryEventStore implements EventStore{
    private final Map<TemporalId, List<TemporalEvent>> byId = new HashMap<>();
    private final SubmissionPublisher<TemporalEvent> bus = new SubmissionPublisher<>();

    @Override
    public synchronized void append(List<TemporalEvent> events) {
        for (var e: events){
            byId.computeIfAbsent(e.entityId(), k -> new ArrayList<>()).add(e);
            bus.submit(e);
        }
    }

    @Override
    public synchronized List<TemporalEvent> read(TemporalId id) {
        return List.copyOf(byId.getOrDefault(id, List.of()));
    }

    @Override
    public Flow.Publisher<TemporalEvent> subscribe() {
        return bus;
    }

    @Override
    public synchronized List<TemporalEvent> search(
            String type, Instant from, Instant to,
            Integer limit, String jsonPath, String jsonValue) {

        Stream<TemporalEvent> stream = byId.values().stream().flatMap(List::stream);

        if (type != null) {
            stream = stream.filter(e -> type.equals(e.type()));
        }
        if (from != null) {
            stream = stream.filter(e -> !e.observedAt().isBefore(from));
        }
        if (to != null) {
            stream = stream.filter(e -> !e.observedAt().isAfter(to));
        }
        if (jsonPath != null && jsonValue != null) {
            // simple top-level containment: payload[jsonPath] == jsonValue (string compare)
            stream = stream.filter(e -> {
                Object v = e.payload().get(jsonPath);
                return Objects.equals(v == null ? null : String.valueOf(v), jsonValue);
            });
        }

        stream = stream.sorted(Comparator.comparing(TemporalEvent::observedAt));

        if (limit != null && limit > 0) {
            stream = stream.limit(limit);
        }

        return stream.toList();
    }

}
