# OMEGAS RED Final Engine Closure Implementation Plan

## 0. ITEMIZATION CONTRACT

- [LOCKED] [PLAN-0001] Every requirement in this plan is an atomic item with a unique ID.
- [LOCKED] [PLAN-0002] Every execution step in this plan is an atomic item with a unique ID.
- [LOCKED] [PLAN-0003] Every test in this plan is an atomic item with a unique ID.
- [LOCKED] [PLAN-0004] Every gate in this plan is an atomic item with a unique ID.
- [LOCKED] [PLAN-0005] Every acceptance criterion in this plan is an atomic item with a unique ID.
- [LOCKED] [PLAN-0006] Every stop condition in this plan is an atomic item with a unique ID.
- [LOCKED] [PLAN-0007] Every evidence requirement in this plan is an atomic item with a unique ID.
- [LOCKED] [PLAN-0008] Another agent MUST NOT merge, reinterpret, weaken, rename, silently drop, or infer equivalence between distinct item IDs.
- [LOCKED] [PLAN-0009] An item status MUST be one of `LOCKED`, `PENDING`, `IN_PROGRESS`, `RED`, `GREEN`, `PROVEN`, `BLOCKED`, or `N/A`.
- [LOCKED] [PLAN-0010] A checkpoint MUST report item IDs and statuses; prose without item IDs is insufficient.
- [LOCKED] [PLAN-0011] A commit MUST state which item IDs it advances.
- [LOCKED] [PLAN-0012] An item can become `PROVEN` only with explicit evidence recorded against that same item ID.

## 1. AUTHORITY

- [LOCKED] [AUTH-0001] Repository authority is `viluadmcontas2-dot/OMEGAS-V8.2`.
- [LOCKED] [AUTH-0002] Canonical branch is `work/omegas-blue-causal-engine`.
- [LOCKED] [AUTH-0003] Canonical spec is `docs/superpowers/specs/2026-09-13-red-final-engine-closure-design.md`.
- [LOCKED] [AUTH-0004] Canonical implementation plan is this file.
- [LOCKED] [AUTH-0005] GitHub remote state is authoritative; local state is temporary execution/cache only.
- [LOCKED] [AUTH-0006] Before every remote write, re-read canonical remote HEAD.
- [LOCKED] [AUTH-0007] Do not create a parallel branch for this plan.
- [LOCKED] [AUTH-0008] Do not force-push.
- [LOCKED] [AUTH-0009] Do not overwrite concurrent remote changes.
- [LOCKED] [AUTH-0010] Do not create a second learning/calibration engine.

## 2. PRODUCT GOAL

- [LOCKED] [GOAL-0001] Close the existing OMEGAS RED engine instead of rebuilding it.
- [LOCKED] [GOAL-0002] Preserve gasoline as the permanent physical reference surface.
- [LOCKED] [GOAL-0003] Support continuous spatial interpolation across valid 2D RPM×MAP neighborhoods, including diagonal support.
- [LOCKED] [GOAL-0004] Restore Predictor as a fast estimated surface before complete evidence coverage.
- [LOCKED] [GOAL-0005] Make Predictor converge toward observed evidence as evidence grows.
- [LOCKED] [GOAL-0006] Keep calibration suggestions manual.
- [LOCKED] [GOAL-0007] Keep Learning UI operational, didactic, practical, and fast.

## 3. SCIENTIFIC INVARIANTS

