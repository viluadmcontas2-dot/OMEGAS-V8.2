# Predictor Authority and Outcome Step155 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enrich the existing typed Predictor IdealTargetCandidate with canonical Physics authority/range/provenance/model/error metadata and add a pure PredictionOutcome calibration ledger model without creating a second Store or allowing Prediction to become Observation.

**Architecture:** Reuse `IdealTargetCandidate` in `PredictorContract.kt` and `MagnitudeAuthority` from Physics. Add one focused pure outcome/calibration model and one versioned serialization projection; no persistence, writer, transport, scheduler, or UI authority enters Predictor.

**Tech Stack:** Kotlin/JVM, existing Android `org.json` projection support, focused exact-blob `kotlinc` probes.

**Spec:** Notion `FASE 07 — 147–164 — Predictor e Suggestions`, Step 155.

## Global Constraints
- GitHub remote is source authority; Linear is execution writer.
- No GitHub Actions.
- Prediction != Observation.
- IdealTarget != StepPolicy result.
- Reuse canonical `MagnitudeAuthority`; do not create a second authority enum.
- UNKNOWN/POLICY_ONLY never self-promote to industrial/actionable authority.
- JSON/serialization is a ledger/projection boundary, not per-frame scientific transport.
- No new Store, Router, Scheduler, writer, USB, serial, or Android UI dependency in the scientific core.

---

### Task 1: Enrich the existing IdealTargetCandidate

**Files:**
- Modify: `app/src/main/java/com/omegas/prohub/learning/PredictorContract.kt`
- Modify: `app/src/test/java/com/omegas/prohub/learning/PredictorContractTest.kt`

**Interfaces:**
- Consumes: `MagnitudeAuthority`, existing `PredictorSourceRevisions`, validated `PredictorObservation`.
- Produces: enriched `IdealTargetCandidate` with estimate/range, authority, assumptions, evidenceRefs, model descriptor, confidence calibration version, prediction error statistics, and a typed industrial-authority eligibility method that remains separate from runtime actionability.

- [ ] Write RED tests proving metadata propagation and UNKNOWN/POLICY_ONLY non-promotion.
- [ ] Verify RED against the Step154 source head.
- [ ] Add minimal metadata types and explicit observation/input authority/model inputs.
- [ ] Derive the numeric range only from the observation's declared uncertainty, without inventing a 95% label.
- [ ] Re-run focused contract tests/property probes and preserve all Step147–154 fail-closed invariants.
- [ ] Commit.

### Task 2: Add PredictionOutcome and calibration reducer

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/learning/PredictorPredictionOutcome.kt`
- Create: `app/src/test/java/com/omegas/prohub/learning/PredictorPredictionOutcomeTest.kt`

**Interfaces:**
- Consumes: immutable prediction revision/cell/estimate/range/P(improve)/context/applied target/real later outcome.
- Produces: `PredictorPredictionErrorStats` and a pure calibration assessment with an explicit `actionabilityDowngraded` signal.

- [ ] Write RED tests proving PredictionOutcome is not PredictorObservation, complete outcomes compute absolute log error/coverage, interval miss increases calibration error, and an interval miss emits a downgrade signal.
- [ ] Verify RED.
- [ ] Implement immutable outcome/statistics/reducer with no persistence side effects.
- [ ] Fuzz cumulative coverage/error invariants and order invariance.
- [ ] Commit.

### Task 3: Add versioned ledger serialization projection

**Files:**
- Create: `app/src/main/java/com/omegas/prohub/learning/PredictorOutcomeCodec.kt`
- Create: `app/src/test/java/com/omegas/prohub/learning/PredictorOutcomeCodecTest.kt`

**Interfaces:**
- Consumes: enriched target metadata and PredictionOutcome.
- Produces: versioned JSON projection roundtrip for ledger/diagnostics only.

- [ ] Write RED roundtrip tests covering authority, bounds, assumptions/evidenceRefs, revisions, model/calibration versions, error stats, P(improve), context, applied target, and actual result.
- [ ] Verify RED.
- [ ] Implement strict versioned encode/decode that rejects unknown schema or missing required scientific fields.
- [ ] Roundtrip/fuzz numeric and nullable fields.
- [ ] Static-scan core files to ensure codec did not introduce transport/store/writer authority.
- [ ] Commit.

### Task 4: Independent verification and closeout

**Files:**
- No new production surface unless a falsifier fails.

- [ ] Fresh-compare branch head to evidence SHA.
- [ ] Reproduce exact remote production blobs locally and verify `git hash-object` before compile/probe claims.
- [ ] Run focused property/fuzz battery and legacy regression checks.
- [ ] Audit that UNKNOWN/POLICY_ONLY remain non-industrial, Prediction never becomes Observation, and no prediction outcome enters the observation support path.
- [ ] Record independent audit and meta-audit in VIT-311.
- [ ] Mark VIT-311 Done only after PASS.
