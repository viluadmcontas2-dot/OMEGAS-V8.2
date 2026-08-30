# RED V8.2 Full-Corpus Forensics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reconstruct a privacy-safe, hash-bound, bounded-memory historical corpus for OMEGAS from all accessible session ZIPs, nested containers, PortMon, AutoCal, calibration writes, learning exports and snapshots before any new scientific experiment is designed.

**Architecture:** A deterministic offline forensics pipeline walks physical sources recursively, fingerprints every blob, parses known session/event structures, deduplicates physical copies by SHA-256, and unions divergent exports of the same logical `sessionId` through disk-backed event identities. Later stages reconstruct calibration, ECU state and chronology from this provenance-preserving base; raw private logs never enter the repository.

**Tech Stack:** Python 3 standard library (`zipfile`, `hashlib`, `json`, `sqlite3`, `pathlib`, `argparse`, `collections`), existing OMEGAS protocol knowledge, GitHub Actions for deterministic tests/audits.

**Spec:** `docs/superpowers/specs/2026-08-30-red-v82-full-corpus-forensics-design.md`

## Global Constraints

- No Android/runtime change is authorized by this work unit.
- Raw/private logs, original filenames, device identifiers and private paths must not be committed.
- Every durable public artifact must be derivable from source SHA-256 identities and privacy-safe aliases.
- Exact blob dedupe and logical event-union are separate operations.
- Logical session union must be bounded-memory; retaining the entire corpus in RAM is forbidden.
- Unknown file/event classes must be surfaced as opaque/unparsed, never silently dropped.
- MAP_K and K-factor/Curva K remain separate calibration dimensions.
- A calibration batch/adjustment is the intervention unit; cells/points inside one batch are not automatically independent interventions.
- `CAUSAL_MAP_K_PROVEN=false`, `CAUSAL_K_FACTOR_PROVEN=false`, `P_IMPROVE_PROVEN=false`, `VEHICLE_PROVEN=false`, `PRODUCTION_RUNTIME_CHANGED=false` until later explicit gates prove otherwise.
- No automatic ECU write.

---

### Task 1: F0/F1 physical-source registry and recursive inventory

**Files:**
- Create: `lab/red_blend/full_corpus_forensics.py`
- Create: `lab/red_blend/test_full_corpus_forensics.py`
- Create: `tools/science/build_full_corpus_inventory.py`
- Generate: `evidence/red_blend/full_corpus/physical-source-manifest.json`

**Interfaces:**
- Consumes: one or more private filesystem roots supplied only at execution time.
- Produces: `inventory_sources(roots) -> list[PhysicalSource]`, `sha256_file(path) -> str`, recursive member records, public-safe aliases and classification counts.

- [ ] **Step 1: Write failing tests for deterministic SHA-256, recursive ZIP enumeration, exact-blob duplicate grouping, APK exclusion classification and opaque-file retention.**

```python
def test_recursive_inventory_groups_exact_blob_duplicates_and_keeps_opaque(tmp_path):
    ...
    records = inventory_sources([tmp_path])
    assert duplicate_group_size(records, known_sha) == 2
    assert any(r.status == "OPAQUE" for r in records if r.alias == "SRC-OPAQUE")
```

- [ ] **Step 2: Run `python -m unittest lab.red_blend.test_full_corpus_forensics.FullCorpusInventoryTests -v` and verify RED because the module/API does not yet exist.**
- [ ] **Step 3: Implement the minimal recursive source registry using streaming SHA-256 and ZIP member inspection without extracting the whole corpus into RAM.**
- [ ] **Step 4: Re-run the tests and require GREEN.**
- [ ] **Step 5: Run the inventory against the mounted OMEGAS corpus and emit only privacy-safe derived JSON.**
- [ ] **Step 6: Commit code/tests and generated manifest separately so code review can reject logic independently from evidence.**

### Task 2: F2 disk-backed logical-session union

**Files:**
- Modify: `lab/red_blend/full_corpus_forensics.py`
- Modify: `lab/red_blend/test_full_corpus_forensics.py`
- Generate: `evidence/red_blend/full_corpus/logical-session-manifest.json`
- Generate: `evidence/red_blend/full_corpus/export-relationships.json`

**Interfaces:**
- Consumes: session event streams discovered in Task 1.
- Produces: `union_logical_sessions(records, sqlite_path) -> LogicalUnionReport`; deterministic event identity SHA-256; source-membership table; subset/superset/equal/divergent relationship classification.

- [ ] **Step 1: Write a failing regression test reproducing the prior memory failure with many repeated synthetic events while asserting bounded process memory proxy through constant-size in-memory buffers.**
- [ ] **Step 2: Write failing tests for identical streams with different ZIP hashes, strict subset, strict superset and divergent same-session exports.**
- [ ] **Step 3: Run those tests and verify RED for missing disk-backed union implementation.**
- [ ] **Step 4: Implement SQLite-backed event identity/source-membership indexing; process JSONL line-by-line; commit no raw event payloads.**
- [ ] **Step 5: Verify GREEN on synthetic tests, then run the real 34-session corpus union.**
- [ ] **Step 6: Verify that the earlier known relationships (identical 8129/8129, strict subset 164490/375006, +56, +110) are reproduced or explicitly explain any change caused by better canonicalization.**
- [ ] **Step 7: Commit implementation and evidence with checksums.**

### Task 3: F3 parser/schema coverage

**Files:**
- Modify: `lab/red_blend/full_corpus_forensics.py`
- Modify: `lab/red_blend/test_full_corpus_forensics.py`
- Generate: `evidence/red_blend/full_corpus/schema-coverage.json`

