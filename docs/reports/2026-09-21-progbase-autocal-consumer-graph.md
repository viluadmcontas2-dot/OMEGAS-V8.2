# ProgBase AutoCal Consumer Graph — 2026-09-21

## Scope

This report reconstructs the **actual ProgBase 4.2.0.6 AutoCal consumer graph** from:

- original `ProgBase.exe` static RTTI/resource/method tables;
- x86 disassembly of published and internal methods;
- the two authoritative raw Portmon captures;
- current OMEGAS protocol object mapping.

It does **not** copy the ProgBase UI as a product design. The purpose is to recover behavior and authority boundaries so OMEGAS can later expose the same real work with a better human interface.

## Product/UX north stars

The following read-only Notion references govern later presentation design:

- `Blueprint Premium UI/UX — Método CUSTOMROM reutilizável`
- `Método aplicado — Omega Dev 4.0 Premium UI/UX`

Binding product principles used here:

- UI exposes the user's mental model, not internal architecture.
- Navigation/destinations represent human work, not code modules.
- Preserve proven backend behavior; bad UI does not justify rebuilding the engine.
- One authority of state.
- Normality stays compact; problems gain space.
- Human summary first, technical detail on demand.
- Every action exposes immediate state and consequence.
- Cards only for semantic units.
- Color is semantic, not decorative.
- Preserve context across navigation.
- Evidence is first-class.
- Design starts from task/device/user, not from visual imitation.

Therefore the native ProgBase graph below is an **engineering authority**, not a visual template.

---

## 1. Ground-truth artifacts

### ProgBase

`C:\Users\hugov\Desktop\Landi Renzo\Landi Renzo Omegas\ProgBase.exe`

- version: 4.2.0.6
- size: 12,643,840 bytes
- SHA-256: `8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4`

### Raw AutoCal capture

`G:\Meu Drive\OMEGAS\benchmark_gpu_20260918\raw\autocal\PortmonAUTOCAL (1).LOG`

- SHA-256: `4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b`

### Raw LOGNOVO capture

`G:\Meu Drive\OMEGAS\benchmark_gpu_20260918\raw\lognovo\PortmonLOGNOVO.LOG`

- SHA-256: `43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64`

---

## 2. Native authority split

The binary exposes two major AutoCal objects:

### `TAutoCalDM`

Data/ECU-facing model.

Recovered fields include:

| Offset | Native object |
|---:|---|
| 0x70 | `MNFLD_PRESS_THD` |
| 0x74 | `PETR_INJ_TBP` |
| 0x78 | `AUTO_CAL_ENABLE` |
| 0x7C | `VECT_AUTOCAL_U8_1` |
| 0x80 | `VECT_AUTOCAL_U8_2` |
| 0x84 | `EN_CDN_T_THD` |
| 0x88 | `PETR_INJ_TBUF` |
| 0x8C | `MNFLD_PRESS_BUF` |
| 0x90 | `NUM_BUF_UPD_PETR` |
| 0x94 | `PETR_INJ_TBUF_GAS_PREV` |
| 0x98 | `MNFLD_PRESS_BUF_GAS_PREV` |
| 0x9C | `PETR_INJ_TBUF_GAS` |
| 0xA0 | `NUM_BUF_UPD_GAS` |
| 0xA4 | `MUL_ACT` |
| 0xA8 | `MNFLD_PRESS_BUF_GAS` |
| 0xAC | `LIMIT_PRESSURE_MIN` |
| 0xB0 | `GAS_POINT_2DELETE` |
| 0xB4 | `PETROL_POINT_2DELETE` |
| 0xB8 | `LIMIT_PRESSURE_MAX` |
| 0xBC | `ACQUIRED_ZONES_PETROL` |
| 0xC0 | `ACQUIRED_ZONES_GAS` |
| 0xC4 | `CALIBRATION_VAL_1` |
| 0xC8 | `MODULE_VERSION` |
| 0xCC | `VECT_AUTOCAL_U8_0` |
| 0xD0 | `NUM_ATUOMATCH_EXECUTED` |
| 0xD4 | `MAX_RPM_FOR_AUTOCAL` |
| 0xD8 | `PETR_MNFLD_PRESS_RV` |
| 0xDC | `GAS_MNFLD_PRESS_RV` |
| 0xE0 | `DISABLE_ACQ_BAND` |
| 0xE4 | `DELTA_MNFLD_PRESS_THD` |
| 0xE8 | `DELTA_PETR_INJ_T_THD` |
| 0xEC | `DIFF_ENG_SPD_THD` |
| 0xF0 | `DIFF_MNFLD_PRESS_THD` |
| 0xF4 | `DIFF_PETR_TINJ_T_THD` |

