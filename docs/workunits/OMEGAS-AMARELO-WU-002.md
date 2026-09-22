# OMEGAS-AMARELO-WU-002 — State machine e consumer graph

Issue: #74
Estado: RESEARCH_ACTIVE_CLOSURE_BLOCKED_BY_WU_001

## Resultado observável
Grafo completo de intenção humana -> handler -> dispatcher -> objeto nativo -> serial/ECU -> refresh -> série/label/banda.

## Alvos
TAutoCalDM, TAutoCalUI, states, actions, curves, points, bands, labels.

## Evidência
Disassembly + RTTI/resource bindings + Portmon.

## Saída
Grafo machine-readable e relatório humano com cada aresta PROVEN/INFERRED/UNKNOWN.

## Proibição
Não preencher UNKNOWN por semelhança com código OMEGAS.

## Continuidade remota — 2026-09-21
Consumer-graph research may proceed independently, but closure remains gated by WU-001.

### Já suportado
- dispatcher comum `0x517568` -> bridge nativo `0x512280` -> `PostActionRefresh 0x5162F8`;
- consumidores amplos de curves/points/axes/polling/counter;
- duas cadências distintas no original: live telemetry e AutoCAL lento.

### Ainda UNKNOWN / incompleto
- máquina de estados física/ECU, caso exista separada do scheduler de refresh;
- transforms exatos de todos os consumers;
- relação completa host write vs ECU mutation.


## Consumer graph v1 — 2026-09-21

Machine-readable graph:
`docs/omegas-amarelo/evidence/WU-002-consumer-graph-v1.json`

New PROVEN Finish relation from ProgBase:
`ActionFinishAutocalExecute@0x51A390 -> getter(DM+0x7C) -> same scalar -> setter(DM+0xCC) -> 100 ms -> PostActionRefresh(1)`.

The semantic identities of `DM+0x7C` and `DM+0xCC` remain **UNKNOWN** between the known Finish-related native wrappers; they are deliberately not named from Verde assumptions.

RTTI/string + use-site evidence exposes:
`state_0..state_5`, `state_acquire_petrol_line`, `state_acquire_gas_line`, `state_draw_gas_petrol_curve`.

Correction after direct ProgBase disassembly: these labels participate in a **refresh-dispatch scheduler**, not a proven ECU/physical state machine. `DM+0x4BC` holds the active table pointer, `DM+0x4C0` its count, and `DM+0x4C4` the cursor. Selector `0x51AE20` chooses a 6-entry (`0xA9EB98`) or 8-entry (`0xA9EBB4`) table and resets the cursor; matcher `0x51ABA8` wraps/matches the current label; `0x51ADE8` advances the cursor. This structure is **PROVEN** from ProgBase 4.2.0.6. The meaning/order of all table entries as physical AutoCAL states and any ECU transition graph remain **UNKNOWN**.

Action codes `1/2/4/8` -> native wire are now PROVEN by the generic ProgBase bridge/checksum path; only action `4` is independently observed in both supplied raw captures.

Contract test:
`tests/test_amarelo_wu002_consumer_graph.py`.


## Verification closure update — 2026-09-21

The WU-002 contract test was repaired after a malformed literal `\\n` and stale field reference were found. The fast-contract workflow now executes `tests.test_amarelo_wu002_consumer_graph` explicitly.

CI receipt before scheduler extension:
- SHA: `21332252106eaf3f0b4a9c70870b511821565eb0`
- run: `35648301543`
- conclusion: **SUCCESS**

Any later scheduler-evidence SHA requires its own CI receipt before being called green.


## Decomposição adicional — GasPoint / GasPointPrev / activity context — 2026-09-21

Dual-Portmon + consumer graph agora provam quatro produtores distintos:
- `0x015D PETR_INJ_TBUF_GAS_PREV -> GasPointPrev.x`;
- `0x015E MNFLD_PRESS_BUF_GAS_PREV -> GasPointPrev.y`;
- `0x015F PETR_INJ_TBUF_GAS -> GasPoint.x`;
- `0x0160 MNFLD_PRESS_BUF_GAS -> GasPoint.y`.

