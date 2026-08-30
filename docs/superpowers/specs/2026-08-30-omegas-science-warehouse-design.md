# OMEGAS Science Warehouse — Design

Date: 2026-08-30
WorkUnit: `RED-BLEND-001`
Issue: `#11`
Branch: `work/red-v82-science-blend`
Status: APPROVED_BY_OWNER / IMPLEMENTATION_AUTHORIZED

## Outcome

Create a persistent, queryable scientific memory for this vehicle so the raw Drive corpus is decoded once and subsequent agents can analyze the extracted evidence without repeatedly reopening dozens of ZIPs, PortMon captures and `.omegas` snapshots.

The warehouse must preserve provenance back to immutable raw source hashes and must not become a new runtime/prediction authority by itself.

## Chosen architecture

Use a hybrid three-layer store:

1. **Google Drive — immutable raw authority**
   - Existing session ZIPs, nested ZIPs, PortMon logs, AutoCal captures and `.omegas` snapshots remain the immutable source material.
   - Raw private files are never committed to GitHub.

2. **Canonical SQLite cache — detailed extracted scientific memory**
   - A deterministic SQLite database contains normalized event-level evidence needed for analysis.
   - It stores telemetry, logical sessions, calibration interventions, AutoCal state and provenance, but not millions of redundant raw USB text lines.
   - The database is compressed with Zstandard and uploaded to the OMEGAS Drive folder as a versioned cache artifact.
   - Any agent can download this single cache instead of reparsing the raw corpus.

3. **Supabase `omegas_science` schema — durable ledger and fast index**
   - Stores corpus versions, source hashes, logical-session inventory, intervention manifests, AutoCal manifests, cache artifact identity and analysis results.
   - Does not duplicate all telemetry frames in Postgres in v1; detailed frames remain in the canonical SQLite cache.
   - The schema is isolated from existing application tables and is not exposed to anon/authenticated roles.

GitHub remains authority for parsers, schema contracts, migrations, tests, evidence manifests and corpus digests.

## Why not repo-only

Git is appropriate for small manifests, code and checksums, not hundreds of thousands of telemetry rows or multi-hundred-MB derived databases.

## Why not Supabase-only

Putting every raw/USB event into Postgres would create unnecessary ingestion cost and storage duplication. The detailed cache is an analytics artifact; Supabase is the durable catalog/index.

## Why SQLite

SQLite is available with no additional dependency, supports hundreds of thousands to millions of normalized rows efficiently, is easy to hash/version and can be compressed into one portable file. The format can later migrate to DuckDB/Parquet without changing the provenance model.

## Corpus identity

Every corpus version is immutable and identified by:

- `corpus_key`;
- parser/schema version;
- ordered set of source SHA-256 values;
- logical-session count;
- normalized telemetry count;
- intervention counts;
- SQLite SHA-256;
- compressed artifact SHA-256;
- code SHA that generated it.

A corpus digest is computed from canonical metadata rather than filenames.

## Idempotent ingestion

A raw source is reparsed only when one of these changes:

- source SHA-256 is new;
- parser contract version changes;
- an explicit forced rebuild is requested.

Otherwise the ingestion ledger records `CACHE_HIT` and reuses the existing normalized evidence.

## SQLite data model

### `source_blob`

- `source_sha256` primary key;
- byte size;
- redacted source class;
- parser version;
- ingestion status;
- provenance metadata.

### `source_occurrence`

Preserves distinct physical occurrences even when two files have identical bytes.

### `logical_session`

- privacy-safe `session_key`;
- raw session-id digest;
- started/ended timestamps when known;
- app version;
- source membership count.

### `session_source`

Many-to-many link between logical sessions and physical source blobs.

### `telemetry`

Normalized telemetry observations with deterministic event identity:

- session key;
- sequence/order;
- captured timestamp when available;
- fuel;
- RPM;
- MAP bar;
- Petrol Inj ms;
- GNV Inj ms diagnostic when available;
- secondary bank values when available;
- gas pressure;
- water/gas temperature;
- plausibility flags;
- source SHA;
- canonical event SHA.

Raw USB payload text is not duplicated here.

