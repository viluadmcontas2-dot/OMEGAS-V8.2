# OMEGAS-SPEED — isolated CodSpeed baseline

Derived from `OmegasPlatina@117da4487b75ebce10aefbe77a74403c652daa3f`.

**Instrumentation only**: the application, ECU safeguards, Predictor abstention and scientific equivalence have not changed. No APK, server, extra npm dependency, paid runner or workflow is added.

Four scenarios cover the actual LearningModel 12×12 grid at regular and heavy comparison loads; the AutoCal reference fingerprint for 18 native points; and the AutoCal projection of 18 bands. The benchmark loads unchanged app JavaScript with synthetic snapshots. Browser boot is suppressed only inside an isolated VM.

## Baseline first

Enable this repository in CodSpeed and install/authenticate the CodSpeed CLI in a suitable runner. Then run `codspeed run -m walltime` and save its run ID. Profile the actual hot path; change one production target; rerun using a comparable environment; use CodSpeed `compare_runs` to quantify gain or regression. Running plain Node only checks correctness, **not performance**.

This scope measures WebView JavaScript algorithms, not the entire Kotlin/USB device path. Verify device CPU/GC/UI frame pacing separately. No automatic ECU writes or claims about physical vehicle test results.
