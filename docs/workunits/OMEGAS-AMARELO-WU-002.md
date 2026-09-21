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