### `map_k_batch` / `map_k_cell_change`

Preserve batch as the intervention unit and cell changes as children.

### `k_factor_batch` / `k_factor_point_change`

Preserve independent Curva K batches and 30-point Q14 changes separately from Mapa K.

### `autocal_snapshot` / `autocal_field`

Preserve raw decoded field identity, element count, values, validity and failure reason. The 18 AutoCal acquisition zones and 30-point K-factor/vector families remain separate concepts.

### `portmon_capture_summary`

Stores capture hash, transaction counts, command families and confirmed write summaries without duplicating the full raw PortMon text.

### `rpm_map_summary`

Materialized per-session/per-fuel RPM×MAP summaries for fast exploratory queries while preserving telemetry as the detailed authority.

### `analysis_result`

Stores versioned derived analyses and their parameters/metrics/artifact digests.

## MAP ↔ Tinj hypothesis

The two observed multimedia points are registered as an exploratory clue, not proof:

- MAP `0.438` bar → GNV Petrol Inj `4.76` ms;
- MAP `0.918` bar → GNV Petrol Inj `10.30` ms;
- MAP ratio ≈ `2.096×`;
- Tinj ratio ≈ `2.164×`;
- `Tinj/MAP` differs by only about `3.2%` between the two observations.

The warehouse must support testing this on the full corpus using:

1. robust `Tinj ~ MAP` fits;
2. `Tinj ~ MAP + RPM` / local geometric field;
3. residual conditioning by Mapa K, Curva K, AutoCal state and session;
4. chronological/leave-one-session-out falsification.

No proportional law is assumed in advance.

## Supabase schema

Create isolated schema `omegas_science` with:

- `corpus_version`;
- `ingestion_run`;
- `source_blob`;
- `logical_session`;
- `session_source`;
- `cache_artifact`;
- `map_k_batch`;
- `map_k_cell_change`;
- `k_factor_batch`;
- `k_factor_point_change`;
- `autocal_snapshot`;
- `autocal_field`;
- `portmon_capture_summary`;
- `rpm_map_summary`;
- `analysis_result`.

Revoke schema/table privileges from `anon` and `authenticated`. Access is through the connected management path or explicit future service-only integration.

## Evidence durability

Every generated cache release produces:

- SQLite file;
- `.zst` compressed artifact;
- SHA-256 for both;
- public-safe corpus manifest in `evidence/red_blend/warehouse/`;
- Supabase corpus row;
- Drive artifact ID;
- GitHub Issue #11 checkpoint.

No raw private filename is required in public evidence; source hashes and privacy-safe aliases are the bridge.

## Failure policy

- malformed source line → record as malformed with source/line hash; do not invent content;
- unknown event type → preserve as opaque count/provenance;
- conflicting same-session event identity → record conflict; never select a winner silently;
- missing calibration state → `UNKNOWN`;
- failed upload → local cache remains non-canonical until Drive ID + digest are recorded;
- failed Supabase ledger write → Drive cache is not declared fully published.

## Runtime and safety boundary

- No Android runtime change is required for the warehouse.
- No automatic ECU write.
- RED remains prediction anchor.
- Warehouse evidence can inform future Predictor candidates only after blind held-out proof.
- `P_IMPROVE_PROVEN=false` unless separately calibrated.
- `VEHICLE_PROVEN=false` until physical vehicle validation.

## Success criteria v1

The first warehouse version is complete when:

1. Supabase `omegas_science` schema exists and is isolated;
2. ingestion code is TDD-covered and idempotent;
3. currently mounted raw corpus is normalized into one SQLite cache;
4. telemetry and calibration counts reconcile with existing forensic checkpoints or discrepancies are explicitly explained;
5. SQLite and compressed artifact hashes are produced;
6. compressed cache is uploaded to the OMEGAS Drive folder;
7. Supabase ledger points to that artifact and records corpus digest;
8. public-safe warehouse manifest is committed;
9. a full-corpus MAP↔Tinj exploratory analysis is generated from the cache, not by reopening raw logs;
10. no production runtime promotion occurs merely because the warehouse exists.
