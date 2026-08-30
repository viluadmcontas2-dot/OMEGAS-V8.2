# OMEGAS Science Warehouse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the decoded OMEGAS vehicle corpus in one versioned SQLite cache plus an isolated Supabase ledger so future analysis does not repeatedly parse the raw Drive logs.

**Architecture:** Raw Drive files remain immutable authority. A deterministic Python ingestion pipeline builds a portable SQLite analytics cache and Zstandard artifact; Supabase `omegas_science` stores compact provenance, corpus/session/calibration indexes and analysis metadata. GitHub stores the migration, parser, tests and public-safe checksums.

**Tech Stack:** Python 3 stdlib (`sqlite3`, `zipfile`, `json`, `hashlib`, `statistics`), Zstandard CLI, PostgreSQL/Supabase, Google Drive, GitHub.

**Spec:** `docs/superpowers/specs/2026-08-30-omegas-science-warehouse-design.md`

## Global Constraints

- Raw/private Drive logs are never committed to GitHub.
- RED Android runtime is not modified by this work.
- `RPM × MAP` is the operating coordinate; Petrol Inj is the response distribution.
- AutoCal 18 acquisition zones, K-factor 30-point curve and Mapa K 12×12 remain separate dimensions.
- Batch/adjustment is the intervention unit; individual cells/points are children.
- No automatic ECU write.
- No prediction promotion without held-out evidence.
- Missing or conflicting evidence stays `UNKNOWN`/conflict; never guessed.
- Ingestion is idempotent by source SHA-256 + parser contract version.

---

### Task 1: Isolated Supabase ledger schema

**Files:**
- Create: `infra/omegas_science/001_init.sql`

**Interfaces:**
- Produces schema `omegas_science` with tables named in the design.
- Supabase management connector applies the exact migration to project `gwssfwtbepaedxgzmjpe`.

- [ ] Write SQL defining `corpus_version`, `ingestion_run`, `source_blob`, `logical_session`, `session_source`, `cache_artifact`, MAP_K/K-factor intervention tables, AutoCal tables, PortMon summary, RPM×MAP summary and `analysis_result`.
- [ ] Include PK/FK/check constraints and indexes on session/time/RPM/MAP.
- [ ] Revoke privileges from `anon` and `authenticated` on schema/tables/sequences.
- [ ] Apply migration through Supabase.
- [ ] Verify the table set from `information_schema` and verify no grants to anon/authenticated.
- [ ] Commit SQL.

### Task 2: TDD cache schema and deterministic identities

**Files:**
- Create: `tools/science/warehouse_cache.py`
- Create: `lab/red_blend/test_science_warehouse.py`

**Interfaces:**
- `create_cache(path: Path, parser_version: str) -> sqlite3.Connection`
- `source_sha256(path: Path) -> str`
- `event_digest(event: dict) -> str`
- `session_key(raw_session_id: str) -> str`

- [ ] Write failing tests proving schema creation, stable event digest, stable privacy-safe session key and source dedupe.
- [ ] Run tests and verify RED because module/functions do not exist.
- [ ] Implement minimal schema/identity helpers.
- [ ] Run focused tests and verify GREEN.
- [ ] Commit.

### Task 3: TDD session ZIP ingestion

**Files:**
- Modify: `tools/science/warehouse_cache.py`
- Modify: `lab/red_blend/test_science_warehouse.py`

**Interfaces:**
- `ingest_session_zip(conn, zip_path, source_alias=None) -> IngestStats`
- Telemetry rows are unique by `(session_key, sequence, event_sha256)`.

- [ ] Write synthetic ZIP tests for telemetry, duplicate export, strict superset export, malformed JSON line and unknown event type.
- [ ] Verify tests fail for missing ingestion behavior.
- [ ] Implement streaming JSONL ingestion without retaining the corpus in RAM.
- [ ] Persist malformed/opaque evidence metadata instead of dropping it.
- [ ] Verify tests GREEN and memory remains bounded on a generated large fixture.
- [ ] Commit.

### Task 4: TDD calibration and AutoCal extraction

**Files:**
- Modify: `tools/science/warehouse_cache.py`
- Modify: `lab/red_blend/test_science_warehouse.py`

**Interfaces:**
- Parse `k_batch_confirmed`, `k_factor_batch_confirmed`, `autocal_native_snapshot` into dedicated normalized tables.