- [LOCKED] [SCI-0001] Valid gasoline evidence does not expire because of elapsed time.
- [LOCKED] [SCI-0002] Valid gasoline evidence does not expire because of GNV epoch changes.
- [LOCKED] [SCI-0003] Valid gasoline evidence does not expire because of Curve K changes.
- [LOCKED] [SCI-0004] Valid gasoline evidence does not expire because of Map K changes.
- [LOCKED] [SCI-0005] Primary gasoline reference query is `(RPM, MAP) -> expected Petrol Inj.`.
- [LOCKED] [SCI-0006] GNV error is `PetrolInj_GNV / PetrolInj_GasolineExpected - 1`.
- [LOCKED] [SCI-0007] Current GNV Map K physical address is `RPM_GNV × PetrolInj_GNV`.
- [LOCKED] [SCI-0008] Gasoline target Petrol Inj. MUST NOT be used as the current GNV Map K address.
- [LOCKED] [SCI-0009] Short temporal windows MUST NOT decide whether a gasoline reference exists.
- [LOCKED] [SCI-0010] Short temporal windows MAY exist only for genuine causal before/after K intervention analysis.
- [LOCKED] [SCI-0011] Surface state `OBSERVED` means direct sufficiently supported evidence.
- [LOCKED] [SCI-0012] Surface state `INTERPOLATED` means estimate inside supported observed geometry.
- [LOCKED] [SCI-0013] Surface state `PREDICTED` means Predictor estimate, not direct observation.
- [LOCKED] [SCI-0014] Surface state `UNSUPPORTED` means insufficient geometry/support for a trustworthy local value.
- [LOCKED] [SCI-0015] Observed, interpolated, predicted, and unsupported states MUST remain distinguishable in data and UI.
- [LOCKED] [SCI-0016] Unsupported regions MUST NOT be aggressively extrapolated as truth.
- [LOCKED] [SCI-0017] Grid occupancy is a projection; continuous RPM×MAP physical support is the scientific authority.

## 4. SAFETY AND PERFORMANCE INVARIANTS

- [LOCKED] [SAFE-0001] No Predictor action writes automatically to ECU.
- [LOCKED] [SAFE-0002] No Learning action writes automatically to ECU.
- [LOCKED] [SAFE-0003] No suggestion writes automatically to ECU.
- [LOCKED] [SAFE-0004] Existing manual sequence remains prepare → review → confirm → write → ACK → readback.
- [LOCKED] [PERF-0001] Heavy surface reconstruction MUST NOT execute per telemetry frame.
- [LOCKED] [PERF-0002] Heavy Predictor reconstruction MUST NOT execute per UI render tick.
- [LOCKED] [PERF-0003] Evidence-driven recomputation SHOULD be revision-driven and cached.
- [LOCKED] [PERF-0004] Rapid updates MUST coalesce instead of creating unbounded work backlog.

## 5. TASK T01 — REMOVE 30-SECOND GASOLINE-REFERENCE REGRESSION

- [PENDING] [T01-0001] Objective: make gasoline-reference validity depend on physical support/quality, never age.
- [LOCKED] [T01-F001] Modify `app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt` only as required by T01.
- [LOCKED] [T01-F002] Modify/test `app/src/test/java/com/omegas/prohub/blue/BlueTemporalPairingTest.kt`.
- [LOCKED] [T01-F003] Test `app/src/test/java/com/omegas/prohub/blue/BluePairedEvidenceAuthorityTest.kt`.
- [LOCKED] [T01-I001] Input interface: `FuelEvidence` gasoline candidates plus current GNV target.
- [LOCKED] [T01-I002] Output interface: `BlueCausalEngine.petrolReference(target, petrolEvidence): BluePetrolReference?`.
- [LOCKED] [T01-I003] Output validity MUST depend on physical geometry and evidence quality.
- [LOCKED] [T01-I004] Output validity MUST NOT depend on gasoline age.
- [PENDING] [T01-RED01] Add a failing test for equivalent gasoline collected 31 seconds before GNV.
- [PENDING] [T01-RED02] Add a failing test for equivalent gasoline collected 30 minutes before GNV.
- [PENDING] [T01-RED03] Add a failing test for equivalent gasoline collected 24 hours before GNV.
- [PENDING] [T01-RED04] Add a failing test for equivalent gasoline collected several days before GNV.
- [PENDING] [T01-RED05] Add a failing test proving timestamp age alone cannot return `null`.
- [PENDING] [T01-E001] Capture RED evidence showing current failure comes from temporal reference gating.
- [PENDING] [T01-IMP01] Remove mandatory temporal gating from gasoline reference selection.
- [LOCKED] [T01-IMP02] Physical-distance and quality ranking MUST remain.
- [LOCKED] [T01-IMP03] Any retained temporal proximity may be metadata or tie-break only.
- [LOCKED] [T01-IMP04] T01 MUST NOT move the 30-second gate into another gasoline-reference path.
- [PENDING] [T01-GREEN01] Run focused T01 tests and require GREEN.
- [PENDING] [T01-GREEN02] Run existing Blue causal regression tests and require GREEN.
- [PENDING] [T01-COMMIT] Commit T01 with message `fix(red): restore permanent gasoline reference surface`.
- [PENDING] [T01-A001] Acceptance: all age cases resolve a reference when physical support and quality are valid.
- [PENDING] [T01-A002] Acceptance: physically invalid/low-quality gasoline still abstains normally.
- [PENDING] [T01-E002] Record exact commit SHA and exact tests for T01.

