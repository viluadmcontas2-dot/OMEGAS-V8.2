# OMEGAS-WU-006 — Evidence Receipt G2–G4

Claim scope: `REPLAY_G2_G4_OFFLINE_NOT_PRODUCTION_NOT_VEHICLE`.

## Source identity

- science source SHA: `29e2f9356a45b31395e83d2c98a07552985ed7cc`
- science source tree: `0c80dbbc790e0e0180afdfe5240a6b88b5862252`
- workflow: `OMEGAS WU-006 Science G2-G4`
- workflow run: `33500808742`
- job: `99833459688`
- conclusion: `success`
- artifact id: `9797604261`
- artifact: `omegas-wu006-g2-g4-29e2f9356a45b31395e83d2c98a07552985ed7cc`
- artifact zip digest: `sha256:e4a787b446ea9c42d0a44d94e99f2309e53f921613288925663026996c628dcb`
- fixture gzip SHA256: `9fd4a4fda3d907af67c9c29c01b17b54cb607f13c3351b66aff553e962980d94`
- fixture: 1,708 episodes = 266 GASOLINA + 1,442 GNV
- independent session identities present in fixture: 10 GASOLINA + 18 GNV

## TDD lineage

The first workflow attempt (`b5ea53a`, run `33500304131`) failed before the intended RED because the workflow referenced a nonexistent baseline test module. It is **not** counted as scientific RED.

Valid RED #1:
- SHA `6fc77cab839b410a9306fb9470cebba1ed572f64`
- run `33500365689`
- existing 13 corpus contracts GREEN;
- fixture reconstruction GREEN, exact 1,708 episodes / gzip SHA above;
- G2 failed exactly because `lab.red_blend.real_corpus` did not yet exist.

Valid RED #2 for the completed G4 metric contract:
- SHA `d23c530e21c2b6dd109a1f96e0967a06efcfc631`
- run `33500737180`
- G2 GREEN; G3 GREEN;
- G4 failed exactly because `PredictorMetrics.mean_abs_relative_error` did not yet exist.

Final GREEN:
- SHA/tree listed above;
- 13 existing corpus tests GREEN;
- G2: 3/3 GREEN;
- G3: 9/9 GREEN;
- G4: 4/4 GREEN;
- evidence receipt creation GREEN;
- evidence artifact upload GREEN.

## G2 — Independent Replay — PROVEN

- governed fixture validated fail-closed by part identity/length/hash, Base64, gzip identity, uncompressed identity and episode schema;
- `runtime_sample_state_used_by_replay=false`;
- replay acceptance/window/trajectory contracts are independently tested;
- this proves the offline replay method on the governed fixture, not Android runtime behavior.

## G3 — Temporal / Session Independence — PROVEN_OFFLINE_METHOD

The science layer now separates local repetition from cross-session persistence. Dense duplication inside one session cannot manufacture independent-session votes in the session-balanced estimator.

Real-fixture audit:

| Fuel | Episodes | Regions with >=4 episodes | Session-audited regions (>=3 independent sessions) | Insufficient independent regions |
| --- | ---: | ---: | ---: | ---: |
| GASOLINA | 266 | 7 | 6 | 1 |
| GNV | 1,442 | 15 | 13 | 2 |

Synthetic falsification also proves that session offsets can create pooled pseudo-multimodality and that true within-session bimodality survives session centering.

`production_runtime_integrated=false`: this gate closes the offline scientific method; Kotlin/runtime integration remains a later production gate and cannot be inferred here.

## G4 — Blind chronological walk-forward — PROVEN

- tested future gasoline episodes: `247`
- leakage violations: `0`
- future data is forbidden from affecting earlier targets;
- no random shuffle of adjacent telemetry;
- dense within-session repetition and independent-session support remain reported separately.

| Estimator | Supported | Coverage | Abstention | Mean abs rel. error | Median abs rel. error | P90 | P95 | Max | Median independent sessions |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| WU-006 neighbor baseline | 213 | 0.8623481781 | 0.1376518219 | 0.0219332429 | 0.0125276948 | 0.0540617710 | 0.0801364710 | 0.1699320911 | 3 |
| Pooled Gaussian | 231 | 0.9352226721 | 0.0647773279 | 0.0415702051 | 0.0228080838 | 0.1108447704 | 0.1566843485 | 0.2190492623 | 4 |
| Session-balanced Gaussian | 231 | 0.9352226721 | 0.0647773279 | 0.0442942972 | 0.0221272123 | 0.1196947272 | 0.1566843485 | 0.2190492623 | 4 |

These results **do not justify choosing the higher-coverage Gaussian estimators as production winners**. In this held-out ruler they increase coverage but have materially worse tail/mean error than the existing neighbor baseline. Model selection/tuning therefore remains G5 and must optimize the precision×coverage×learning-speed tradeoff rather than maximize support count.

## Safety / claim boundary

- `predictor_runtime = ABSTAIN_UNCHANGED`
- `auto_write_ecu = false`
- `kotlin_runtime_integrated = false`
- `model_proven = false`
- `apk_ready_for_physical_test = false`
- `vehicle_proven = false`

`next_unproven_item = G5_RPM_MAP_TINJ_TUNING`.