**Interfaces:**
- Consumes: unique physical files and logical-session events.
- Produces: file-class counts, event-type counts, parser status (`PARSED`, `OPAQUE`, `EXCLUDED_WITH_REASON`) and unknown-key/type samples without private payloads.

- [ ] **Step 1: RED tests require unknown types to survive inventory instead of disappearing.**
- [ ] **Step 2: Implement parser coverage accounting and explicit opaque registry.**
- [ ] **Step 3: GREEN tests + real audit.**
- [ ] **Step 4: Commit evidence and checksum.**

### Task 4: F4 calibration reconstruction

**Files:**
- Create: `lab/red_blend/calibration_forensics.py`
- Create: `lab/red_blend/test_calibration_forensics.py`
- Generate: `evidence/red_blend/full_corpus/map-k-interventions.json`
- Generate: `evidence/red_blend/full-corpus/k-factor-interventions.json`

**Interfaces:**
- Consumes: deduplicated `k_batch_confirmed`, `k_factor_batch_confirmed`, `k_read_map`, PortMon write transactions and source/session provenance.
- Produces: independently deduplicated MAP_K batches and K-factor batches, ordered by proven timestamps with hashes and confirmation/readback status.

- [ ] **Step 1: RED tests for batch identity, ACK/readback fail-closed behavior and prevention of cell/point pseudo-replication.**
- [ ] **Step 2: Implement reconstruction separately for MAP_K and K-factor.**
- [ ] **Step 3: Replay real corpus and reconcile preliminary 70/357 MAP_K and 29/101 K-factor counts.**
- [ ] **Step 4: Cross-check PortMon 144-cell K=100 capture as transaction evidence without automatically equating it to an app adjustment batch.**
- [ ] **Step 5: Commit manifests and explicit unresolved joins.**

### Task 5: F5 AutoCal and PortMon ECU-state reconstruction

**Files:**
- Create: `lab/red_blend/ecu_state_forensics.py`
- Create: `lab/red_blend/test_ecu_state_forensics.py`
- Generate: `evidence/red_blend/full_corpus/autocal-snapshots.json`
- Generate: `evidence/red_blend/full_corpus/portmon-summary.json`

**Interfaces:**
- Consumes: AutoCal snapshot events, PortMon request/response streams and governed protocol identities.
- Produces: snapshot hashes, decoded-field coverage, validity/coherence status, command families and temporal anchors.

- [ ] **Step 1: RED tests for known AutoCal field identity and invalid-shape retention.**
- [ ] **Step 2: Implement privacy-safe snapshot indexing and PortMon role classification.**
- [ ] **Step 3: Reproduce the governed PortmonAUTOCAL 36,463-transaction / 21-command summary and the calibration-write capture counts.**
- [ ] **Step 4: Commit evidence.**

### Task 6: F6 July-August chronology

**Files:**
- Create: `lab/red_blend/chronology_forensics.py`
- Create: `lab/red_blend/test_chronology_forensics.py`
- Generate: `evidence/red_blend/full_corpus/chronology.jsonl.gz` or privacy-safe chunked JSONL if repository size permits; otherwise commit a manifest plus chunk hashes.
- Generate: `evidence/red_blend/full_corpus/chronology-manifest.json`

**Interfaces:**
- Consumes: logical sessions, calibration batches, AutoCal snapshots, software/export metadata.
- Produces: ordered evidence epochs with provenance links and explicit unknown-state intervals.

- [ ] **Step 1: RED tests for ordering, no guessed state across unknown intervals and deterministic tie-breaking.**
- [ ] **Step 2: Implement chronology builder.**
- [ ] **Step 3: Build real July-August timeline and validate hash stability on repeat generation.**
- [ ] **Step 4: Commit only privacy-safe chronology products.**

### Task 7: F7 scientific-readiness matrix and brainstorming inputs

**Files:**
- Create: `evidence/red_blend/full_corpus/scientific-readiness.json`
- Create: `docs/superpowers/specs/2026-08-30-red-v82-full-corpus-experiment-candidates.md`

**Interfaces:**
- Consumes: F0-F6 evidence products.
- Produces: support matrix for vertical, horizontal/transversal and diagonal analyses; candidate hypotheses ranked by evidential support; explicit falsification criteria.

- [ ] **Step 1: Compute which cells/curve points/regions span multiple independently observed calibration states and sessions.**
- [ ] **Step 2: Compute which candidate analyses have enough overlap for descriptive, transfer or causal claims.**
- [ ] **Step 3: Brainstorm 2-3 strongest experiment families from the actual corpus, including trade-offs and ways each could be falsified.**
- [ ] **Step 4: Do not implement any predictor experiment yet; present candidates for architectural approval.**

### Task 8: Final verification and authority reconciliation

**Files:**
- Modify: `evidence/red_blend/full-corpus-checkpoint-2026-08-30.json`
- Update: Issue `#11`

**Interfaces:**
- Consumes: all prior gate artifacts.
- Produces: final F0-F7 status, artifact hashes, unresolved-source list and explicit non-claims.

- [ ] **Step 1: Run all new unit tests plus the existing RED Blend workflow tests.**
- [ ] **Step 2: Regenerate all manifests twice and verify deterministic SHA-256 identity where inputs are unchanged.**
- [ ] **Step 3: Compare branch against the RED Android baseline and confirm no Android/runtime input changed.**
- [ ] **Step 4: Update Issue #11 with exact SHAs, counts, gate statuses and unresolved evidence.**
- [ ] **Step 5: Invoke `superpowers:verification-before-completion` before any completion claim.**
