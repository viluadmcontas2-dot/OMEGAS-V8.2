# Status do OMEGAS V8.0 RED Performance

- WorkUnit de produto: `OMEGAS-RED-WU-001`
- Issue de produto: `#9`
- WorkUnit de CI: `RED-CI-001`
- Issue de CI: `#10`
- Branch: `hotfix/v8.0-red-performance`
- Estado: `ENGINEERING_COMPLETE`
- SHA de produto verificado: `e8c446b3cbd54194a8bc8b805b44e2770e252a93`
- Árvore de produto verificada: `de204d27968ca800af9eba6c35b553496612370b`
- SHA da política/CI provado remotamente: `b637f5fff19b1ece93f22d1fced9640618609a60`
- Árvore da política/CI provada: `7762be9c06fb0b0d53b577fe8b1251129983388e`
- Autoridade: `REPO_FIRST_ENGINEERING=TRUE`
- Notion/Linear: `HISTORICAL_MEMORY_ONLY=TRUE`
- CI desta RED pública: `PUBLIC_REPO_STANDARD_ACTIONS=PRIMARY_REMOTE_EXECUTION`
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

## Evidência de produto

- Node UI: `90/90 PASS`.
- Contratos Python locais executáveis: `36/36 PASS`.
- GitHub Actions run: [33313235501](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/33313235501) — `SUCCESS`.
- CI: contratos rápidos, regressões Kotlin focadas, JVM completo, `lintDebug` e `assembleDebug` — `PASS`.
- Artefato: `omegas-v80-red-fast-learning-e8c446b3cbd54194a8bc8b805b44e2770e252a93`, 4.626.611 bytes.

## CI remoto otimizado — RED-CI-001

A exceção abaixo vale somente para o OMEGAS V8.0 RED enquanto este repositório permanecer público e usar runners GitHub-hosted padrão sem custo adicional.

- GitHub Actions é o executor remoto primário da RED.
- Push relevante na branch RED dispara `FAST → FULL → APK/evidence` automaticamente.
- FAST falho impede FULL; SHA novo cancela execução antiga pela política de `concurrency`.
- Alteração somente documental não compila Android.
- FULL executa uma única passagem `testDebugUnitTest lintDebug assembleDebug`; a passagem Kotlin focada redundante e `clean` foram removidos.
- `actions/checkout@v7`, `actions/setup-java@v5` e `actions/upload-artifact@v6` estão em uso.
- Cache Gradle está ativo.
- Larger runners, runners pagos ou qualquer rota com custo adicional continuam proibidos sem nova aprovação explícita.
- Se o repositório deixar de ser público ou a condição de custo mudar materialmente, esta exceção expira e deve ser reavaliada.

### Prova remota

- Run: [33316187383](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/33316187383) — `SUCCESS` no SHA `b637f5fff19b1ece93f22d1fced9640618609a60`.
- FAST: `SUCCESS`; etapa de contratos ~29 s.
- FULL frio: Gradle `BUILD SUCCESSFUL in 3m 58s`; o primeiro run sem cache semeou os caches.
- FULL quente, rerun do mesmo SHA: `BUILD SUCCESSFUL in 20s`, com `28/51` tarefas vindas do cache; o job FULL inteiro levou ~33 s.
- Cache remoto confirmado por `Cache hit` e `Cache restored successfully` para Gradle e Gradle wrapper.
- APK do rerun: 4.625.867 bytes; SHA-256 `ea60dd894c529b6b29e2a934f2d07b053edc0a4c29c2c0357098a9f238d20a07`.
- Artefato atual do rerun: `omegas-v80-red-b637f5fff19b1ece93f22d1fced9640618609a60`, artifact ID `9733590282`, ZIP 4.626.680 bytes, digest `sha256:8d072a9dfe00f6887bc7a4d7579e6484207aaa42983435240228d2f0b68c2613`.

## Gates

- G1 Repo-first/Issue/Spec Kit: `PASS`
- G2 Política K e autonomia por RPM: `PASS`
- G3 Procedência auditável: `PASS`
- G4 Superfície contínua global + local: `PASS`
- G5 Sugestões, Predictor e UI: `PASS`
- G6 Auto-Cal/regressões: `PASS`
- G7 JVM/lint/APK/publicação: `PASS`
- CI1 Actions remoto primário desta RED pública: `PASS`
- CI2 FAST → FULL → artifact: `PASS`
- CI3 cache Gradle remoto: `PASS`

## Limite da afirmação

Não houve validação física no veículo nesta execução. A branch está pronta para uso controlado e medição; não se declara economia real de combustível antes de comparar consumo e dirigibilidade no carro.
