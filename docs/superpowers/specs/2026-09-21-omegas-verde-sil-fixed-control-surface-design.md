# OMEGAS Verde SIL — Fixed Control Surface + Session-Invariant Learning Design

**Date:** 2026-09-21  
**Repository:** `viluadmcontas2-dot/OMEGAS-V8.2`  
**Scientific branch:** `work/omegas-verde-sil`  
**Issue:** #66  
**Purpose:** finish and falsify the learning method in SIL before porting proven slices to the then-current `OmegasVerde`.

## 1. Intent

The Map K is not a collection of 144 independent buckets whose coordinates drift toward the mean of observations that happened to fall nearby.

It is a **continuous correction surface represented by 144 immutable control points**.

Physical evidence is continuous and may fall between control points. The evidence is distributed to the bounding control points by interpolation. The control-point coordinates never change.

This design also makes USB/session boundaries scientifically irrelevant. A reconnect is an operational event, not independent physical evidence.

The target outcome is a Difference/Suggestion system that cannot produce large adjacent contradictions merely because observations were binned independently, a USB session changed, or stale evidence from a previous calibration revision remains active.

## 2. Scope

This design governs the scientific SIL implementation and the later porting contract for:

- canonical gasoline reference;
- physical visit independence;
- Curve K global-error estimation;
- Map K local residual estimation;
- interpolation onto the 144 fixed Map K control points;
- robust cell/node stability;
- AutoCal evidence fusion;
- calibration-revision invalidation/revalidation;
- Difference and Suggestion semantics;
- cold-start-on-GNV replay.

It does not authorize production changes to `OmegasVerde` yet.

## 3. Non-goals

- no APK generation;
- no automatic ECU or Map K write;
- no wholesale merge from the old SIL branch into current Verde;
- no session count as a confidence signal;
- no conversion of the high-resolution canonical gasoline surface into 144 gasoline buckets;
- no assumption that a SIL result equals physical-vehicle validation;
- no smoothing that hides a real localized defect merely to make the map look visually smooth.

## 4. Physical contracts

### 4.1 Fixed Map K axes

The operational Map K has 144 editable control points:

**RPM**
`[850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500]`

**Petrol Inj. ms**
`[2.0, 2.5, 3.0, 3.5, 4.5, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0]`

For control point `N[i,j]`:

`N[i,j].controlRpm = RPM[j]`

`N[i,j].controlPetrolMs = PETROL_MS[i]`

These two values are immutable.

A node may contain evidence whose weighted observed center is 5.3 ms while its control coordinate remains 4.5 ms. Those are different fields with different semantics.

### 4.2 Continuous evidence position

A CNG physical observation has at minimum:

`E = (rpm, mapBar, petrolObservedMs, expectedPetrolMs, quality, visitKey, calibrationRevision, collectedAt)`

Its Map K position is the **observed live petrol injection signal while on CNG** together with RPM:

`position(E) = (rpm, petrolObservedMs)`

This matches the existing Map K projection semantics and must be independently falsified before production porting.

The gasoline-equivalent expected time is used to compute the physical error, not to relabel the Map K address.

### 4.3 Bilinear basis

For an observation between RPM controls `R0,R1` and petrol-time controls `T0,T1`:

`u = (rpm - R0)/(R1 - R0)`

`v = (petrolObservedMs - T0)/(T1 - T0)`

The four weights are:

`w00=(1-u)(1-v)`

`w10=u(1-v)`

`w01=(1-u)v`

`w11=uv`

Required properties:

- all weights >= 0;
- weights sum to 1 within deterministic floating-point tolerance;
- only the bounding nodes receive weight;
- exact control coordinate gives 100% to that coordinate;
- edge observation gives weight to at most two nodes;
- interior observation gives weight to at most four nodes;
- weights vary continuously with the physical position;
- out-of-domain behavior is explicit, never silently relabeled.

The interpolation distributes **evidence contribution**, not control-point identity.

