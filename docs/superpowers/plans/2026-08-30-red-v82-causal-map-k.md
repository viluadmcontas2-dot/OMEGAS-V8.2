# RED V8.2 Causal MAP_K Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic, fail-closed offline causal MAP_K gate that treats each confirmed manual adjustment as one intervention unit and only estimates improvement when comparable pre/post telemetry exists.

**Architecture:** Keep all causal analysis in `lab/red_blend`, outside the RED Android hot path. A privacy-safe fixture carries only the confirmed ACK/readback/final-map-hash envelope from the owner snapshot; the evaluator groups cell writes by hashed adjustment identity, derives pre/post outcome support without future leakage, and returns ABSTAIN whenever causal comparability is missing.

**Tech Stack:** Python 3 standard library, `unittest`, deterministic JSON, GitHub Actions standard hosted runners.

**Spec:** `docs/superpowers/specs/2026-08-30-red-v82-science-blend-design.md`

## Global Constraints

- RED source branch remains untouched.
- `work/wu-006-calibration-science-hardening` is research input only; no wholesale merge.
- Raw Google Drive / `.omegas` source files are never committed to the public repository.
- RPM×MAP defines physical comparison support; Tinj is the learned/compared response.
- Dense repeated frames contribute fully to local-distribution knowledge; adjustment/session identity governs causal independence.
- The 133 confirmed cell writes are not 133 independent interventions; inference unit is the adjustment/batch identity.
- Only confirmed events with before/after, `readback == after`, `batchFinalized`, writable 12×12 coordinates, and `finalMapHash` are admissible.
- No causal claim from temporal ordering alone.
- `p_improve` remains null unless calibrated from held-out causal outcomes.
- No automatic ECU write; no production Android runtime change in this plan.
- Any real-corpus insufficiency is a valid scientific outcome and must return ABSTAIN rather than be converted into a pass.

---

### Task 1: Privacy-safe causal fixture and failing contract

**Files:**
- Create: `tests/fixtures/science/k_history/confirmed_map_k_20260818.json`
- Create: `lab/red_blend/test_causal_science.py`
- Modify: `.github/workflows/red-v82-science-blend.yml`

**Interfaces:**
- Consumes: owner-private `confirmedKHistory` only during fixture derivation.
- Produces: `load_confirmed_k_fixture(path) -> list[dict]`, `group_adjustments(events) -> list[Adjustment]`, `evaluate_adjustment(pre, post, adjustment) -> CausalResult`.

- [ ] **Step 1: Write the failing tests**

Tests must prove all of the following before implementation exists:

```python
self.assertEqual(len(events), 133)
self.assertEqual(len(group_adjustments(events)), 11)
self.assertRaises(ValueError, normalize_confirmed_event, event_without_readback)
self.assertEqual(result.status, "ABSTAIN_INSUFFICIENT_COMPARABLE_PRE_POST")
self.assertLess(improving.effect_abs_error_delta, 0.0)
self.assertGreater(worsening.effect_abs_error_delta, 0.0)
```

The fixture may contain only: schema, source-content SHA256, axis schema/lock hash, normalized event timestamp, hashed adjustment key, row, column, petrol axis value, RPM axis value, before, after, readback, batch-finalized flag, and final-map hash. Raw device/session IDs, human reason text and un-hashed adjustment IDs are forbidden.

- [ ] **Step 2: Commit tests + sanitized fixture + workflow step**

Commit message:

```text
test(blend): define causal MAP_K contracts
```

- [ ] **Step 3: Verify RED in GitHub Actions**

Expected failure is import/module absence for `lab.red_blend.causal_science`; inherited RED fast contracts and all pre-existing Blend science tests must remain green before the new causal test fails.

---

### Task 2: Minimal causal evaluator

**Files:**
- Create: `lab/red_blend/causal_science.py`
- Test: `lab/red_blend/test_causal_science.py`

