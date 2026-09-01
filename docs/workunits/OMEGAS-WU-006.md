# OMEGAS-WU-006 — Calibration Science Hardening / Evidence Economy

- WorkUnit: `OMEGAS-WU-006`
- Objetivo humano: endurecer cientificamente o aprendizado gasolina↔GNV usando o corpus real já coletado, melhorar confiança/velocidade sem inflar evidência e chegar ao menor número possível de APKs/testes físicos.
- Issue: #7
- Branch: `work/wu-006-calibration-science-hardening`
- Base: `cad7bb2860200ebd4f6f76720ca681da10d7f9c0`
- Estado: `IMPLEMENTING`
- Risco: alto científico, baixo operacional enquanto offline; qualquer escrita física na ECU permanece fora do caminho automático.
- Custo: `ZERO_MONETARY_SPEND=ABSOLUTE`
- Rota pelo PC do proprietário: proibida.
- Estratégia: `CORPUS_FIRST → REPLAY_FIRST → SIMULATION_FIRST → SHADOW_FIRST → PHYSICAL_LAST`.
- Guardrail canônico: `governance/engineering-guardrails.md`.
- Evidence receipt G2–G4: `docs/evidence/OMEGAS-WU-006-G2-G4.md`.

## Contrato científico congelado

A equivalência não é “RPM e MAP isoladamente”. O contrato correto é:

1. `(RPM, MAP)` identifica a região/condição operacional física comparável;
2. em gasolina aprendemos `Tinj_petrol_ref(RPM, MAP)`;
3. em GNV observamos `Tinj_petrol_on_CNG(RPM, MAP)`;
4. a equivalência/resíduo compara esses tempos de injeção sob suporte RPM×MAP compatível;
5. `RPM × Petrol Inj.` localiza downstream a célula física do Mapa K conforme os eixos vivos da ECU.

Temperatura, ΔP e contexto ambiental não são eixos do Mapa K nem dimensões obrigatórias do matching nesta WorkUnit. Permanecem diagnósticos/ablação offline; qualquer promoção exige nova WorkUnit e evidência cega de falha do modelo atual.

Mapa K e Curva K continuam separados. Nenhum aprendizado, Predictor ou sugestão autoriza escrita automática na ECU.

## Não escopo

- redesign ou mudança de UI/UX;
- OBD como novo requisito;
- escrita automática na ECU;
- remover proteções de ACK/readback;
- gerar APK a cada alteração;
- declarar validação veicular com replay/software;
- aumentar dimensionalidade ambiental sem falsificação que justifique.

## Gates

### G1 — Corpus Contract — PROVEN
Fixture privacy-safe/hash-bound e deduplicação por sessão lógica canonizadas. Corpus bruto não é versionado. Fixture: 1.708 episódios, gzip SHA-256 `9fd4a4fda3d907af67c9c29c01b17b54cb607f13c3351b66aff553e962980d94`.

### G2 — Independent Replay — PROVEN
Replay governado foi exercitado no SHA `29e2f9356a45b31395e83d2c98a07552985ed7cc` / tree `0c80dbbc790e0e0180afdfe5240a6b88b5862252`. O avaliador não usa `sample_state`; fixture é validado fail-closed e os contratos de janela/trajectory/reutilização temporal passam independentemente.

### G3 — Temporal Independence / Evidence Mass — PROVEN_OFFLINE_METHOD
Frame/janela repetidos não equivalem a suporte entre sessões. O lab separa variância within/between-session, LOSO e voto balanceado por sessão; duplicação densa dentro da mesma sessão não fabrica nova sessão independente.

No corpus governado:
- GASOLINA: 266 episódios; 7 regiões analisáveis; 6 auditadas com >=3 sessões independentes; 1 insuficiente;
- GNV: 1.442 episódios; 15 regiões analisáveis; 13 auditadas; 2 insuficientes.

Este gate prova o método científico offline. `production_runtime_integrated=false`; integração Kotlin permanece G11.

