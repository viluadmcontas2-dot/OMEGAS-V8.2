# OMEGAS-BLUE-ALGO-VERIFY-001 — Algorithm chain verification + didactic refinement

- Lineage: closed convergence issue `#16` + closed recovery epic `#18` + runtime defect `#25`
- Branch authority: `work/omegas-blue-causal-engine`
- Starting SHA: `022945165a590781f4245c5ca0f9a8b51cbbc9f3`
- Latest verified software/artifact-source SHA before this documentation checkpoint: `6fd49dbe299122f8bed756c0b6d575a202663930`
- Artifact gate: owner-authorized for manual APK artifact generation only
- Physical validation: `NOT VALIDATED`

## Purpose
Verify the real runtime chain `MP48 evidence -> learning -> petrol/CNG equivalence -> measured deviation -> causal actuator gain -> Blue proposal -> Learning/Curve K presentation -> manual review` and reduce operator confusion without creating a second scientific engine.

## Runtime defect #25 — measured deviation remained empty
The owner physically observed on the prior installed artifact that gasoline and GNV could both be collected while Learning cells still showed no measured-deviation value.

Root-cause chain verified against production runtime:
1. normal Learning publication exported/stored gasoline and GNV but did not refresh that snapshot into `BlueCalibrationCoordinator` before asking for active comparisons;
2. the coordinator could exist with no calibration state, so ingestion could not reconcile equivalent petrol/CNG evidence;
3. the causal engine discarded valid zero/small deviations because the action deadband was incorrectly applied as an evidence filter.

Correction in software SHA `6fd49dbe299122f8bed756c0b6d575a202663930`:
- normal state/proposal reads ingest current Learning evidence;
- Blue initializes only from already-confirmed same-session `k_map_cache.json` + `k_factor_cache.json` readbacks and never starts a hidden serial full read;
- missing calibration readback is explicit as `CALIBRATION_READBACK_REQUIRED`;
- repeated evidence updates the same region/visit instead of freezing first-observation quality;
- measured zero/small deviation remains visible evidence;
- the deadband controls only whether a correction action is proposed;
- no automatic ECU write, MP48 mutation or new correction authority was introduced.

Focused regression coverage added:
- normal Blue publication is statically bound to Learning snapshot ingestion;
- calibration hydration requires complete, confirmed, same-session Map K + Curve K caches and records `serialReadStarted=false`;
- zero deviation remains a `FuelComparison`;
- small deviation remains measured while action stays in deadband;
- proposal availability remains false inside the action deadband even when a gain exists.

## Current exact-SHA software evidence
Canonical `OMEGAS Blue CI` run `34331755849` targets `6fd49dbe299122f8bed756c0b6d575a202663930`.

Attempt 1:
- the new Blue ingestion contract executed successfully;
- unrelated `curve-runtime-interaction.test.cjs` browser execution returned empty output after about 15.5 seconds, so FAST failed and FULL/APK were correctly blocked.

Attempt 2, same exact SHA, after rerunning the failed FAST job through the GitHub connector:
- `FAST contracts`: success;
- `FULL JVM lint`: success;
- `./gradlew testDebugUnitTest lintDebug`: `BUILD SUCCESSFUL`;
- `READY_FOR_APK_GENERATION=true`;
- `SOURCE_SHA=6fd49dbe299122f8bed756c0b6d575a202663930`.

The successful rerun distinguishes the first browser failure as executor/browser flakiness rather than a reproducible regression in the changed code. No unrelated Curve K production code was modified in response to that one-off failure.

## Pre-documentation APK evidence
Because the existing GitHub owner-rerun gate keys off `run_attempt > 1`, attempt 2 also executed the artifact job. This is valid artifact evidence for the exact code SHA but is intentionally treated as pre-documentation/intermediate evidence because this workunit update advances HEAD.

- Artifact ID `10096255535`.
- Name `omegas-blue-6fd49dbe299122f8bed756c0b6d575a202663930`.
- `OMEGAS_BLUE_APK_GATE=PASS`.
- `OWNER_AUTHORIZED=true`.
- `OWNER_AUTHORIZATION_MODE=owner_rerun`.
- `SOURCE_SHA=6fd49dbe299122f8bed756c0b6d575a202663930`.
- `APK_PATH=app/build/outputs/apk/debug/app-debug.apk`.
- `APK_SHA256=b62e40b4d8c6b094b5aecec53a00a7d5278c4d8d028ef684a3088364a2b4236e`.
- `APK_BYTES=4325842`.
- `APK_RECEIPT_VERIFICATION=PASS`.
- ZIP bytes `4326619`.
- ZIP digest `sha256:a1fa33e2f4b82fd8400b61a52d030782ef71c2b235e2f992953595420eb24ae3`.
- `SIMULATED_ECU_ONLY=true`.
- `NO_INSTALL_PERFORMED=true`.
- `PHYSICAL_FUEL_ECONOMY_CLAIMED=false`.

## Invariants
- `BlueCausalEngine` is the only correction-math authority.
- MP48 is physical/calibration truth and was not changed by this work.
- OBD is read-only GNV STFT witness; LTFT does not vote; conflict cannot raise confidence.
- Map K address uses current GNV RPM x current GNV Petrol Inj.
- No automatic ECU writer. Manual mutation remains review -> confirm -> ACK -> readback.
- Runtime Learning must not trigger a hidden full Map K/Curve K serial read; it consumes only readbacks already confirmed for the current USB session.
- RPM is not an arbitrary write-authorization gate.

## Required behavior status
- Valid petrol/CNG pair shows measured deviation: software path and regressions corrected in `6fd49dbe...`; physical retest pending.
- Zero/small valid deviation remains a measurement rather than disappearing: regression-covered.
- Deadband suppresses action, not evidence: regression-covered.
- Missing calibration readback explains the missing prerequisite instead of silently yielding an empty Blue state: software-covered.
- Missing equivalent pair explains what is missing: covered by current Learning tests.
- Missing causal gain never fabricates a K target: covered by current Learning tests.
- Agreeing OBD witness may raise confidence; conflict may not; LTFT remains absent: covered by current contracts.
- Map K location is the current GNV region: covered by current contracts/UI tests.
- Selection/preview never writes ECU; relative delta and absolute assignment remain distinct: covered by current Curve K contracts.
- Missing values render as unknown, never as valid zero: covered by current Learning UI tests.
- A valid before/write-readback/after sequence producing a runtime causal gain across attributed multi-point/multi-cell interventions remains pending policy.

## Causal-attribution guardrail
Full runtime integration of causal gain remains `PENDING` until the policy specifies how to attribute `K_effective` to a confirmed manual intervention across before/after evidence, especially when the intervention changes multiple Curve K points or multiple Map K cells.

Do not substitute a constant gain, `1.0`, guessed K percentage, or automatic calibration. Without that attribution rule, production must continue to surface measured deviation and missing-gain state instead of inventing a proposal target.

## Documentation and final exact-SHA gate
This file records the `6fd49dbe...` artifact only for that exact source SHA. Because this documentation update advances HEAD, that artifact must not be called the artifact of the documentation commit. Before software delivery is complete, the final documentation HEAD must itself pass canonical FAST/FULL and receive a fresh owner-authorized APK artifact.

Physical validation remains explicitly `NOT VALIDATED`: the corrected APK has not yet been installed/retested against the owner scenario, and no real fuel-economy claim is made.