# OMEGAS Verde — Product Engineering, MAXDEBUG, AutoCal Refactor and Global Consolidation Design

**Date:** 2026-09-20  
**Repository:** `viluadmcontas2-dot/OMEGAS-V8.2`  
**Authorized branch:** `OmegasVerde`  
**Authoritative baseline at spec creation:** `8344643c644f6b388adabdd31cf7dfb2b6cfed55`  
**Governance:** REPO-FIRST + GITHUB-ISSUES-BASED. No Linear. No Notion as a parallel plan. No new branch/worktree/checkout. No APK without explicit owner authorization.

## 1. Purpose

This design consolidates the owner’s executive continuation into one implementation authority for the next OMEGAS Verde cycle.

The product goal is not “make tests green” or “make AutoCal prettier.” The goal is an OMEGAS Verde that is simple and obvious in the car, while preserving strict native/protocol/scientific authority underneath.

The final product must:

- make AutoCal immediately understandable at 1280×720;
- show the native Gasoline × GNV reference as the dominant visual surface when the ECU has valid data;
- never reduce AutoCal to a floating live cursor in an empty rectangle;
- detect and surface meaningful ECU changes without dumb USB polling;
- distinguish acquired zones, mature regions and physically correlated regions;
- expose the existing GNV level producer to a real HMI consumer without inventing semantics;
- make AutoCal start/pause one-touch while preserving USB/session/exclusion/ACK/readback/receipt protections;
- preserve destructive reset review and all Map K / Curve K safety;
- prove telemetry coherence instead of correcting perceived lag by cosmetic filtering;
- stabilize Suggestions and Difference semantically, not merely visually;
- prove Map K physical indexing/labels;
- close producer/consumer graphs and remove only dead-proven legacy;
- finish with independent integrated verification and explicit physical-test limitations.

## 2. Authority and evidence hierarchy

### 2.1 Runtime and source authority

1. GitHub remote `OmegasVerde` is the only source authority.
2. AgentRed/MMMACHINE/local runtimes are ephemeral executors/caches.
3. Every production write begins from a fresh remote reconciliation.
4. Concurrent writers to the same write surface are forbidden.
5. Branch ref movement is serialized and performed only after integrated verification for the slice being promoted.

### 2.2 Product/UX authority

Binding product requirements come from:

- the owner continuation in this mission;
- issues #57, #58, #59, #61 and their accepted owner observations;
- repository contracts and runtime evidence actually present on the authorized branch;
- protocol, fixture, Portmon and runtime evidence already recorded in GitHub issues/commits.

The authorized branch currently does **not** expose the complete source documents for the full `OME-STATE-HUMAN-UI`, `UIUX-CUSTOMROM` and `UIUX-OMEGADEV` blueprints. Their bindings are referenced in repository history/issues, but this design does not pretend to have read unavailable complete source material.

Therefore this implementation uses only verifiable principles already recorded by the owner and repo:

- understanding in about 2 seconds;
- one dominant job per screen;
- hierarchy before decoration;
- human model before RAW;
- compact normal state;
- explicit error + next action;
- large touch targets;
- 1280×720 canonical automotive viewport;
- technical detail under progressive disclosure;
- no ornamental information;
- runtime/click proof, not static-button existence.

### 2.3 Scientific authority

This mission must not create a new learning engine or move critical science into JavaScript.

Existing scientific decisions remain binding unless separately falsified with evidence:

- gasoline/GNV paired physical comparison remains primary where applicable;
- prediction is not physical evidence;
- Curve global precedes Map residual;
- Map spatial projection remains physically defined by the existing geometry unless a new falsification proves otherwise;
- no automatic K write is introduced;
- AutoCal visualization may use existing native/inferred analysis only as a view of already-established semantics, never as new ECU firmware truth.

## 3. Current baseline and experimental candidate

At spec creation, remote `OmegasVerde` is identical to `8344643c644f6b388adabdd31cf7dfb2b6cfed55`.

Known experimental AutoCal candidate outside the branch:

- `d802f8be7ee9a39263305006a4b6a8eba5eeb357`

The candidate is evidence and a possible implementation base, **not authority**. It must be reconciled against this spec and may be rejected or rebuilt in focused slices.

Known candidate defects already proved or strongly evidenced:

