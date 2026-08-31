# OMEGAS Science Cache — Design

Date: 2026-08-30
WorkUnit: `RED-BLEND-001`
Issue: `#11`
Branch: `work/red-v82-science-blend`
Status: APPROVED_BY_OWNER / IMPLEMENTATION_AUTHORIZED

## Outcome

Decode the vehicle corpus once, preserve the extracted facts with cryptographic provenance, and let future agents analyze the derived corpus without reopening dozens of raw ZIPs, PortMon captures and `.omegas` snapshots for every question.

The cache is scientific memory, not a runtime/prediction authority.

## Authority and persistence

1. **Google Drive — immutable raw authority.** Session ZIPs, nested ZIPs, PortMon logs, AutoCal captures, `.omegas` snapshots and exports remain the source material. Raw private files are never committed to GitHub.
2. **Canonical SQLite cache — extracted scientific memory.** A deterministic SQLite database stores normalized telemetry, logical sessions, calibration interventions, AutoCal state, semantic JSON artifacts and provenance. Millions of redundant raw USB text lines are summarized rather than copied.
3. **GitHub + Issue #11 — engineering and evidence authority.** Parsers, tests, cache schema, public-safe manifests, hashes, analysis evidence and checkpoints live in the repository/Issue.

There is **no external database dependency** in the OMEGAS science-cache architecture.

## Corpus identity

Every cache release is immutable and identified by parser/schema version, ordered source SHA-256 set, logical-session count, normalized telemetry count, intervention counts, cache SHA-256, optional compressed-artifact SHA-256 and the code SHA that generated it.

A source is reparsed only when its SHA is new, the parser contract changes, or a forced rebuild is explicitly requested. Repeated physical occurrences remain recorded while identical blob content is parsed once.

## Scientific dimensions kept separate

- operating coordinate: `RPM × MAP(bar)`;
- observed response: Petrol Inj. distribution;
- AutoCal acquisition zones: 18-zone acquisition structures;
- Curva K / K-factor: independent 30-point Q14 vector;
- Mapa K: independent 12×12 calibration surface;
- OMEGAS observational gasoline/GNV curves and suggestions: separate derived concepts.

A batch/adjustment is an intervention. Its cells/curve points are children, never independent experiments.

## Cache model

The SQLite cache preserves:

- physical source blobs and occurrences by SHA;
- logical sessions and many-to-many source membership;
- semantic event identity and explicit conflicts;
- malformed records by source/segment/line/hash without guessed recovery;
- normalized telemetry plus canonical semantic payload;
- `map_k_batch` / `map_k_cell_change`;
- `k_factor_batch` / `k_factor_point_change`;
- `autocal_snapshot` / `autocal_field`;
- PortMon capture summaries and raw source hashes;
- whole semantic JSON artifacts when small enough to preserve usefully;
- per-session/per-fuel RPM×MAP summaries for fast exploration;
- derived analysis results with parameters and claim scope.

## MAP ↔ Tinj clue

The multimedia observations remain an exploratory clue, not a law:

- MAP `0.438` bar → GNV Petrol Inj `4.76` ms;
- MAP `0.918` bar → GNV Petrol Inj `10.30` ms;
- MAP ratio ≈ `2.096×`;
- Tinj ratio ≈ `2.164×`;
- `Tinj/MAP` differs by roughly `3.2%` between the two observations.

The cache must support falsifying this on the corpus through `Tinj ~ MAP`, `Tinj ~ MAP + RPM`, local geometric fields, session-balanced/chronological validation and conditioning on explicit calibration state. No proportional law is assumed beforehand.

## Failure policy

- malformed line → record; never invent content;
- unknown type → preserve as opaque/provenance;
- conflicting session event → record conflict; never silently choose a winner;
- unknown calibration state → `UNKNOWN`;
- missing raw source → mark missing/inaccessible rather than synthesizing it.

## Runtime boundary

- RED remains the Predictor/performance anchor;
- cache construction does not itself promote any model;
- no automatic ECU write;
- `P_IMPROVE_PROVEN=false` until independently calibrated;
- `VEHICLE_PROVEN=false` until physical vehicle validation.

## Success criteria

The cache phase is successful when the mounted corpus is normalized and integrity-checked, the counts reconcile with forensic checkpoints or discrepancies are explained, hashes are produced, a portable cache is persisted in Drive, public-safe evidence is committed, and subsequent scientific analyses can execute from the cache instead of reparsing raw logs.
