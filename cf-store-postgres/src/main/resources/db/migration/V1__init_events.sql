-- Enable uuid + crypto if available (no-op if already there)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Core event table
CREATE TABLE IF NOT EXISTS cf_event (
  event_id    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_id   UUID        NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL,
  event_type  TEXT        NOT NULL,
  node        TEXT        NOT NULL,
  clock       JSONB       NOT NULL,   -- vector clock snapshot {node: counter}
  payload     JSONB       NOT NULL,   -- domain data
  hash        BYTEA       NOT NULL,   -- idempotency hash
  UNIQUE (hash)
);

-- Read patterns
CREATE INDEX IF NOT EXISTS idx_cf_event_entity_time ON cf_event (entity_id, observed_at);
CREATE INDEX IF NOT EXISTS idx_cf_event_type ON cf_event (event_type);
CREATE INDEX IF NOT EXISTS idx_cf_event_payload_gin ON cf_event USING GIN (payload);
CREATE INDEX IF NOT EXISTS idx_cf_event_clock_gin   ON cf_event USING GIN (clock);
CREATE INDEX IF NOT EXISTS brin_cf_event_time ON cf_event USING BRIN (observed_at);

-- Optional: notify on insert (used by LISTEN/NOTIFY subscribers)
CREATE OR REPLACE FUNCTION cf_event_notify() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  PERFORM pg_notify('cf_event_appends', NEW.entity_id::text);
  RETURN NEW;
END$$;

DROP TRIGGER IF EXISTS cf_event_append_trigger ON cf_event;
CREATE TRIGGER cf_event_append_trigger
AFTER INSERT ON cf_event
FOR EACH ROW EXECUTE FUNCTION cf_event_notify();
