# OMEGAS Verde SIL Fixed-Control Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and falsify, in the SIL branch only, a session-invariant 144-node residual learning surface that uses canonical gasoline + AutoCal-informed global prior + robust bilinear local evidence, so sparse post-AutoCal observations cannot appear as stable extreme differences while real repeated local defects can still be learned.

**Architecture:** Keep the high-resolution gasoline reference separate from the 144-node Map K control surface. Each current-revision CNG visit produces a total physical error, a global Curve K component is removed first, and the remaining local residual is distributed bilinearly to immutable Map K nodes. Sparse node evidence is robustly aggregated and optionally shrunk toward a provenance-bound AutoCal/global neutral prior; the prior never marks a node consolidated by itself and repeated independent local evidence can override it.

**Tech Stack:** Python 3 SIL tooling + pytest/unittest-style deterministic tests; existing OMEGAS JSONL/Portmon replay corpus; Kotlin/Android production code is read-only evidence during this plan.

**Spec:** `docs/superpowers/specs/2026-09-21-omegas-verde-sil-fixed-control-surface-design.md`

## Global Constraints

- Scientific branch only: `work/omegas-verde-sil`.
- Do not modify `OmegasVerde`.
- No APK.
- No automatic ECU write.
- USB/session identity has zero scientific weight.
- Map K has exactly 144 immutable editable control points (12 RPM × 12 Petrol Inj. ms).
- A 4.5-ms node is always 4.5 ms; a 6.0-ms node is always 6.0 ms.
- Canonical gasoline is a separate high-resolution RPM×MAP reference surface.
- Curve K global component is removed before Map K local residual estimation.
- AutoCal is a prior/evidence source, not fabricated local truth.
- Raw one-visit evidence cannot be published as stable Difference or actionable Suggestion.
- Real repeated local evidence must be able to defeat the prior.
- All scientific claims require deterministic test/replay evidence.

## Review Focus

1. **USB reconnect during one physical dwell:** reconnect must not create an independent scientific visit or alter node confidence/output.
2. **Observation exactly on a control boundary:** interpolation must remain deterministic, nonnegative and sum to 1 without duplicate-node weight.
3. **Strong AutoCal prior + true local defect:** repeated local evidence must eventually override the prior rather than being smoothed away.
4. **Sparse edge-of-domain observation:** model must explicitly clamp/abstain according to the declared policy and never relabel the nearest node coordinate.
5. **Mixed global + local error:** global correction must not be double-counted in both Curve K and Map K residual.

---

### Task 1: Fixed 144-node geometry and interpolation contract

**Files:**
- Create: `tools/omegas-sil/fixed_control_surface.py`
- Create: `tools/omegas-sil/test_fixed_control_surface.py`

**Interfaces:**
- Consumes: fixed physical axes from the spec.
- Produces:
  - `RPM_BINS: tuple[float, ...]`
  - `PETROL_MS_BINS: tuple[float, ...]`
  - `ControlNode(row: int, column: int, control_rpm: float, control_petrol_ms: float)`
  - `BilinearWeight(row: int, column: int, weight: float)`
  - `control_node(row: int, column: int) -> ControlNode`
  - `bilinear_weights(rpm: float, petrol_ms: float) -> tuple[BilinearWeight, ...]`

- [ ] **Step 1: Write the failing geometry tests**

Add tests equivalent to:

```python
def test_map_has_exactly_144_fixed_control_nodes():
    nodes = [control_node(r, c) for r in range(12) for c in range(12)]
    assert len(nodes) == 144
    assert control_node(4, 2).control_petrol_ms == 4.5
    assert control_node(5, 2).control_petrol_ms == 6.0
    assert control_node(4, 2).control_rpm == 1850.0

def test_exact_node_receives_all_weight():
    weights = bilinear_weights(1850.0, 4.5)
    assert weights == (BilinearWeight(row=4, column=2, weight=1.0),)

def test_interior_observation_uses_only_four_bounding_nodes():
    weights = bilinear_weights(2100.0, 5.3)
    assert {(w.row, w.column) for w in weights} == {(4,2),(4,3),(5,2),(5,3)}
    assert abs(sum(w.weight for w in weights) - 1.0) < 1e-12
    assert all(w.weight > 0.0 for w in weights)

def test_interpolation_never_changes_node_identity():
    for value in (4.7, 5.0, 5.3, 5.7):
        bilinear_weights(1850.0, value)
        assert control_node(4, 2).control_petrol_ms == 4.5
        assert control_node(5, 2).control_petrol_ms == 6.0
```

