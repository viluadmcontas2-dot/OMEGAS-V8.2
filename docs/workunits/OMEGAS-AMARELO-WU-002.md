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
- state-machine completa;
- transforms exatos de todos os consumers;
- bit/byte das zonas adquiridas;
- código de ação 1/2/8 -> wire;
- relação completa host write vs ECU mutation.


## Consumer graph v1 — 2026-09-21

Machine-readable graph:
`docs/omegas-amarelo/evidence/WU-002-consumer-graph-v1.json`

New PROVEN Finish relation from ProgBase:
`ActionFinishAutocalExecute@0x51A390 -> getter(DM+0x7C) -> same scalar -> setter(DM+0xCC) -> 100 ms -> PostActionRefresh(1)`.

The semantic identities of `DM+0x7C` and `DM+0xCC` remain **UNKNOWN** between the known Finish-related native wrappers; they are deliberately not named from Verde assumptions.

RTTI/string evidence also exposes:
`state_0..state_5`, `state_acquire_petrol_line`, `state_acquire_gas_line`, `state_draw_gas_petrol_curve`.
Their presence/order is evidence; transition/ordinal semantics remain **INFERRED** until switch/use sites are correlated.

Contract test:
`tests/test_amarelo_wu002_consumer_graph.py`.
