# OMEGAS-AMARELO-WU-002 — State machine e consumer graph

Issue: #74
Estado: BLOCKED_BY_WU_001

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