Also test horizontal edge, vertical edge, corner, below-min and above-max declared clamp behavior.

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
python -m unittest tools/omegas-sil/test_fixed_control_surface.py -v
```

Expected: import/function failures because the module does not exist yet.

- [ ] **Step 3: Implement immutable geometry and bilinear interpolation**

Implement:
- exact axes from the spec;
- index-range validation;
- axis blend with deterministic clamping at endpoints;
- zero-weight candidate removal;
- duplicate-node merge when lower==upper on one axis;
- normalized final weights with an assertion/guard that total is within floating tolerance.

Do not add learning/confidence behavior in this task.

- [ ] **Step 4: Run tests and verify GREEN**

Run the same command.

Expected: all Task 1 tests pass.

- [ ] **Step 5: Commit**

Commit message:

`feat(sil): define fixed 144-node interpolation surface`

---

### Task 2: Session-invariant physical visit evidence

**Files:**
- Modify: `tools/omegas-sil/fixed_control_surface.py`
- Modify: `tools/omegas-sil/test_fixed_control_surface.py`

**Interfaces:**
- Consumes: `bilinear_weights()` from Task 1.
- Produces:
  - `VisitObservation(visit_key, revision, rpm, petrol_ms, residual_percent, quality, collected_at_ms, session_id='')`
  - `NodeContribution(row, column, visit_key, residual_percent, weight)`
  - `project_visit(observation: VisitObservation) -> tuple[NodeContribution, ...]`
  - `dedupe_visits(observations) -> tuple[VisitObservation, ...]`
  - session ID is metadata only and never enters equality/weight/confidence calculations.

- [ ] **Step 1: Write the failing session-invariance tests**

Add:

```python
def scientific_signature(observations):
    visits = dedupe_visits(observations)
    projected = [c for v in visits for c in project_visit(v)]
    return tuple(sorted((c.row, c.column, c.visit_key, round(c.residual_percent, 9), round(c.weight, 9)) for c in projected))

def test_session_split_does_not_change_scientific_projection():
    base = [
        VisitObservation('v1', 7, 1850, 4.5, 1.2, 0.9, 1000, 'USB-A'),
        VisitObservation('v2', 7, 2100, 5.3, -0.8, 0.8, 2000, 'USB-A'),
    ]
    split = [
        replace(base[0], session_id='USB-1'),
        replace(base[1], session_id='USB-99'),
    ]
    assert scientific_signature(base) == scientific_signature(split)

def test_same_visit_key_is_one_vote_even_if_usb_session_changes():
    observations = [
        VisitObservation('physical-v1', 7, 1850, 4.5, 2.0, 1.0, 1000, 'USB-A'),
        VisitObservation('physical-v1', 7, 1850, 4.5, 2.0, 1.0, 1001, 'USB-B'),
    ]
    assert len(dedupe_visits(observations)) == 1