## 6. TASK T02 — CONTINUOUS GASOLINE SURFACE ACROSS 2D/DIAGONAL SUPPORT

- [PENDING] [T02-0001] Objective: close the existing interpolation so supported internal RPM×MAP regions do not remain artificial holes.
- [LOCKED] [T02-F001] Modify `app/src/main/java/com/omegas/prohub/learning/ContinuousLearningMath.kt` only as required by T02.
- [LOCKED] [T02-F002] Modify `app/src/main/java/com/omegas/prohub/learning/LearningGridProjection.kt` only as required by T02.
- [LOCKED] [T02-F003] Create/test `app/src/test/java/com/omegas/prohub/learning/GasolineSurfaceInterpolationTest.kt` if no existing equivalent test file exists.
- [LOCKED] [T02-I001] Input interface: observed gasoline support points carrying RPM, MAP, Petrol Inj., quality, and provenance.
- [LOCKED] [T02-I002] Output interface MUST return expected Petrol Inj., support state, confidence, and provenance.
- [LOCKED] [T02-I003] T02 MUST reuse `ContinuousLearningMath`; it MUST NOT create a competing engine.
- [PENDING] [T02-RED01] Add failing horizontal-gap interpolation test.
- [PENDING] [T02-RED02] Add failing vertical-gap interpolation test.
- [PENDING] [T02-RED03] Add failing diagonal-support interpolation test.
- [PENDING] [T02-RED04] Add failing irregular non-grid-aligned 2D neighborhood test.
- [PENDING] [T02-RED05] Add failing internal-hole test where no frame landed at the exact query point.
- [PENDING] [T02-RED06] Add failing outside-supported-domain test requiring `UNSUPPORTED`.
- [PENDING] [T02-E001] Capture RED evidence for every T02 interpolation shape.
- [PENDING] [T02-IMP01] Implement the smallest continuous surface query that satisfies T02 tests.
- [LOCKED] [T02-IMP02] Weight support by physical proximity.
- [LOCKED] [T02-IMP03] Include evidence quality in support weighting or confidence.
- [LOCKED] [T02-IMP04] Preserve provenance/support IDs or equivalent auditable source identity.
- [LOCKED] [T02-IMP05] Do not convert unsupported extrapolation into `OBSERVED` or `INTERPOLATED`.
- [LOCKED] [T02-IMP06] Keep physical grid as downstream projection only.
- [PENDING] [T02-GREEN01] Run focused interpolation tests and require GREEN.
- [PENDING] [T02-GREEN02] Run existing learning projection tests and require GREEN.
- [PENDING] [T02-COMMIT] Commit T02 with message `feat(red): close continuous gasoline surface interpolation`.
- [PENDING] [T02-A001] Acceptance: horizontal/vertical/diagonal/internal supported gaps no longer remain empty.
- [PENDING] [T02-A002] Acceptance: outside-domain query abstains as `UNSUPPORTED`.
- [PENDING] [T02-E002] Record exact commit SHA and exact tests for T02.

## 7. TASK T03 — RESTORE PREDICTOR AS FAST CONVERGENT SURFACE

