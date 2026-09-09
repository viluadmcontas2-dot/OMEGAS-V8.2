# Issue 25 amendment — measured-deviation runtime publication

This amendment refines FR-009 of `spec.md` after physical observation on the owner-installed APK.

## Physical symptom
After collecting both gasoline and GNV, Learning may have stored evidence while measured-deviation cells remain empty.

## Required runtime behavior
1. Normal Learning publication MUST reconcile the current gasoline/GNV Learning snapshot into the Blue causal coordinator before presenting Blue comparisons.
2. Blue calibration hydration for that publication MUST use only complete calibration readbacks already confirmed in the current USB session. It MUST NOT silently start a full Map K or Curve K serial read.
3. If current-session Map K + Curve K readbacks are unavailable, the runtime MUST surface an explicit calibration-readback prerequisite rather than representing the state as a valid zero or silently empty measurement.
4. A valid gasoline/GNV equivalent pair MUST produce a measured comparison even when the measured deviation is zero or inside the no-action deadband.
5. The deadband MUST govern correction action/proposal only. It MUST NOT delete measurement evidence.
6. Repeated evidence for a previously seen region/visit MUST be allowed to refresh its quality/timestamp rather than permanently freezing first-observation quality.
7. This binding MUST NOT create a second correction authority, automatic ECU writer, or new MP48 protocol behavior.

## Acceptance evidence
Software SHA `6fd49dbe299122f8bed756c0b6d575a202663930` introduced focused runtime/static and JVM regressions for these requirements. Canonical run `34331755849`, attempt 2, passed FAST and `testDebugUnitTest lintDebug` on the same SHA after an unrelated one-off browser-runner flake in attempt 1.

Physical acceptance remains pending: a newly generated exact-final-SHA APK must be installed by the owner and the original petrol + GNV collection scenario repeated. Issue #25 stays open until the measured-deviation cells are confirmed populated (including valid near-zero measurements) or a new runtime failure is observed.