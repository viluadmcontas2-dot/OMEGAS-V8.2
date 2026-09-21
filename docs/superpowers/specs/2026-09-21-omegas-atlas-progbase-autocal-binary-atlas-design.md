# OMEGAS ATLAS — ProgBase AutoCal Binary Atlas Design

**Date:** 2026-09-21  
**Branch:** `OmegasAtlas`  
**Tracking issue:** #97  
**Status:** design approved in conversation; implementation gated on review of this written spec.

## 1. Mission

Reconstruct the reachable **Auto Calibration** subsystem of the authoritative ProgBase executable as an evidence graph, not as a collection of notes.

The Atlas must answer, with reproducible evidence:

- what every AutoCal control/event does;
- which native Delphi/VCL classes, VMTs, RTTI entries, fields and globals participate;
- which functions call or mutate which objects;
- which serial reads/writes correspond to which actions and states;
- who produces, transforms and consumes every curve, point, band, label and counter;
- how scheduling, refresh and repaint happen;
- where the ECU mutates state independently of the host;
- which observed Portmon transactions correspond to which binary paths.

The Atlas is not an OMEGAS implementation project. OMEGAS Verde/Amarelo may supply hypotheses or reusable evidence, but never define ProgBase truth.

## 2. Canonical authority

### 2.1 Primary binary

Canonical executable:

`ProgBase.exe`

Acquisition source currently verified read-only on MMMACHINE:

`G:\Meu Drive\OMEGAS\Copy of ProgBase (3).exe`

Properties verified on 2026-09-21:

- size: `12,643,840` bytes;
- SHA-256: `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`.

The local machine is only an acquisition transport. The hash-bound remote artifact becomes authority after materialization.

### 2.2 Raw dynamic evidence

Authoritative captures include:

- `PortmonAUTOCAL (1).LOG`;
- `PortmonLOGNOVO.LOG`.

Every raw or compact derived artifact must preserve the source hash and derivation recipe.

### 2.3 Authority order

1. hash-matched canonical ProgBase binary;
2. hash-matched raw Portmon captures;
3. deterministic derived artifacts generated from 1/2;
4. independent cross-tool agreement tied to addresses/xrefs/bytes;
5. historical Verde/Amarelo evidence as leads only;
6. public documentation as contextual hypothesis only.

No OMEGAS implementation formula or label may override binary/raw evidence.

## 3. Scope

The target is the reachable AutoCal subgraph rooted in all of the following when present:

- `TAutoCalDM`;
- `TAutoCalUI`;
- Auto Calibration form/resource/DFM;
- actions and event handlers containing AutoCal/AutoMatch/reset/finish/enable semantics;
- state variables and functions for acquisition/drawing;
- `PETR_INJ_TBP`;
- `MNFLD_PRESS_THD`;
- `PETR_MNFLD_PRESS_RV`;
- `GAS_MNFLD_PRESS_RV`;
- `MUL_ACT`;
- `AUTO_CAL_ENABLE`;
- AutoMatch execution counter(s);
- acquired-zone vectors;
- live telemetry used by the AutoCal form;
- serial objects `0x014A..0x018E` that are reachable from AutoCal;
- render consumers: petrol/gas curves, live points, current band, zones, labels, counters, K line and repaint/update paths.

Reachability expands transitively. If a dependency is necessary to classify an in-scope node, that dependency becomes in scope automatically.

## 4. Explicit non-goals

During Atlas reconstruction:

- do not modify OMEGAS product behavior;
- do not redesign the AutoCal UX;
- do not apply CUSTOMROM/Omegas Dev visual translation yet;
- do not generate APKs;
- do not treat Verde or Amarelo as a source of native truth;
- do not write to a real ECU;
- do not infer a semantic label merely because the current OMEGAS uses that label.

## 5. Core completeness invariant

`UNKNOWN` is forbidden as a terminal result.

Allowed claim states are:

- `PROVEN`: direct reproducible evidence establishes the claim;
- `REFUTED`: direct reproducible evidence contradicts the claim;
- `ESCALATE`: evidence is insufficient; this claim must be returned to the investigation frontier;
- `BROKEN`: the investigation harness failed and must be repaired/re-run.