- [PENDING] [T03-0001] Objective: Predictor provides early useful estimates before complete evidence coverage and converges toward real evidence.
- [LOCKED] [T03-F001] Before editing, discover the single current production Predictor/advisor runtime owner on canonical branch.
- [LOCKED] [T03-F002] Do not add a second Predictor engine.
- [LOCKED] [T03-F003] Reuse existing Predictor/advisor files only after T03-F001 identifies them.
- [LOCKED] [T03-T001] Use existing `tests/test_predictor_map_residual_contract.py` if present.
- [LOCKED] [T03-T002] Use existing `tests/ui/predictor-consumer.test.cjs` if present.
- [LOCKED] [T03-T003] Use existing `tests/ui/predictor-live-cell.test.cjs` if present.
- [LOCKED] [T03-T004] Use existing `tests/ui/predictor-route.test.cjs` if present.
- [LOCKED] [T03-T005] Use existing `tests/test_runtime_predictor_router_soak.py` if present.
- [LOCKED] [T03-I001] Predictor consumes gasoline surface estimate.
- [LOCKED] [T03-I002] Predictor consumes observed GNV comparisons.
- [LOCKED] [T03-I003] Predictor consumes global Curve K trend.
- [LOCKED] [T03-I004] Predictor consumes supported local RPM×MAP residuals.
- [LOCKED] [T03-I005] Predictor outputs explicit state `PREDICTED`, `GLOBAL_ONLY`, or `UNSUPPORTED` where appropriate.
- [LOCKED] [T03-I006] Predictor outputs confidence/uncertainty.
- [PENDING] [T03-RED01] Add failing sparse-supported-region prediction test.
- [PENDING] [T03-RED02] Add failing evidence-convergence test.
- [PENDING] [T03-RED03] Add failing observation-dominance test.
- [PENDING] [T03-RED04] Add failing outside-support `GLOBAL_ONLY`/`UNSUPPORTED` test.
- [PENDING] [T03-E001] Capture RED evidence for T03 tests.
- [PENDING] [T03-IMP01] Implement with existing conceptual model `error_ratio = global_curve(petrol_target_ms) + local_residual(rpm,map_bar) + noise`.
- [LOCKED] [T03-IMP02] Local residual MUST remain support-bounded.
- [LOCKED] [T03-IMP03] Local residual MUST NOT be copied across distant RPM×MAP regions.
- [LOCKED] [T03-IMP04] Observed evidence MUST dominate conflicting Predictor output when direct evidence is sufficiently strong.
- [LOCKED] [T03-IMP05] More coherent evidence MUST reduce dependence on prediction, never increase it.
- [LOCKED] [T03-IMP06] Output provenance MUST distinguish observed/interpolated/predicted.
- [PENDING] [T03-GREEN01] Run focused Predictor tests and require GREEN.
- [PENDING] [T03-GREEN02] Run Predictor route/soak regressions and require GREEN.
- [PENDING] [T03-COMMIT] Commit T03 with message `feat(red): restore evidence-convergent predictor surface`.
- [PENDING] [T03-A001] Acceptance: sparse supported regions receive useful estimated surface values with explicit uncertainty.
- [PENDING] [T03-A002] Acceptance: increasing observed evidence drives estimates toward observed truth.
- [PENDING] [T03-A003] Acceptance: unsupported local geometry is never presented as observed truth.
- [PENDING] [T03-E002] Record exact commit SHA and exact tests for T03.

## 8. TASK T04 — GNV EPOCH ISOLATION AND MAP K ADDRESS

- [PENDING] [T04-0001] Objective: preserve permanent gasoline while keeping GNV calibration epochs isolated and Map K addressing physically correct.
- [LOCKED] [T04-F001] Verify/modify `app/src/main/java/com/omegas/prohub/blue/BlueMapKAddressing.kt` only if a T04 test fails.
- [LOCKED] [T04-F002] Verify/modify `app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt` only if a T04 test fails.
- [PENDING] [T04-RED01] Add failing test proving Curve K change does not clear or epoch-shift gasoline evidence.
- [PENDING] [T04-RED02] Add failing test proving Map K change does not clear or epoch-shift gasoline evidence.
- [PENDING] [T04-RED03] Add failing test proving confirmed/readback calibration creates a new GNV state/epoch.
- [PENDING] [T04-RED04] Add failing test proving old GNV remains history rather than current state.
- [PENDING] [T04-RED05] Add failing test proving Map K row/column comes from current `RPM_GNV × PetrolInj_GNV`.
- [PENDING] [T04-E001] Capture RED evidence for any current violations.
- [PENDING] [T04-IMP01] Change runtime only where a T04 RED test proves a violation.
- [LOCKED] [T04-IMP02] No style-only refactor is allowed in T04.
- [PENDING] [T04-GREEN01] Run calibration/addressing regression tests and require GREEN.
- [PENDING] [T04-COMMIT] Commit T04 with message `fix(red): preserve gasoline authority across gnv calibration epochs` if code changes are required.
- [PENDING] [T04-A001] Acceptance: gasoline authority survives all GNV calibration epoch transitions.
- [PENDING] [T04-A002] Acceptance: Map K address always uses current GNV RPM and current GNV Petrol Inj.
- [PENDING] [T04-E002] Record exact commit SHA or `N/A` with proof if no runtime change was required.