A hipótese de que `GasPointPrev` seja sempre uma cópia do `GasPoint` imediatamente anterior foi **FALSIFICADA** nos dois Portmons. O buffer anterior continua sendo um produtor nativo distinto, mas seu significado/lifecycle interno da firmware permanece UNKNOWN.

Também ficou PROVEN que `GasPoint` pode mudar enquanto `ACQUIRED_ZONES_GAS=[0,0,0,0]`. `NUM_BUF_UPD_GAS` expõe atividade mais fina que as quatro flags, porém o threshold/guard interno que promove atividade para flag de zona permanece UNKNOWN. Assim:
- pontos atuais não podem ser escondidos esperando a flag de zona;
- as quatro flags não são porcentagem nem progresso monotônico;
- mudança de ponto não exige transição de `NUM_AUTOMATCH_EXECUTED`.

Evidence fixtures:
- `tests/fixtures/amarelo-autocal-gaspoint-current-prev-v1.json`;
- `tests/fixtures/amarelo-autocal-point-context-v1.json`.

Implementation boundary:
`AutoCalInstrumentProjection -> AutoCalUiProjection.instrument -> autocal-cockpit.js`.
A HMI consome a projeção tipada; não reconstrói a semântica nativa dos buffers.

Último SHA com pipeline publisher/consumer confirmado verde antes desta reconciliação documental:
`277effc942da48661f1567a05b88425ad1068ab9`, run `35654147198` = SUCCESS.
Cada SHA posterior continua exigindo receipt próprio.


## Finish AutoCAL / família 0x0165 — 2026-09-21

ProgBase RTTI resolveu os wrappers antes anônimos:
- `DM+0x7C = VECT_AUTOCAL_U8_1` — PROVEN;
- `DM+0xCC = VECT_AUTOCAL_U8_0` — PROVEN;
- `DM+0xD0 = NUM_ATUOMATCH_EXECUTED` — PROVEN.

O handler `ActionFinishAutocalExecute@0x51A390`:
1. lê `VECT_AUTOCAL_U8_1` via `0x976C44`;
2. passa o mesmo inteiro ao setter de `VECT_AUTOCAL_U8_0` via `0x976CB8`;
3. o setter entra no caminho genérico TAebNumber `0x976DF0 -> 0x977014`, que é connection-aware e pode atingir serial quando conectado;
4. espera 100 ms;
5. chama `PostActionRefresh(1)`.

DFM + wire:
- U8_1: SerialCode `0x0165`, RowIndex 1 — PROVEN;
- U8_2: SerialCode `0x0165`, RowIndex 2 — PROVEN;
- U8_0: SerialCode `0x0165`, RowIndex omitido/default; inventário DFM mostra exatamente três componentes `0x0165` (`U8_0/_1/_2`) e o wire exatamente índices `0/1/2`; como U8_1=1 e U8_2=2 são explícitos, U8_0=0 fica **PROVEN_RESOURCE_WIRE_MAPPING**;
- LOGNOVO contém 3 leituras de cada índice 0/1/2 (`0A 65 01 00 70`, `...01 71`, `...02 72`), todas retornando payload `03`;
- AUTOCAL não contém transação 0x0165;
- nenhum write 0x0165 foi observado nos dois raws fornecidos.

Classificação correta:
`Finish -> MAX_AUTOMATCH -> NUM_AUTOMATCH_EXECUTED -> connection-aware commit -> 100 ms -> refresh` = **PROVEN_STATIC**.
O write físico do Finish e seu efeito exato dentro da ECU = **NOT OBSERVED / UNKNOWN**.

Fixture/gate:
- `tests/fixtures/amarelo-autocal-finish-0165-v1.json`;
- `tests/test_amarelo_autocal_finish_0165_evidence.py`.

LEVELS não participa desta cadeia.


