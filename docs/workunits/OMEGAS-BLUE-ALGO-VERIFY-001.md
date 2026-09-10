# OMEGAS-BLUE-ALGO-VERIFY-001 — Algorithm chain verification + didactic refinement

- Lineage: closed convergence issue `#16` + closed recovery epic `#18` + runtime defect `#25` + causal/OBD issue `#28`
- Branch authority: `work/omegas-blue-causal-engine`
- Starting SHA: `022945165a590781f4245c5ca0f9a8b51cbbc9f3`
- Final verified software SHA before this documentation checkpoint: `da8f25f0299dd4fe5a22515ae41198be57df5403`
- Canonical software CI: run `34419736026` — `completed/success`
- APK on `da8f25f...`: `NOT GENERATED` (owner gate remained skipped)
- Physical vehicle validation: `NOT VALIDATED`

## Purpose
Verify the real runtime chain `MP48 evidence -> petrol/CNG equivalence -> measured deviation -> identifiable manual intervention -> causal gain -> exact Blue proposal -> Curve/Map preview -> manual review/write -> ACK/readback`, while keeping OBD as a read-only GNV STFT witness and making OBD connection failures actionable.

## Algorithm authority confirmed
The runtime contract remains intentionally narrow:
- `BlueCausalEngine` is the only correction-math authority;
- the primary error comes from paired petrol/GNV Petrol Inj. evidence under comparable RPM x MAP conditions;
- Map K addressing uses current GNV `RPM x current GNV Petrol Inj.` rather than the gasoline reference injection time;
- STFT on GNV is optional corroborating/conflicting witness evidence;
- LTFT is diagnostic context and has no correction vote;
- no `gain=1.0`, guessed `K_effective`, automatic ECU write or MP48 protocol mutation is allowed.

This matches the approved paired-calibration continuation: fast gasoline->GNV comparison is the primary measured signal; OBD strengthens or challenges confidence instead of replacing MP48.

## Issue #25 baseline
The earlier installed artifact exposed a real defect: gasoline and GNV could both be collected while Learning still showed no measured deviation. Software SHA `6fd49dbe299122f8bed756c0b6d575a202663930` corrected ingestion/calibration hydration and separated measured evidence from the correction deadband. Physical retest of that scenario is still pending.

## Issue #28 — causal suggestions + OBD diagnosis
The `70c1a701818b212bf5f89743ab7241dbd8e7219e -> da8f25f0299dd4fe5a22515ae41198be57df5403` compare is 16 commits ahead and changes only the intended causal/UI/test/documentation surfaces. Relevant production changes include `BlueCausalAttribution.kt`, `BlueCausalLedger.kt`, `BlueCalibrationCoordinator.kt`, `BlueCalibrationAccess.kt`, `BlueJavascriptBridge.kt`, `curve.js` and `obd.js`; no MP48 protocol file is part of that compare.

### Confirmed software behavior
- Evidence identity/provenance uses one region-level evidence object instead of cloning visits as independent scientific votes, and preserves physical observation time.
- Causal gain is accepted only for an identifiable isolated actuator intervention with matching region/revision and valid response direction; ambiguous multi-actuator interventions abstain.
- Pending interventions are persisted; prepare occurs before the existing manual writer and confirmation only after the writer's ACK/readback path.
- Exact Blue proposals project only the matching Curve or Map actuator target and keep `automaticWrite=false`.
- Curve/Map consumption remains preview/manual-review oriented; no automatic ECU mutation was introduced.
- OBD UI now consumes the native `connectionStage`, `errorCode`, `detail` and `retryable` state and exposes bounded manual retry without adding a parallel scheduler or changing RFCOMM transport.

## Task 5 TDD evidence
RED SHA `8de50123332df6bc3932a1bc02847dc45587c813` (`test(obd): require actionable native connection diagnostics`) produced canonical run `34419567232` with FAST failure. The failing test was specifically `obd-runtime-controls.test.cjs` case `falha OBD expõe estágio, código, detalhe e repetição manual delimitada`; the assertion failed because `errorCode` was not yet rendered. FULL was correctly skipped.

GREEN SHA `da8f25f0299dd4fe5a22515ae41198be57df5403` (`feat(obd): expose native stage code detail and manual retry`) produced canonical run `34419736026`:
- `FAST contracts`: `completed/success`;
- `FULL JVM lint`: `completed/success`;
- software-ready marker step: `success`;
- owner-authorized APK artifact job: `skipped`.

That is a real RED -> GREEN closure for the Task 5 behavior, not a retry of the same failing code.

## Task 6 exact-SHA readback
For software SHA `da8f25f0299dd4fe5a22515ae41198be57df5403`:
- remote branch HEAD was read back at that exact SHA before documentation mutation;
- canonical run `34419736026` is terminal `completed/success`;
- both FAST and FULL JVM/lint jobs are terminal `success`;
- compare against issue-#28 baseline is `ahead`, 16 commits, 0 behind;
- no new APK was generated in this continuation;
- no installation or physical vehicle validation was performed.

## Requirement status
- Paired MP48 gasoline/GNV evidence remains primary calibration signal: **CONFIRMED BY SOFTWARE CONTRACT**.
- Current-GNV Map K address: **CONFIRMED BY SOFTWARE CONTRACT/TEST COVERAGE**.
- STFT GNV optional witness; LTFT no correction vote: **CONFIRMED BY SOFTWARE CONTRACT**.
- Identifiable isolated intervention -> causal gain: **CONFIRMED BY UNIT/JOURNEY COVERAGE AND FINAL CI**.
- Ambiguous/multi-actuator intervention -> abstain: **CONFIRMED BY UNIT COVERAGE AND FINAL CI**.
- Exact Curve/Map proposal without automatic write: **CONFIRMED BY UI/JVM COVERAGE AND FINAL CI**.
- Manual prepare -> review/write -> ACK/readback attribution: **CONFIRMED IN SOFTWARE/SIMULATED TESTS**.
- OBD native stage/code/detail/retry surface: **CONFIRMED BY RED->GREEN UI CONTRACT AND FINAL CI**.
- Actual ELM/RFCOMM failure stage in the owner's vehicle: **UNVERIFIED UNTIL PHYSICAL RETEST**.
- Physical fuel-economy improvement: **UNVERIFIED**.
- Physical confirmation that issue #25 symptom is gone: **UNVERIFIED**.

## Historical artifact boundary
The prior owner-authorized artifact on software SHA `6fd49dbe...` remains valid evidence only for that SHA. This continuation intentionally generated no APK for `da8f25f...` and does not relabel an older artifact as current.

## Documentation gate
This documentation mutation advances branch HEAD beyond `da8f25f...`. The new documentation HEAD must itself pass the canonical FAST -> FULL JVM/lint workflow before the workunit can be called fully reconciled. Issue `#28` may be closed only after that final documentation-HEAD CI is terminal success.
