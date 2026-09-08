# OMEGAS-BLUE-ALGO-VERIFY-001 — Algorithm chain verification + didactic refinement

- Lineage: closed convergence issue `#16` + closed recovery epic `#18`
- Branch authority: `work/omegas-blue-causal-engine`
- Starting SHA: `022945165a590781f4245c5ca0f9a8b51cbbc9f3`
- Latest verified software/artifact-source SHA before this documentation checkpoint: `753d04f80c395dcfe039d5840be6b45ee08e605b`
- Artifact gate: owner-authorized on 2026-09-08 for manual APK artifact generation only
- Physical validation: `NOT VALIDATED`

## Purpose
Verify the real runtime chain `MP48 evidence -> learning -> petrol/CNG equivalence -> measured deviation -> causal actuator gain -> Blue proposal -> Learning/Curve K presentation -> manual review` and reduce operator confusion without creating a second scientific engine.

## Current exact-SHA software and APK evidence
Canonical `OMEGAS Blue CI` run `34258711514` on `753d04f80c395dcfe039d5840be6b45ee08e605b` completed successfully.

Attempt 1 proved the normal push gate:
- `FAST contracts`: success; `QUALITY_GATE_FAST=PASS python=24 node=24`.
- `FULL JVM lint`: success.
- `Owner-authorized APK artifact`: skipped.

After the owner-authorized GitHub connector re-run, attempt 2 proved the manual artifact gate:
- `FAST contracts`: success.
- `FULL JVM lint`: success.
- `Owner-authorized APK artifact`: success.
- `OMEGAS_BLUE_APK_GATE=PASS`.
- `OWNER_AUTHORIZED=true`.
- `OWNER_AUTHORIZATION_MODE=owner_rerun`.
- `SOURCE_SHA=753d04f80c395dcfe039d5840be6b45ee08e605b`.
- `APK_PATH=app/build/outputs/apk/debug/app-debug.apk`.
- `APK_SHA256=5a86a8d3a49a484c1761d0a809bfaf189b8ee1f3763091cb3ff0bc31e5d09b33`.
- `APK_BYTES=4324190`.
- `APK_RECEIPT_VERIFICATION=PASS`.
- `VERIFIED_APK_SHA256=5a86a8d3a49a484c1761d0a809bfaf189b8ee1f3763091cb3ff0bc31e5d09b33`.
- `VERIFIED_APK_BYTES=4324190`.
- `SIMULATED_ECU_ONLY=true`.
- `NO_INSTALL_PERFORMED=true`.
- `PHYSICAL_FUEL_ECONOMY_CLAIMED=false`.

Artifact evidence:
- Artifact ID `10069101616`.
- Name `omegas-blue-753d04f80c395dcfe039d5840be6b45ee08e605b`.
- ZIP bytes `4324967`.
- ZIP digest `sha256:c649e4ad07debbab3a8852be7858413067181cc2f1ee45003b9f65a1d0edbf7d`.
- The artifact was downloaded through the GitHub connector and contained the APK plus `build/evidence/omegas-blue-apk.txt`; independent read-back of the downloaded APK matched the receipt SHA256 and byte size.

## WebView regression correction
Ancestor commit `4aa2d1b0ab1feb88e4cc3f3eedfb20376694961c` keeps the Aprender/Learning and OBD UI assets parseable on legacy WebView by avoiding unsupported modern JavaScript syntax that could abort rendering before mount. The focused contract and later canonical FAST evidence are green.

## Invariants
- `BlueCausalEngine` is the only correction-math authority.
- MP48 is physical/calibration truth and was not changed by this work.
- OBD is read-only GNV STFT witness; LTFT does not vote; conflict cannot raise confidence.
- Map K address uses current GNV RPM x current GNV Petrol Inj.
- No automatic ECU writer. Manual mutation remains review -> confirm -> ACK -> readback.
- RPM is not an arbitrary write-authorization gate.

## Required behavior status
- Valid petrol/CNG pair shows measured deviation: covered by current Learning tests.
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
This file records a real APK only for its explicitly stated source SHA. Because recording that evidence advances HEAD, the recorded `753d04f...` artifact must not be called an artifact of this documentation commit. Before final completion, the new documentation HEAD must itself pass canonical FAST/FULL and receive a fresh owner-authorized APK artifact. The final-HEAD run/artifact receipt remains authoritative in GitHub Actions so documentation does not create a recursive SHA/artifact loop.

Physical vehicle validation remains explicitly `NOT VALIDATED`: no APK installation, no vehicle test, and no real fuel-economy claim were performed by this work unit.