**Interfaces:**
- `normalize_confirmed_event(event: dict) -> ConfirmedKCellEvent`
- `load_confirmed_k_fixture(path: Path) -> tuple[ConfirmedKCellEvent, ...]`
- `group_adjustments(events) -> tuple[Adjustment, ...]`
- `evaluate_adjustment(pre: Sequence[Outcome], post: Sequence[Outcome], adjustment: Adjustment) -> CausalResult`

- [ ] **Step 1: Implement only the minimum needed for the RED tests**

`Adjustment` must aggregate all cell writes sharing one hashed adjustment key and expose `cell_count`, `started_at_ms`, `ended_at_ms`, and final map hash. Multiple cells in one adjustment must never increase independent intervention count.

`Outcome` carries timestamp, RPM, MAP, gasoline-reference Tinj, CNG petrol-command Tinj, and derives:

```python
error_fraction = (cng_petrol_ms - gasoline_reference_ms) / gasoline_reference_ms
absolute_error = abs(error_fraction)
```

`evaluate_adjustment` may compare robust pre/post medians only when both sides are non-empty and RPM×MAP support is comparable; otherwise it must ABSTAIN.

- [ ] **Step 2: Run the causal tests in Actions**

Expected: causal unit tests GREEN, all inherited fast/science tests GREEN.

- [ ] **Step 3: Commit**

```text
feat(blend): add fail-closed causal MAP_K lab
```

---

### Task 3: Real-corpus causal support audit

**Files:**
- Create: `lab/red_blend/test_real_causal_science.py`
- Extend: `lab/red_blend/causal_science.py`
- Modify: `.github/workflows/red-v82-science-blend.yml`

**Interfaces:**
- Consumes governed episode fixture plus sanitized K-history fixture.
- Produces `audit_real_causal_support(...) -> CausalAudit` with explicit counts for interventions, comparable interventions, abstentions, leakage violations, and calibrated outcomes (if any).

- [ ] **Step 1: Write failing real-corpus tests**

Tests require:

```python
self.assertEqual(audit.cell_event_count, 133)
self.assertEqual(audit.intervention_count, 11)
self.assertEqual(audit.leakage_violations, 0)
self.assertEqual(audit.p_improve, None)
```

If the governed telemetry cannot be aligned to comparable pre/post support around the interventions, the expected real result is an explicit insufficiency status, not an inferred effect.

- [ ] **Step 2: Prove RED in Actions**

Expected: failure because real causal support audit is not implemented yet.

- [ ] **Step 3: Implement minimum deterministic audit**

The implementation must freeze chronological prefixes, never use future telemetry in a pre-intervention reference, and only count intervention-level outcomes. It must not manufacture timestamps or map session order into event time without evidence that the clocks are comparable.

- [ ] **Step 4: Run Actions to GREEN or scientifically valid ABSTAIN**

A workflow succeeds when code/tests behave as specified. Scientific gate state is independent:
- `G8_CAUSAL_MAP_K=SUPPORTED` only with comparable real outcomes.
- otherwise `G8_CAUSAL_MAP_K=INSUFFICIENT_CAUSAL_OUTCOME_SUPPORT`.

---

### Task 4: Evidence receipt and downstream authority

**Files:**
- Create: `docs/evidence/red-blend-causal-map-k.md`
- Update only if needed: Issue #11 comment.

**Interfaces:**
- Consumes exact source SHA/tree and Actions run/job logs.
- Produces a human-readable receipt that distinguishes engineering GREEN from scientific causal support.

- [ ] **Step 1: Record exact reproducible evidence**

Receipt must include source SHA/tree, run/job IDs, fixture identity, 133 cell events / 11 interventions, test counts, real causal support count, abstentions, leakage count, `p_improve` state, and explicit `PRODUCTION_RUNTIME_CHANGED=false`.

- [ ] **Step 2: Self-review against the design spec**

Reject the receipt if it conflates cell writes with independent experiments, labels correlation causal, exposes private source metadata, or promotes `p_improve` without held-out causal calibration.

- [ ] **Step 3: Continue to independent falsification/performance gates**

If causal support is insufficient, preserve the unresolved G8 state and continue only gates that do not logically depend on a calibrated causal effect (OOD/falsification and RED-vs-Blend performance). Do not unblock `P(improve)` by assumption.