1. live telemetry participates in chart `xMin/xMax/yMin/yMax`, so the AGORA point can rescale/distort the native reference view;
2. the plot can enter a live-only state when reference points are absent;
3. X has sparse point labels but lacks a proper physical axis contract;
4. Y lacks a readable numeric scale/title contract;
5. `AutoMatchSnapshotAnalysis.getAnalysis()` and `gasEquivalentTimeMs` exist in native code but are not consumed by the current candidate JS HMI;
6. zone dots and persisted session “4/4” can derive from different authorities;
7. current automated 1280×720 checks are insufficient if they only prove chart size/path count rather than readable physical semantics.

These defects block promotion of the candidate.

## 4. Design scope and execution waves

This mission is one product program with serialized integration, but implementation is split into independent reviewable waves.

### Wave A — AutoCal product core

Covers:

- route/HMI clean-slate;
- curve authority/source selection;
- native monitor bootstrap and refresh;
- ECU change visibility/revision;
- start/pause operational action;
- zone/maturity/correlation semantics;
- GNV LEVELS consumer;
- AutoCal session summary;
- 1280×720 runtime interaction.

### Wave B — runtime integrity

Covers:

- USB permission lifecycle;
- telemetry atomicity/sequence/stale/backpressure;
- onboarding permissions/background/overlay UX;
- route and action-state reliability.

### Wave C — learning product semantics

Covers:

- suggestion publication lifecycle/stability;
- Difference instant vs stable vs trend;
- advisor → calibrationState → UI consumer graph;
- Map K physical indexing/labels;
- JavaScript/Kotlin geometry parity.

### Wave D — global consumer/dead-code audit

Covers:

- active/compatibility/test-only/dead-proven/unknown classification;
- buttons/handlers;
- bridges/callers;
- stores/producers;
- persistence compatibility;
- route reachability;
- removal only of DEAD-PROVEN code.

No wave may silently weaken another wave’s contracts.

## 5. AutoCal product architecture

### 5.1 One route, one dominant job

AutoCal is a top-level human task, not a Curve K subview.

The main route has three visual bands:

1. **Operational top bar**
2. **Dominant native reference plot**
3. **Compact secondary strip + progressive details**

No numeric form inputs are allowed on the primary surface.

### 5.2 Operational top bar

Must show, with stable human semantics:

- AutoCal state: CONNECTING / PAUSED / ACQUIRING / DISCONNECTED / ERROR;
- primary contextual CTA: Start acquisition **or** Pause acquisition;
- USB state, compact;
- current fuel;
- GNV LEVELS;
- AutoMatch count/revision only when it communicates a material native event.

The top bar must not show five competing actions.

Every action transitions in place through:

- received;
- sending/executing;
- confirming readback;
- confirmed;
- failed + next action.

### 5.3 Main plot

The plot is the dominant visual surface.

Axes:

- X = **Petrol Inj. (ms)**
- Y = **MAP (bar)**

The axis ranges and tick values are derived from the reference authority, not from styling and not from the live cursor.

The plot must include:

- numeric X ticks;
- numeric Y ticks;
- explicit X/Y titles and units;
- Gasoline reference;
- GNV reference;
- AGORA cursor only as an overlay;
- live Petrol Inj. and MAP values in text;
- optional prior revision/delta only if it remains legible.

The live cursor must **never** define or expand the reference-domain axes. If the current engine point is outside the visible native-domain range, represent this explicitly (edge marker/out-of-range state or safe fit behavior) without silently rescaling the reference.

### 5.4 No “floating cursor in empty square”

If no usable reference exists:

- do not render a semantic-looking empty coordinate plane with only AGORA;
- show an explicit empty state;
- explain which authority is missing/stale/partial;
- provide the next useful action;
- keep live values available separately if useful.

A valid native snapshot with usable vectors must deterministically produce visible reference curves.

Regression contract:

`valid native vectors -> visible Gasoline + GNV reference`

### 5.5 Reference authority and fallback

Native 30-point reference family is primary where valid:

- `PETR_INJ_TBP`
- `PETR_MNFLD_PRESS_RV`
- `GAS_MNFLD_PRESS_RV`

The protocol defines these as module-sized reference vectors; for the observed module V4 the expected shape is 30.

Physical conversion stays Kotlin-owned through `AutoCalScale`.