```

Also test that distinct physical visit keys remain distinct even when session IDs match.

- [ ] **Step 2: Run tests and verify RED**

Run:

`python -m unittest tools/omegas-sil/test_fixed_control_surface.py -v`

Expected: missing visit/projection APIs.

- [ ] **Step 3: Implement visit model and session-invariant projection**

Rules:
- scientific identity is `(revision, visit_key)`;
- reconnect/session metadata never enters the dedupe key;
- per-node contribution weight = `quality * bilinear_weight`;
- validate quality in `[0,1]`;
- do not infer a new visit from session changes.

- [ ] **Step 4: Run tests and verify GREEN**

Expected: all Task 1+2 tests pass.

- [ ] **Step 5: Commit**

Commit message:

`feat(sil): make node evidence invariant to USB sessions`

---

### Task 3: Global-first residual decomposition and AutoCal-informed prior

**Files:**
- Modify: `tools/omegas-sil/fixed_control_surface.py`
- Modify: `tools/omegas-sil/test_fixed_control_surface.py`

**Interfaces:**
- Consumes: visit contributions from Task 2.
- Produces:
  - `GlobalPoint(petrol_ms: float, correction_percent: float, weight: float=1.0)`
  - `GlobalCurve(points: tuple[GlobalPoint, ...]).estimate(petrol_ms) -> float`
  - `AutoCalPrior(aligned: bool, median_abs_delta_percent: float, valid_points: int)`
  - `prior_strength(prior: AutoCalPrior) -> float`
  - `residual_percent(total_error_percent, global_error_percent) -> float`

AutoCal prior strength is bounded and cannot itself create a consolidated node.

Use this deterministic candidate rule for the first SIL implementation:

```python
if not prior.aligned or prior.valid_points < 6:
    return 0.0
if prior.median_abs_delta_percent <= 1.0:
    return 2.0
if prior.median_abs_delta_percent <= 2.5:
    return 1.0
return 0.5
```

The values are laboratory parameters to be falsified in Task 5; they are not production constants.

- [ ] **Step 1: Write failing decomposition tests**

Add:

```python
def test_pure_global_bias_leaves_zero_local_residual():
    curve = GlobalCurve((GlobalPoint(4.5, 8.0), GlobalPoint(6.0, 8.0)))
    assert residual_percent(8.0, curve.estimate(5.3)) == 0.0

def test_local_defect_survives_global_removal():
    curve = GlobalCurve((GlobalPoint(4.5, 8.0), GlobalPoint(6.0, 8.0)))
    assert residual_percent(13.0, curve.estimate(5.3)) == 5.0

def test_aligned_autocal_prior_is_bounded():
    assert prior_strength(AutoCalPrior(True, 0.7, 20)) == 2.0
    assert prior_strength(AutoCalPrior(False, 0.1, 30)) == 0.0
```

Also test interpolation between GlobalCurve points and endpoint clamping.

- [ ] **Step 2: Run tests and verify RED**

Run the full module test.

Expected: missing global/prior APIs.

- [ ] **Step 3: Implement global decomposition and bounded AutoCal prior**

Do not yet estimate node values. Only provide the global component and prior strength.

- [ ] **Step 4: Run tests and verify GREEN**

Expected: all Task 1–3 tests pass.

- [ ] **Step 5: Commit**

Commit message:

`feat(sil): decompose global curve before local map residual`

---

### Task 4: Robust fixed-node estimator and publication state

**Files:**
- Modify: `tools/omegas-sil/fixed_control_surface.py`
- Modify: `tools/omegas-sil/test_fixed_control_surface.py`

**Interfaces:**
- Consumes: `NodeContribution` and `AutoCalPrior`.
- Produces:
  - `NodeEstimate(control_node, raw_center_percent, stable_percent, mad_percent, effective_visits, unique_visits, confidence, state, actionable)`
  - `estimate_node(contributions, node, prior=None) -> NodeEstimate`
  - `estimate_surface(observations, global_curve, prior=None) -> dict[(row,column), NodeEstimate]`

Estimator contract:
1. one contribution per physical visit after dedupe;
2. robust evidence center = weighted median of local residual contributions;
3. MAD = weighted median absolute deviation;
4. `effective_visits = sum(contribution.weight)`;
5. prior shrinkage only affects `stable_percent` while support is sparse:
   `alpha = effective_visits / (effective_visits + prior_strength)`;
   `stable = alpha * robust_center + (1-alpha) * 0.0`;
6. prior does **not** increase `effective_visits` or `unique_visits`;
7. `CONSOLIDATED` requires live evidence only:
   - `effective_visits >= 3.0`;
   - `unique_visits >= 3`;
   - weighted directional consensus >= 0.75;
   - MAD <= 2.5 percentage points for the first laboratory model;
8. otherwise state is `LEARNING`;
9. actionable requires `CONSOLIDATED` and `abs(stable_percent) > 2.5`;
10. one observation can never be actionable.

These thresholds are SIL candidates and are explicitly re-tuned/falsified in Task 5.

- [ ] **Step 1: Write failing robust-estimator tests**

Add:

```python
def test_one_post_autocal_spike_is_not_published_as_stable_truth():
    prior = AutoCalPrior(True, 0.5, 24)
    obs = [VisitObservation('v1', 9, 1850, 4.5, 30.0, 1.0, 1000)]
    est = estimate_surface(obs, GlobalCurve.zero(), prior)[(4,2)]
    assert est.raw_center_percent == 30.0
    assert est.stable_percent < 30.0
    assert est.state == 'LEARNING'
    assert est.actionable is False