The binary also contains an EEPROM-oriented `TAutoCalDM_EE` with fields such as `MUL_ACT_EE`, `MUL_PREV_EE`, and `MUL_UPD_CALL_CNTR_EE`.

### `TAutoCalUI`

Presentation/interaction object.

Recovered UI fields include:

| Offset | UI object |
|---:|---|
| 0x2B8 | `LabelNumAutoMatch` |
| 0x2BC | `LabNumAutoMatch` |
| 0x2C0 | `ChartData` |
| 0x2C4 | `RunAxes` |
| 0x2C8 | `PetrolLine` |
| 0x2CC | `GasLine` |
| 0x2D0 | `PetrolPoint` |
| 0x2D4 | `GasPointPrev` |
| 0x2D8 | `GasPoint` |
| 0x2DC | `RunPoint` |
| 0x2EC | `ButtonAutoMatch` |
| 0x2F0 | `ChartKLine` |
| 0x2F4 | `KRunAxes` |
| 0x2F8 | `KLine` |
| 0x2FC | `ChartPoint` |
| 0x300 | `NRunAxes` |
| 0x304 | `NumPetrPt` |
| 0x308 | `NumGasPt` |
| 0x30C | `CheckAutoCalEnable` |
| 0x310 | `EditNumAutomatch` |
| 0x34C | `ActionAutoCalRif` |
| 0x350 | `ActionAutoMatch` |
| 0x354 | `ActionResetPetrol` |
| 0x358 | `ActionResetGas` |
| 0x35C | `ActionResetAll` |
| 0x360 | `ActionGoToMinCalibration` |
| 0x3D8 | `ActionResetKFactor` |
| 0x3E0 | `TimerMessageSwitchToGas` |
| 0x3FC | `ActionFinishAutocal` |
| 0x404 | `BtnFinishAutomatch` |
| 0x410 | `CurrentBand` |
| 0x414 | `BtnShowBand` |
| 0x41C | `UserMessageLabel2` |
| 0x420 | `Panel3` |
| 0x424 | `PetrolCurve` |
| 0x428 | `GasCurve` |
| 0x42C | `PollingPetrol` |
| 0x430 | `PollingGas` |

This split is strong evidence that the native application already separates ECU/data authority from UI projection.

---

## 3. Published UI handlers and real code addresses

Recovered from the C++Builder published-method table:

| Handler | Address |
|---|---:|
| `ActionAutoCalRifExecute` | 0x5187A0 |
| `ActionAutoMatchExecute` | 0x5189B4 |
| `ActionResetPetrolExecute` | 0x5189C0 |
| `ActionResetGasExecute` | 0x5189CC |
| `ActionResetAllExecute` | 0x5189D8 |
| `ActionGoToMinCalibrationExecute` | 0x5189E4 |
| `ActionResetKFactorExecute` | 0x51A070 |
| `ActionFinishAutocalExecute` | 0x51A390 |
| `BtnFinishAutomatchClick` | 0x51A454 |
| `CheckAutoCalEnableBeforeSetData` | 0x51A474 |
| `BtnShowBandClick` | 0x51A74C |
| `ChartDataAfterDraw` | 0x51A998 |
| `ChartKLineClickSeries` | 0x5176D0 |
| `ChartDataClickSeries` | 0x518C3C |
| `ChartDataDblClick` | 0x518E3C |

DFM/resource bindings independently confirm these are the handlers attached to the named UI actions/components.

