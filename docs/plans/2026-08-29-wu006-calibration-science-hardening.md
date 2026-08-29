# WU-006 — Plano executável de endurecimento científico

**WorkUnit:** `OMEGAS-WU-006`  
**Issue:** #7  
**Branch:** `work/wu-006-calibration-science-hardening`  
**Objetivo:** fechar todos os gates offline possíveis antes de gerar um único candidato Android para teste físico.

## Método de execução

- Repo-first: nenhuma decisão mutável depende do chat.
- TDD: qualquer comportamento novo começa com teste falhando pelo motivo esperado e só então implementação mínima.
- Dados reais primeiro: corpus existente antes de simulação ou nova condução.
- Avaliação independente: o avaliador não usa `sample_state` nem rótulos de aceite gerados pelo próprio algoritmo.
- Temporalidade preservada: nenhuma divisão aleatória de frames adjacentes para treino/teste.
- Segurança: Predictor fail-closed; escrita ECU manual; ACK/readback preservados.
- Economia de CI: PR/Actions somente após maturidade offline.

## Tarefa 1 — Canonicalizar WU e governança

Arquivos:
- `docs/workunits/OMEGAS-WU-006.md`
- `PROJECT.md`
- `STATUS.md`

Ações:
1. registrar contrato RPM×MAP→comparação de Tinj;
2. registrar não-dimensionalização ambiental;
3. apontar estado ativo para Issue #7/branch WU-006;
4. preservar provenance WU-005 como release funcional anterior.

## Tarefa 2 — Harness do corpus

Criar:
- `tools/science/corpus_replay.py`
- `tests/test_science_corpus_replay.py`
- `tests/fixtures/science/omegas_corpus_20260828_manifest.json`
- `tests/fixtures/science/omegas_corpus_20260828_episodes.jsonl`
- `tests/fixtures/science/omegas_corpus_20260828_report.json`

Contrato:
- descobrir ZIPs de sessão aninhados;
- agrupar por `sessionId`;
- escolher representante determinístico sem inflar duplicatas;
- validar byte count + SHA-256 declarados;
- filtrar apenas telemetria V8 `mp48-progbase-v2` plausível;
- não ler `sample_state`;
- extrair janelas estáveis não sobrepostas e episódios por região;
- pseudonimizar `sessionId` por hash;
- walk-forward com treino estritamente anterior ao teste;
- produzir outputs determinísticos.

## Tarefa 3 — Evidência protocolar/causal

Adicionar fixtures pequenas derivadas, nunca logs gigantes:
- resumo golden de writes MAP_K do Portmon;
- histórico normalizado de intervenções confirmadas `.omegas`;
- hashes/source lineage dos derivados.

Falhar fechado se checksum, bounds, ACK/readback/final hash ou identidade necessária não forem prováveis.

## Tarefa 4 — Independência e incerteza

Medir autocorrelação residual por combustível/região e comparar:
- frames adjacentes;
- janelas;
- episódios;
- sessões/épocas.

Alterar produção apenas se o benchmark demonstrar inflação material da evidência atual. Preferir suporte por episódio/trajetória/sessão e componente de drift que não desapareça com frame count bruto.

## Tarefa 5 — Benchmark temporal cego

Avaliar referência gasolina e equivalência em walk-forward integral:
- median absolute relative error;
- MAE;
- P90/P95;
- coverage/abstention;
- interval coverage;
- tempo/evidência até primeira referência útil;
- erro versus suporte independente.

Os números do harness são evidência do método de avaliação, não performance de produção até o caminho Kotlin equivalente ser validado.

## Tarefa 6 — Tuning RPM×MAP→Tinj

Comparar configurações com o mesmo split temporal congelado. Não aceitar tuning que melhore treino e piore held-out. Temperatura/ΔP podem ser usados somente em ablação diagnóstica, nunca como eixo primário nesta WU.

## Tarefa 7 — Replay causal MAP_K

Reconstruir as intervenções confirmadas e congelar épocas pré/pós. Estimar efeito apenas quando contexto, identidade e semântica do delta forem comparáveis. Não converter raw K em fator físico sem prova.

## Tarefa 8 — Sensibilidade, risco e P(improve)

TDD em produção para:
- causal sensitivity update;
- leave-one-epoch risk coverage;
- calibração empírica de P(improve).

Até evidência suficiente, comportamento esperado é ABSTAIN.

## Tarefa 9 — Falsificação/shadow

Cobrir:
- regiões sem suporte;
- transições combustível;
- cutoff;
- drift temporal;
- duplicatas;
- OOD;
- contradições pós-write;
- alta autocorrelação;
- baixa diversidade de sessões.

## Tarefa 10 — Integração e release

Somente após gates offline:
1. executar testes Python/Node/JVM afetados;
2. abrir PR único da Issue #7;
3. rodar CI Android seletivo/full release conforme necessário;
4. corrigir qualquer RED sem enfraquecer teste;
5. gerar artifact APK;
6. baixar e recalcular SHA-256/tamanho/package;
7. atualizar `STATUS.md`, evidência e Issue;
8. marcar `APK_READY_FOR_PHYSICAL_TEST` apenas se todos os gates necessários estiverem verdes.

## Stop conditions válidas

Parar somente em:
- `OBJECTIVE_COMPLETE`;
- necessidade real de credencial/autorização humana;
- custo monetário não autorizado;
- decisão física/produto subjetiva indispensável;
- impossibilidade técnica externa sem fallback seguro.

Falhas de teste, bugs e necessidade de refatoração **não** são stop conditions.