- [ ] Write RED tests proving MAP_K and K-factor remain separate and duplicate adjustments collapse by adjustment identity.
- [ ] Write RED test proving an AutoCal field with 18 acquisition-zone elements is not conflated with 30-point K-factor data.
- [ ] Implement minimal normalized extraction.
- [ ] Run focused tests GREEN.
- [ ] Commit.

### Task 5: Build the mounted-corpus cache

**Files:**
- Create generated local artifact only: `/mnt/data/OMEGAS_SCIENCE_CACHE_V1.sqlite`
- Create generated local manifest: `/mnt/data/omegas_science_cache_v1_manifest.json`

**Interfaces:**
- Input: all currently mounted OMEGAS data files in `/mnt/data`, excluding APK/build artifacts and generated warehouse outputs.
- Output: one deterministic cache plus public-safe counts/hashes.

- [ ] Inventory selected physical inputs and SHA-256 them.
- [ ] Recursively include session ZIPs nested inside `LOGS HUB.zip`.
- [ ] Ingest all supported session events exactly once by event identity.
- [ ] Insert current forensic summaries for PortMon captures when full transaction reparse is unnecessary; preserve their raw source SHA as authority.
- [ ] Build per-session/per-fuel RPM×MAP summary table from normalized telemetry.
- [ ] Run SQLite `PRAGMA integrity_check` and foreign-key check.
- [ ] Reconcile session, MAP_K, K-factor and AutoCal counts against the latest evidence checkpoint; explain differences rather than altering data.
- [ ] Compute SQLite SHA-256 and canonical corpus digest.

### Task 6: Compress and publish cache to Drive

**Files:**
- Create generated local artifact: `/mnt/data/OMEGAS_SCIENCE_CACHE_V1.sqlite.zst`

- [ ] Compress with `zstd -19` without modifying source SQLite.
- [ ] Verify decompression recreates byte-identical SQLite SHA-256.
- [ ] Upload compressed cache to OMEGAS Drive folder `1eK0IISPKFeY4FRaCDrYZcSY_zPLsWoIq`.
- [ ] Record Drive file ID, compressed bytes and SHA-256.

### Task 7: Publish compact ledger to Supabase

**Files:**
- Create: `evidence/red_blend/warehouse/warehouse-v1-manifest.json`

- [ ] Insert/upsert corpus version metadata, source hashes, logical-session inventory, calibration batch indexes, AutoCal snapshot indexes and cache artifact identity into `omegas_science`.
- [ ] Insert RPM×MAP summary rows in bounded SQL batches; do not upload raw USB text.
- [ ] Query counts back from Supabase and compare with SQLite manifest.
- [ ] Generate public-safe warehouse manifest with both SQLite/Drive/Supabase identities.
- [ ] Commit manifest.

### Task 8: Full-corpus MAP ↔ Tinj analysis from cache only

**Files:**
- Create: `tools/science/warehouse_analysis.py`
- Create: `lab/red_blend/test_warehouse_analysis.py`
- Create generated evidence: `evidence/red_blend/warehouse/map-tinj-analysis-v1.json`

**Interfaces:**
- `analyze_map_tinj(sqlite_path, fuel, min_samples) -> dict`
- Analysis must read only SQLite, never raw ZIP/Drive sources.

- [ ] Write RED synthetic tests for proportional data, RPM-confounded data and nonlinear data.
- [ ] Implement robust descriptive fits by fuel and RPM band plus `Tinj/MAP` distribution.
- [ ] Add session-balanced summaries and leave-one-session-out diagnostics where support exists.
- [ ] Run against V1 cache.
- [ ] Record sample count, sessions, slopes, ratios, residual quantiles and explicit non-causal interpretation.
- [ ] Commit code + compact analysis evidence.

### Task 9: Verification and durable checkpoint

**Files:**
- Issue `#11`

- [ ] Run focused warehouse tests plus existing RED Blend Python tests affected by import paths.
- [ ] Verify Supabase schema/counts fresh.
- [ ] Verify Drive cache ID is downloadable and hash-bound.
- [ ] Compare branch HEAD against pre-warehouse HEAD and confirm no unintended Android runtime changes from this work.
- [ ] Add Issue #11 checkpoint with exact SHA, corpus digest, cache hash, Drive ID, Supabase project/schema, counts and scientific boundaries.
- [ ] Invoke `verification-before-completion` before any completion claim.