### G4 — Blind Walk-Forward — PROVEN
Treino usa somente ordens/sessões anteriores; futuro não altera alvo anterior; `leakage_violations=0`; random shuffle de telemetria adjacente é proibido.

Em 247 episódios futuros gasolina:
- baseline WU-006: 213 suportados; coverage `0.8623481781`; abstention `0.1376518219`; mean abs rel error `0.0219332429`; mediana `0.0125276948`; P90 `0.0540617710`; P95 `0.0801364710`; mediana de 3 sessões independentes;
- pooled Gaussian: 231 suportados; coverage `0.9352226721`; mean abs rel error `0.0415702051`; mediana `0.0228080838`; P90 `0.1108447704`; P95 `0.1566843485`;
- session-balanced Gaussian: 231 suportados; coverage `0.9352226721`; mean abs rel error `0.0442942972`; mediana `0.0221272123`; P90 `0.1196947272`; P95 `0.1566843485`.

A maior cobertura dos Gaussianos **não** os promove a vencedores: erro médio/cauda pioraram. Seleção/tuning pertence a G5.

Prova remota final:
- workflow run `33500808742` / job `99833459688` = GREEN;
- 13 testes de corpus + 3 G2 + 9 G3 + 4 G4 = GREEN;
- artifact `9797604261`, digest `sha256:e4a787b446ea9c42d0a44d94e99f2309e53f921613288925663026996c628dcb`.

### G5 — RPM×MAP→Tinj Model Tuning — NEXT_UNPROVEN
Otimizar lattice, vizinhança, janela, peso, deadband e critérios de aceitação apenas na disciplina walk-forward. Objetivo: precisão × cobertura × velocidade de aprendizado, nunca maior contagem bruta.

### G6 — Causal MAP_K Replay
Reconstruir intervenções manuais confirmadas e comparar épocas pré/pós com referência congelada, preservando ACK/readback/identidade.

### G7 — Sensitivity Calibration
Calibrar sensibilidade apenas com pares causais comparáveis; contradição aumenta incerteza.

### G8 — Risk Coverage
Exigir risco empiricamente menor nos subconjuntos de maior confiança fora da amostra.

### G9 — P(improve)
Calibrar probabilidade de melhora em outcomes causais held-out. Até fechar: `pImprove=null`, `riskCalibrated=false`, `actionable=false`.

### G10 — Shadow + Falsification
Rodar candidato sem mutação ECU, incluindo OOD/baixa cobertura/drift/contradições.

### G11 — Production Integration / Software Proof
Integrar somente mudanças justificadas ao Kotlin com TDD e regressão proporcional.

### G12 — APK Candidate
Somente após gates offline necessários: suíte Android/JVM, lint, assemble, artifact e hashes. Pode chegar a `APK_READY_FOR_PHYSICAL_TEST`, nunca `VEHICLE_PROVEN` por software.

## Evidência e estados

- `REPLAY_PROVEN_G1_G4=true` para os contratos/métodos offline explicitamente exercitados;
- `MODEL_PROVEN=false`;
- `KOTLIN_RUNTIME_INTEGRATED=false`;
- `APK_READY_FOR_PHYSICAL_TEST=false`;
- `VEHICLE_PROVEN=false`;
- Predictor permanece `ABSTAIN_UNCHANGED`;
- `AUTO_WRITE_ECU=false`.

Estado da WorkUnit: `IMPLEMENTING`.

`next_unproven_item = G5_RPM_MAP_TINJ_TUNING`.

## Estratégia de verificação e custo

T0 estático/sintético → T1 focado → T2 replay governado/falsificação → T3 Android afetado somente quando necessário → T4 full release antes do APK candidato. Actions seletivas são superfície de prova remota, com `contents: read`, SHA exato, timeout, cancelamento e receipt. PR precoce/CI Android pesado continuam proibidos sem ganho de evidência.

## Fechamento

`PROVEN` exige evidência reproduzível e linhagem Issue #7 → esta branch → PR único → checks → merge. Chat não é autoridade e nenhuma etapa fecha por narrativa.