## 5. Session invariance

### 5.1 Session is metadata only

A USB/session identifier may exist for:

- provenance;
- logs;
- UI lifecycle;
- recorder organization;
- debugging.

It must not affect:

- visit independence;
- effective evidence count;
- confidence;
- stable center;
- canonical gasoline reference;
- Curve K estimate;
- Map K residual;
- suggestion magnitude or lifecycle.

### 5.2 Reconnect is not a new independent visit

A USB disconnect/reconnect must not itself create a new scientific vote.

After a continuity gap, the system must conservatively assume that independence is **unproven** until a physical exit and re-entry is observed under the visit rule.

Therefore an arbitrary replay split into one or many USB sessions must produce the same scientific result if the physical evidence stream is otherwise identical.

### 5.3 Known session leaks to remove before production port

Current Verde evidence already shows session-coupled behavior that violates this design:

- `MotorLearningMemory.startSession()` resets `activeVisit`;
- `AdaptivePetrolReference.promoteScale()` promotes persistent scale at a session boundary;
- per-session caps can make reconnect count affect the convergence path.

The SIL method must replace these boundaries with evidence-driven boundaries.

## 6. Canonical gasoline surface

### 6.1 Separate geometry

The canonical gasoline surface is high-resolution and answers:

`expectedPetrolMs = F_petrol(rpm, mapBar)`

It is **not** the Map K.

It may contain thousands of populated physical regions/cells because its job is to estimate the gasoline reference accurately over RPM × MAP.

### 6.2 Current corpus evidence

Current discovered corpus includes:

- 46,762 plausible deduplicated gasoline frames from OMEGAS JSONL sessions;
- 24 session/file labels, used only for provenance and holdout partitioning;
- 13,032 additional Portmon gasoline frames pending overlap reconciliation;
- thousands of populated RPM × MAP positions at fine resolution.

Session/file labels are allowed for cross-validation splits, but never as a confidence multiplier.

### 6.3 Canonical query

A query returns at minimum:

- `expectedPetrolMs`;
- robust spread/uncertainty;
- support;
- interpolation/extrapolation state;
- provenance/version/hash;
- confidence derived from physical support and dispersion, not session count.

Candidate hierarchy remains:

1. high-resolution local canonical support;
2. coarser local support;
3. bounded interpolation;
4. adaptive/global fallback;
5. unavailable/abstain outside supported behavior.

Live real gasoline remains capable of refining or overriding the historical prior when supported by physical evidence.

## 7. Physical error decomposition

### 7.1 Total error

For a CNG observation:

`t_ref = F_petrol(rpm, mapBar)`

`t_obs = petrolObservedMs`

Define the signed physical ratio consistently with the existing advisor contract and lock that sign by tests.

Conceptually:

`e_total = relation(t_obs, t_ref)`

The exact production sign convention must have a single source of truth; SIL tests must prevent UI/advisor inversions.

### 7.2 Curve K first

The first decomposition estimates the **global trend** as a function of gasoline-equivalent injection time:

`g = G_curve(t_ref)`

This represents the error family that Curve K can explain globally.

AutoCal same-pressure equivalence may contribute independent evidence to this global layer when its provenance and calibration revision are valid.

### 7.3 Map K residual second

Only the remainder reaches the local map:

`e_local = e_total - g`

The local residual is projected onto the fixed Map K basis using:

`(rpm, t_obs)`

This prevents Curve K and Map K from correcting the same global error twice.

### 7.4 Synthetic decomposition invariants

- Pure global synthetic bias => Curve K captures it; Map K residual approaches zero.
- Pure local synthetic defect after global removal => residual remains spatially localized.
- Global + local synthetic case => recovered components match their respective families within the deterministic test tolerance.

## 8. Robust node state

Each Map K control point stores immutable identity plus robust evidence state.

### 8.1 Identity

- `row`
- `column`
- `controlRpm`
- `controlPetrolMs`