`ESCALATE` is a queue state, never a completion state.

The campaign may claim completion only when the reachable AutoCal graph contains no `ESCALATE` or `BROKEN` items.

If evidence is genuinely unavailable, the campaign remains open and red. It must not silently convert that condition into a guessed fact.

## 6. Why this is not “256 random jobs”

GitHub Actions is the compute fabric. A matrix wave may contain up to 256 investigator jobs, but parallelism is useful only when lanes are independent.

The farm therefore works as a graph search:

1. build a deterministic seed atlas;
2. generate a frontier of concrete targets;
3. assign each target to independent analyzers;
4. reconcile claims centrally;
5. spawn dependency and challenger targets;
6. repeat until graph closure.

Each target has an identity such as:

- `function:0x00512280`;
- `vmt:TAutoCalUI`;
- `event:ActionAutoMatchExecute`;
- `field:TAutoCalDM+0xNN`;
- `serial:0x0161`;
- `consumer:PetrolCurve`;
- `resource:AutoCalForm.control.X`;
- `state:state_draw_gas_petrol_curve`.

Jobs never receive another investigator's conclusion as expected truth. They may receive only raw evidence, target identity and previously proven dependencies.

## 7. Multi-engine analysis stack

The Atlas deliberately uses different engines because correlated tool failure is a major risk.

### A. Delphi metadata / VCL recovery

Primary:

- `undelphi` pinned to an exact upstream commit;
- Ghidra Delphi helpers such as Drtti / DelphiReSym where compatible;
- direct PE resource/DFM extraction.

Required outputs:

- compiler/toolchain signals;
- RTTI;
- class hierarchy;
- VMTs;
- published fields/properties/methods;
- DFM/form components;
- event-handler bindings;
- form/resource xrefs.

### B. Generic static disassembly

Independent engines:

- Ghidra headless;
- Rizin or radare2 headless;
- Capstone + LIEF/pefile custom extractors.

Required outputs:

- function boundaries;
- basic blocks;
- call edges;
- data xrefs;
- string xrefs;
- imports;
- constants;
- read/write locations;
- control-flow strongly connected components.

### C. Deep control/data-flow escalation

When ordinary xrefs cannot close a claim:

- Ghidra decompiler/p-code slicing;
- angr or equivalent symbolic/data-flow analysis where useful;
- backward slices from a consumer;
- forward slices from a producer;
- predecessor/successor expansion through indirect calls;
- signature matching for Delphi/VCL/RTL functions.

### D. Dynamic escalation

Only after static evidence identifies a concrete question.

Preferred remote-reproducible experiments:

- run the hash-matched binary in an isolated Wine/Xvfb environment if compatible;
- trace WinAPI/serial/GDI calls without a real ECU;
- optionally map a synthetic COM/PTY replay endpoint backed by recorded responses;
- instrument only for observation;
- record screenshots/traces as artifacts tied to the binary hash.

A dynamic experiment never writes to a physical ECU.

## 8. Battle-royale assignment

Each material target should receive at least three independent perspectives when practical:

- one metadata/Delphi-specific analyzer;
- one generic static analyzer;
- one challenger using a different representation or evidence source.

Example:

`TAutoCalUI.PaintCurve`

Lane A:
DFM/VMT/RTTI -> handler identity and class layout.

Lane B:
Ghidra -> decompiled call/data flow.

Lane C:
Rizin/Capstone -> independent xrefs/basic-block/call confirmation.

If A/B/C disagree, the reconciler emits a new `ESCALATE` target, never a majority vote.

Tie-break examples:

- raw instruction-byte verification;
- alternative decompiler;
- upstream/downstream slicing;
- Portmon temporal alignment;
- dynamic WinAPI/GDI trace;
- dependency expansion.

## 9. Wave architecture

### 9.1 Seed

A non-matrix `seed` job:

- verifies artifact hashes;
- extracts PE headers/resources;
- runs Delphi metadata discovery;
- builds initial function/VMT/resource/string indices;
- identifies all AutoCal roots;
- creates `frontier.json`.

### 9.2 Investigator matrix

A wave contains up to 256 independent lanes.

The planner orders high-information targets first:

1. AutoCal form/DFM/VMT roots;
2. event handlers;
3. fields/globals;
4. serial bridge/dispatcher;
5. producers/consumers;
6. draw/repaint;
7. scheduling/state machine;
8. unresolved indirect edges.

No artificial `max-parallel` cap should be imposed unless observed runner contention proves necessary; GitHub controls actual available concurrency.

### 9.3 Reconciler

The reconciler consumes every receipt and creates:

- `claims.jsonl`;
- `nodes.jsonl`;
- `edges.jsonl`;
- `contradictions.json`;
- `frontier-next.json`;
- human-readable report;
- machine-readable coverage metrics.

A claim is promoted to `PROVEN` only from direct evidence or independent evidence that can be reproduced from the canonical inputs.

### 9.4 Automatic continuation

If `frontier-next.json` is non-empty:

- the current workflow is not considered campaign-complete;
- a next-wave workflow is dispatched automatically;
- it receives the previous run ID and downloads its aggregate artifacts;
- each escalated target carries its evidence history and attempted engines;
- the planner chooses a different escalation method rather than repeating an identical search.

No unresolved claim is dropped because a wave ended.

## 10. Novelty and loop control

“No UNKNOWN” must not become an infinite loop that repeats the same useless command.

For every target the orchestrator stores:

- engines already attempted;
- exact tool/version;
- search parameters;
- evidence hashes;
- dependencies discovered;
- prior claim fingerprints.

A repeated attempt with the same evidence/engine/parameters is forbidden.

When a target does not advance, the escalation ladder changes method:

1. metadata and direct symbol lookup;
2. xref expansion;
3. decompiler/p-code analysis;
4. data-flow slicing;
5. indirect-call/VMT resolution;
6. neighboring-function/SCC expansion;
7. raw-byte/manual-pattern verifier;
8. Portmon alignment;
9. dynamic isolated observation;
10. synthetic replay experiment.

If every applicable method reaches a fixpoint without evidence, the campaign must fail closed as an evidence-gap, stay open in #97 and request a new oracle/experiment. It still cannot claim the Atlas complete.

## 11. Receipt contract

Every investigator writes JSON and Markdown.

Minimum JSON shape:

```json
{
  "schema": "omegas.atlas.receipt.v1",
  "campaign": "progbase-autocal",
  "binary_sha256": "8a2d297c...",
  "target_id": "function:0x00512280",
  "engine": "ghidra",
  "engine_version": "...",
  "status": "PROVEN",
  "claims": [],
  "evidence": [],
  "dependencies": [],
  "new_targets": [],
  "contradictions": [],
  "attempt_fingerprint": "..."
}
```

Evidence items must include enough detail to reproduce the claim, such as:

- RVA/VA;
- file offset;
- instruction bytes;
- function/basic-block address;
- xref source/destination;
- class/VMT/member identity;
- resource/control/event identity;
- serial request/response bytes;
- Portmon event/index;
- input artifact hash.

Narrative-only conclusions are insufficient.

## 12. Evidence graph

Canonical graph layers:

1. `UI_CONTROL`
2. `EVENT_HANDLER`
3. `FUNCTION`
4. `CLASS/VMT`
5. `FIELD/GLOBAL`
6. `STATE`
7. `SERIAL_OBJECT`
8. `SERIAL_FRAME`
9. `ECU_OBSERVED_STATE`
10. `TRANSFORM`
11. `RENDER_CONSUMER`

Core edge kinds:

- `BINDS_TO`
- `CALLS`
- `READS`
- `WRITES`
- `DISPATCHES`
- `SERIALIZES`
- `REQUESTS`
- `RESPONDS_WITH`
- `MUTATES`
- `TRANSFORMS`
- `FEEDS`
- `REPAINTS`
- `SCHEDULES`
- `CORRELATES_WITH`