## Geometria das 4 regiões MAP adquiridas — 2026-09-21

A rotina nativa `0x517254` fecha a geometria das quatro regiões usadas por `ACQUIRED_ZONES_PETROL/GAS`.

ProgBase:
- campo vertical: `MNFLD_PRESS_THD@0x014C / DM+0x6C`;
- eixo horizontal: `PETR_INJ_TBP@0x014B / DM+0x70`;
- cortes internos: índices `5, 9, 13`;
- extremo superior: índice `17`;
- quatro regiões construídas consecutivamente em ordem crescente de MAP.

LOGNOVO contém três leituras idênticas dos dois vetores:
- `MNFLD_PRESS_THD`: writes 5024, 222125, 556056;
- `PETR_INJ_TBP`: writes 5070, 222171, 556102.

Com as escalas canônicas AutoCal (1000 counts/bar; 500 counts/ms), os limites observados são:
- R1: `0.000 -> 0.461 bar`;
- R2: `0.461 -> 0.666 bar`;
- R3: `0.666 -> 0.870 bar`;
- R4: `0.870 -> 1.126 bar`.

Conclusão:
- índice 0..3 = regiões MAP ordenadas baixa->alta: **PROVEN**;
- nomes físicos/humanos além de R1..R4 + limite numérico: **UNKNOWN**;
- flags continuam não monotônicas e não representam porcentagem.

Implementação:
`AutoCalInstrumentProjection.zoneRegions` publica limites + flags gasolina/GNV.
O JavaScript apenas desenha as quatro faixas discretas no gráfico; não conhece os índices 5/9/13 e não recalcula a ciência.

Evidence:
- `tests/fixtures/amarelo-autocal-zone-geometry-v1.json`;
- `tests/test_amarelo_autocal_zone_geometry_evidence.py`.


## Maturity / polling visibility — 2026-09-21

`0x516F64` does not invent a completion percentage and does not decide ACQUIRED_ZONES. It applies a host presentation rule over ECU-provided counters and ECU-provided thresholds.

- GNV index 0..5: threshold = `CALIBRATION_VAL_1[5]`.
- GNV index 6..17: threshold = `CALIBRATION_VAL_1[8]`.
- Gasoline index 0..5: threshold = `VECT_AUTOCAL_U8_1` (`0x0165:1`).
- Gasoline index 6..17: threshold = `CALIBRATION_VAL_1[2]`.
- condition: `counter[index] >= threshold` -> polling/maturity layer renders the native Y; otherwise it renders `-1.0` sentinel.

In PortmonLOGNOVO, all four effective thresholds are `3`, but this is capture evidence only and is **not** a product constant. Product reads the values from the ECU.

Important separation:
- point buffers may change before acquired-zone flags change;
- acquired-zone flags are independent ECU objects;
- index split 0..5 / 6..17 is proven only for choosing maturity threshold;
- R1..R4 MAP region must be derived from the point's MAP against `MNFLD_PRESS_THD`, not from the buffer index.

Evidence: `tests/fixtures/amarelo-autocal-maturity-visibility-v1.json` + `tests/test_amarelo_autocal_maturity_visibility_evidence.py`.


## Point-buffer ownership — 2026-09-21

Across both supplied Portmons, `0x015D..0x0160` are accessed only with command `0x29` read-vector, while GasPoint current buffers change hundreds of times.

- PortmonAUTOCAL: 322 changed-index events; 744/744/747/747 reads for 0x015D..0x0160; zero non-read commands.
- PortmonLOGNOVO: 250 changed-index events; 437/437/440/440 reads; zero non-read commands.

Therefore ECU/firmware-side point-buffer update ownership is **PROVEN FOR SUPPLIED CAPTURES**. Exact firmware averaging/replacement formula remains **UNKNOWN**.

Product rule: OMEGAS reads/projects the native point buffers and must not synthesize or overwrite them.


## Native point-update dynamics — 2026-09-21

