package io.chronoforge.store.pg;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.chronoforge.core.TemporalEvent;
import io.chronoforge.core.TemporalId;
import io.chronoforge.core.VectorClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostgresEventStoreTest {

    @Mock  private JdbcTemplate jdbc;
    private ObjectMapper json;
    private PostgresEventStore store;

    private TemporalId entityId;
    private TemporalEvent event;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        json = spy(new ObjectMapper());
        store = new PostgresEventStore(jdbc, json);

        entityId = new TemporalId(UUID.randomUUID());
        event = new TemporalEvent(
                entityId,
                "DOC_SET",
                Instant.parse("2025-09-16T12:00:00Z"),
                VectorClock.from(Map.of("nodeA", 1L)),
                Map.of("foo", "bar"),
                "api"
        );
    }

    @Test
    void append_insertsEventAndPublishes() {
        when(jdbc.update(anyString(), (Object[]) any())).thenReturn(1);

        var received = new AtomicReference<TemporalEvent>();
        store.subscribe().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(TemporalEvent item) { received.set(item); }
            @Override public void onError(Throwable t) {}
            @Override public void onComplete() {}
        });

        store.append(List.of(event));

        // Verify the varargs overload was called once
        ArgumentCaptor<String> sqlCap  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCap = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(1)).update(sqlCap.capture(), argsCap.capture());

        Object[] args = argsCap.getValue();
        assertThat(args).hasSize(8);
        assertThat(args[3]).isEqualTo("DOC_SET");
        assertThat(args[4]).isEqualTo("api"); // default node()
        assertThat(args[2]).isInstanceOf(Timestamp.class);
        assertThat(args[5]).asString().contains("nodeA");
        assertThat(args[6]).asString().contains("foo");
        assertThat(args[7]).isInstanceOf(byte[].class);

        for (int i = 0; i < 50 && received.get() == null; i++) Thread.onSpinWait();
        assertThat(received.get()).isEqualTo(event);
    }

    @Test
    void append_nullOrEmptyDoesNothing() {
        reset(jdbc);

        store.append(null);
        store.append(List.of());

        // verify the varargs overload was never called
        verify(jdbc, never()).update(anyString(), (Object[]) any());
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any());
    }


    @Test
    void read_executesQuery() {
        when(jdbc.query(
                anyString(),
                ArgumentMatchers.<RowMapper<TemporalEvent>>any(),
                eq(entityId.value())
        )).thenReturn(List.of(event));

        var result = store.read(entityId);

        assertThat(result).containsExactly(event);

        verify(jdbc).query(
                anyString(),
                ArgumentMatchers.<RowMapper<TemporalEvent>>any(),
                eq(entityId.value())
        );
    }


    @Test
    void search_withAllFiltersBuildsCorrectSql() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(event));

        var from = Instant.parse("2025-09-16T00:00:00Z");
        var to   = Instant.parse("2025-09-16T23:59:59Z");

        var result = store.search("DOC_SET", from, to, 5, "foo", "bar");

        assertThat(result).containsExactly(event);

        // capture the sql string
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCap.capture(), any(RowMapper.class), any(Object[].class));
        String sql = sqlCap.getValue();
        assertThat(sql)
                .contains("event_type = ?")
                .contains("observed_at >=")
                .contains("observed_at <=")
                .contains("payload @>")
                .contains("LIMIT ?");
    }

    @Test
    void computeHashProducesStableDigest() {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        doAnswer(inv -> null).when(jdbc).update(anyString(), captor.capture());

        store.append(List.of(event));

        Object[] args = captor.getValue();
        byte[] hash1 = (byte[]) args[7]; // position of hash in update

        store.append(List.of(event));
        Object[] args2 = captor.getValue();
        byte[] hash2 = (byte[]) args2[7];

        assertThat(hash1).isEqualTo(hash2); // deterministic hash
    }

    @Test
    void toJsonAndBackRoundTrip() {
        Map<String, Long> clock = Map.of("nodeA", 1L);
        String jsonStr = store.toJson(clock);
        Map<String, Long> parsed = store.readJson(jsonStr);

        assertThat(parsed).isEqualTo(clock);
    }

    @Test
    void nodeDefaultsToApi() {
        String node = store.node();
        assertThat(node).isEqualTo("api");
    }
}
