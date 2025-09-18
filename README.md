ChronoForge

Local-first event/document store on Java 24 (preview) + Spring Boot 3.4.x, backed by Postgres.
It gives you an append-only event log, vector clocks for causality, a JSON snapshot (projection) per document, and SSE for live updates. Deterministic headers let you reproduce the exact same timeline across runs.

Modules

cf-core — domain primitives: TemporalId, TemporalEvent (with node), VectorClock, Determinism.

cf-store — EventStore interface (append/read/search/subscribe).

cf-store-postgres — Postgres implementation (Spring JDBC + Flyway; JSONB; idempotency hash).

cf-api — Spring Boot API, projector, and SSE streaming.

Stack & Requirements

JDK 24 (with preview)

Maven 3.9+

Docker (Postgres 16)

Spring Boot 3.4.x

Postgres JSONB + Flyway migrations

Quick start
1) Start Postgres (Docker)

From repo root:

docker compose up -d postgres pgadmin


Compose exposes Postgres at localhost:5432 with db chronoforge, user cf, pass cf.

2) Build & run API (profile = pg)
   mvn -q -DskipTests clean package

cd cf-api
$env:SPRING_PROFILES_ACTIVE="pg"
java --enable-preview -jar .\target\cf-api-0.1.0-SNAPSHOT.jar


On startup you should see Flyway validate/migrate and Tomcat on :9090.

3) Smoke test (PowerShell)

Append an event (deterministic):

curl.exe -X POST "http://localhost:9090/api/time/11111111-1111-1111-1111-111111111111/events" `
-H "Content-Type: application/json" `
-H "X-CF-Seed: 42" `
-H "X-CF-Node: nodeA" `
--data-raw '{ "type":"DOC_SET", "payload": { "foo":"bar" } }'


Read events:

curl.exe "http://localhost:9090/api/time/11111111-1111-1111-1111-111111111111/events"


Stream live events (SSE):

# all entities
curl.exe -N "http://localhost:9090/api/time/stream"
# or just one
# curl.exe -N "http://localhost:9090/api/time/stream?entityId=11111111-1111-1111-1111-111111111111"

4) Documents (projection)

Set fields:

$ID="11111111-1111-1111-1111-111111111111"
curl.exe -X POST "http://localhost:9090/api/docs/$ID/set" `
-H "Content-Type: application/json" `
-H "X-CF-Seed: 1" -H "X-CF-Node: nodeA" `
--data-raw '{ "title":"ChronoForge", "owner":"kreso", "tags":["db","events"] }'


Delete fields:

curl.exe -X POST "http://localhost:9090/api/docs/$ID/del" `
-H "Content-Type: application/json" `
-H "X-CF-Seed: 2" -H "X-CF-Node: nodeA" `
--data-raw '["tags"]'


Read snapshot (current):

curl.exe "http://localhost:9090/api/docs/$ID"


Time-travel read:

$at = (Get-Date).ToUniversalTime().ToString("s") + "Z"
curl.exe "http://localhost:9090/api/docs/$ID?at=$at"

API (current)
Events

POST /api/time/{id}/events — append one event (body: {type, payload, [node]}; node normally comes from headers)

GET /api/time/{id}/events — list events (optional asOf=ISO_INSTANT)

GET /api/time/search?type=&from=&to=&limit=&jsonPath=&jsonValue= — filter by type/time and simple JSONB containment (payload @> {"jsonPath":"jsonValue"})

Docs (projection)

POST /api/docs/{id}/set — body {...fields} → emits DOC_SET

POST /api/docs/{id}/del — body ["field1","field2"] → emits DOC_DEL

GET /api/docs/{id} — current snapshot; ?at=ISO_INSTANT for time-travel (in-memory replay)

Streaming

GET /api/time/stream — SSE stream of all events

GET /api/time/stream?entityId={uuid} or /api/time/{id}/stream — stream one entity

Determinism headers

X-CF-Seed: <long> — fixes observedAt to Instant.EPOCH + seed (deterministic)

X-CF-Node: <string> — logical node for vector clock & tie-breaks

(Optional) env CF_NODE — default node if header/body omits it

How it works (short)

Append-only: every write is an event in cf_event (JSONB payload, JSONB clock, node, observed_at).

Idempotency: we hash (entityId|type|observedAt|node|clockJson|payloadJson) and UNIQUE(hash); duplicate attempts are ignored.

Vector clocks: VectorClock.compare() gives happens-before / after / concurrent; for concurrent we temporarily tie-break by lexicographic node (higher wins).

Projection: DocProjector subscribes to the store, applies DOC_SET/DOC_DEL, and upserts cf_doc_snapshot.

SSE: in-JVM bus publishes newly inserted events to connected clients.

Build notes

ChronoForge uses Java 24 preview (for ScopedValue in Determinism), and Spring reads method parameter names:

Maven (parent or module) — ensure:

<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <version>3.13.0</version>
  <configuration>
    <release>24</release>
    <compilerArgs>--enable-preview</compilerArgs>
    <parameters>true</parameters>
  </configuration>
</plugin>


Run with --enable-preview.

If you hit IDE issues, delegate builds/run to Maven and use JDK 24 for the Maven Runner.

Database schema

Flyway migrations (on classpath):

V1__init_events.sql — table cf_event (+ indexes, LISTEN/NOTIFY trigger)

V2__doc_snapshots.sql — table cf_doc_snapshot (GIN on doc)

cf-api profile pg points to:
jdbc:postgresql://localhost:5432/chronoforge, user cf, pass cf.