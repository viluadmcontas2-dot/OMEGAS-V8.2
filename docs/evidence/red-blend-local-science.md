# RED Blend Local Science — Evidence

Date: 2026-08-30
WorkUnit: `RED-BLEND-001`
Issue: `#11`
Branch: `work/red-v82-science-blend`

## Scope

This evidence closes only the first synthetic/offline harness milestone for local `Tinj | RPM, MAP` distribution characterization and multimodality falsification. It does not prove the real historical corpus, transfer across sessions/epochs, causal MAP_K response, `P(improve)`, vehicle behavior, fuel economy, or production readiness.

## TDD RED proof

- Source SHA: `75eb2f09eb70f28bba7da764159073ebc92d81b5`
- Source tree: `126a3374398adb1dfd8cff08c21fada2bd882222`
- Workflow: `OMEGAS RED V8.2 Science Blend`
- Run: `33323262548`
- Job: `99288842264`
- RED fast contracts: `PASS` / `QUALITY_GATE_FAST=PASS`
- Blend science tests: `FAIL` as designed
- Exact failure reason: `ModuleNotFoundError: No module named 'lab.red_blend.local_science'`

This proves the new tests were present and failed before the scientific implementation existed, while the inherited RED fast contracts remained green.

## Implementation GREEN proof

- Source SHA: `508548b2a354bf02f91bd7246933ef73fafebb8b`
- Source tree: `38a2093ec9f2477cd986dee20a38d72327d84e84`
- Workflow: `OMEGAS RED V8.2 Science Blend`
- Run: `33323399196`
- Job: `99289202995`
- Runner class: standard GitHub-hosted `ubuntu-latest`
- RED fast contracts: `PASS`
- Blend local science tests: `PASS` (6/6)
- Android build: not run in this phase
- ECU operation: none
- Production Android/runtime source changed: no

## Scientific harness proven in this milestone

The dependency-free offline laboratory now tests and implements:

- local sample count, mean, median, sample standard deviation, MAD, P10/P90 and CV;
- seeded bootstrap/Monte Carlo interval for the sample mean;
- deterministic reproducibility of the bootstrap;
- empirical tightening of the bootstrap interval when the same distribution is sampled more densely;
- maximum-likelihood one-Gaussian fit with finite variance floor;
- deterministic two-component one-dimensional Gaussian mixture fit using EM;
- numerically stable log-sum-exp likelihood evaluation;
- BIC model comparison with 2 parameters for one Gaussian and 5 free parameters for the two-component mixture;
- conservative multimodality decision requiring all three: BIC gain, minimum component mass and standardized mode separation;
- explicit `LAB_HEURISTIC` label so the current thresholds cannot masquerade as production-calibrated authority;
- rejection of a tiny 3% separated outlier cluster as a second operational regime under the test policy;
- finite behavior in an extremely low-variance region.

## Gate state

`SYNTHETIC_HARNESS_PROVEN=true`

`G3_LOCAL_TINJ_DISTRIBUTION=SYNTHETIC_ONLY`

`G4_MULTIMODALITY_FALSIFICATION=SYNTHETIC_ONLY`

`REAL_CORPUS_LOCAL_DISTRIBUTION_PROVEN=false`

`REAL_CORPUS_MULTIMODALITY_PROVEN=false`

`TRANSFER_PROVEN=false`

`P_IMPROVE_PROVEN=false`

`VEHICLE_PROVEN=false`

`PRODUCTION_RUNTIME_CHANGED=false`

## Next gate

Build/reuse a privacy-safe deterministic fixture from the already governed historical corpus, verify its manifest/hash contract, and run the exact local-distribution/multimodality harness over real RPM×MAP regions. Only real-corpus counterexample search can promote G3/G4 beyond `SYNTHETIC_ONLY`.