Current branch scale authority:

- injection: 500 counts/ms;
- MAP: 1000 counts/bar.

The 18-point acquisition buffers are not silently promoted to equivalent 30-point reference truth:

- `PETR_INJ_TBUF`
- `MNFLD_PRESS_BUF`
- `PETR_INJ_TBUF_GAS`
- `MNFLD_PRESS_BUF_GAS`

They may be used as an explicitly-labelled acquisition fallback when the native reference is unavailable, provided the UI states that this is acquisition evidence, not the consolidated native reference.

### 5.6 Same-pressure equivalence view

Existing native analysis already exposes a horizontal same-pressure interpretation:

- `AutoMatchSnapshotAnalysis`
- formula family `HORIZONTAL_SAME_PRESSURE`
- `gasEquivalentTimeMs`

This may be used for the didactic equivalence overlay only after a focused contract proves the UI consumes the native analysis correctly.

Constraints:

- do not call it OEM firmware-exact;
- do not replace raw native reference with inferred values;
- do not invent warping in JS;
- native reference remains inspectable;
- equivalence is a derived visual interpretation of existing Kotlin analysis.

## 6. Snapshot/monitor authority and refresh model

### 6.1 Problem to solve

Past audit proved a split:

- manual reader action could reach READY;
- UI continued reading native monitor state/snapshot;
- manual reader state was therefore invisible.

Separately, native monitor bootstrap could remain without a full usable snapshot until a later trigger.

### 6.2 Required model

Define one UI-facing AutoCal projection that resolves:

- native monitor status;
- native monitor snapshot;
- manual reader status;
- manual reader snapshot;
- freshness/session generation;
- semantic revision/source.

The UI must not choose a source ad hoc.

The projection must preserve source identity:

- NATIVE_MONITOR;
- MANUAL_READER;
- RECEIPT_BEFORE/AFTER;
- ACQUISITION_FALLBACK where applicable.

A manual reader completion may refresh the visible reference, but must not permanently mask a newer monitor snapshot.

### 6.3 Native monitor bootstrap

On a new physical USB generation, the monitor must obtain enough initial state to resolve:

- `AUTO_CAL_ENABLE`;
- relevant thresholds/counters;
- native reference vectors required for the main plot.

A monitor cannot stay indefinitely in a state where the user’s Start/Pause CTA is disabled solely because no first full snapshot was requested.

The bootstrap must remain read-only.

### 6.4 Native material-change detection

Use existing native material indicators before considering periodic broad reads:

- `NUM_AUTOMATCH_EXECUTED`;
- compact native status changes;
- vector/hash revision;
- relevant maturity/reference change signals;
- existing `markDataChanged`/session events where appropriate.

When material native state changes:

1. detect;
2. read the minimum required native group;
3. compute/store a semantic revision/hash;
4. publish UI refresh;
5. persist the material event in the SessionRecorder/ledger;
6. optionally retain prior reference for short visual comparison.

No dumb continuous full snapshot polling.

## 7. Zones, maturity and physical correlation

Three concepts are distinct and must remain distinct.

### 7.1 ECU acquired zones

Authority:

- `ACQUIRED_ZONES_PETROL`
- `ACQUIRED_ZONES_GAS`

The UI must represent the four actual zone positions/flags, not merely count N active values and fill the first N dots.

If the native vector is `[1,0,1,0]`, the UI must not draw `[1,1,0,0]`.

### 7.2 Mature regions

Authority:

- native counters;
- thresholds;
- maturity events.

Maturity is not automatically physical correlation.

### 7.3 OMEGAS physically correlated regions

Authority:

- correlation event/anchor evidence;
- session generation;
- temporal/telemetry validity;
- persisted semantic ledger.

It is legitimate for “ECU GNV zones 4/4” to coexist with “OMEGAS correlation 0/18” if OMEGAS did not physically witness a reproducible correlation.

The HMI must therefore say exactly what is known:

- ECU acquisition;
- maturity;
- OMEGAS-observed correlation.

Correlation must not dominate the main driving surface if it does not help the immediate operator task.

### 7.4 Persistent correlation lifecycle

The current one-crossing event model must be audited so a mature-but-uncorrelated band can later correlate when valid telemetry becomes available, without duplicating votes/anchors.

