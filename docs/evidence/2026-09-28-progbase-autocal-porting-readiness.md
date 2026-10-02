# ProgBase AutoCal -> OMEGAS porting readiness

**Branch:** `OmegasVerde`  
**Recorded at source SHA:** `8081bf71feadb0c68fd991fd73cad3cd421fda13`  
**Scope:** AutoCal only. This is a porting-control document, not a claim that every hidden Pascal routine has been recovered.

## 1. Snapshot publication, in plain language

The new full-snapshot fence does **not** hide a good snapshot.

It blocks only a torn full read:

1. OMEGAS probes the native AutoCal epoch.
2. It reads the full AutoCal field group.
3. It probes the epoch again.
4. If the epoch changed, OMEGAS discards that full read before building/publishing it.

Reason: original Lognovo/Portmon evidence shows the ECU can roll CURRENT -> PREV, update `MUL_ACT`, and increment AutoMatch state at the exact boundary where AutoCal data matures. Publishing a mixed snapshot would show a false truth. This is a safety/fidelity guard, not a UI suppression rule.

## 2. Authority order for porting

| Rank | Authority | Use |
|---:|---|---|
| 1 | Real Portmon/log fixture from original ProgBase session | Wire timing, observed frames, observed effects |
| 2 | `tests/fixtures/progbase-autocal-action-map-v1.json` | Host action identity and mode/frame map |
| 3 | `tests/fixtures/progbase-autocal-consumer-map-v1.json` | Producers, consumers, payload offsets, chart roles |
| 4 | `docs/evidence/2026-09-21-progbase-autocal-byte-consumer-matrix.md` | Human-readable explanation of byte/consumer evidence |
| 5 | `docs/evidence/2026-09-21-omegas-autocal-progbase-parity-matrix.md` | Current OMEGAS parity status |
| 6 | `STATUS.md` | Operational summary; can be stale when it conflicts with fixtures |

Important conflict: `STATUS.md` currently lists the AutoCal action modes differently from `progbase-autocal-action-map-v1.json`. For porting, the fixture wins because it declares canonical EXE raw RTTI + wrapper proof.

## 3. Routine map ready for port

| Routine | ProgBase UI/handler | Original command/evidence | OMEGAS port contract | Current port status |
|---|---|---|---|---|
| Enable AutoCal | `CheckAutoCalEnableBeforeSetData` / `AUTO_CAL_ENABLE` | `0x014A`, `TAebNumber`, checkbox consumer | Expose enable/disable with explicit state, no hidden writer, ACK/readback where mutation happens | Present; verify against UI/manual action contracts |
| Manual AutoMatch | `ActionAutoMatchExecute` | Fixture v2: mode `0x08`, frame `02 24 04 08 32` | Treat as ECU-native action, not app-calculated formula; require human intent and readback | Needs final comparison against OMEGAS action manager |
| Reset petrol point | `ActionResetPetrolExecute` | Fixture v2: mode `0x01`, frame `02 24 04 01 2B` | Do not promise selective physical effect unless log proves it | Should stay blocked or strongly interlocked until effect is proven |
| Reset gas point | `ActionResetGasExecute` | Fixture v2: mode `0x02`, frame `02 24 04 02 2C` | Do not promise harmless/selective reset; backup before mutation | Existing #98 policy must be rechecked against fixture conflict |
| Reset all | `ActionResetAllExecute` | Fixture v2: mode `0x04`, frame `02 24 04 04 2E`; broad reset observed in Lognovo | Operationally dangerous; do not expose casually; if exposed, require backup, warning, confirmation, ACK/readback | Must remain guarded; no automatic use |
| Modify map refs | `ActionAutoCalRifExecute` | Separate complex path, not a `0x24` mode wrapper | Needs its own port plan; do not fake with mode frame | Not ready for full port; needs deeper evidence |
| Reset K factor | `ActionResetKFactorExecute` | Sets every `MUL_ACT[i] = 1.0` through `TAebVector.SetDouble -> SetData -> SetDataInEcu` | Destructive calibration write; require explicit human review and full pre-write backup | Not safe to auto-port; needs UI interlock and readback |
| Finish AutoCal | `ActionFinishAutocalExecute` | `NUM_ATUOMATCH_EXECUTED` / EE mirror indicates finish/persist path, but exact wire sequence not fully resolved here | Must be documented before porting; finish cannot be guessed from UI caption | Evidence gap |
| Delete selected points | `ActionDeleteSelectedPointsExecute` | `GAS_POINT_2DELETE`, `PETROL_POINT_2DELETE` vectors exist | Need point identity, selection semantics, ACK/readback path | Evidence gap |
| Go to min calibration | `ActionGoToMinCalibrationExecute` | UI action exists; command/effect not resolved here | Treat as unknown navigation/calibration action | Evidence gap |
| Export chart / preview / color settings | UI-only actions | DFM/UI action list | OMEGAS can implement modern UX equivalent; no ECU equivalence needed | Low risk |

## 4. Producer/consumer map to preserve