Every edge records evidence and confidence state.

## 13. AutoCal closure gates

The campaign cannot close until all applicable gates pass.

### UI/VCL gate

- every AutoCal control is identified;
- every event is bound to a handler or direct evidence proves no handler;
- display labels/controls and their backing state are mapped.

### State-machine gate

- enable/disable/start/finish/reset/AutoMatch paths are mapped;
- state transitions and guards are mapped;
- automatic ECU-side transitions are distinguished from host writes.

### Protocol gate

- every AutoCal serial object observed in scope has request/response shape;
- writes are distinguished from reads;
- checksums/status/length semantics are known;
- action frames are linked to binary handlers, not guessed from arithmetic.

### Consumer gate

For every displayed AutoCal datum:

`producer -> storage -> transform -> consumer -> render/update trigger`

must exist.

### Render gate

- petrol curve;
- gas curve;
- petrol/gas live point(s);
- current band;
- acquired zones;
- counters;
- K/multiplier line where applicable;
- refresh/repaint cadence.

### Contradiction gate

Existing conflicts from Verde/Amarelo are imported only as challenge cases. No conflict may survive into the final Atlas.

### Reachability gate

Every function/data object reachable from the chosen AutoCal roots and materially connected to AutoCal must have a classification or explicit proof that the edge is not part of the AutoCal path.

## 14. Reproducibility and supply-chain controls

- pin third-party tools by exact release/commit;
- verify downloaded tool artifacts by checksum when published;
- cache toolchains by immutable key;
- never execute instructions embedded in the target binary/resources;
- run the target executable only in an isolated analysis environment;
- use least-privilege workflow permissions;
- no repository secrets in fork-visible jobs;
- artifacts include source SHA and tool manifest;
- `fail-fast: false` for investigator matrices;
- harness failures are `BROKEN`, not scientific findings.

## 15. Repository layout

Planned Atlas-only surfaces:

```text
atlas/
  originals/
    progbase/
  manifests/
  schemas/
  tools/
  seeds/
  graph/
  reports/
  fixtures/
  campaigns/progbase-autocal/

.github/workflows/
  atlas-seed.yml
  atlas-wave.yml
  atlas-verify.yml

docs/
  superpowers/specs/...
  superpowers/plans/...
  omegas-atlas/
```

The product app directories are read-only for the Atlas campaign.

## 16. Binary materialization policy

The user explicitly authorizes committing the canonical ProgBase binary to the repository.

Before upload:

1. resolve the source path;
2. verify size and SHA-256;
3. ensure it matches the canonical expected hash;
4. preserve original filename in provenance;
5. store a normalized repository filename and manifest;
6. verify the remote blob after commit.

If GitHub's normal blob limits are exceeded, use a remote Git/LFS or equivalent GitHub-hosted artifact strategy rather than silently changing the file.

## 17. Acceptance criteria for infrastructure

Before launching the full campaign, prove the farm with a thin deterministic slice:

- seed job verifies canonical binary hash;
- at least three engines independently identify one known AutoCal anchor;
- reconciler produces a graph edge from evidence;
- an intentionally unresolved dependency produces `ESCALATE`;
- next-wave dispatch occurs automatically;
- an intentionally contradictory claim produces challenger work;
- duplicate-attempt detection prevents a useless identical retry;
- broken analyzer yields `BROKEN` and cannot be promoted to fact;
- artifacts remain retrievable by run/SHA.

Only after this vertical slice passes is a 256-lane wave justified.

## 18. Final deliverables

The final Atlas package must contain:

- machine-readable AutoCal graph;
- address/function/class/VMT index;
- field/global producer-consumer table;
- serial protocol table;
- state machine;
- render pipeline;
- scheduling/cadence model;
- action paths;
- contradiction ledger with resolutions;
- raw-evidence provenance manifest;
- reproducible extraction scripts;
- human-readable reconstruction report;
- coverage report proving zero unresolved frontier.

Only then can a later OMEGAS task translate the recovered behavior through the CUSTOMROM/Omegas Dev blueprint.
