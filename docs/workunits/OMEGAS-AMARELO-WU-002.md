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
`Finish -> U8_1 -> U8_0 -> connection-aware commit -> 100 ms -> refresh` = **PROVEN_STATIC**.
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
