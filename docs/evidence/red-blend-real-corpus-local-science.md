# RED Blend — Governed Real-Corpus Local Science Evidence

Date: 2026-08-30
WorkUnit: `RED-BLEND-001`
Issue: `#11`
Branch: `work/red-v82-science-blend`

## Scope

This checkpoint proves that the dependency-free Blend science harness can consume the exact governed privacy-safe WU-006 episode fixture, preserve its identity fail-closed, and audit local `Tinj | RPM, MAP` distributions on the real historical corpus.

It does **not** prove cross-session transfer, causal MAP_K response, production thresholds, `P(improve)`, vehicle behavior, fuel economy, or Android runtime promotion.

## Corpus identity

The Blend branch reuses the exact Git blobs from `work/wu-006-calibration-science-hardening` for the governed fixture and reconstruction tool; it does not regenerate or reinterpret the source corpus.

- index schema: `omegas-science-episode-fixture-index-v1`
- split parts: 8
- compressed bytes: 34,846
- compressed SHA256: `9fd4a4fda3d907af67c9c29c01b17b54cb607f13c3351b66aff553e962980d94`
- uncompressed bytes: 347,449
- uncompressed SHA256: `ae050e6770143bd042cc0416fc66cbd91d5694d7ca7917e2d9cfdf078f34a8fd`
- episode lines: 1,708
- gasoline episodes: 266
- CNG episodes: 1,442

Only privacy-safe episode fields are accepted: `session_key`, `order`, `fuel`, `start_ms`, `end_ms`, `rpm`, `map_bar`, `petrol_ms`, `window_count`, `rpm_bin`, `map_bin`. `session_key` must be a 16-hex privacy-preserving key.

## TDD RED proof

- test-only source SHA: `b8ff1a599b04af3ad4b013b0e9b6728804bf54da`
- tree: `bfd1baa69b201e0e7f7a3668f5e45ff658726694`
- Actions run: `33323738909`
- job: `99290104138`
- inherited RED fast contracts: PASS / `QUALITY_GATE_FAST=PASS`
- synthetic science: 6/6 PASS
- governed fixture reconstruction/hash verification: PASS
- real-corpus tests: FAIL exactly because `lab.red_blend.real_corpus` did not yet exist
- exact failure: `ModuleNotFoundError: No module named 'lab.red_blend.real_corpus'`

## GREEN proof

- implementation source SHA: `30ae4c43fc3852710b0b60ab92cf5f5b37021bee`
- tree: `b7b4044659d1b34adfd31d851dbd691944e73697`
- Actions run: `33323835571`
- job: `99290370439`
- standard GitHub-hosted Ubuntu runner
- inherited RED fast contracts: PASS / `QUALITY_GATE_FAST=PASS`
- synthetic science: 6/6 PASS
- governed fixture reconstruction/hash verification: PASS
- real-corpus tests: 3/3 PASS
- gasoline local audit: PASS
- CNG local audit: PASS
- Android build: not run in this phase
- ECU operation: none
- production Android/runtime source changed: no

## Real gasoline local audit

Policy: `LAB_HEURISTIC`, `min_samples=4`, seeded bootstrap 95%, current governed `RPM×MAP` bins.

- total gasoline episodes: 266
- analyzed episodes: 252
- sparse episodes: 14
- analyzed regions: 7
- `UNIMODAL_SUPPORTED`: 4
- `AMBIGUOUS_MIXTURE_SIGNAL`: 2
- `MULTIMODAL`: 1

The two densest gasoline regions are both near idle and are **not** called multimodal despite GMM BIC pressure:

- bin `(rpm=5,map=10)`: `n=92`, 10 sessions, mean ~870 RPM / 0.410 bar / 4.352 ms, CV ~2.80%, BIC gain ~12.84, but separation only ~1.14 sigma -> `AMBIGUOUS_MIXTURE_SIGNAL`.
- bin `(5,9)`: `n=82`, 9 sessions, mean ~865 RPM / 0.388 bar / 4.142 ms, CV ~4.11%, BIC gain ~42.40, but separation only ~1.57 sigma -> `AMBIGUOUS_MIXTURE_SIGNAL`.

Four other gasoline regions with sufficient mass favor the one-Gaussian model under BIC.

The only current gasoline `MULTIMODAL` candidate is sparse:

- bin `(5,13)`: `n=6`, only 2 sessions, mean ~880 RPM / 0.531 bar / 5.539 ms, BIC gain ~25.16, minimum component weight ~0.167, separation ~9.46 sigma.

Because this candidate has only six episodes and two sessions, it is a **counterexample candidate**, not evidence of a true hidden physical regime.

## Real CNG local audit

- total CNG episodes: 1,442
- analyzed episodes: 1,387
- sparse episodes: 55
- analyzed regions: 15
- `UNIMODAL_SUPPORTED`: 5
- `AMBIGUOUS_MIXTURE_SIGNAL`: 6
- `MULTIMODAL`: 4

The three densest CNG idle-ish regions contain 595, 426 and 119 episodes across 17, 17 and 10 sessions. GMM BIC often improves strongly, but standardized mode separation remains small, so all three are conservatively classified `AMBIGUOUS_MIXTURE_SIGNAL` rather than true multimodality.

The governed corpus also contains real higher-load/higher-RPM support:

- ~2,330 RPM / 0.857 bar, `n=13`, 4 sessions, petrol command ~10.017 ms, CV ~4.43% -> `UNIMODAL_SUPPORTED`.
- ~2,459 RPM / 0.856 bar, `n=13`, 5 sessions, petrol command ~9.855 ms, CV ~8.10% -> `AMBIGUOUS_MIXTURE_SIGNAL` because BIC gain is only ~2.90 despite ~3.14 sigma fitted separation.
- ~2,184 RPM / 0.858 bar, `n=10`, 5 sessions, petrol command ~10.721 ms -> `UNIMODAL_SUPPORTED`.
- ~2,347 RPM / 0.814 bar, `n=6`, 3 sessions -> current small-sample `MULTIMODAL` candidate.

## Scientific interpretation

The real corpus does **not** justify promoting GMM into the RED runtime.

The strongest apparent multimodality is concentrated in low-sample regions, while dense regions more often show either a stable one-Gaussian local reference or a broad/non-Gaussian shape that fails the physical-separation guard.

This creates a high-value falsification target: determine whether pooled mixture structure is actually explained by **between-session offsets/drift** rather than by two physical regimes at the same RPM×MAP condition.

The next gate therefore decomposes:

`petrol_ms = local_region_mean + session_effect + within_session_noise`

and evaluates leave-one-session-out transfer. A tight pooled bootstrap is not allowed to masquerade as new-session certainty.

## Gate state

`REAL_CORPUS_FIXTURE_IDENTITY_PROVEN=true`

`REAL_CORPUS_LOCAL_AUDIT_PROVEN=true`

`G3_LOCAL_TINJ_DISTRIBUTION=REAL_CORPUS_AUDITED`

`G4_MULTIMODALITY_FALSIFICATION=REAL_CORPUS_CANDIDATES_FOUND_NOT_PROMOTED`

`TEMPORAL_SESSION_INDEPENDENCE_PROVEN=false`

`TRANSFER_PROVEN=false`

`CAUSAL_MAP_K_PROVEN=false`

`P_IMPROVE_PROVEN=false`

`VEHICLE_PROVEN=false`

`PRODUCTION_RUNTIME_CHANGED=false`

## Next gate

Session-conditioned variance decomposition + leave-one-session-out replay, with explicit tests that distinguish within-session local precision from between-session persistence.