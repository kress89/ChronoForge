package io.chronoforge.store;
import io.chronoforge.core.TemporalEvent;
import io.chronoforge.core.TemporalId;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Flow.Publisher;

public interface EventStore {
    void append(List<TemporalEvent> events);
    List<TemporalEvent> read(TemporalId id);
    Publisher<TemporalEvent> subscribe();
    List<TemporalEvent> search(String type, Instant from, Instant to, Integer limit,
                               String jsonPath, String jsonValue);
}