Required tests:

- bootstrap already above threshold;
- failed stale correlation followed by valid later correlation;
- persisted correlation survives future snapshots;
- retry does not duplicate anchor/vote;
- reconnect/session generation prevents cross-session false correlation.

## 8. GNV LEVELS consumer

### 8.1 Producer already exists

Current decoder extracts:

- MP48 telemetry payload byte 13 -> `levelRaw`;
- range is U8 0..255;
- telemetry JSON already exposes `level_raw`.

This is a real producer.

### 8.2 Existing percentage is not automatically product authority

`Mp48TelemetryScale.levelPercentage(raw)` currently maps the inverted 0..255 scale to a percentage and contains a domain comment about Landi/AEB sensors.

This design does **not** treat that comment alone as sufficient independent proof for a product-critical percentage.

Required HMI behavior for this mission:

- show the reliable raw LEVELS 0..255 value in the AutoCal top bar when telemetry is fresh;
- label it as raw/native if needed;
- preserve `level_raw`;
- percentage is allowed only after a focused evidence gate proves the mapping for this vehicle/sensor or an authoritative protocol source;
- do not claim `255 = 100%` or any other unproved interpretation.

Regression:

`valid level_raw produced -> AutoCal HMI renders LEVELS value`

## 9. Start/Pause AutoCal

### 9.1 User interaction

Start/Pause is a one-touch operational control.

No WebView review.
No Android AlertDialog.
No inherited “car stopped / RPM < 1200” requirement merely because another ECU write path uses it.

### 9.2 Engineering reality

`ENABLE_AUTO_CAL` is not a no-op. Existing action metadata states that enabling allows the ECU to continue native acquisition and may allow internal AutoMatch behavior affecting `MUL_ACT`.

Therefore the simplification is **interaction simplification**, not denial of ECU mutation.

### 9.3 Required protections

Keep:

- USB connected;
- valid physical session/generation;
- mutual exclusion with other calibration operations;
- exact known command bytes;
- protocol echo/ACK;
- readback of `AUTO_CAL_ENABLE`;
- before/after receipt sufficient to detect material native changes;
- inline status/error;
- SessionRecorder/ledger event.

The generic `CalibrationWriteSafetyPolicy` remains authoritative for Map K/Curve K and destructive calibration writes, but its driving/RPM policy is not blindly reused for operational AutoCal start/pause.

The operational toggle gets a dedicated, minimal precondition policy instead of bypassing all safety.

### 9.4 Destructive actions

`RESET_PETROL` and `RESET_GAS` remain separate and explicitly reviewed.

`RESET_ALL` remains prohibited.

No Map K / Curve K write is added to the AutoCal route.

## 10. Session and evidence UI

Primary HMI shows only a compact session state:

- current recording/persistence state;
- material error;
- last meaningful native revision/event.

Session history/export goes into a drawer/disclosure.

RAW field names, hashes, thresholds, command hex, Bxx identifiers and internal correlation reasons stay in technical details unless an error requires a human translation.

## 11. Telemetry coherence and runtime integrity

### 11.1 Atomicity objective

The UI must prove that the displayed RPM, Petrol Inj., MAP and fuel belong to a coherent runtime snapshot.

Trace and test:

capture -> decode -> canonical event/frame -> TelemetryStateStore -> RuntimeSnapshotBus -> bridge -> JS store -> scheduler -> renderer.

Record/compare:

- sequence/frame id;
- captured timestamp;
- published timestamp;
- UI age/stale state.

The implementation must explicitly falsify:

`MAP sequence != Petrol Inj. sequence`

before claiming coherence.

### 11.2 Backpressure/stale

Fast rendering may update AGORA without rebuilding the full AutoCal reference SVG every fast tick.

The reference layer changes only on a reference/revision change, viewport gesture or explicit fit/history action.

The live layer updates independently.

Stale telemetry is visible and cannot continue masquerading as current AGORA.

## 12. USB permission and onboarding

Audit and correct:

- `hasPermission`;
- `permissionPending`;
- device identity;
- attach/detach/re-enumeration;
- session generation;
- permission broadcast lifecycle;
- health/reconnect behavior.

A pending permission request must not be re-requested repeatedly.

Onboarding rules:

