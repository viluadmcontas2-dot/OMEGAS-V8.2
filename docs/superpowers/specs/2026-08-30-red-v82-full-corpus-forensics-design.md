# RED V8.2 Full-Corpus Forensics — Design

Date: 2026-08-30
WorkUnit: `RED-BLEND-001`
Issue: `#11`
Branch: `work/red-v82-science-blend`
Status: DESIGN_APPROVED_BY_OWNER_DIRECTION / IMPLEMENTATION_NOT_STARTED
Supersedes as immediate next step: `2026-08-30-red-v82-cross-session-calibration-response-design.md` and its implementation plan. Those documents remain historical context but are frozen until this inventory closes.

## Objective

Reconstruct the largest evidence-preserving historical corpus available for this vehicle before defining new causal, transfer, sensitivity or predictor experiments.

The scientific corpus is not a `.omegas` file and is not the 1,708 WU-006 episodes. Those are two evidence products among many. The corpus authority for this phase is the union of all accessible raw/session sources, including session ZIPs, nested ZIPs, `OMEGAS_Dados` exports, LOGS HUB, PortMon captures, AutoCal snapshots, USB raw traffic, telemetry, full snapshots, confirmed MAP_K writes, confirmed K-factor writes, learning exports and `.omegas` snapshots when present.

No Android/runtime change is authorized by this work. No scientific promotion decision is authorized until the inventory and provenance gates below are green.

## Why this is architectural

The previous experiments operated on a compact episode fixture. That was useful for controlled blind prediction, but the newly recovered corpus contains historical calibration and ECU-state evidence that the episode fixture intentionally omitted.

The evidence model must therefore change from:

`selected export -> episodes -> experiment`

to:

`physical source -> recursively extracted evidence -> exact-blob dedupe -> event-level union -> logical session -> calibration/ECU timeline -> derived scientific views -> experiments`.

This prevents three known failure modes:

1. privileging one file class such as `.omegas`;
2. treating the largest export of a session as complete when a smaller/different export may contain unique evidence;
3. counting copies or incremental exports as independent observations.

## Chosen approach

### Approach A — Single canonical export per session

Rejected. Simple, but already falsified by the recovered corpus: exports sharing one logical `sessionId` can contain strict supersets/subsets or metadata-only differences.

### Approach B — Concatenate every file

Rejected. Maximizes recall but creates double counting, pseudo-replication and duplicated intervention events.

### Approach C — Provenance-preserving event union

Chosen.

Every physical blob retains immutable provenance. Exact duplicate blobs are collapsed by SHA-256. Divergent exports of the same logical session are then unioned event-by-event using deterministic event identities, while retaining all source memberships. Scientific independence is assigned only after this union.

## Evidence layers

### L0 — Physical source registry

For every accessible file/container:

- source key;
- source class;
- byte size;
- SHA-256;
- container parent when nested;
- extraction status;
- parser/schema status;
- privacy classification.

Raw/private source bytes are never committed to the repository.

### L1 — Recursive container inventory

ZIPs and nested ZIPs are enumerated recursively. Every member receives path, size, compression metadata and content hash where practical.

APK/build artifacts are classified but excluded from the scientific corpus unless needed to identify software version provenance.

### L2 — Logical-session reconstruction

Session packages expose `sessionId` when available.

Rules:

- exact ZIP duplicates collapse by SHA-256;
- same `sessionId` + identical event stream = duplicate export;
- same `sessionId` + subset/superset event stream = incremental/divergent export;
- logical session = union of unique events across all exports carrying that `sessionId`;
- no single export is declared authoritative merely because it is larger.

### L3 — Event evidence

Preserve at minimum these observed event classes:

- `telemetry`;
- `engine_event`;
- `full_snapshot`;
- `usb_raw`;
- `app_log`;
- `autocal_native_snapshot`;
- `k_batch_confirmed`;
- `k_factor_batch_confirmed`;
- `k_read_map`;
- `settings_changed`;
- `session_started` / `session_stopped` / `export_boundary`.

Unknown future event types are retained and reported rather than dropped.

### L4 — Calibration timeline

MAP_K and Curva K/K-factor are separate calibration dimensions.

For confirmed MAP_K batches preserve:

- adjustment identity;
- old/new map hash;
- physical axes contract;
- full post-write map when logged;
- affected cells;
- before/after/readback;
- confirmation/batch-finalization state;
- timestamp and source provenance.

For confirmed K-factor batches preserve:

- adjustment identity;
- old/new curve hash;
- 30-point petrol-ms axis;
- full post-write curve when logged;
- changed points;
- raw Q14 before/after;
- physical factor before/after;
- confirmation and timestamp.

A calibration batch is an intervention/event unit. Frames, cells and points inside one batch are not automatically independent interventions.

### L5 — AutoCal / ECU internal state

AutoCal snapshots are first-class evidence, including validity/coherence metadata and every decoded field available from the ECU protocol. Invalid fields are retained with failure reason; they are not silently corrected.

Relevant observed families include Petrol Inj axes/buffers, manifold-pressure thresholds/buffers, acquisition counters, `MUL_ACT`, AutoMatch controls/counts and acquired zones.

### L6 — PortMon transaction evidence

