# Projeto OMEGAS V8.2 Blue

## Objetivo humano

Regular o GNV com o mínimo de esforço humano, usando a MP48 para verdade de combustível/calibração e a evidência física disponível para comparar comportamento GNV × gasolina, sem automatizar escrita na ECU.

O produto deve ser utilizável na multimídia do carro: rápido, legível, didático, resistente a WebViews lentas e capaz de continuar aquisição/aprendizado no serviço Android mesmo sem a tela redesenhando.

## Contrato científico atual

- `BlueCausalEngine` é a única autoridade de comparação/correção.
- Gasolina é a referência física.
- `(RPM, MAP)` identifica condição física comparável.
- `Petrol Inj.` é a resposta comandada pela ECU e também localiza a geometria física do Mapa K.
- MP48 é autoridade de combustível/calibração/write/readback.
- `TRANSITION` continua fisicamente em gasolina; `CUT-OFF` é distinto e não é evidência de equivalência.
- OBD pode fornecer evidência física de correção, mas não possui autoridade de escrita K.
- Contagem de visitas é auditoria/suporte, não mecanismo de confiança por quantidade.
- Nenhuma correção K é aplicada automaticamente: preparar → revisar → writer → ACK → readback.

## Recuperação sistêmica ativa

A validação física de 2026-09-06 provou regressões importantes na Blue apesar de CI/build anteriores verdes. A prioridade atual **não é gerar outro APK**; é recuperar coerência e testes comportamentais antes de novo candidato.

- Epic: `#18 BLUE-RECOVERY-001`
- Work Unit: `docs/workunits/OMEGAS-BLUE-RECOVERY-001.md`
- Spec Kit: `specs/003-blue-system-recovery/`
- Branch: `work/omegas-blue-causal-engine`
- RED estável: referência funcional `hotfix/v8.0-red-performance`; não é autoridade matemática da Blue.

### Workstreams
- `#17` Agora/OBD runtime + WebView.
- `#19` Learning: Desvio, qualidade, TRANSITION e tolerâncias.
- `#20` Telemetria, backpressure, segundo plano e overlay.
- `#21` Ferramentas, retenção e logs.
- `#22` Curva K cockpit UX/performance.

## Regra de release desta recuperação

Não gerar/publicar novo APK enquanto #18 não completar triagem e remediação. Antes do próximo APK precisam existir provas de runtime/browser das telas essenciais, testes comportamentais dos bugs corrigidos, regressões científicas, FAST/JVM/lint verdes no mesmo SHA e nenhuma regressão P0/P1 conhecida aberta. Validação física no carro permanece um gate separado.