Examples:
- `ActionAutoMatch`: caption `Manual automatch` → `ActionAutoMatchExecute`.
- `ActionResetPetrol`: caption `Reset petrol point` → `ActionResetPetrolExecute`.
- `ActionResetKFactor`: caption `Reset k-factor...` → `ActionResetKFactorExecute`.
- `ActionFinishAutocal`: caption `FinishAutocal` → `ActionFinishAutocalExecute`.
- `CheckAutoCalEnable.OnBeforeSetData` → `CheckAutoCalEnableBeforeSetData`.

---

## 4. UI action → shared native action dispatcher

This is one of the strongest call-graph findings.

### Thin wrappers

`ActionAutoMatchExecute`:

- pushes action code `8`;
- calls shared routine `0x517568`;
- returns.

`ActionResetPetrolExecute`:
- action code `1`;
- same shared routine.

`ActionResetGasExecute`:
- action code `2`;
- same shared routine.

`ActionResetAllExecute`:
- action code `4`;
- same shared routine.

Therefore:

```text
Manual AutoMatch ── code 8 ─┐
Reset Petrol     ── code 1 ─┤
Reset Gas        ── code 2 ─┼─> shared dispatcher 0x517568
Reset All        ── code 4 ─┘
```

### Dispatcher `0x517568`

Verified flow:

1. checks native AutoCal readiness via `0x512264`;
2. performs confirmation/status logic;
3. forwards the selected action code to `0x512280`;
4. if accepted, waits/settles;
5. calls `PostActionRefresh 0x5162F8`.

### Native action bridge `0x512280`

This routine:
- re-checks whether AutoCal can operate;
- embeds the action code in a small request structure;
- invokes a deeper transport/object call;
- returns status to the UI dispatcher.

The exact serial bytes generated by this path are **not yet declared decoded**. They must be paired byte-for-byte with the raw Portmon timeline before assigning command names beyond the action-code relationship.

This strongly supports:

```text
UI intention
   ↓
thin event handler
   ↓
shared AutoCal dispatcher
   ↓
native/transport action
   ↓
ECU state changes
   ↓
refresh/projection
```

not:

```text
UI button
   ↓
local UI algorithm calculates new curve
```

---

## 5. Native curve self-adjustment

Raw Portmon evidence already proves that `MUL_ACT` changes without ProgBase transmitting a replacement 30-point vector.

Therefore the current supported authority model is:

```text
ECU native AutoCal
   ├─ acquires/maintains native buffers/state
   ├─ updates MUL_ACT internally
   └─ increments AutoMatch state/counter
            ↓
ProgBase reads state
            ↓
TAutoCalDM
            ↓
TAutoCalUI projection
```

ProgBase can still orchestrate start/finish/reset/enable/manual-AutoMatch actions. This evidence does not mean the host is passive in every lifecycle step.

---

## 6. Major projection fan-out: `PostActionRefresh 0x5162F8`

This is currently the central verified bridge from native AutoCal objects to visible UI state.

It touches these UI objects directly:

- `GasLine`
- `GasCurve`
- `PetrolPoint`
- `GasPointPrev`
- `KRunAxes`
- `RunAxes`
- `PetrolLine`
- `PetrolCurve`
- `LabNumAutoMatch`
- `PollingPetrol`
- `Panel3`

It reads/refreshes native data objects including:

- `AUTO_CAL_ENABLE`
- `EN_CDN_T_THD`
- `PETR_INJ_TBUF`
- `MNFLD_PRESS_BUF`
- `NUM_BUF_UPD_PETR`
- `PETR_INJ_TBUF_GAS_PREV`
- `MNFLD_PRESS_BUF_GAS_PREV`
- `PETR_INJ_TBUF_GAS`
- `NUM_BUF_UPD_GAS`
- `MUL_ACT`
- `MNFLD_PRESS_THD`
- `MAX_RPM_FOR_AUTOCAL`
- `PETR_MNFLD_PRESS_RV`

### Verified source → visual-series relationships

The detailed loops prove at least:

- `NUM_BUF_UPD_PETR` + `PETR_INJ_TBUF_GAS_PREV` feed the `PetrolPoint` series.
- `MNFLD_PRESS_BUF_GAS_PREV` + `MUL_ACT` feed the `GasPointPrev` series.
- `MNFLD_PRESS_THD` + `NUM_BUF_UPD_GAS` feed `KRunAxes`.
- `MNFLD_PRESS_THD` feeds axis/line construction in `RunAxes` / `PetrolLine`.
- `PETR_MNFLD_PRESS_RV` participates in `PetrolCurve` construction.
- `MUL_ACT` is also read by `ChartKLineClickSeries`, so interaction with the K chart reflects the current native curve rather than a disconnected UI copy.

Some additional relationships are visible in disassembly but remain deliberately unlabeled until scale/unit semantics are proven.

---

## 7. Point/curve helper behavior

### `UpdateDataSeriesHelper 0x516F64`

This helper chooses different native sources depending on which series family is being rebuilt.

For one branch it selects:
- `PETR_INJ_TBUF_GAS` / related gas-state input;
- `MUL_ACT`;
- UI `NumPetrPt`.

For the other it selects:
- petrol/reference-side native inputs;
- UI `NRunAxes`.

It iterates native element indices, checks acquisition/support thresholds, and writes either:
- a real point value; or
- an explicit invalid/sentinel value (observed `-1.0`) into the UI series.

This is important: ProgBase does not merely plot every raw slot. It conditionally exposes points based on native acquisition/support state.

### `BuildReferenceCurvesHelper 0x516D8C`

This routine:
- initializes internal curve/reference state;
- updates a top-level label;
- reads `VECT_AUTOCAL_U8_0` and `VECT_AUTOCAL_U8_1`;
- toggles AutoCal-related actions depending on state;
- calls the helper that enables/disables chart interaction modes.

Exact human semantics of every state byte are still pending raw-trace pairing.

---

## 8. Bands, zones and after-draw overlays

### `BtnShowBandClick 0x51A74C`

Directly consumes:
- `MUL_ACT`
- `PETR_INJ_TBUF`
- `ACQUIRED_ZONES_GAS`
- `MODULE_VERSION`

and touches visible AutoCal label/action state.

Supported interpretation:
- band inspection is derived from native curve/buffer/acquisition-zone data;
- it is not an independent visual-only model.

### `ChartDataAfterDraw 0x51A998`

Directly consumes:
- `ACQUIRED_ZONES_PETROL`
- `CALIBRATION_VAL_1`

and manipulates UI action/label state after chart rendering.

Supported interpretation:
- at least part of the chart overlay/annotation state is drawn from native acquisition-zone/calibration state.

Still pending:
- exact mapping of each bit/zone to each visual overlay/legend;
- exact text construction for every label.

---

## 9. Finish AutoCal path

`ActionFinishAutocalExecute 0x51A390` is not just a button that hides the screen.

Verified behavior includes:

1. confirmation/state checks;
2. access to `VECT_AUTOCAL_U8_1`;
3. access to `VECT_AUTOCAL_U8_0`;
4. paired deeper object operations;
5. short settle/wait;
6. forced `PostActionRefresh`.

`BtnFinishAutomatchClick 0x51A454` invokes the same pair of underlying native objects without the same surrounding confirmation flow.

The exact meaning of those two deeper vector operations must still be correlated with raw serial transactions before naming them as a specific ECU command.

---

## 10. Reset K-factor path

`ActionResetKFactorExecute 0x51A070`:

- performs confirmation/state gating;
- iterates all current K-factor elements;
- writes factor `1.0` through the relevant underlying data object;
- triggers subsequent refresh/rebuild behavior.

Unlike native AutoMatch self-adjustment, this is a user-requested host-side reset workflow.

This distinction matters for OMEGAS:
- **native AutoMatch**: ECU modifies `MUL_ACT`;
- **explicit reset/manual edit**: host workflow may perform writes.

---

## 11. Current consumer graph

