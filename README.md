# ChronoForge

Local-first event/document store on **Java 24 (preview)** + **Spring Boot 3.4.x** with Postgres.  
Modules:
- `cf-core` – domain primitives (TemporalId, TemporalEvent, VectorClock)
- `cf-store` – EventStore interface
- `cf-store-postgres` – Postgres-backed implementation (Flyway, Spring JDBC)
- `cf-api` – REST API + projectors

## Requirements
- JDK **24** (preview)
- Maven **3.9+**
- Docker (for Postgres)
- (Optional) Adminer for DB inspection



API (high level)

Events

POST /api/time/{id}/events – append one or many TemporalEvent

GET /api/time/{id}/events – list events for an entity

GET /api/events/search?type=&from=&to=&limit=&jsonPath=&jsonValue= – filter stream with basic JSONB containment

Docs (projection)

POST /api/docs/{id}/set – {...fields} → emits DOC_SET

POST /api/docs/{id}/del – ["field1","field2"] → emits DOC_DEL

GET /api/docs/{id} – current snapshot from cf_doc_snapshot


Why / Project Idea

ChronoForge is an append-only event store with a JSON document projection layer designed for local-first apps:

Local-first & offline: clients can write events locally and project them to a JSON “doc” view; later they sync.

Causality preserved: every event carries a VectorClock so we know what happened-before and what’s concurrent.

Deterministic projections: a DocProjector reduces events (e.g., DOC_SET, DOC_DEL) into the latest JSON snapshot per entity.

Conflict handling: when two events are concurrent, we use a simple tie-break rule (lexicographic by node) now; we’ll evolve into proper CRDT operators (OR-Set, PN-Counter, etc.).

Simple sync: Postgres stores the authoritative log; we can LISTEN/NOTIFY or use a feed API for cross-instance updates.

Dev-friendly: JSONB payloads, typed vector clocks, and clean Spring wiring. Bring your own schema, projections, and CRDTs.

TL;DR — ChronoForge lets you build collaborative JSON docs on top of event sourcing with causality, snapshots, and a clear path to CRDTs.