| Original producer | Wire/cadence evidence | Original consumer | OMEGAS requirement |
|---|---|---|---|
| Live telemetry `48 01 49` | Portmon median around 45-46 ms; UI TimerDati 75 ms | `RunPoint`, axes, `CurrentBand` | AGORA/live point must stay independent from slow AutoCal snapshots |
| `PETR_INJ_TBP` `0x014B` | DFM/DM, no compact cadence assigned | `KLine.x`, `PetrolCurve.x`, `GasCurve.x`, acquisition geometry | Refresh with reference group; do not invent separate X axis |
| `MNFLD_PRESS_THD` `0x014C` | DFM/DM, signed vector | `CurrentBand`, `AcqusitionAreas` | Use threshold interval `threshold[i] < MAP <= threshold[i+1]` |
| `NUM_BUF_UPD_PETR` `0x015B` | `29 5B 01 85`, about 2 s | petrol maturity / `NumPetrPt` | Part of acquisition refresh group |
| `NUM_BUF_UPD_GAS` `0x015C` | `29 5C 01 86`, about 2 s | gas maturity / `NumGasPt` | Part of acquisition refresh group and maturity tracker |
| `PETR_INJ_TBUF_GAS_PREV` `0x015D` | about 2 s | `GasPointPrev.x` | Preserve previous-current distinction |
| `MNFLD_PRESS_BUF_GAS_PREV` `0x015E` | about 2 s, signed | `GasPointPrev.y` | Preserve previous-current distinction |
| `PETR_INJ_TBUF_GAS` `0x015F` | about 2 s | `GasPoint.x` | Current GNV acquisition point |
| `MNFLD_PRESS_BUF_GAS` `0x0160` | about 2 s, signed | `GasPoint.y` | Current GNV acquisition point |
| `MUL_ACT` `0x0161` | `29 61 01 8B`; reference coherence needed | `KLine.y` | Keep coherent with reference/curve updates; never auto-write |
| `PETR_INJ_TBUF` `0x0162` | about 2 s | `PetrolPoint.x` | Petrol acquisition point |
| `MNFLD_PRESS_BUF` `0x0163` | about 2 s, signed | `PetrolPoint.y` | Petrol acquisition point |
| `VECT_AUTOCAL_U8_1` index 1 | `0A 65 01 01 71` in Lognovo | buffer/update threshold | Preserve indexed identity |
| `VECT_AUTOCAL_U8_2` index 2 | `0A 65 01 02 72`; DFM `MaxAutomatch` | max automatch | Treat index 2 as `MAX_AUTOMATCH` |
| `ACQUIRED_ZONES_PETROL` `0x016F` | `29 6F 01 99`, about 2 s | `AcqusitionAreas` | Preserve Z1..Zn identity, not only count |
| `ACQUIRED_ZONES_GAS` `0x0170` | `29 70 01 9A`, about 2 s | `AcqusitionAreas` | Preserve Z1..Zn identity, not only count |
| `NUM_AUTOMATCH_EXECUTED` `0x0174` | `09 74 01 7E`, sparse/event-like | finish/automatch count | Use as state/counter, not as formula proof |
| `PETR_MNFLD_PRESS_RV` `0x018D` | `29 8D 01 B7`, about 4 s | `PetrolCurve.y` | Reference refresh group |
| `GAS_MNFLD_PRESS_RV` `0x018E` | `29 8E 01 B8`, about 4 s | `GasCurve.y` | Reference refresh group |

## 5. OMEGAS architecture required by the evidence

Do not build one giant slow snapshot loop as the main AutoCal truth.

Porting shape:

1. Live plane: `48 01 49` remains latest-only telemetry for AGORA/RunPoint.
2. Native epoch probe: compact AutoCal status before sensitive grouped reads.
3. Acquisition group around 2 s: current/previous gas, petrol, counters, zones.
4. Reference group around 4 s: `PETR_INJ_TBP`, `MNFLD_PRESS_THD`, `MUL_ACT`, petrol RV, gas RV.
5. Full snapshot only for bootstrap/scientific/event boundaries.
6. Every grouped/full read must be fenced by native epoch before publication.
7. Manual destructive commands require backup, warning, human confirmation, ACK/readback.
8. No app-side automatic Map K/Curve K write.

## 6. Not ready to port yet

| Area | Missing evidence | Stop condition |
|---|---|---|
| `FinishAutocal` | Exact command/write/persist sequence | Do not implement finish as guessed write |
| `Modify map refs` | Data write path and point serialization for all 18 refs | Do not infer from dialog layout alone |
| Delete selected petrol/gas points | Exact vector payload and action sequencing | Do not expose point deletion without replay/readback proof |
| Reset K factor | UI interlock and complete readback/backup policy | Do not expose as quick button |
| Automatch formula | ECU internal algorithm not recovered from observable evidence | Do not invent formula; observe inputs/outputs and classify deltas |
| EE persistence mirror | Exact commit/finish timing | Do not claim EEPROM persistence without readback evidence |

## 7. Port checklist

| Gate | Required proof |
|---|---|
| Original evidence | Fixture/log/DFM/action-map line identifies producer/action |
| OMEGAS producer | Kotlin/native class and method identified |
| OMEGAS consumer | UI/projection/session consumer identified |
| RED | A test or fixture proves the missing/wrong behavior |
| GREEN | Focused test passes on remote SHA |
| Reality gate | Replay/render/receipt proves the operator-visible effect when applicable |
| Safety | No automatic write; destructive actions have backup + confirmation + readback |

## 8. Immediate next work units

1. Reconcile action-mode conflict in `STATUS.md` against `progbase-autocal-action-map-v1.json`.
2. Audit `AutoCalNativeActionManager.kt` against the fixture action map and produce REDs for any mismatch.
3. Add a fixture/test for `FinishAutocal` only after exact command evidence is found.
4. Build an AutoMatch discovery table from real sessions: before/after `MUL_ACT`, `NUM_AUTOMATCH_EXECUTED`, petrol/gas points, MAP/Tinj bands, and observed percentage movement.
5. Keep the full snapshot epoch fence from `8081bf71...`; it is a fidelity guard against false mixed AutoCal state.
