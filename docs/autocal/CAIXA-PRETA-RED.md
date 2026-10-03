# Caixa-preta — RED confirmado em Actions
Base produto: 323a0f3aa99244bbb415a79f2f6bf77ffe0fef15.
Contrato #121 integrado em 78d4eda57a5fa80978aa8f732d669888a70c5bb1.
Testes: head 366ee061a2990aaf7efc1d3957ac149ad3decdcd; merge checkout 9bbfd89a141672bef04bae941360b983d7a6832b.
Run 37105089434, job 111151912069. Classe 2, sem físico.
Fast contracts PASS. JVM compila e executa: **605 testes, 5 falhas novas**:
- offlineNeverPresentsLastStableStateAsCurrent: ComparisonFailure, linha 24.
- readingWithoutAnyEcuResponseExpiresInThirtySeconds: ComparisonFailure, linha 36.
- automaticWaitHasCeilingWithoutDeclaringEcuDone: ComparisonFailure, linha 69.
- everyPhaseDecisionExplainsNumbersAndCause: JSONException por reasonCode ausente, linha 56.
- watchdogIsVisibleAndDoesNotBecomeSuccess: AssertionError, linha 17.
Nenhum teste existente falhou. failureThenCureProgressesWithoutUserReset já passava: é caracterização, não RED.
Log fonte: https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/37105089434

## Correção mínima em avaliação
- Offline vence fase, contador e autorização nativa antigos.
- Teto de tentativa por fase, duração monotônica no serviço, diagnóstico com elapsed/budget, razão e domínio.
- Tentativa expirada não oferece proposta antiga. Evidência nova retoma sem reset manual.
- Eventos de decisão/timeout separados da telemetria; worker da sessão faz flush/fsync.
- RESUMO reconstrói decisões/anomalias a partir do mesmo JSONL após kill.
- Journal, ciência do motor, limites estatísticos e writer não foram corrigidos nesta fatia.

## Radar 1–11 desta fatia
1. Frase de etapa expirada e próximo passo automático; render pendente.
2. Timeout não declara que writer parou, não escreve e não declara AutoMatch concluído.
3. Leitura 30s; automático/coleta/verificação 40min; revisão/restauração 30min; offline/estável são terminais observacionais.
4. Offline contador atual null; ratio histórico ainda demanda auditoria da tela.
5. Política numérica D2/D3 pendente; timers numa tabela PHASE_BUDGET_MS.
6. Relógio monotônico em produção; duração acumulada persiste, autorização de ECU relida. Teste de restart completo pendente.
7. Classe 2 por funções de produção; ponte/serviço/render integrado pendente.
8. Cada alteração ligada LC01/LC08/LC10; sem estética ou refatoração adicional.
9. Journal offline, monitor/source freshness, writers globais e publish continuam lacunas registradas.
10. Retoma com evidência nova, sem adicionar botão/chave.
11. Falsificação seguinte: timestamp extremo, antigo latch mesmo contador, timeout com proposta velha, kill no instante do diagnóstico, falha-e-cura.


## AF1 — correção isolada de contrato antigo
CI 37105574334, job 111153295465, fonte e66c0f06: 605 testes, 1 falha.
O teste EcuReferenceCycleTest exigia PROPOSTA_PRONTA sem ECU (linha 122).
Prova de contrato errado, classe 1: blueprint vinculante exige estado antes da ação,
sem falso sucesso visual e estado sem conexão; invariantes exigem intenção, ACK/readback.
Prova comportamental, classe 2: novo offlineNeverPresentsLastStableStateAsCurrent falhou
no código antigo (37105089434) e passou na correção. Histórico permanece no ledger.
Troca em commit só de teste/documentação: SEM_ECU, sem autorização nem contador atual,
mesma amostragem/referência preservada, proposta retomada após reconexão.
Não relaxa a matemática, os limites ou os oráculos de veredito.
Novo adversarial offlineCannotReuseStaleGasAcquisition deve falhar no código atual:
a revisão encontrou gasValid e gasZones lendo a aquisição velha; ainda sem correção.


## RED adicional — reinício e relato honesto
Fonte 5012d2c77e22, run 37106082688, job 111154763346: 610 testes / 3 falhas novas;
somente stale gas, prazo zerado ao reabrir e VERIFICADO apresentado como melhora confirmada.
Saltos de calendário com relógio monotônico e falha-cura nas quatro fases extras passaram.
Correção preserva durationAt e clock domain no estado v1 compatível; versão antiga inicia
tentativa segura e exige nova ECU; delta negativo após reboot não inventa duração.
VERIFICADO significa comparação encerrada: somente CONFIRMADA por faixa afirma tolerância.
Classe 2 até GREEN remoto; P5 completo de todos os módulos e corpus fechado ainda pendentes.


## AF1 — silêncio não confirma protocolo
Contrato CICLO-DE-VIDA já integrado em #121 exige que o host jamais conclua AutoMatch por silêncio.
AGENTS e blueprint exigem presunção crítica; contador abaixo do máximo, sem máximo lido,
aquisição completa e ausência de evento não provam finalização nem bloqueiam nova escrita nativa.
O teste legado "aquisição completa e silêncio contam como ECU parou" codificava essa presunção.
Commit isolado troca a expectativa por ECU_TRABALHANDO/ecuDone=false, mantém acompanhamento
do contador novo. Acrescenta testes independentes contra aquisição completa silenciosa e latch
de conclusão sobrevivendo à perda do contador. Ambos têm de falhar na fonte 2677823 antes do fix.
Nem tolerâncias/vereditos, nem ACK/readback são relaxados. Classe 1 contrato; RED classe 2 pendente.
