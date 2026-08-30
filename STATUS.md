# Status do OMEGAS V8.0 RED Performance

- WorkUnit: `OMEGAS-RED-WU-001`
- Issue: `#9`
- Branch: `hotfix/v8.0-red-performance`
- Estado: `ENGINEERING_COMPLETE`
- SHA verificado: `e8c446b3cbd54194a8bc8b805b44e2770e252a93`
- Árvore verificada: `de204d27968ca800af9eba6c35b553496612370b`
- Autoridade: `REPO_FIRST_ENGINEERING=TRUE`
- Notion/Linear: `HISTORICAL_MEMORY_ONLY=TRUE`
- Escrita automática ECU: `FALSE`
- Política operacional de novos alvos Mapa K: `100..180`
- Campo U8 da ECU: `0..255` (não confundido com a política operacional)

## Resultado entregue

1. bloqueio por RPM removido; conexão, engine pronta, telemetria atual, confirmação, ACK e readback permanecem;
2. procedência durável separa resumo agregado, par observado, suporte da gasolina e contexto de calibração;
3. tendência global por Petrol Inj. e residual contínuo RPM × MAP;
4. projeção de 144 células com suporte `DIRECT`, `NEAR` ou `GLOBAL_ONLY`; residual distante não vira sugestão local;
5. Predictor rejeita alvo nulo e qualquer alvo fora de 100–180;
6. Auto-Cal possui entrada estática, abertura estável, orientação de próximo passo, ações avançadas recolhidas e diagnóstico técnico separado;
7. toda aplicação continua manual.

## Evidência

- Node UI: `90/90 PASS`.
- Contratos Python locais executáveis: `36/36 PASS`.
- GitHub Actions run: [33313235501](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/33313235501) — `SUCCESS`.
- CI: contratos rápidos, regressões Kotlin focadas, JVM completo, `lintDebug` e `assembleDebug` — `PASS`.
- Artefato: `omegas-v80-red-fast-learning-e8c446b3cbd54194a8bc8b805b44e2770e252a93`, 4.626.611 bytes.

## Gates

- G1 Repo-first/Issue/Spec Kit: `PASS`
- G2 Política K e autonomia por RPM: `PASS`
- G3 Procedência auditável: `PASS`
- G4 Superfície contínua global + local: `PASS`
- G5 Sugestões, Predictor e UI: `PASS`
- G6 Auto-Cal/regressões: `PASS`
- G7 JVM/lint/APK/publicação: `PASS`

## Limite da afirmação

Não houve validação física no veículo nesta execução. A branch está pronta para uso controlado e medição; não se declara economia real de combustível antes de comparar consumo e dirigibilidade no carro.