def test_compatible_near_zero_evidence_confirms_fast_after_autocal():
    prior = AutoCalPrior(True, 0.5, 24)
    obs = [
        VisitObservation('v1', 9, 1850, 4.5, 0.8, 1.0, 1000),
        VisitObservation('v2', 9, 1850, 4.5, -0.4, 1.0, 2000),
        VisitObservation('v3', 9, 1850, 4.5, 0.3, 1.0, 3000),
    ]
    est = estimate_surface(obs, GlobalCurve.zero(), prior)[(4,2)]
    assert est.state == 'CONSOLIDATED'
    assert abs(est.stable_percent) <= 2.5
    assert est.actionable is False

def test_repeated_real_local_defect_overrides_zero_prior():
    prior = AutoCalPrior(True, 0.5, 24)
    obs = [
        VisitObservation(f'v{i}', 9, 1850, 4.5, 10.0 + jitter, 1.0, i*1000)
        for i, jitter in enumerate((0.0, 0.4, -0.3, 0.2, -0.2), 1)
    ]
    est = estimate_surface(obs, GlobalCurve.zero(), prior)[(4,2)]
    assert est.state == 'CONSOLIDATED'
    assert est.stable_percent > 5.0
    assert est.actionable is True
```

Also add a test where a 5.3-ms observation influences both 4.5 and 6.0 nodes while both node identities remain fixed.

- [ ] **Step 2: Run tests and verify RED**

Run the full module test.

Expected: estimator APIs missing.

- [ ] **Step 3: Implement weighted median, MAD, shrinkage and publication gate**

Implementation details:
- weighted median rejects nonpositive weights;
- compute and expose both support mass and Kish effective sample size;
- directional consensus uses weight on the sign/direction of the robust center;
- prior strength never enters live evidence counts;
- empty node => `NO_EVIDENCE`, no stable percentage, not actionable;
- raw center is diagnostic only;
- return immutable control coordinates from Task 1.

- [ ] **Step 4: Run tests and verify GREEN**

Expected: all tests pass.

- [ ] **Step 5: Commit**

Commit message:

`feat(sil): add robust AutoCal-informed node estimator`

---

### Task 5: Adversarial parameter tournament and method selection

**Files:**
- Create: `tools/omegas-sil/fixed_control_tournament.py`
- Create: `tools/omegas-sil/test_fixed_control_tournament.py`
- Modify: `tools/omegas-sil/fixed_control_surface.py` only if the tournament proves a parameter change is required.

**Interfaces:**
- Consumes: `estimate_surface()`.
- Produces:
  - deterministic scenario generator;
  - `TournamentConfig(prior_strength_cap, min_effective_visits, min_unique_visits, max_mad_percent, consensus_minimum)`;
  - `TournamentResult`;
  - selected laboratory configuration written as JSON to a caller-provided output path.

Candidate grid:

- prior strength cap: `0.5, 1.0, 2.0, 3.0`
- minimum effective visits: `2.0, 2.5, 3.0, 4.0`
- minimum unique visits: `2, 3, 4`
- maximum MAD: `1.5, 2.5, 4.0` percentage points
- directional consensus: `0.70, 0.75, 0.85`

Required scenarios:
1. perfect/near-zero post-AutoCal;
2. one +30% spike after alignment;
3. alternating +20/-20 noise;
4. repeated true +10% local defect;
5. repeated true -10% local defect;
6. global +8% with zero local residual;
7. global +8% plus local +5%;
8. sparse edge/corner visits;
9. same evidence split into 1 vs 20 fake USB sessions.

Hard acceptance constraints:
- session split scientific signature delta = 0;
- no single visit actionable;
- repeated local ±10% becomes actionable by 5 exact-node visits;
- near-zero becomes consolidated by 3 exact-node visits when AutoCal prior is strong;
- six independent visits of equal 0.5 interpolation weight report Kish ESS ≈ 6, not 3, while support mass remains 3;
- pure global case local absolute stable residual <= 1.0%;
- one +30% spike after strong AutoCal prior is not actionable;
- node identity violations = 0.

Selection order among candidates satisfying all hard constraints:
1. lowest false-actionable count;
2. lowest median visits-to-correct-classification;
3. lowest absolute error on mixed global+local scenarios;
4. lower prior strength as final tie-breaker.

- [ ] **Step 1: Write failing tournament tests**

Test deterministic candidate count, hard-gate rejection, and deterministic winner selection on a tiny fixture.

- [ ] **Step 2: Run tests and verify RED**

Expected: tournament module missing.

- [ ] **Step 3: Implement tournament**

Use no randomness unless a fixed seed is explicitly passed; default scenarios are fixed.

- [ ] **Step 4: Run tests and execute full synthetic tournament**

Run:

```bash
python -m unittest tools/omegas-sil/test_fixed_control_tournament.py -v
python tools/omegas-sil/fixed_control_tournament.py --output <SIL_OUTPUT>/fixed-control-tournament.json
```

Expected: at least one configuration satisfies all hard constraints. If none does, do not weaken constraints silently; record the failure and use systematic debugging.

- [ ] **Step 5: Apply the selected laboratory parameters through TDD if they differ from Task 4 defaults**

First add/update a test asserting the selected values; watch it fail; then change constants/config; rerun all fixed-control tests.

- [ ] **Step 6: Commit**

Commit message:

`test(sil): falsify fixed-node estimator parameters`

---

### Task 6: Canonical gasoline adapter and GNV-only replay integration

**Files:**
- Create: `tools/omegas-sil/canonical_petrol_surface.py`
- Create: `tools/omegas-sil/test_canonical_petrol_surface.py`
- Create: `tools/omegas-sil/fixed_control_replay.py`
- Modify: `tools/omegas-sil/README.md`

**Interfaces:**
- Consumes:
  - deduplicated gasoline observations `(rpm, map_bar, petrol_ms, visit_key/source_key)`;
  - fixed-control estimator;
  - existing SIL replay corpus loaders where reusable.
- Produces:
  - `CanonicalPetrolSurface.query(rpm, map_bar) -> PetrolReference`;
  - `PetrolReference(expected_ms, spread_ms, support, mode, confidence)`;
  - replay JSON report.

Canonical hierarchy:
1. 25 RPM × 0.005 bar local cell when support >= 5;
2. 50 RPM × 0.01 bar fallback when support >= 5;
3. inverse-distance interpolation among supported neighboring cells within bounded radius;
4. unavailable/abstain if no bounded support.

Use weighted/ordinary median and MAD, not arithmetic mean, for cell reference.

Session labels may be retained for holdout grouping only; they cannot enter query confidence.

- [ ] **Step 1: Write failing canonical-surface tests**

Include:
- exact supported query;
- fine-to-coarse fallback;
- bounded interpolation;
- out-of-domain abstention;
- same data with different session labels => identical reference/confidence.

- [ ] **Step 2: Run tests and verify RED**

Expected: canonical module missing.

- [ ] **Step 3: Implement canonical surface**

Keep it independent of Android/runtime region limits.

- [ ] **Step 4: Run tests and verify GREEN**

- [ ] **Step 5: Integrate real GNV-only replay**

Replay contract:
- zero live gasoline frames during the selected GNV runtime segment;
- gasoline reference supplied only by the canonical surface;
- total error computed from canonical expected petrol ms vs live CNG petrol ms;
- global component removed;
- local residual projected into fixed nodes;
- output both raw and stable node state;
- no automatic write.

Report must include:
- source corpus hash/path label;
- GNV frame/visit counts;
- canonical coverage;
- node support distribution;
- max raw adjacent discontinuity;
- max stable adjacent discontinuity;
- consolidated/learning/no-evidence node counts;
- actionable count;
- session-split invariance delta.

- [ ] **Step 6: Run real replay**

Use the existing LOGNOVO/OMEGAS corpus source already used by SIL. If the environment variable/path is unavailable, the test remains green on fixtures but the real-replay gate is **inconclusive**, not passed.

- [ ] **Step 7: Commit**

Commit message:

`feat(sil): replay canonical petrol through fixed-node residual field`

---

### Task 7: AutoCal-informed replay and code-evidence report

**Files:**
- Create: `tools/omegas-sil/autocal_fixed_control_replay.py`
- Create: `tools/omegas-sil/test_autocal_fixed_control_replay.py`
- Modify: `docs/reports/omegas-verde-sil-scientific-results.md`
- Create: `docs/reports/2026-09-21-fixed-control-porting-matrix.md`

**Interfaces:**
- Consumes:
  - AutoCal snapshot analysis inputs available in corpus/fixtures;
  - selected tournament configuration;
  - canonical gasoline surface;
  - fixed-node estimator.
- Produces:
  - AutoCal alignment prior summary;
  - before/after blind-start comparison;
  - porting matrix for current Verde.

- [ ] **Step 1: Write failing AutoCal replay contract tests**

Fixtures must prove:
- aligned AutoCal creates bounded prior but zero live visits;
- prior alone never consolidates a node;
- first +30% visit stays non-actionable;
- three compatible near-zero exact-node visits consolidate under selected lab configuration;
- five repeated true +10% visits override prior;
- same replay with fake session splits is scientifically identical.

- [ ] **Step 2: Run tests and verify RED**

- [ ] **Step 3: Implement AutoCal prior extraction adapter**

Use available analysis values only:
- valid point count;
- median absolute `deltaPercent`;
- alignment classification.

Do not claim native firmware exactness when the underlying analysis is inferred.

- [ ] **Step 4: Run tests and verify GREEN**

- [ ] **Step 5: Run comparative replay**

Compare:
- current-style blind local learning baseline;
- fixed-node model without AutoCal prior;
- fixed-node model with AutoCal prior.

Measure:
- visits to stable neutral after aligned AutoCal;
- false extreme stable cells;
- false actionable cells;
- time/visits to detect injected local defect;
- session-split delta.

- [ ] **Step 6: Update scientific report**

Record:
- exact commands;
- exact input corpus hashes;
- selected parameters and why;
- passes/failures;
- limitations;
- no physical-validation claim.

- [ ] **Step 7: Create porting matrix**

Map each proven contract to the then-current `OmegasVerde` seam, expected production files/tests, and migration risk. Explicitly include:
- `LearningGridProjection` node identity separation;
- session-coupled `AdaptivePetrolReference.promoteScale` removal/replacement;
- post-AutoCal reset + prior bridge;
- `LearningStabilityV7` effective-visit semantics;
- `learning.js stableComparisonError()` raw-display policy;
- advisor global-then-local residual path.

Do not modify `OmegasVerde` in this task.

- [ ] **Step 8: Commit**

Commit message:

`docs(sil): record fixed-control evidence and Verde porting matrix`

---

## Final scientific gate

Run all relevant SIL tests:

```bash
python -m unittest discover tools/omegas-sil -p "test_*.py" -v
```

Then run the real replay commands from Tasks 6–7.

The method is ready for a production port proposal only if:

- all deterministic tests are green;
- session-split delta is zero within declared tolerance;
- exact node identities never drift;
- no one-visit extreme becomes actionable;
- near-zero post-AutoCal recognition is materially faster than blind cold-start;
- repeated local defects still become actionable within the declared bound;
- pure global error is not double-counted into the local map;
- real-corpus replay has no unresolved scientific contradiction.

No APK is generated and no official branch is modified by this plan.
