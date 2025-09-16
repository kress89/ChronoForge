CREATE TABLE IF NOT EXISTS cf_doc_snapshot (
  entity_id   UUID        PRIMARY KEY,
  doc         JSONB       NOT NULL DEFAULT '{}'::jsonb,
  clock       JSONB       NOT NULL DEFAULT '{}'::jsonb,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS gin_cf_doc_snapshot ON cf_doc_snapshot USING GIN (doc);