- request direct Android permissions early when the OS supports direct prompting;
- when Settings is required, deep-link to the exact settings surface with human explanation;
- do not reprompt already-granted permissions without evidence of a real identity/session change.

## 13. Suggestions lifecycle

The principal defect is not “confidence wording”; it is publication lifecycle.

Separate states:

1. observation;
2. evidence;
3. estimator;
4. candidate;
5. published stable suggestion;
6. applied/superseded/revalidating.

A single strong visit can become a promising observation/candidate, not a durable recommendation.

A published direction cannot flip immediately on a short new region/visit. Conflict first transitions to revalidating/conflicted state.

Published suggestion identity includes:

- revision;
- provenance;
- independent visits/sessions;
- stability;
- last material change;
- physical target/region;
- reason for readiness.

No automatic write.

## 14. Difference semantics

Difference presents three separate truths:

- AGORA: instantaneous;
- LOCAL STABLE: robust/current local estimate;
- TREND: direction over evidence history.

A new frame does not rewrite durable narrative.

UI distinguishes observed, estimated and uncertain values.

## 15. Map K physical indexing

There must be one physical geometry authority for:

- row/column;
- centers;
- edges;
- bin lookup;
- labels;
- JS projection;
- Kotlin storage;
- ECU address mapping.

Tests must include:

- exact centers;
- just-below/just-above edges;
- 4.5, 5.5, 6.0, 7.0 ms examples;
- min/max;
- JS ↔ Kotlin parity;
- UI label ↔ physical ECU cell parity.

Bilinear contribution from an observation to neighboring cells is not itself a labeling bug.

The UI must distinguish:

- physical cell address/center;
- evidence mean/contribution.

It must never label a physical 4.5 ms cell as if its physical address were 5.5 ms merely because evidence near 5.5 contributed to it.

## 16. Producer/consumer and legacy classification

Every suspicious component is traced through:

producer -> interface/API -> store -> bridge -> caller -> route -> renderer -> tests -> persistence/dynamic/reflection/manifest compatibility.

Classifications:

- ACTIVE
- COMPATIBILITY
- TEST-ONLY
- DEAD-PROVEN
- UNKNOWN

Only DEAD-PROVEN is removed.

After each removal:

- focused tests;
- route smoke;
- bridge parity;
- handler audit;
- persistence compatibility check where applicable.

Final integrated state must have:

- no dead button;
- no caller waiting on removed producer;
- no duplicate state authority without an explicit owner;
- no silent legacy fallback masking a broken primary path.

## 17. HMI layout contract — 1280×720

Canonical first viewport:

### Top

- AutoCal state;
- USB;
- fuel;
- LEVELS raw;
- AutoMatch/revision if material;
- Start/Pause CTA.

### Main

Large Gasoline × GNV plot with:

- physical axes;
- numeric scales;
- Gasoline reference;
- GNV reference;
- AGORA overlay;
- optional previous revision/delta without visual noise.

### Bottom compact

- 18-region maturity strip;
- session/persistence summary;
- technical-details affordance.

No forest of generic cards.
No center-screen numeric form controls.
No critical CTA hidden behind scroll.
No console-like primary surface.

## 18. State coverage

Runtime HMI tests must cover at minimum:

- USB disconnected;
- permission pending;
- connecting;
- paused;
- acquiring;
- complete reference snapshot;
- partial snapshot;
- no reference;
- valid reference + AGORA;
- ECU changes reference;
- AutoMatch count increases;
- session OK;
- persistence/mirror failure;
- stale telemetry;
- manual reader busy;
- start/pause success;
- start/pause ACK/readback failure;
- bridge unavailable.

## 19. Interaction coverage

Must prove:

- Start/Pause one touch;
- status feedback in the same visual locus;
- no Android modal for operational toggle after safety review;
- zoom/pan/fit;
- AGORA remains visual-only and does not mutate reference data;
- details disclosure;
- sessions/history/export;
- reset protection;
- every visible button has a reachable handler.

## 20. Verification contract

No completion claim from worker summaries.

Each production slice uses:

1. deterministic reproduction/failing test;
2. confirm RED for the intended reason;
3. smallest coherent change;
4. focused GREEN;
5. refactor only while green;
6. affected suites;
7. integrated verification;
8. independent review.

Final verification requires:

- fresh remote reconciliation;
- exact integrated diff;
- requirement -> evidence matrix;
- Python contracts;
- JS unit/runtime;
- JVM unit;
- affected Gradle tests;
- full `testDebugUnitTest`;
- `lintDebug`;
- fast checks;
- mutation/falsification for critical defects;
- 1280×720 DOM/click smoke;
- consumer graph;
- bridge parity;
- dead-button audit;
- no automatic K write;
- no protocol-byte regression;
- independent integrated review.

**APK generation is explicitly excluded from this mission until a new owner authorization.**

## 21. Parallel execution model

AgentRed is used as an execution accelerator, not as authority.

Parallelize aggressively for:

- static reachability;
- consumer graphs;
- independent test suites;
- protocol/docs searches;
- UI-state audits;
- mutation/falsification;
- read-only reviewers.

Serialize:

- edits to the same file/write surface;
- build-lock workloads according to actual capacity;
- branch ref movement;
- shared USB/hardware;
- contract changes;
- final integration.

Gemini is a reviewer/co-planner only. Use few high-signal calls, never API calls for grep, and never accept Gemini output without deterministic confirmation.

## 22. Write-surface ownership for implementation planning

The implementation plan after owner approval must assign exactly one writer at a time to each surface:

1. AutoCal native monitor/projection;
2. AutoCal action manager/bridge;
3. AutoCal JS model/renderer;
4. AutoCal CSS/HMI;
5. telemetry canonical frame/store;
6. USB permission lifecycle;
7. suggestion publication state;
8. Difference projection;
9. Map K physical geometry;
10. legacy removal/integration.

Readers/reviewers may fan out; writers do not overlap.

## 23. Required acceptance matrix

The final program is not complete until fresh evidence covers all of the following:

1. AutoCal is structurally refactored, not cosmetically patched.
2. Valid native vectors produce visible Gasoline/GNV reference.
3. AGORA maps to the correct physical surface and does not rescale native reference.
4. Material ECU reference change becomes visible with revision/provenance.
5. Start/Pause is low-friction and readback-proven.
6. Zones/maturity/correlation are semantically distinct and consistent.
7. LEVELS raw has a real HMI consumer.
8. USB permission does not reprompt without cause.
9. Telemetry frame coherence is proven.
10. Suggestions have a durable publication lifecycle.
11. Difference separates instant/stable/trend.
12. Map K physical labels/indexing are correct.
13. Producer/consumer graph is closed.
14. Dead-proven legacy may be removed; unknown/compatibility code is preserved.
15. No critical science is moved to JS for convenience.
16. No automatic K write exists.
17. 1280×720 HMI passes functional/runtime criteria.
18. Final suites pass on the final remote SHA.
19. Independent integrated code/product review is complete.
20. Physical vehicle/ECU checks not performed are explicitly reported.

## 24. Deliberate non-goals

This design does not authorize:

- APK generation;
- a new scientific learning engine;
- automatic Map K or Curve K writes;
- RESET_ALL;
- manual AutoMatch exposure;
- a new branch/worktree;
- Notion/Linear control planes;
- unproved LEVELS percentage semantics;
- unproved OEM-exact AutoMatch firmware claims;
- replacing native Kotlin authority with JavaScript calculation;
- broad legacy deletion based only on grep.

## 25. Current pre-implementation findings to carry into TDD

The implementation plan must convert these into explicit RED→GREEN tasks rather than lose them:

- candidate AGORA contaminates chart ranges;
- candidate can render live-only plot;
- candidate lacks complete physical axis semantics;
- candidate does not consume existing same-pressure analysis;
- persisted zone narrative and current dots can diverge;
- native reference visibility must be proven from snapshot chain;
- monitor bootstrap/source selection requires explicit integration tests;
- operational toggle must separate user friction from ECU mutation semantics;
- LEVELS producer exists but HMI consumer is absent;
- percentage mapping requires its own evidence gate;
- prior tests that only count SVG paths or static button strings are insufficient as product proof.

## 26. Approval boundary

This spec is the single architecture/product approval gate for the mission.

After owner approval, the next artifact is one dependency-ordered implementation plan with explicit RED→GREEN tasks, write-surface ownership, AgentRed fan-out, integration gates and independent review.

No production implementation should be promoted into `OmegasVerde` before this spec is approved.
