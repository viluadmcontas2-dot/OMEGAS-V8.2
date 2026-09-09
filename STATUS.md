# Status — OMEGAS V8.2 Blue

- Branch: `work/omegas-blue-causal-engine`
- Current Work Unit: `OMEGAS-BLUE-ALGO-VERIFY-001`
- Primary spec lineage: `specs/001-blue-runtime-convergence/` + `specs/003-blue-system-recovery/`
- Starting SHA for this work: `022945165a590781f4245c5ca0f9a8b51cbbc9f3`
- Latest verified software/artifact-source SHA before this documentation checkpoint: `6fd49dbe299122f8bed756c0b6d575a202663930`
- Mathematical authority: `BlueCausalEngine`
- Automatic ECU write: `FALSE`
- Owner-authorized APK generation: `AUTHORIZED_AND_PROVEN_ON_PRE_DOC_SHA`
- Physical vehicle validation: `NOT VALIDATED`

## Issue #25 — measured deviation empty after petrol + CNG collection
Physical observation from the installed `19c3b2df92e13398126cdc82b1dbf0f7b2ba5a2d` artifact: after collecting both gasoline and GNV, Learning cells still showed no measured-deviation value.

Root cause verified in the runtime path:
- normal Learning publication exported/stored gasoline and GNV evidence but did not ingest that current snapshot into `BlueCalibrationCoordinator` before reading Blue comparisons;
- the coordinator could start without a synchronized calibration state, so evidence ingestion could not produce active comparisons;
- `BlueCausalEngine.compare()` also treated the action deadband as an evidence filter, discarding valid zero/small measured deviations entirely.

Software correction published in `6fd49dbe299122f8bed756c0b6d575a202663930`:
- normal Blue state/proposal publication refreshes the current Learning snapshot into the coordinator;
- initial Blue calibration is hydrated only from complete, `sessionConfirmed` Map K + Curve K readback caches belonging to the same USB session; no hidden serial full-map/curve read is started;
- if those readbacks are missing, runtime returns an explicit `CALIBRATION_READBACK_REQUIRED` state instead of silently pretending measured data exists;
- repeated evidence for the same region/visit updates the stored evidence instead of freezing the first sample forever;
- zero/small deviation remains a real `FuelComparison`; the deadband now suppresses only a correction action/target, not the measured value itself;
- no MP48 protocol change and no automatic ECU writer were introduced.

Canonical `OMEGAS Blue CI` run `34331755849` on `6fd49dbe299122f8bed756c0b6d575a202663930`:
- attempt 1: new Blue ingestion contracts passed, while an unrelated real-browser Curve K test flaked with empty runner output; FULL/APK were skipped by dependency;
- attempt 2, same exact SHA: `FAST contracts` success; `FULL JVM lint` success; Gradle `testDebugUnitTest lintDebug` completed `BUILD SUCCESSFUL`; `READY_FOR_APK_GENERATION=true`.
- because the connector retry advances `github.run_attempt`, the existing owner-rerun artifact gate also ran on attempt 2. This artifact is recorded only as pre-documentation evidence, not as the final documentation-HEAD artifact.

Pre-documentation artifact evidence for `6fd49dbe...`:
- Artifact ID: `10096255535`.
- Name: `omegas-blue-6fd49dbe299122f8bed756c0b6d575a202663930`.
- `OMEGAS_BLUE_APK_GATE=PASS`.
- `SOURCE_SHA=6fd49dbe299122f8bed756c0b6d575a202663930`.
- `APK_PATH=app/build/outputs/apk/debug/app-debug.apk`.
- `APK_SHA256=b62e40b4d8c6b094b5aecec53a00a7d5278c4d8d028ef684a3088364a2b4236e`.
- `APK_BYTES=4325842`.
- `APK_RECEIPT_VERIFICATION=PASS` with independently recomputed identical SHA256 and bytes inside GitHub Actions.
- Artifact ZIP bytes: `4326619`.
- Artifact ZIP digest: `sha256:a1fa33e2f4b82fd8400b61a52d030782ef71c2b235e2f992953595420eb24ae3`.
- `SIMULATED_ECU_ONLY=true`; `NO_INSTALL_PERFORMED=true`; `PHYSICAL_FUEL_ECONOMY_CLAIMED=false`.

## Previous exact-SHA artifact evidence
The earlier documentation checkpoint `19c3b2df92e13398126cdc82b1dbf0f7b2ba5a2d` had canonical run `34259363038` and an owner-authorized APK with SHA256 `fdd0a0a46ba1cc4a9e2c7531693192a8c91ceb0fe7fb8c6b38f696b641c6ec62`. That APK was physically opened and exposed issue #25; its prior software gates do not prove the corrected runtime behavior.

## WebView black-screen correction
The Learning/Aprender and OBD black-screen regression was addressed in ancestor commit `4aa2d1b0ab1feb88e4cc3f3eedfb20376694961c` by keeping browser UI assets parseable on legacy WebView, avoiding unsupported modern JavaScript syntax while preserving the Node test exports. Exact-SHA CI and later FAST suites remained green after this correction.

## Causal-gain boundary
Full runtime integration of causal gain remains `PENDING` until a policy specifies how to attribute `K_effective` to a confirmed manual intervention across before/after evidence, especially when one intervention changes multiple Curve K points or multiple Map K cells.

Do not substitute a constant gain, `1.0`, guessed K percentage, or automatic calibration. Production must keep surfacing measured deviation plus missing-gain state rather than inventing a target.

## Safety invariants
- MP48 remains physical/calibration truth and was not altered by this work.
- `BlueCausalEngine` remains the only correction-math authority.
- No automatic ECU writer was introduced.
- Manual mutation remains review -> confirm -> ACK -> readback.
- Calibration hydration for Learning uses only already-confirmed same-session caches; it does not silently perform a full ECU read.
- APK generation does not authorize installation, distribution, release upload, or a physical vehicle claim.

## Exact-SHA documentation rule
This documentation commit necessarily advances branch HEAD after recording the real `6fd49dbe...` software/artifact evidence above. Therefore that artifact is evidence for its stated source SHA only and is never relabeled as an artifact of this documentation commit. Before completion, the canonical workflow must be green on the new documentation HEAD and a fresh owner-authorized artifact must be generated for that exact final HEAD. Physical confirmation that issue #25 is actually resolved remains pending installation/retest by the owner.