A targeted Portmon sequence for GNV buffer index 6 shows multiple point coordinates under the same latest observed `NUM_BUF_UPD_GAS` value (for example counter 9 with 3721/541 then 3704/538; counter 10 with 3704/538 then 3640/538).

Because counter and point vectors are separate serial transactions, this does not prove the ECU counter was unchanged at the exact mutation instant. It **does** prove that the host-visible counter is insufficient to reconstruct the point-update formula.

Therefore:
- do not treat `NUM_BUF_UPD_GAS` as a running-average sample count;
- do not emulate native point averaging/replacement in OMEGAS;
- exact firmware point-update formula remains **UNKNOWN**;
- native point buffer remains the product authority.


## ACQUIRED_ZONES as native epoch latches — 2026-09-21

Dual-capture correlation closes the observed behavior of the four native region flags without claiming an unknown firmware formula.

- PortmonAUTOCAL: 14/14 observed `0->1` activations have live MAP visitation in the same native region within the current zone-poll window.
- PortmonLOGNOVO: 13/14 in the current window; the one delayed observation has region visitation in the immediately preceding window, giving 14/14 within two windows.
- Both captures contain 3 falling-edge events; every observed falling event produces `[0,0,0,0]` rather than an isolated region loss.
- Zone vectors are ECU-owned/read-only from the ProgBase host in supplied captures.
- Maturity thresholds do not explain zone activation and are a separate concept.

Supported operational meaning: each flag behaves as a **latched native region marker within an acquisition epoch**. Exact ECU set/clear guards remain UNKNOWN. AutoMatch is related to some clears but is not proven to be the sole clear guard.

Product rule: show `Região registrada pela ECU neste ciclo`; use the ECU flag as authority. Live MAP is explanatory context only and must never synthesize the flag.

Evidence: `tests/fixtures/amarelo-autocal-zone-latch-behavior-v1.json`.


## GasPointPrev = previous native AutoMatch epoch — 2026-09-21

`PETR_INJ_TBUF_GAS_PREV@0x015D` + `MNFLD_PRESS_BUF_GAS_PREV@0x015E` do not behave as previous polling refresh.

PortmonAUTOCAL: only 3 Prev-vector changes in the capture. New Prev matches prior Current at 12/12, 15/16 and 15/16 changed positions.
PortmonLOGNOVO: 4 Prev-vector changes. The capture-start event lacks a usable prior Current baseline; subsequent events match 13/13, 15/16 and 14/15 changed positions.
Bulk Prev replacements align with `NUM_AUTOMATCH_EXECUTED` epoch transitions. One-element mismatches are compatible with X/Y vectors being separate serial transactions.

Operational conclusion: **GasPointPrev is the previous native AutoMatch epoch snapshot**. Exact firmware copy guard/timing remains UNKNOWN.

UX rule: render Current and Previous as two native epoch layers; never animate Prev as a 2-second-old ghost of Current.


## Zone-set guard evidence boundary — 2026-09-21

The supplied captures do not expose a single host-visible formula for `ACQUIRED_ZONES_GAS 0->1`.

Observed:
- every activation remains associated with live MAP visitation in the same native region within at most two slow zone-poll windows;
- AUTOCAL contains an activation after only one live frame in-region (~46 ms before observation);
- other activations appear after ~2 s of in-region live MAP;
- one observed activation has no current native point in that region at the latest heavy snapshot;
- four AUTOCAL activations have no mature point in-region under the proven host polling threshold.

Therefore these simple guards are falsified:
- fixed dwell / fixed live-frame count;
- current native point presence as a required guard;
- mature-point presence as a required guard.

The exact ECU set guard is **not recoverable from the supplied polling cadence**. Product rule remains unchanged: ECU flag is authority; live MAP, point presence and maturity are explanatory context only and must not synthesize the flag.


## ACQUIRED_ZONES set-guard boundary — 2026-09-22

Additional dual-capture testing falsifies the remaining simple host-visible guards.