## 9. TASK T05 — SIMPLIFY LEARNING UI

- [PENDING] [T05-0001] Objective: make Learning UI operational instead of exposing internal engineering state.
- [LOCKED] [T05-F001] Modify only the existing Learning screen owner identified on canonical branch.
- [LOCKED] [T05-I001] Primary pane input includes RPM/MAP context.
- [LOCKED] [T05-I002] Primary pane input includes gasoline expected value.
- [LOCKED] [T05-I003] Primary pane input includes GNV observed value.
- [LOCKED] [T05-I004] Primary pane input includes measured deviation.
- [LOCKED] [T05-I005] Primary pane input includes confidence.
- [LOCKED] [T05-I006] Primary pane input includes source state `medido`, `interpolado`, or `predito`.
- [LOCKED] [T05-I007] Primary pane input includes suggested adjustment when available.
- [PENDING] [T05-RED01] Add failing UI test forbidding `BlueCausalEngine` text in primary cell pane.
- [PENDING] [T05-RED02] Add failing UI test forbidding calibration-state internals in primary cell pane.
- [PENDING] [T05-RED03] Add failing UI test forbidding epoch-history text in primary cell pane.
- [PENDING] [T05-RED04] Add failing UI test forbidding ACK/readback contract text in primary cell pane.
- [PENDING] [T05-RED05] Add failing UI test forbidding internal IDs in primary cell pane.
- [PENDING] [T05-RED06] Add failing UI test requiring all T05-I001..T05-I007 operational fields.
- [PENDING] [T05-RED07] Add failing UI test requiring compact grid cells without repeated engineering labels.
- [PENDING] [T05-E001] Capture RED UI evidence.
- [PENDING] [T05-IMP01] Simplify primary Learning pane to required operational fields only.
- [LOCKED] [T05-IMP02] Backend diagnostic data MUST NOT be deleted merely because primary UI hides it.
- [LOCKED] [T05-IMP03] Diagnostic/internal data may remain in diagnostic tooling outside primary operation.
- [PENDING] [T05-GREEN01] Run all affected Learning UI tests and require GREEN.
- [PENDING] [T05-COMMIT] Commit T05 with message `refactor(red-ui): simplify learning surface for calibration`.
- [PENDING] [T05-A001] Acceptance: operator can understand gasoline, GNV, difference, confidence, origin, and suggestion without engineering jargon.
- [PENDING] [T05-E002] Record exact commit SHA and UI test evidence.

## 10. TASK T06 — KEEP SURFACE/PREDICTOR OFF HOT PATH