These never come from weighted evidence means.

### 8.2 Evidence diagnostics

A node may expose:

- weighted observed RPM center;
- weighted observed petrol-ms center;
- weighted MAP center;
- robust residual center;
- residual MAD/spread;
- effective physical visits;
- quality-weighted support;
- directional consensus;
- source/provenance summary;
- AutoCal agreement/conflict;
- calibration revision;
- state: `NO_EVIDENCE | LEARNING | CONSOLIDATED | REVALIDATING | CONFLICT`.

Observed centers are diagnostics only.

### 8.3 Robust estimator

The primary stable local value is a robust weighted center, with MAD/robust spread and effective visit support.

No single frame and no single reconnect-created boundary can move a consolidated node to a new extreme.

Repeated coherent physical evidence can establish a new trend.

## 9. Spatial consistency without fake smoothing

Neighbor information is used as a **publication/confidence check**, not to overwrite valid local evidence.

A node that sharply disagrees with its spatial neighborhood is not automatically forced to the neighborhood mean.

Instead:

- if local independent support is weak, state becomes/stays `REVALIDATING`;
- stronger independent support is required to publish a spatially isolated extreme;
- if repeated physical evidence proves the local discontinuity, it may become `CONSOLIDATED`.

This allows real localized faults/corrections while preventing one noisy bucket from publishing a +200% style spike next to stable ±2% neighbors.

## 10. Calibration revision / AutoCal lifecycle

### 10.1 Derived GNV evidence is revision-bound

Gasoline reference is permanent across GNV calibration changes.

GNV comparisons, local residuals, Difference state, confidence derived from those comparisons, and Suggestions are bound to the calibration revision that produced them.

### 10.2 Material native AutoCal change

If native AutoCal materially changes the effective calibration/reference used by GNV:

- create/observe a new calibration revision;
- preserve gasoline evidence;
- preserve old GNV evidence only as history;
- exclude old-revision GNV residuals from current Difference/Suggestion;
- enter `LEARNING` or `REVALIDATING` until current-revision evidence exists.

The UI must never show stale pre-AutoCal local differences as if they describe the post-AutoCal state.

### 10.3 Why this matters

A physically well-aligned AutoCal result must not coexist indefinitely with old local extremes merely because the app's prior cell evidence is still cached.

If the new calibration is aligned, current-revision evidence should converge toward neutral.

## 11. AutoCal scientific role

AutoCal is a first-class evidence source, not the sole truth.

It can provide:

- native Gasoline/GNV reference curves;
- same-pressure equivalent time;
- material calibration/reference revision;
- maturity/acquisition status;
- global-equivalence evidence.

It can provide **local Map K** evidence only after physical correlation to runtime telemetry establishes RPM/MAP/time provenance.

Every AutoCal-derived evidence item must carry:

- source;
- snapshot/revision hash;
- USB generation where relevant;
- calibration revision;
- capture time/window;
- physical correlation state;
- independence/fingerprint key.

The same underlying physical event must not be counted twice merely because it is visible through two software paths.

## 12. Difference semantics

The Difference surface must distinguish:

- **Agora**: current/raw observation, explicitly volatile;
- **Estável**: consolidated current-revision residual field;
- **Tendência recente**: candidate deviation under revalidation.

The 144 displayed nodes always use their immutable axis labels.

A node with no current-revision evidence displays no stable conclusion rather than inheriting an old percentage.

## 13. Suggestion semantics

Suggestion consumes the robust current-revision field.

A Map K suggestion requires:

- global Curve K component removed first;
- current-revision residual support;
- stable or explicitly allowed state;
- sufficient effective physical visits;
- bounded MAD/spread;
- directional consensus;
- no unresolved high-risk AutoCal/source conflict;
- node identity from the immutable axes.

A single contradictory visit cannot publish an extreme correction.

No automatic write is introduced. Human review, ACK and readback remain outside this SIL method and mandatory for later production integration.