PortmonLOGNOVO, after AUTO_CAL_ENABLE state becomes known:
- 320 zone-poll windows had known enable state;
- 193 region/windows contained **CNG active + AutoCAL enabled + live MAP visitation in that same region** while the ECU flag remained `0 -> 0`.

Combined with prior evidence:
- every observed activation is associated with same-region live MAP visitation in the current or immediately preceding zone-poll window;
- fixed dwell is falsified;
- current point presence is not required;
- mature point presence is not required;
- maturity counter threshold is not the zone-flag formula;
- ProgBase never writes the zone vectors in supplied captures.

Conclusion:
`CNG + AUTO_CAL_ENABLE + same-region live MAP visit` is **not sufficient** to set the flag.
The exact set guard depends on ECU-internal state not present in current host-side artifacts.

Stop condition:
do not reconstruct or predict ACQUIRED_ZONES in OMEGAS. Consume the ECU flag as authority and use live MAP only as explanatory context.


## GasPointPrev / AutoMatch epoch — bidirectional closure — 2026-09-22

LOGNOVO provides a complete observable epoch sequence:
- `NUM_AUTOMATCH_EXECUTED`: `3->0`, `0->1`, `1->2`, `2->3`;
- four bulk `GasPointPrev` replacements;
- every non-baseline counter transition has exactly one nearby Prev bulk replacement;
- every Prev bulk replacement has exactly one nearby counter transition;
- observed offset is ~0.36–3.43 s, compatible with the independent polling cadence.

AUTOCAL:
- measurable transitions `1->2` and `2->3` each align with a Prev replacement;
- one earlier Prev replacement occurs before the capture has an earlier AutoMatch counter baseline.

Therefore `GasPointPrev` as **previous native AutoMatch epoch snapshot** is operationally closed with a capture-start caveat.
Exact firmware copy instruction/instant remains UNKNOWN because vectors and counter are polled asynchronously and ECU firmware is not available.


## Native AutoMatch epoch lifecycle — 2026-09-22

Cross-object readback around every measurable AutoMatch epoch closes the observable lifecycle.

AUTOCAL measurable epochs: `1->2`, `2->3`.
LOGNOVO measurable epochs: `3->0`, `0->1`, `1->2`, `2->3`.

Every measurable epoch contains:
1. bulk `GasPointPrev` replacement;
2. `NUM_BUF_UPD_GAS` reset/reseed;
3. full `MUL_ACT` change across all 30 native nodes;
4. acquired-zone clear when the vector is non-zero in the observed window;
5. a subsequent read of `NUM_AUTOMATCH_EXECUTED` reflecting the new epoch.

The same readback ordering repeats across both captures. Because every object is polled independently, this is **observed readback order**, not proof of firmware instruction order.

Product consequence:
- new AutoMatch epoch = ECU-native event;
- Current/Previous GNV layers represent current/prior epochs;
- Curve K change is shown as native ECU readback;
- OMEGAS must not claim it computed the native adjustment;
- post-epoch state is accepted only after fresh native readback.

Evidence:
- `tests/fixtures/amarelo-autocal-epoch-lifecycle-v1.json`;
- `tests/test_amarelo_autocal_epoch_lifecycle_evidence.py`.


## Single epoch authority — 2026-09-22

The epoch consumer path is now intentionally one-way:

`NativeAutoCalMonitor.nativeAutoMatchEpochEvent`
→ `AutoCalInstrumentProjection.epoch.transition`
→ `AutoCalUxModel.instrumentEpoch()`
→ cockpit presentation.

Rules:
- JavaScript no longer compares Curve K values to invent a scientific epoch transition.
- The prior K curve is retained only as a visual before/after baseline.
- A counter change without a typed native event is rendered as pending native confirmation, not promoted to an adjustment.
- Session changes invalidate any local epoch baseline.
- Typed event must report native readback and no app write before the UI labels it as a native epoch adjustment.

This keeps firmware/monitor readback as scientific authority and the UI as a consumer only.
