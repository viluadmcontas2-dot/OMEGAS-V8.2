# OMEGAS Science Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans task-by-task.

**Goal:** Decode the OMEGAS vehicle corpus once into a versioned portable SQLite cache so future analysis does not repeatedly parse raw Drive logs.

**Architecture:** Drive is immutable raw authority. SQLite is detailed extracted scientific memory. GitHub/Issue #11 contain parsers, tests, public-safe manifests, hashes and derived evidence. There is no external database dependency.

**Tech Stack:** Python stdlib (`sqlite3`, `zipfile`, `json`, `hashlib`, `statistics`), optional Zstandard CLI, Google Drive, GitHub.

**Spec:** `docs/superpowers/specs/2026-08-30-omegas-science-warehouse-design.md`

## Global constraints

- Raw/private logs never enter GitHub.
- RED remains the performance/prediction anchor.
- `RPM × MAP` is the operating coordinate; Petrol Inj. is the response.
- AutoCal 18 acquisition zones, Curva K 30-point Q14 and Mapa K 12×12 remain separate.
- Batch/adjustment is the intervention unit.
- No automatic ECU write.
- Missing/conflicting state is never guessed.

## Task 1 — Cache contracts and ingestion

- TDD deterministic source SHA, event SHA and privacy-safe session keys.
- Stream session JSONL; union repeated exports by semantic event identity.
- Preserve malformed records and conflicts explicitly.
- Normalize telemetry while retaining canonical semantic payload.
- Summarize raw USB provenance instead of copying millions of text records.
- Extract Mapa K, Curva K and AutoCal into independent tables.
- Recursively process nested session ZIPs and preserve useful small semantic JSON artifacts.

## Task 2 — Build and verify mounted corpus

- Build `/mnt/data/OMEGAS_SCIENCE_CACHE_V1.sqlite` from all mounted OMEGAS data sources, excluding build/APK artifacts.
- Reconcile logical sessions, telemetry, Mapa K, Curva K and AutoCal counts against forensic checkpoints.
- Run `PRAGMA integrity_check` and foreign-key checks.
- Record cache byte size and SHA-256.
- Explain discrepancies; never edit data to force reconciliation.

## Task 3 — Persist portable artifact

- Compress the SQLite cache without changing the source file.
- Verify decompression recreates the exact SQLite SHA-256.
- Upload the compressed cache to the OMEGAS Drive folder.
- Commit a public-safe manifest under `evidence/red_blend/warehouse/` containing parser version, source/corpus counts, cache SHA/bytes, compressed SHA/bytes, Drive file identity and scientific boundaries.

## Task 4 — Reuse the existing scientific battery

Before inventing new experiments, execute the already implemented suite:

- local science / real corpus;
- session independence;
- blind walk-forward;
- risk-gated hybrid;
- mechanistic calibration;
- geometric field;
- causal MAP_K / causal science;
- sensitivity;
- empirical risk coverage;
- P(improve) fail-closed;
- OOD transfer;
- RED hot-path/performance gate.

Record which tests are GREEN and the numerical evidence each experiment produced.

## Task 5 — Full-cache MAP ↔ Tinj falsification

Read **only the SQLite cache**, not raw Drive files, and measure:

- `Tinj/MAP` distribution by fuel and session;
- robust/descriptive `Tinj ~ MAP` relation;
- `Tinj ~ MAP + RPM` and the existing local geometric field;
- RPM-band slopes/residual quantiles;
- session-balanced and chronological/leave-one-session-out diagnostics;
- residual association with explicit Mapa K, Curva K and AutoCal states where chronology is proven.

No proportional law is assumed in advance.

## Task 6 — Evidence checkpoint

- Commit compact results and checksums to `evidence/red_blend/warehouse/`.
- Add Issue #11 checkpoint with exact source SHA, cache digest, tests and limits.
- Confirm production runtime status truthfully: byte-identical RED only when it actually is; otherwise distinguish pinned UI/projection changes from hot-path scientific changes.
- Invoke `verification-before-completion` before any completion claim.
