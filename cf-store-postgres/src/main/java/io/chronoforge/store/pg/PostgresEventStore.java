package io.chronoforge.store.pg;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.chronoforge.core.TemporalEvent;
import io.chronoforge.core.TemporalId;
import io.chronoforge.core.VectorClock;
import io.chronoforge.store.EventStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.stream.Stream;

public final class PostgresEventStore implements EventStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper json;
    private final SubmissionPublisher<TemporalEvent> bus = new SubmissionPublisher<>();

    public PostgresEventStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbcTemplate = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
    }


    @Override
    public void append(List<TemporalEvent> events) {
        if (events == null || events.isEmpty()) return;
        final String sql = """
      INSERT INTO cf_event (event_id, entity_id, observed_at, event_type, node, clock, payload, hash)
      VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
      ON CONFLICT (hash) DO NOTHING
      """;
        for (TemporalEvent e : events) {
            var eventId = UUID.randomUUID();
            byte[] hash = computeHash(e);
            jdbcTemplate.update(sql,
                    eventId,
                    e.entityId().value(),
                    Timestamp.from(e.observedAt()),
                    e.type(),
                    node(),                                   // capture logical node
                    toJson(e.clock().snapshot()),
                    toJson(e.payload()),
                    hash
            );
            bus.submit(e);
        }
    }

    @Override
    public List<TemporalEvent> read(TemporalId id) {
        final String sql = """
      SELECT entity_id, event_type, observed_at, node, clock, payload
      FROM cf_event
      WHERE entity_id = ?
      ORDER BY observed_at ASC
      """;
        return jdbcTemplate.query(sql, mapper(), id.value());
    }

    @Override
    public Flow.Publisher<TemporalEvent> subscribe() {
        return bus;
    }

    @Override
    public List<TemporalEvent> search(
            String type, Instant from, Instant to,
            Integer limit, String jsonPath, String jsonValue) {

        record Clause(String sql, Object param) {}

        var clauses = new ArrayList<Clause>();

        Optional.ofNullable(type)
                .ifPresent(t -> clauses.add(new Clause("event_type = ?", t)));

        Optional.ofNullable(from)
                .ifPresent(f -> clauses.add(new Clause("observed_at >= ?", Timestamp.from(f))));

        Optional.ofNullable(to)
                .ifPresent(ti -> clauses.add(new Clause("observed_at <= ?", Timestamp.from(ti))));

        if (jsonPath != null && jsonValue != null) {
            var jsonb = "{\"" + jsonPath + "\":\"" + jsonValue + "\"}";
            clauses.add(new Clause("payload @> ?::jsonb", jsonb));
        }

        // Base query
        var sql = new StringBuilder("""
        SELECT entity_id, event_type, observed_at, node, clock, payload
        FROM cf_event
        WHERE 1=1
    """);

        clauses.forEach(c -> sql.append(" AND ").append(c.sql()));

        sql.append(" ORDER BY observed_at ASC");

        var params = clauses.stream()
                .map(Clause::param)
                .toList();

        // Handle LIMIT separately (optional at the end)
        if (limit != null && limit > 0) {
            sql.append(" LIMIT ?");
            return jdbcTemplate.query(sql.toString(), mapper(),
                    Stream.concat(params.stream(), Stream.of(limit)).toArray());
        }

        return jdbcTemplate.query(sql.toString(), mapper(), params.toArray());
    }


    private RowMapper<TemporalEvent> mapper() {
        return (ResultSet rs, int rowNum) -> {
            var entityId = new TemporalId(UUID.fromString(rs.getString("entity_id")));
            var type = rs.getString("event_type");
            Instant at = rs.getTimestamp("observed_at").toInstant();

            Map<String, Long> clock = readJson(rs.getString("clock"));
            Map<String, Object> payload = readJsonObj(rs.getString("payload"));

            var vc = VectorClock.from(clock);
            return new TemporalEvent(entityId, type, at, vc, payload);
        };
    }

    private byte[] computeHash(TemporalEvent e) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var key = (e.entityId().value() + "|" + e.type() + "|" + e.observedAt() + "|" + json.writeValueAsString(e.payload()))
                    .getBytes(StandardCharsets.UTF_8);
            return md.digest(key);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    String node() {
        // improve this: hostname, instance id, or config property.
        return Optional.ofNullable(System.getenv("CF_NODE")).orElse("api");
    }

    String toJson(Object o) {
        try { return json.writeValueAsString(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    Map<String, Long> readJson(String jsonStr) {
        try { return json.readValue(jsonStr, new TypeReference<>() {
        }); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private Map<String, Object> readJsonObj(String jsonStr) {
        try { return json.readValue(jsonStr, new TypeReference<>() {
        }); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