## 14. Required SIL acceptance tests

### T1 — Session split invariance
Replay identical physical evidence:
- once as one USB session;
- again split into many artificial USB sessions.

Canonical query outputs, stable errors, node support, confidence, Curve K result, Map K residuals and suggestions must be identical within deterministic numeric tolerance.

### T2 — Fixed 4.5/6.0 identity
Feed evidence at 4.7, 5.0, 5.3 and 5.7 ms.

The 4.5 and 6.0 nodes may receive different contribution weights, but:
- `controlPetrolMs` remains exactly 4.5 and 6.0;
- export/UI contract uses those values as addresses;
- observed mean is exposed under a separate diagnostic field.

### T3 — Exact node
Evidence exactly at `1850 RPM × 4.5 ms` gives weight 1.0 to that node and 0 elsewhere.

### T4 — Edge/interior
Test horizontal, vertical, diagonal/interior and corner positions. Weights remain bounded and sum to 1.

### T5 — Global then local
Synthetic global-only, local-only and mixed datasets prove decomposition ordering.

### T6 — Isolated spike
After a coherent consolidated field, inject one extreme contradictory visit. It may appear as recent/revalidation evidence but must not become an actionable extreme suggestion.

### T7 — Repeated real local defect
Repeated independent evidence at the same local area must eventually be able to prove a real discontinuity despite neighbor disagreement.

### T8 — AutoCal revision invalidation
Create current residual evidence, then apply a material AutoCal/calibration revision. Old local residuals must disappear from current Difference/Suggestion while gasoline reference survives.

### T9 — AutoCal aligned
With global AutoCal equivalence aligned and compatible current live evidence, the stable field converges toward neutral rather than retaining stale pre-revision extremes.

### T10 — Cold start directly on GNV
Load canonical gasoline with zero live gasoline frames in the runtime replay and prove:
`canonical reference -> live CNG observation -> total error -> global Curve K -> local Map K residual -> robust current-revision Difference -> manual-only suggestion`.

## 15. Real-corpus validation

The SIL must use both synthetic falsification and real recorded data.

Real-data work includes:

- canonical gasoline leave-out / holdout tests;
- GNV-only replay segments;
- current LOGNOVO/AUTOCAL/OMEGAS session corpus;
- Portmon as external or overlap-reconciled evidence, not blindly duplicated support;
- calibration-revision scenarios;
- adversarial spatial spikes;
- sparse-region abstention.

Metrics should include:

- reference MAE / median absolute percentage error / P90 where applicable;
- coverage;
- Map residual MAD;
- max adjacent-node discontinuity before and after robust publication gate;
- count of `CONSOLIDATED / REVALIDATING / CONFLICT / NO_EVIDENCE` nodes;
- actionable suggestion count;
- stale-revision contribution count, required to be zero for current state;
- session-split delta, required to be zero within tolerance.

## 16. Porting rule

The SIL branch is a laboratory, not a merge candidate.

When this method is proven:

1. fetch the then-current `OmegasVerde`;
2. build a porting matrix `scientific contract -> current Verde seam -> current test -> required patch`;
3. port only the smallest validated slices;
4. never merge the old SIL branch wholesale;
5. re-run all affected tests against the current architecture;
6. keep official branch and APK gates separate.

## 17. Done when

This scientific design is complete only when:

- session split no longer changes scientific output;
- reconnect alone cannot manufacture a new independent visit;
- all 144 control addresses remain immutable in every projection;
- evidence interpolation is continuous and conservative;
- Difference uses current-revision residuals after global removal;
- isolated spikes cannot publish extreme suggestions;
- repeated real local defects can still be proven;
- material AutoCal changes invalidate stale GNV-derived conclusions;
- canonical gasoline + GNV-only cold-start replay works;
- evidence and limitations are recorded in the SIL report;
- a porting matrix exists for the current official Verde.

No APK and no physical-validation claim are part of this definition of done.
