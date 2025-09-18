package io.chronoforge.store.pg;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DocSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public DocSnapshotRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc; this.json = json;
    }

    public Optional<Snapshot> get(UUID id) {
        var sql = "SELECT entity_id, doc, clock, updated_at FROM cf_doc_snapshot WHERE entity_id = ?";
        return jdbc.query(sql, mapper(), id).stream().findFirst();
    }

    public void upsert(UUID id, Map<String,Object> doc, Map<String,Long> clock) {
        var sql = """
          INSERT INTO cf_doc_snapshot (entity_id, doc, clock, updated_at)
          VALUES (?, ?::jsonb, ?::jsonb, now())
          ON CONFLICT (entity_id)
          DO UPDATE SET doc = EXCLUDED.doc, clock = EXCLUDED.clock, updated_at = now()
          """;
        jdbc.update(sql, id, toJson(doc), toJson(clock));
    }

    private String toJson(Object o) {
        try { return json.writeValueAsString(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private RowMapper<Snapshot> mapper() {
        return (rs, rn) -> new Snapshot(
                UUID.fromString(rs.getString("entity_id")),
                readJsonObj(rs.getString("doc")),
                readJsonClock(rs.getString("clock")),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
    private Map<String,Object> readJsonObj(String s) {
        try { return json.readValue(s, new TypeReference<>(){}); }
        catch (Exception e){ throw new RuntimeException(e); }
    }
    private Map<String,Long> readJsonClock(String s) {
        try { return json.readValue(s, new TypeReference<>(){}); }
        catch (Exception e){ throw new RuntimeException(e); }
    }

    public record Snapshot(UUID entityId, Map<String,Object> doc, Map<String,Long> clock, Instant updatedAt) {}
}
