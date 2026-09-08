# Status — OMEGAS V8.2 Blue

- Branch: `work/omegas-blue-causal-engine`
- Current Work Unit: `OMEGAS-BLUE-ALGO-VERIFY-001`
- Primary spec lineage: `specs/001-blue-runtime-convergence/` + `specs/003-blue-system-recovery/`
- Starting SHA for this work: `022945165a590781f4245c5ca0f9a8b51cbbc9f3`
- Latest verified software/artifact-source SHA before this documentation checkpoint: `753d04f80c395dcfe039d5840be6b45ee08e605b`
- Mathematical authority: `BlueCausalEngine`
- Automatic ECU write: `FALSE`
- Owner-authorized APK generation: `AUTHORIZED_AND_PROVEN_ON_PRE_DOC_SHA`
- Physical vehicle validation: `NOT VALIDATED`

## Current remote evidence
Canonical workflow `OMEGAS Blue CI` run `34258711514` proved source SHA `753d04f80c395dcfe039d5840be6b45ee08e605b`.

Attempt 1 preserved the normal push boundary:
- `FAST contracts`: success.
- FAST log: `SOURCE_SHA=753d04f80c395dcfe039d5840be6b45ee08e605b` and `QUALITY_GATE_FAST=PASS python=24 node=24`.
- `FULL JVM lint`: success.
- `Owner-authorized APK artifact`: skipped.

Attempt 2 was an explicit repository-owner re-run through the GitHub connector after the software gate was green:
- `FAST contracts`: success.
- `FULL JVM lint`: success.
- `Owner-authorized APK artifact`: success.
- `OWNER_AUTHORIZATION_MODE=owner_rerun`.
- `OMEGAS_BLUE_APK_GATE=PASS`.
- `SOURCE_SHA=753d04f80c395dcfe039d5840be6b45ee08e605b`.
- `APK_PATH=app/build/outputs/apk/debug/app-debug.apk`.
- `APK_SHA256=5a86a8d3a49a484c1761d0a809bfaf189b8ee1f3763091cb3ff0bc31e5d09b33`.
- `APK_BYTES=4324190`.
- `APK_RECEIPT_VERIFICATION=PASS` with independently recomputed identical SHA256 and byte size inside GitHub Actions.
- `SIMULATED_ECU_ONLY=true`.
- `NO_INSTALL_PERFORMED=true`.
- `PHYSICAL_FUEL_ECONOMY_CLAIMED=false`.

GitHub Actions artifact:
- ID: `10069101616`.
- Name: `omegas-blue-753d04f80c395dcfe039d5840be6b45ee08e605b`.
- Artifact ZIP bytes: `4324967`.
- Artifact ZIP digest: `sha256:c649e4ad07debbab3a8852be7858413067181cc2f1ee45003b9f65a1d0edbf7d`.
- The ZIP downloaded through the GitHub connector contained exactly `app/build/outputs/apk/debug/app-debug.apk` and `build/evidence/omegas-blue-apk.txt`; independent read-back of the downloaded APK matched `5a86a8d3a49a484c1761d0a809bfaf189b8ee1f3763091cb3ff0bc31e5d09b33` and `4324190` bytes.

## WebView black-screen correction
The Learning/Aprender and OBD black-screen regression was addressed in ancestor commit `4aa2d1b0ab1feb88e4cc3f3eedfb20376694961c` by keeping browser UI assets parseable on legacy WebView, avoiding unsupported modern JavaScript syntax while preserving the Node test exports. Exact-SHA CI and the later FAST suite remained green after this correction.

## Causal-gain boundary
Full runtime integration of causal gain remains `PENDING` until a policy specifies how to attribute `K_effective` to a confirmed manual intervention across before/after evidence, especially when one intervention changes multiple Curve K points or multiple Map K cells.

Do not substitute a constant gain, `1.0`, guessed K percentage, or automatic calibration. Production must keep surfacing measured deviation plus missing-gain state rather than inventing a target.

## Safety invariants
- MP48 remains physical/calibration truth and was not altered by this work.
- `BlueCausalEngine` remains the only correction-math authority.
- No automatic ECU writer was introduced.
- Manual mutation remains review -> confirm -> ACK -> readback.
- APK generation does not authorize installation, distribution, release upload, or a physical vehicle claim.

## Exact-SHA documentation rule
This documentation commit necessarily advances branch HEAD after recording the real APK above. Therefore the artifact recorded above is evidence for its stated source SHA only and is never relabeled as an artifact of this documentation commit. Before completion, the canonical workflow must be green on the new documentation HEAD and a fresh owner-authorized artifact must be generated for that exact final HEAD. The authoritative final-HEAD run/artifact receipt lives in GitHub Actions to avoid an impossible recursive documentation-commit loop.
