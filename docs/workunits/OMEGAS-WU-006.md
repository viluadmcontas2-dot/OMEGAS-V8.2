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

## Contrato científico congelado

A equivalência não é “RPM e MAP isoladamente”. O contrato correto é:

1. `(RPM, MAP)` identifica a região/condição operacional física comparável;
2. quando o motor opera em gasolina, aprendemos `Tinj_petrol_ref(RPM, MAP)`;
3. quando o motor opera em GNV, observamos o tempo de injeção de gasolina que a ECU continua comandando nessa região, `Tinj_petrol_on_CNG(RPM, MAP)`;
4. a equivalência/resíduo compara esses tempos de injeção em regiões RPM×MAP compatíveis;
5. a tradução downstream para uma célula física do Mapa K usa `RPM × Petrol Inj.` conforme os eixos vivos da ECU.

Temperatura, ΔP e outros canais ambientais **não são eixos do Mapa K, não são dimensões obrigatórias do matching e não podem aumentar por padrão a quantidade de amostras necessária para aprender**. Podem permanecer como metadados diagnósticos/estratificação offline para falsificar a hipótese de invariância. Qualquer promoção a dimensão primária exige nova WorkUnit e evidência cega de falha do modelo atual.

Mapa K e Curva K continuam separados. Nenhum aprendizado, Predictor ou sugestão autoriza escrita automática na ECU.

## Evidência disponível na entrada

- 33 sessões lógicas após deduplicar exports/prefixos da mesma sessão;
- conteúdo declarado dos representantes verificado por SHA-256 em aproximadamente 2.634 GB descompactados sem mismatch no levantamento anterior;
- telemetria V8 contém gasolina e GNV com schema `mp48-progbase-v2`;
- telemetria consecutiva é autocorrelacionada e não pode ser tratada como IID;
- snapshot `.omegas` contém 133 mudanças MAP_K confirmadas em 11 adjustment IDs, com ACK/readback/final-map-hash;
- existe época natural pré/pós intervenção para replay causal;
- Predictor de produção permanece fail-closed/ABSTAIN enquanto risco/P(improve) não forem empiricamente calibrados.

Esses fatos entram como hipóteses/evidências a serem reproduzidas pelo harness canônico desta WorkUnit; não viram `PROVEN` apenas por constarem neste documento.

## Não escopo

- redesign ou mudança de UI/UX;
- OBD como novo requisito;
- escrita automática na ECU;
- remover proteções de ACK/readback;
- gerar APK a cada alteração de código;
- declarar validação veicular com replay/software;
- aumentar dimensionalidade com temperatura/pressão sem falsificação que justifique.

## Plano e gates

### G1 — Corpus Contract — PROVEN
Manifesto privacy-safe, hash-bound e fixture derivada determinística estão canonizados. O ZIP bruto não é versionado; deduplicação por sessão lógica impede inflação por exports/prefixos. A evidência detalhada está em `STATUS.md` e nos fixtures de `tests/fixtures/science/`.

### G2 — Independent Replay
Extrair telemetria e episódios estáveis diretamente dos eventos brutos, sem consultar `sample_state` ou rótulos de aceitação do próprio algoritmo.

**Aceita quando:** replay não pode “dar nota para si mesmo”; fuel transition/cutoff/plausibility/gaps/instabilidade são tratados explicitamente; reutilização temporal é limitada; reconstrução do fixture e testes independentes passam no SHA remoto.

### G3 — Temporal Independence / Evidence Mass
Separar estabilidade de janela de independência científica. Frames próximos podem provar estabilidade, mas não equivalem a observações independentes. Tornar suporte por episódio/sessão explícito e medir persistência entre sessões; quando trajetória/época não estiverem materializadas no fixture governado, não inventar esse suporte.

**Aceita quando:** repetição densa dentro de uma sessão não fabrica sessões independentes; decomposição within/between-session e leave-one-session-out tornam drift/persistência visíveis; qualquer integração Kotlin posterior deve preservar essa semântica.

### G4 — Blind Walk-Forward
Treinar somente com sessões/épocas anteriores e avaliar sessões futuras inteiras. Proibido random-shuffle de frames adjacentes.

**Métricas:** mediana/MAE relativo, P90, P95, cobertura/abstention, suporte independente e leakage count. Interval coverage e tempo até utilidade permanecem obrigatórios antes de qualquer claim de modelo final, mas não serão fabricados se o estimator atual não produzir intervalos calibrados.

### G5 — RPM×MAP→Tinj Model Tuning
Otimizar lattice, vizinhança, janela, peso, deadband e critérios de aceitação usando apenas evidência walk-forward. Objetivo é melhor compromisso precisão×velocidade×cobertura, não maior contagem de frames.

### G6 — Causal MAP_K Replay
Reconstruir intervenções manuais confirmadas, preservar identidade/ordem/ACK/readback e comparar épocas pré/pós com referência congelada. Semântica física do valor K falha fechada quando não provada.

### G7 — Sensitivity Calibration
Calibrar/validar sensibilidade apenas com pares causais comparáveis. Contradições elevam incerteza; contexto incomparável é abstention, não dado útil forçado.

### G8 — Risk Coverage
Exercitar validação leave-one-epoch/held-out e exigir que subconjuntos de maior confiança apresentem risco empiricamente menor fora da amostra.

### G9 — P(improve)
Calibrar probabilidade de melhora em outcomes causais held-out. Até fechar este gate, `pImprove=null`, `riskCalibrated=false`, `actionable=false` e Predictor continua ABSTAIN onde depender dessa probabilidade.

### G10 — Shadow + Falsification
Rodar candidato sobre replay e, quando aplicável, telemetria sem mutação da ECU. Incluir casos adversariais, OOD, baixa cobertura, drift e contradições.

### G11 — Production Integration / Software Proof
Integrar somente mudanças justificadas ao Kotlin de produção com TDD. Rodar gate rápido, testes afetados, regressão relevante e segurança de escrita manual. PR único da Issue #7 apenas quando os gates offline estiverem maduros.

### G12 — APK Candidate
Somente após os gates offline necessários: suíte Android/JVM, lint, assemble, artifact e hashes. Resultado pode ser marcado `APK_READY_FOR_PHYSICAL_TEST`, nunca `VEHICLE_PROVEN` sem teste real.

## Evidência e estados

Níveis nunca se misturam:

- `SOFTWARE_PROVEN`: contratos/implementação/testes atuais passam;
- `REPLAY_PROVEN`: comportamento reproduzido deterministicamente no corpus histórico;
- `MODEL_PROVEN`: held-out/falsificação/causalidade/risco atendem critérios congelados;
- `VEHICLE_PROVEN`: evidência física nova atende envelope pré-declarado.

Estado da WorkUnit: `IMPLEMENTING`.

`next_unproven_item = G2_INDEPENDENT_REPLAY / G3_TEMPORAL_INDEPENDENCE`.

## Estratégia de verificação e custo

T0 estático/sintético → T1 testes focados → T2 replay governado/falsificação → T3 Android afetado somente quando necessário → T4 full release antes do APK candidato. Actions seletivas são permitidas como prova remota quando o fixture/SDK só está disponível no repositório/runner, com `contents: read`, SHA exato, timeout, cancelamento e receipt. Abrir PR cedo é proibido se isso apenas disparar CI pesado sem ganho de evidência.

## Fechamento

`PROVEN` exige evidência reproduzível e linhagem Issue #7 → esta branch → PR único → checks → merge. O chat não é autoridade e nenhuma etapa pode ser fechada apenas por narrativa.