- [PENDING] [T06-0001] Objective: preserve real-time telemetry responsiveness while adding surface/predictor logic.
- [LOCKED] [T06-F001] Discover affected telemetry, persistence, and scheduler owners before editing.
- [LOCKED] [T06-T001] Use `tests/test_runtime_predictor_router_soak.py` if present.
- [LOCKED] [T06-T002] Use existing self-paced scheduler contract if present.
- [PENDING] [T06-RED01] Add failing contract test detecting full-history/surface recomputation from telemetry frame path.
- [PENDING] [T06-RED02] Add failing contract test detecting full-history/surface recomputation from UI render tick.
- [PENDING] [T06-RED03] Add failing contract test proving rapid revisions coalesce rather than queue unbounded work.
- [PENDING] [T06-E001] Capture RED performance-contract evidence.
- [PENDING] [T06-IMP01] Implement revision-driven cached recomputation only if T06 tests prove current behavior violates contract.
- [LOCKED] [T06-IMP02] Latest relevant semantic revision MUST win.
- [LOCKED] [T06-IMP03] Stale calculations MUST NOT create unbounded backlog.
- [PENDING] [T06-GREEN01] Run soak/scheduler tests and require GREEN.
- [PENDING] [T06-COMMIT] Commit T06 with message `perf(red): keep learning surface off telemetry hot path` if code changes are required.
- [PENDING] [T06-A001] Acceptance: surface/predictor work is not tied to every telemetry frame/render tick.
- [PENDING] [T06-E002] Record exact commit SHA or `N/A` with proof if no runtime change was required.

## 11. TASK T07 — ADVERSARIAL INTEGRATION MATRIX

- [PENDING] [T07-0001] Objective: prove Tasks T01..T06 work together without semantic contradiction.
- [PENDING] [T07-TEST01] Add exact regression scenario: large gasoline history + large GNV history + nearly identical RPM/MAP + no recent fuel switch → valid comparison.
- [PENDING] [T07-TEST02] Add center-hole interpolation scenario.
- [PENDING] [T07-TEST03] Add horizontal-hole interpolation scenario.
- [PENDING] [T07-TEST04] Add vertical-hole interpolation scenario.
- [PENDING] [T07-TEST05] Add diagonal-hole interpolation scenario.
- [PENDING] [T07-TEST06] Add irregular-neighborhood interpolation scenario.
- [PENDING] [T07-TEST07] Add edge-of-support abstention scenario.
- [PENDING] [T07-TEST08] Add sparse Predictor scenario.
- [PENDING] [T07-TEST09] Add Predictor convergence scenario.
- [PENDING] [T07-TEST10] Add direct-observation-dominance scenario.
- [PENDING] [T07-TEST11] Add gasoline-persistence-across-epoch scenario.
- [PENDING] [T07-TEST12] Add current-GNV Map K address scenario.
- [PENDING] [T07-TEST13] Add no-auto-write assertion for Predictor suggestions.
- [PENDING] [T07-TEST14] Add no-auto-write assertion for Learning suggestions.
- [PENDING] [T07-E001] Capture integration test evidence.
- [PENDING] [T07-GREEN01] Run focused integration matrix and require GREEN.
- [PENDING] [T07-COMMIT] Commit T07 with message `test(red): lock final engine closure invariants`.
- [PENDING] [T07-A001] Acceptance: no integration item contradicts SCI-0001..SCI-0017.
- [PENDING] [T07-E002] Record exact commit SHA and integration results.

## 12. TASK T08 — FINAL VERIFICATION, ADVERSARIAL REVIEW, AND APK GATE

