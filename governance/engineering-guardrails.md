# OMEGAS V8.2 — Engineering & Science Guardrails

Este contrato combina a disciplina remote-first do PULSE RIDE com a disciplina científica/falsificável da RED Science Blend, sem importar seus ponteiros históricos como autoridade desta WorkUnit.

## 1. Invariantes

1. **Repo-first/readback:** toda claim começa no ref remoto atual; toda escrita material é lida de volta antes de ser tratada como durável.
2. **No fake completion:** teste, CI, replay, build, APK e veículo provam camadas diferentes. Nunca promover uma evidência para uma camada que não foi exercitada.
3. **TDD para comportamento:** RED pelo motivo esperado → implementação mínima → GREEN → regressão proporcional.
4. **Root cause before fix:** falha inesperada ou regressão não é convite a editar teste/dado; primeiro identificar causa e blast radius.
5. **Chronology first:** claims de generalização usam treino estritamente anterior ao teste. Sem random-shuffle de telemetria adjacente.
6. **Evidence mass is typed:** frame/janela = precisão local; episódio = repetição temporal separada; sessão/época = persistência/transferência. Contagem bruta não cria independência.
7. **Fail closed:** corrupção, leakage, identidade insuficiente, baixa cobertura ou causalidade não provada resultam em erro/ABSTAIN, não preenchimento conveniente.
8. **No plugin authority inflation:** Superpowers/Codex/outros auxiliam execução; não mudam escopo, custo, arquitetura, segurança ou critérios de aceite.

## 2. Router de engenharia

### Ciência/replay offline

- preservar fixture hash-bound;
- avaliador não consulta `sample_state`/rótulo produzido pelo algoritmo avaliado;
- reportar claim scope explicitamente (`NOT_PRODUCTION`, `NOT_VEHICLE` quando aplicável);
- separar métrica local de suporte entre sessões;
- guardar leakage count e fazê-lo bloquear promoção se diferente de zero.

### Mudança de produção Android/Kotlin

Só entra quando um gate offline justificar mudança concreta. Exige teste comportamental RED quando prático, menor diff seguro e prova fresca no candidato final. G2–G4 podem provar o método científico sem fingir que o runtime Kotlin já foi integrado; integração pertence ao gate de produção.

### Bug/falha

Reproduzir → hipóteses concorrentes → causa → teste/reproducer → correção mínima → regressão. Não mascarar sintoma com fallback silencioso.

## 3. Evidência e gates

- `STATIC_PROVEN`: contrato/config/estrutura.
- `UNIT_PROVEN`: lógica isolada.
- `REPLAY_PROVEN`: corpus/fixture governado reproduziu o comportamento no SHA declarado.
- `MODEL_PROVEN`: held-out/falsificação/causalidade/risco satisfazem gates congelados.
- `ANDROID_PROVEN`: implementação Android/JVM final passou sua superfície de regressão.
- `APK_READY_FOR_PHYSICAL_TEST`: artifact do mesmo SHA, hash-bound e gates prévios necessários verdes.
- `VEHICLE_PROVEN`: somente evidência física nova no carro.

Estados não são transitivos automaticamente.

## 4. GitHub Actions seletivo

Actions não é proibido; é uma superfície de prova. Usar quando reduz ambiguidade e custo total.

Workflows científicos WU-006 devem:
- rodar apenas na branch WU-006 e em paths científicos relevantes;
- `permissions: contents: read`;
- `concurrency` com `cancel-in-progress: true`;
- timeout explícito;
- provar `git rev-parse HEAD == GITHUB_SHA`;
- reconstruir/verificar fixture antes de análise;
- rodar testes antes dos audits;
- produzir receipt JSON/TXT com SHA/tree, métricas e claim scope;
- subir artifact de evidência quando material;
- não executar Android/APK em docs-only ou ciência offline.

Tiers: `T0 static/synthetic → T1 focused tests → T2 governed replay → T3 affected Android → T4 full release/APK`.

## 5. Segurança física

- `AUTO_WRITE_ECU=false`.
- Predictor ABSTAIN até risco e P(improve) provados.
- Toda escrita ECU continua manual com confirmação, ACK e readback.
- Resultado offline nunca é autorização física automática.
