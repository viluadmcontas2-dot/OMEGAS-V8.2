# ADR-001: O app só propõe/grava Curva K ou Mapa K depois que a ECU terminou o AutoMatch

**Status:** Accepted
**Date:** 2026-10-13
**Feature:** ciclo-fechado-refino
**Author:** 10x-Team (Architect + Staff Engineer), a pedido do dono

## Context
Cada AutoMatch da ECU calcula a correção com a curva e os buffers que ela conhecia quando COMEÇOU. Se o app grava Curva K ou Mapa K durante o automático, a ECU aplica a correção dela em cima da nossa, com buffers adquiridos sob o produto antigo ("cabo de guerra"). Em 5 sessões reais com mudança nativa, cada AutoMatch reescreveu 28–30 dos 30 pontos (até 25%).

## Decision
Toda gravação que o APP propõe (reforço de engasgo, ajuste local, exportação Curva K→Mapa K) passa por `core/write-gate.js` e só abre com `autopilot.ecuDone !== false`. `ecuDone` já é definido no Kotlin (`RefinementAutopilot`): AutoCal desligado, máximo de AutoMatch atingido, ou aquisição completa e quieta. Estado desconhecido não bloqueia. Edição manual na aba Curva K fica fora (intenção explícita). Em paralelo, `NativeKMovementTracker` MEDE (sem decidir) o vai-e-volta da curva entre AutoMatch.

## Alternatives Considered
| Alternative | Pros | Cons | Why Not |
|-------------|------|------|---------|
| Deixar gravar e avisar | simples | a ECU desfaz/soma em cima; dado contaminado | o dono pediu para não misturar |
| Só pontos não tocados pela ECU durante o automático | usa mais dados cedo | exige saber qual buffer a ECU já congelou (não observável) | sem prova de qual ponto é seguro |
| Bloquear tudo, inclusive manual | mais seguro | tira controle do dono | manual é intenção explícita |

## Consequences
### Positive
- Nenhuma correção nossa é sobrescrita pela ECU; sem buffers contaminados.
### Negative
- Espera até 3 de 3 (ou 10/25 min quietos). Para a conversora (15–20 min) o limite é o próprio ritmo da ECU.
### Risks
- `ecuDone` errado (ex.: contador sem leitura) mantém a porta fechada: mitigado por "desconhecido não bloqueia" e pela mensagem com "n de m".

## Dependencies
- `RefinementAutopilot.ecuDone`; futuro: AutoMatch manual (modo 8) para encurtar a sessão — exige decisão do dono.
