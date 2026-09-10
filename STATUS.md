# Status — OMEGAS V8.2 Blue

- Branch: `work/omegas-blue-causal-engine`
- Current Work Unit: `OMEGAS-BLUE-ALGO-VERIFY-001`
- Primary spec lineage: `specs/001-blue-runtime-convergence/` + `specs/003-blue-system-recovery/` + issue `#28`
- Starting SHA for this work: `022945165a590781f4245c5ca0f9a8b51cbbc9f3`
- Latest verified software SHA before documentation reconciliation: `da8f25f0299dd4fe5a22515ae41198be57df5403`
- Canonical software CI: run `34419736026` — `completed/success`
- Mathematical authority: `BlueCausalEngine`
- Automatic ECU write: `FALSE`
- APK generation in current causal/OBD continuation: `NOT PERFORMED`
- Physical vehicle validation: `NOT VALIDATED`

## Current software state — BLUE-ALGO-002 / issue #28
The causal suggestion path and OBD diagnostic surface have advanced beyond the older `6fd49dbe...` checkpoint.

Software SHA `da8f25f0299dd4fe5a22515ae41198be57df5403` now provides:
- region-level evidence identity/provenance and physical timestamps rather than multiplying one aggregate by visit count;
- causal attribution only from identifiable, confirmed, isolated manual interventions;
- persisted intervention ledger and binding to the existing manual writer/ACK/readback path;
- exact Curve-or-Map proposal projection with no gain fallback and `automaticWrite=false`;
- manual editor consumption of exact Blue targets;
- OBD connection UI exposing native stage, error code, detail and bounded manual retry;
- no RFCOMM transport redesign, MP48 protocol change or automatic ECU writer.

The approved calibration authority remains:
- paired gasoline/GNV Petrol Inj. comparison under comparable RPM x MAP is the primary measured signal;
- the correction address is current GNV `RPM x current GNV Petrol Inj.`;
- GNV STFT is an optional read-only witness that can support/conflict with the MP48 measurement;
- LTFT is diagnostic only and has no additive correction vote.

## TDD proof for the last open OBD task
RED SHA `8de50123332df6bc3932a1bc02847dc45587c813` produced canonical run `34419567232` with FAST failure specifically in the new OBD diagnostic contract because `errorCode` was not yet rendered. FULL was skipped.

GREEN SHA `da8f25f0299dd4fe5a22515ae41198be57df5403` produced canonical run `34419736026`:
- `FAST contracts`: success;
- `FULL JVM lint`: success;
- software-ready marker: success;
- owner-authorized APK artifact: skipped.

Therefore no APK was generated in this continuation.

## Readback / scope audit
Compared with issue-#28 baseline `70c1a701818b212bf5f89743ab7241dbd8e7219e`, `da8f25f...` is 16 commits ahead and 0 behind. The changed production surfaces are confined to Blue causal attribution/ledger/coordinator/access/bridge plus Curve and OBD UI; no MP48 protocol file is in the compare.

## Issue #25 baseline
The earlier physical symptom — gasoline and GNV collected but measured deviation absent — was corrected in software starting at `6fd49dbe299122f8bed756c0b6d575a202663930`. That correction remains covered by current CI, but the owner has not yet physically retested the corrected/current APK in the vehicle.

## Historical APK evidence
The prior owner-authorized artifact for `6fd49dbe...` remains historical exact-SHA evidence only:
- Artifact ID `10096255535`;
- APK SHA256 `b62e40b4d8c6b094b5aecec53a00a7d5278c4d8d028ef684a3088364a2b4236e`;
- simulated ECU validation only;
- no physical economy claim.

It is not an artifact of `da8f25f...` or of later documentation commits.

## Confirmed vs unverified
Confirmed in software/CI:
- single Blue correction authority;
- no automatic ECU write;
- measured evidence remains separate from action deadband;
- causal gain requires identifiable confirmed intervention;
- ambiguous multi-actuator cases abstain;
- exact Curve/Map proposal consumption is manual;
- OBD stage/code/detail/retry diagnostics render from native state;
- FAST + full JVM/unit/lint are green on `da8f25f...`.

Still unverified physically:
- exact ELM/RFCOMM failing stage in the owner's vehicle;
- whether the OBD reconnects successfully after the new diagnostic/retry UI exposes the real failure;
- physical confirmation that issue #25 no longer reproduces;
- fuel-economy improvement.

## Final documentation gate
Documentation reconciliation started in commit `9363250bef040fca99cb16a5ecb452a8ce764ba7` and advances HEAD beyond the verified software SHA. The eventual documentation HEAD must itself finish canonical FAST -> FULL JVM/lint successfully before issue `#28` is closed. No APK should be generated merely to satisfy this documentation gate.