- [PENDING] [T08-0001] Objective: produce exact-SHA software proof before APK artifact generation.
- [LOCKED] [T08-F001] `STATUS.md` is updated only after product code/tests are green.
- [PENDING] [T08-GATE01] Re-read canonical remote HEAD before final verification.
- [PENDING] [T08-GATE02] Run `python3 -B tools/run_checks.py` on exact candidate SHA.
- [PENDING] [T08-GATE03] Require T08-GATE02 GREEN.
- [PENDING] [T08-GATE04] Run `./gradlew testDebugUnitTest lintDebug -PomegasAbis=armeabi-v7a --no-daemon --stacktrace` on exact candidate SHA.
- [PENDING] [T08-GATE05] Require T08-GATE04 GREEN.
- [PENDING] [T08-REV01] Perform adversarial review of permanent gasoline behavior.
- [PENDING] [T08-REV02] Perform adversarial review of interpolation support bounds.
- [PENDING] [T08-REV03] Perform adversarial review of Predictor convergence.
- [PENDING] [T08-REV04] Perform adversarial review of Map K address semantics.
- [PENDING] [T08-REV05] Perform adversarial review of GNV epoch isolation.
- [PENDING] [T08-REV06] Perform adversarial review of no-auto-write safety.
- [PENDING] [T08-REV07] Perform adversarial review of hot-path performance contract.
- [LOCKED] [T08-STOP01] If adversarial review changes product code, exact-SHA verification resets and T08-GATE01..T08-GATE05 MUST rerun.
- [PENDING] [T08-DOC01] Update `STATUS.md` with exact SHA, tree, verification results, and known physical-validation limits.
- [LOCKED] [T08-STOP02] If `STATUS.md` changes the final governance SHA, required exact-SHA gates MUST correspond to the final documented SHA.
- [PENDING] [T08-A001] Declare `READY FOR APK GENERATION` only after all required T08 software/review items are PROVEN.
- [LOCKED] [T08-STOP03] APK generation MUST use canonical owner-authorized workflow only.
- [PENDING] [T08-APK01] Invoke the existing owner-authorized APK artifact gate after `READY FOR APK GENERATION`.
- [PENDING] [T08-APK02] Verify workflow run and jobs belong to exact final SHA.
- [PENDING] [T08-APK03] Verify artifact ZIP integrity.
- [PENDING] [T08-APK04] Verify APK SHA-256 and byte size.
- [PENDING] [T08-APK05] Verify signing with `apksigner verify --verbose --print-certs` when tool is available in the canonical execution environment.
- [PENDING] [T08-APK06] Verify package metadata with `aapt dump badging` when tool is available in the canonical execution environment.
- [LOCKED] [T08-STOP04] CI/APK evidence MUST NOT be represented as physical vehicle validation.
- [LOCKED] [T08-STOP05] Fuel economy, real-vehicle stability, ANR/soak absence, and real ELM/device compatibility remain physical validation claims unless actually tested.
- [PENDING] [T08-E001] Record final SHA/tree/run/jobs/artifact/receipt evidence.

## 13. EXECUTION ORDER

- [LOCKED] [ORDER-0001] Execute T01 before T02.
- [LOCKED] [ORDER-0002] Execute T02 before T03.
- [LOCKED] [ORDER-0003] Execute T03 before T04.
- [LOCKED] [ORDER-0004] Execute T04 before T05.
- [LOCKED] [ORDER-0005] Execute T05 before T06.
- [LOCKED] [ORDER-0006] Execute T06 before T07.
- [LOCKED] [ORDER-0007] Execute T07 before T08.
- [LOCKED] [ORDER-0008] Do not skip a task by assuming an earlier agent completed it; require remote evidence and item status.
- [LOCKED] [ORDER-0009] A task with no required code change may be marked `N/A` only after its RED/verification item proves current behavior already satisfies the contract.

## 14. CHECKPOINT FORMAT

- [LOCKED] [CP-0001] Every checkpoint MUST list `CURRENT_REMOTE_HEAD=<sha>`.
- [LOCKED] [CP-0002] Every checkpoint MUST list `CURRENT_TASK=<task-id>`.
- [LOCKED] [CP-0003] Every checkpoint MUST list changed item IDs.
- [LOCKED] [CP-0004] Every checkpoint MUST list each changed item status.
- [LOCKED] [CP-0005] Every checkpoint MUST list exact tests run.
- [LOCKED] [CP-0006] Every checkpoint MUST list test result per item.
- [LOCKED] [CP-0007] Every checkpoint MUST list commit SHA if a remote write occurred.
- [LOCKED] [CP-0008] Every checkpoint MUST list unresolved/blocking item IDs.
- [LOCKED] [CP-0009] Every checkpoint MUST list the next single item ID to execute.

## 15. COMPLETION CONTRACT

- [LOCKED] [DONE-0001] The plan is not complete while any required item remains `PENDING`, `IN_PROGRESS`, `RED`, `BLOCKED`, or unreported.
- [LOCKED] [DONE-0002] `GREEN` means the item test passes; it does not automatically mean the item is `PROVEN`.
- [LOCKED] [DONE-0003] `PROVEN` requires recorded remote evidence tied to the exact item ID.
- [LOCKED] [DONE-0004] Final project completion requires all mandatory T01..T08 acceptance/evidence items to be `PROVEN` or explicitly justified `N/A`.
- [LOCKED] [DONE-0005] Physical vehicle validation remains separate from software completion.
