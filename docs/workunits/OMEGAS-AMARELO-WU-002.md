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
- bit/byte das zonas adquiridas;
- identidade exata de `DM+0x7C` / `DM+0xCC` e subíndices `0x0165`;
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
