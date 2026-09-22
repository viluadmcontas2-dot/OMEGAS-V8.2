# OMEGAS ATLAS — ProgBase AutoCal Binary Atlas Implementation Plan

**Date:** 2026-09-21  
**Branch:** `OmegasAtlas`  
**Design:** `docs/superpowers/specs/2026-09-21-omegas-atlas-progbase-autocal-binary-atlas-design.md`  
**Issue:** #97

## Goal

Build and prove a remote-first GitHub Actions research fabric that recursively decomposes the canonical ProgBase AutoCal subsystem until the investigation frontier is empty.

## Source of truth

- Remote branch `OmegasAtlas`.
- Canonical ProgBase SHA-256 `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`.
- Original Portmon captures by SHA-256 plus deterministic derivation recipes.
- MMMACHINE is permitted only for acquisition/materialization.

## Phase 0 — Canonical inputs

1. Materialize the hash-verified ProgBase executable into `atlas/originals/progbase/ProgBase.exe`.
2. Locate `PortmonAUTOCAL` and `Lognovo1`; record original names, sizes and SHA-256.
3. If a raw log exceeds GitHub's normal blob limit, store a lossless compressed canonical copy when under the limit; otherwise use Git LFS or a GitHub-hosted artifact strategy.
4. Create `atlas/manifests/canonical-inputs.json` with hashes and provenance.
5. Re-read remote blobs/manifest and verify exact hash agreement.

**Stop condition:** any canonical input hash mismatch.

## Phase 1 — Deterministic seed tooling

Implement:
- PE/header/resource/string inventory;
- Delphi/VCL metadata extraction adapters;
- raw disassembly/xref adapter;
- Portmon parser/index adapter;
- target/frontier schema;
- receipt schema;
- evidence graph schema.

Initial high-information roots include AutoCal form/classes/actions plus known AutoCal serial/state labels only as search seeds, never as expected answers.

**Stop condition:** tooling emits narrative-only conclusions without address/byte/xref provenance.

## Phase 2 — Battle-royale vertical slice

Before scaling, prove one end-to-end target with at least three independent engines.

The slice must demonstrate:
- canonical hash verification;
- one AutoCal anchor independently detected;
- reconciler graph edge;
- deliberate `ESCALATE`;
- automatic next-wave dispatch;
- deliberate contradiction -> challenger target;
- duplicate attempt suppression;
- `BROKEN` analyzer cannot produce a fact.

**Stop condition:** no 256-lane wave until this slice passes remotely.

## Phase 3 — Full frontier expansion

Launch adaptive waves, each with up to 256 lanes.

Planner priority:
1. DFM/VCL/classes/VMT/RTTI;
2. events/actions;
3. fields/globals;
4. producer/consumer chains;
5. serial bridge and objects;
6. state machine;
7. drawing/repaint;
8. scheduler/timing;
9. indirect/unresolved edges.

Every material target is analyzed by heterogeneous engines. Disagreement creates a challenger, not a vote.

## Phase 4 — Recursive closure

Reconciler outputs:
- claims;
- nodes;
- edges;
- contradictions;
- coverage;
- `frontier-next`.

If frontier is non-empty, dispatch the next wave automatically with prior evidence and a different method.

No terminal `UNKNOWN`.

## Phase 5 — Final proof

Atlas is complete only when:
- frontier is empty;
- BROKEN=0;
- unresolved contradictions=0;
- all applicable UI/state/protocol/consumer/render/reachability gates pass;
- final graph is reproducible from canonical inputs on a fresh workflow run.

## Verification ladder

After each implementation slice:
1. focused unit/fixture test;
2. schema validation;
3. deterministic local-in-runner replay;
4. workflow dry/small matrix;
5. vertical slice;
6. full wave;
7. fresh final verification on the final remote SHA.

## Safety/invariants

- product app code remains untouched;
- no APK generation;
- no physical ECU writes;
- third-party RE tools pinned;
- least-privilege workflow permissions;
- target binary is never executed outside isolated analysis;
- claims require reproducible evidence;
- remote repository/run artifacts are authority.