```text
                     ┌────────────────────────────────────┐
                     │          ECU / native AutoCal      │
                     │ buffers · zones · counters ·       │
                     │ pressure refs · MUL_ACT · states   │
                     └─────────────────┬──────────────────┘
                                       │ serial objects
                                       ▼
                     ┌────────────────────────────────────┐
                     │             TAutoCalDM             │
                     │ native data-object authority       │
                     └──────────────┬───────────┬─────────┘
                                    │           │
                       native action│           │read/refresh
                                    │           ▼
       ┌──────────────────────┐     │   ┌───────────────────────────┐
       │ TAutoCalUI actions   │─────┘   │ PostActionRefresh        │
       │ AutoMatch / reset /  │         │ 0x5162F8                 │
       │ finish / enable      │         └─────────────┬─────────────┘
       └──────────┬───────────┘                       │
                  │                                   ├─> PetrolPoint
                  ├─ code 8 AutoMatch                 ├─> GasPointPrev
                  ├─ code 1 Reset Petrol             ├─> PetrolLine
                  ├─ code 2 Reset Gas                ├─> GasLine
                  └─ code 4 Reset All                ├─> PetrolCurve
                                                      ├─> GasCurve
                                                      ├─> KRunAxes
                                                      ├─> RunAxes
                                                      ├─> PollingPetrol
                                                      ├─> AutoMatch label/count
                                                      └─> panel/state projection

Native action handlers
        ↓
shared dispatcher 0x517568
        ↓
native action bridge 0x512280
        ↓
ECU/native state
        ↓
PostActionRefresh
        ↓
visible product state
```

This is the strongest current architecture model.

---

## 12. What is proven vs still open

### Proven

- native data/UI separation exists in ProgBase;
- UI action handlers are thin in key AutoCal operations;
- reset petrol/gas/all and manual AutoMatch share one dispatcher with action codes 1/2/4/8;
- dispatcher crosses into native/transport path and then refreshes UI;
- ECU/native mechanism changes `MUL_ACT` without host replacement-vector write in the raw AutoCal capture;
- `PostActionRefresh` is a major native-data → UI fan-out;
- native buffers/curve/counters/zones directly feed visible series/state;
- K chart interaction reads current `MUL_ACT`;
- AutoCal uses RPM at least as a native constraint (`MAX_RPM_FOR_AUTOCAL=3000` in LOGNOVO).

### Still open

- exact serial frame produced by each action code 1/2/4/8;
- exact firmware algorithm that updates `MUL_ACT`;
- exact meaning of every `VECT_AUTOCAL_U8_*` state;
- complete bit-level interpretation of acquired-zone masks;
- exact state machine around `state_acquire_petrol_line` and `state_draw_gas_petrol_curve`;
- exact source/value transformation for every visual series;
- exact legend/message text mapping and transition conditions;
- whether any host-side calculation contributes numerically to native AutoMatch beyond orchestration.

No OMEGAS implementation should close these gaps by assumption.

---

## 13. UX consequence for future OMEGAS AutoCal surface

Do **not** clone the ProgBase screen.

Preserve its native authority graph and translate it according to CUSTOMROM + Omega Dev principles.

Candidate human model:

```text
AUTO-CALIBRAÇÃO

Estado atual
  Coletando gasolina / Coletando GNV / Ajustando / Verificando / Concluído

Curvas
  Gasolina
  GNV
  Curva K atual da ECU

Cobertura
  regiões adquiridas / faltantes

Resultado
  o que a ECU acabou de alterar
  nível de alinhamento observado
  próxima ação útil

[Detalhes técnicos]
  buffers · zonas · contador · MUL_ACT · raw snapshot · protocolo
```

Key rule:

> O usuário sees **what the ECU is doing, what it changed, and what remains to be verified**. The UI must not expose `TAutoCalDM`, serial addresses, buffers or handler names at the primary level.

Technical evidence remains available on demand and exportable.

---

## 14. Next reverse-engineering gate

Before any product/UI implementation:

1. pair dispatcher action codes 1/2/4/8 with exact raw Portmon request/response transactions;
2. decode the native state machine around `state_acquire_petrol_line` and `state_draw_gas_petrol_curve`;
3. finish source→series mapping for `PetrolCurve`, `GasCurve`, points and polling markers;
4. decode acquired-zone masks and chart overlays;
5. map label/message state transitions;
6. extract a machine-readable consumer graph used as an automated contract;
7. test every decoded relationship against **both raw captures**.

Only then should the OMEGAS AutoCal UX contract be frozen.
