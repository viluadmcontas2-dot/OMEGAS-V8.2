# OMEGAS RED Final Engine Closure — Tasks

Parent: #30

GitHub Issues are the execution units. This file mirrors issue order/status; it does not create parallel task IDs.

## #31 — Core gasoline surface / equivalence / interpolation / causal K
- [ ] Reconcile existing test-only commits `0f760aef...` and `6931da8c...` as unexecuted RED preparation; rename/remove arbitrary T01 labels while preserving behavior intent.
- [ ] RED: gasoline 31 s old remains usable if physically/quality valid.
- [ ] RED: gasoline 30 min old remains usable.
- [ ] RED: gasoline 24 h old remains usable.
- [ ] RED: gasoline several days old remains usable.
- [ ] RED: physically incompatible gasoline remains rejected.
- [ ] RED: insufficient-quality gasoline remains rejected.
- [ ] RED: horizontal/vertical internal hole interpolates.
- [ ] RED: diagonal internal support interpolates.
- [ ] RED: irregular supported 2D neighborhood interpolates.
- [ ] RED: outside supported local geometry abstains.
- [ ] RED: Map K address is current RPM_GNV × PetrolInj_GNV.
- [ ] Run focused RED and attach failure evidence to #31.
- [ ] Implement minimum changes in existing owners only.
- [ ] Run focused GREEN + broad core regressions.
- [ ] Commit(s) reference `#31`.
- [ ] Attach exact SHA/tests/results to #31 and close only when acceptance is proven.

## #36 — Predictor + Learning UI
- [ ] Identify single reachable Predictor/advisor production owner; do not add a second one.
- [ ] RED: sparse-but-supported region produces prediction with uncertainty.
- [ ] RED: increasing coherent observation moves prediction toward observed truth and lowers uncertainty.
- [ ] RED: sufficiently strong direct observation dominates previous prediction.
- [ ] RED: outside local support returns `GLOBAL_ONLY`/`UNSUPPORTED`, not local observed truth.
- [ ] RED: primary Learning pane excludes engine names/epochs/calibration IDs/ACK-readback explanations.
- [ ] RED: pane includes RPM/MAP, gasoline expected, GNV observed, deviation, confidence/origin and suggestion when available.
- [ ] RED: grid cell copy is compact.
- [ ] Implement minimally using the #31 authority.
- [ ] Run focused GREEN + Predictor/UI regressions.
- [ ] Verify no automatic ECU write.
- [ ] Commit(s) reference `#36`; attach SHA/results and close only with evidence.

## #32 — Runtime/hot path/backpressure
- [ ] Trace actual runtime seams after #31/#36 implementation.
- [ ] RED: no full surface rebuild on each telemetry frame.
- [ ] RED: no full Predictor rebuild on each UI render/poll tick.
- [ ] RED: rapid revisions coalesce/latest relevant state wins.
- [ ] RED: UI scheduler remains self-paced/non-overlapping.
- [ ] RED: evidence persistence does not dominate polling.
- [ ] Implement cache/coalescence only where the tests prove a violation.
- [ ] Run scheduler/backpressure/soak regressions.
- [ ] Commit(s) reference `#32`; attach exact evidence.

## #33 — OBD learner/witness
- [ ] Preserve independent GNV STFT learner.
- [ ] Verify dedupe/freshness/session/epoch/READY gates.
- [ ] Verify old/insufficient witness cannot boost MP48 confidence.
- [ ] Verify absent/conflicting OBD never blocks/recalculates valid MP48 measurement.
- [ ] Verify unresolved address abstains from write while measured percentage remains observable.
- [ ] Verify any optional address bridge remains outside OBD scientific math.
- [ ] Attach RED/GREEN + exact SHA and close only when acceptance is proven.

## #34 — Consumption
- [ ] Separate estimated pressure/volume from confirmed refill consumption.
- [ ] Verify valid `distanceKm / addedM3` produces km/m³.
- [ ] Verify invalid distance/volume abstains.
- [ ] Verify restart/history/refill behavior.
- [ ] Verify UI communicates estimation uncertainty.
- [ ] Attach RED/GREEN + exact SHA and close only when acceptance is proven.

## #35 — Integrated release
- [ ] Confirm #31, #32, #33, #34, #36 software acceptance is evidenced.
- [ ] Run adversarial matrix listed in #35.
- [ ] Run `python3 -B tools/run_checks.py` on exact candidate SHA.
- [ ] Run `./gradlew testDebugUnitTest lintDebug -PomegasAbis=armeabi-v7a --no-daemon --stacktrace` on same SHA.
- [ ] Perform independent adversarial diff review.
- [ ] If review changes product code, rerun exact-SHA gates and review.
- [ ] Update `STATUS.md` with SHA/tree/runs/jobs/known limits.
- [ ] Declare `READY FOR APK GENERATION` only after all above is proven.
- [ ] Generate APK only via canonical owner-authorized workflow.
- [ ] Verify artifact integrity, APK SHA-256/bytes, signing/package metadata when tools are available.
- [ ] Record receipt without claiming physical validation.

## #25 — Physical vehicle gate
- [ ] Run only with the final APK from #35.
- [ ] Verify gasoline reference is usable without recent fuel switch.
- [ ] Verify measured deviation in supported RPM×MAP regions.
- [ ] Verify interpolable internal regions do not remain artificial holes.
- [ ] Verify source distinction measured/interpolated/predicted.
- [ ] Verify Learning usability on the multimedia.
- [ ] Verify no ECU write occurs without manual review/confirmation.
- [ ] Attach physical screenshots/logs before closing #25.