PortMon captures are decoded as deterministic request/response transaction streams using the governed MP48/AutoCal protocol knowledge in the repository.

Captures must be classified by role rather than merged blindly. A read-heavy AutoCal capture and a calibration-write capture can answer different questions.

### L7 — Derived scientific views

Only after L0-L6 pass may the corpus produce views such as:

- RPM x MAP -> Tinj distributions;
- gasoline reference surfaces;
- GNV response surfaces;
- MAP_K state through time;
- K-factor curve through time;
- AutoCal state through time;
- intervention-centered pre/post windows;
- cross-session calibration-state contrasts;
- vertical cell/point trajectories;
- horizontal full-state snapshots;
- diagonal physical trajectories combining operating coordinate + calibration state.

Derived views must link back to source/evidence hashes.

## Frozen science rule

`RPM x MAP defines the operating coordinate. Tinj is the response distribution observed repeatedly inside that coordinate.`

All valid repeated observations in a coordinate contribute strongly to local characterization. Session/epoch metadata measures persistence/transfer and must not artificially erase dense local evidence. Multimodality or incompatible dispersion is evidence of hidden state/regime/transient/missing dimension, not permission to average incompatible modes into one confident mean.

Calibration state, AutoCal state and other recovered dimensions are explanatory variables until corpus evidence proves they improve prediction/generalization.

## Privacy and evidence durability

Repository artifacts may contain only privacy-safe derived evidence.

Required durable artifacts:

1. public-safe physical-source manifest with SHA-256 identities and redacted aliases;
2. logical-session/export relationship manifest;
3. event-type counts and parser coverage report;
4. MAP_K intervention manifest;
5. K-factor intervention manifest;
6. AutoCal snapshot manifest;
7. PortMon transaction summaries;
8. chronology/timeline manifest;
9. explicit list of unresolved/opaque sources;
10. checksums for every derived artifact.

Private filenames/paths may be kept outside the public repository; SHA-256 remains the provenance bridge.

Every material discovery must be written to one of these artifacts or an issue checkpoint before the analysis proceeds far enough that the discovery could be lost in chat history.

## Inventory gates

### F0 — Physical completeness

Every currently accessible source in the OMEGAS data folder is classified as inventoried, intentionally excluded, inaccessible, or pending.

### F1 — Blob deduplication

Exact physical duplicates are identified by SHA-256; duplicate counts are reported.

### F2 — Logical-session union

Exports sharing a `sessionId` are compared event-by-event. Subset/superset/divergent relationships are recorded and unique-event union is reproducible with bounded memory.

### F3 — Schema coverage

All observed event types and file classes are either parsed or explicitly opaque. Silent dropping is forbidden.

### F4 — Calibration reconstruction

Confirmed MAP_K and K-factor batches are deduplicated, ordered and linked to hashes/sessions/sources.

### F5 — ECU-state reconstruction

AutoCal snapshots and useful PortMon read transactions are indexed with validity and temporal-coherence metadata.

### F6 — Chronology

A single July-to-August chronology is produced with software version, logical session, fuel evidence, map/curve state and ECU-state evidence where available.

### F7 — Scientific readiness review

Only now may new experiment designs be proposed. Candidate tests must be selected from what the corpus actually supports, not from the convenience of a particular file.

## Known preliminary evidence at design time

The first forensic pass already established:

- 40 observed session-package occurrences;
- 38 physically unique session ZIP blobs by SHA-256;
- 34 logical `sessionId` values;
- examples of identical event-stream exports with different ZIP hashes;
- examples where one export is a strict event superset of another sharing the same `sessionId`;
- raw physical-export counts include about 5.765M `usb_raw`, 281k `telemetry`, 32k `engine_event`, 13k `app_log`, and 11k `full_snapshot` events before logical-session event-union dedupe;
- 70 distinct confirmed MAP_K adjustment IDs with 357 distinct confirmed cell events found in session logs;
- 29 distinct confirmed K-factor adjustment IDs with 101 distinct confirmed point events found in session logs;
- 12 distinct AutoCal snapshot hashes found in the current session packages;
- one PortMon capture is read-heavy AutoCal evidence (36,463 writes/transactions, 21 distinct write commands, no MAP_K or K-factor write identified in the first pass);
- another PortMon capture contains 144 unique MAP_K cell-write commands covering rows 0..11 x columns 0..11, all writing K=100, plus AutoCal toggles and K-insertion commands; no K-factor write was identified in that first pass.

These are checkpoint findings, not final corpus totals. They must be regenerated by the durable inventory pipeline before being treated as closure metrics.

## Explicit non-claims

Until later gates prove otherwise:

- `CAUSAL_MAP_K_PROVEN=false`;
- `CAUSAL_K_FACTOR_PROVEN=false`;
- `P_IMPROVE_PROVEN=false`;
- `VEHICLE_PROVEN=false`;
- `PRODUCTION_RUNTIME_CHANGED=false`;
- no fuel-economy claim;
- no automatic ECU write;
- no production predictor promotion.

## Success condition for this phase

This design is complete when the corpus can be reconstructed from the accessible private sources into privacy-safe, hash-bound derived manifests without loading the entire corpus into RAM, and every subsequent scientific result can point backward to the exact evidence set from which it was derived.
