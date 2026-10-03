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


## RED Journal e conclusão nativa
Silêncio/latch: run 37106470438, job 111155878452, 612 testes / 3 falhas
nos dois novos adversariais e no contrato de silêncio corrigido isoladamente.
Journal: run 37106640121, job 111156353700, 614 testes / 5 falhas:
as três anteriores e as duas novas exigindo código/operandos na saída pública existente.
Correção: confirmação nativa precisa ser sustentada nesta observação; silêncio/tempo
não afirmam AutoMatch concluído. Contador ausente remove autorização anterior.
Journal grava operandos exatos de cada comparação, limiares atuais e motivo; serviço
registra somente mudança de veredito/estado, separa anomalias funcionais de transporte.
Não altera matemática nesta PR: os limiares atuais ainda serão unificados em D2/D3.
Arquivo/sessão antigos são contexto, não evento novo: assinatura inicial impede replay
de veredito antigo como decisão desta sessão. Eventos persistem no worker, fora da main.
GREEN amplo, mutantes, APK/render desta fonte pendentes.


## Passada de falsificação — mutantes de comportamento
Run 37107213575, job 111157984399, fonte a5595656: os 8 mutantes produziram
o RED esperado em relatórios JUnit compilados; nenhum sobreviveu. Baseline restaurada
passou; gate final falhou por gradlew 100644→100755, causado pelo chmod do build anterior.
APK/render deste run não foram publicados/executados; não alegar prontidão.
Correção do executor preserva git diff --exit-code integral: confere bytes de gradlew
contra HEAD antes de restaurar somente o modo registrado; invoca wrapper por bash.
Não afrouxa nenhum oráculo, não ignora diff de fonte, não mascara conteúdo.
Primeiro veredito pode sumir no baseline antes do primeiro healthTick: novo teste de
render integra service→SessionRecorder worker→JSONL→RESUMO, sem mock de persistência;
deve demonstrar RED antes da correção do latch. UI offline/timeout também aguarda RED.


## Identidade do build após mutação
Run 37107664004, job 111159268243, fonte 11d9baeca929:
baseline 15/15 antes/depois; 8/8 mutantes detectados, 0 sobreviventes; git diff integral limpo.
Gate de identidade corretamente bloqueou publicação: BuildConfig foi regenerado durante
os testes de mutantes com GITHUB_SHA do merge (6d70b82ced77), não PRODUCT_SHA (11d9baeca929).
Não afrouxar conferência: fornecer OMEGAS_SOURCE_SHA=PRODUCT_SHA também na etapa mutante,
como já acontece na etapa de build canônico. APK/render deste run não são entrega verde.
Falha é do pipeline de prova, separada dos vereditos funcionais e do transporte.


## AF1 — recuperação não inventa confirmação por faixa
Fonte 4aef0d66fbe9, run 37108136313, job 111161423744: recuperação real após SIGKILL
executou publicação e comparou ZIP/eventos; falhou somente na frase antiga de sucesso.
O fixture grava status VERIFICADO e bands=[]; razão global 1,06→1,01 não prova tolerância
por faixa. Journal fecha VERIFICADO também com CURTA/PASSOU: verificação encerrada não
é melhora confirmada. Prova independente: SessionResumoDiagnosticTest
verifiedWithoutConfirmedBandsDoesNotInventImprovement falhou antes do fix em 37106082688
e passou em 37106319388; mutante false-summary-confirmation foi morto em 37108136313.
Blueprint proíbe falso sucesso. Commit isolado corrige SOMENTE este oráculo textual e
acrescenta assertFalse para confirmação inventada. Fixture, SIGKILL, processo novo,
ZIP único, digest byte a byte, sequências contíguas e ausência de duplicata ficam intactos.
Não ajusta tolerância ou matemática; classe 4 limitada à recuperação em emulador sem ECU.


## RED integrado — sessão e destaque desconectado
Fonte 4aef0d66fbe95ce6f9a3e430a1bd10618e1cfd32, run 37108136313:
- job 111161422918: AndroidTest compilado/instalado, service→worker→JSONL→RESUMO;
  primeiro veredito fechado antes do primeiro healthTick esperado=1, encontrado=0.
- job 111161422943: WebView real 1280×720; SEM_ECU correto, mas destaque da razão
  mostra -4,8% histórico quando deveria mostrar —. Sem mock do render.
- job 111161423002: timeout honesto passou (frase visível ≥12px, próximo passo, sem CTA antigo).
- APK/mutação job 111160579045: baseline 15 antes/depois, 8 mortos/0 sobreviventes,
  fonte/BuildConfig exatos; APK 4.830.796 bytes, sha256
  0d27324a2904be54c3eb42c06d4088acb747e4e40104b46dd929b8bf543a5ee5.
  Artifact 11269265181, ZIP digest ad39792bb789759e29d82fa73e654742f036b7a692238f5b651769af671bd08b.
  Este APK é evidência RED da UI/sessão, NÃO entrega verde desta PR.
Correção mínima LC08: registrar como histórico somente o id já fechado quando o arquivo
foi carregado; primeira checagem nunca apaga o primeiro experimento novo.
Correção mínima LC10: destaque da razão desconhecido sem ECU/leitura/tentativa encerrada;
gráfico e diário continuam preservados. Não muda matemática, escrita ou tolerâncias.
GREEN desta fonte e revisão independente pendentes. Lacuna irmã encontrada: legenda
histórica VERIFICADO na UI ainda diz "Chegou na gasolina"; precisa RED próprio em D5.
Download para inspeção visual local bloqueado: ambiente de execução desconectado (409).
Não alegar inspeção humana das imagens; os testes Android e seus logs são a prova obtida.


## GREEN integrado anterior e falsificação independente
Fonte de12f9ac53e1e609fb7a626dbd9e7f67e68d33a4:
CI37109036435 success; gate37109036557 30/30 jobs success (APK + 29 cenários Android).
Root conferiu diretamente lista completa de jobs e logs de primeiro veredito111164109831,
offline111164109804 e SIGKILL111164110683, cada SCENARIO_RESULT=PASS.
APK4.831.148 bytes, SHA256 e7bb3c2d7053e4b8c634af5409efa1fe9efe1a8b4e5ba5f63b043d03ddc13942;
artifact11269136496, ZIP60724ae92d69208c0bfc9f61d0212e9630bd3111dc79d7b0aad721792c965d71.
Baseline15/15 antes/depois; mutantes8 mortos/0 sobreviventes. Não libera toda Fase1:
revisão independente encontrou Journal lendo somente latest a cada tick, perdendo transições
write1→interrupt→write2 antes da próxima checagem, e onlineMs dentro da assinatura visual.
Há também identidade EXP-clock duplicada com calendário repetido: outro latch por relógio.
Novo RED antes do código: 2 testes JVM (identidade repetida, tick sem dado) e
1 integração Android (quatro motivos/dois encerramentos no JSONL antes de parar a sessão).
Nenhum oráculo antigo alterado neste commit. PRIMEIRO coletar RED JVM; depois fix mínimo
identidade/assinatura e executar RED Android; somente então corrigir transporte de decisões.
Performance observada: cold median18,64ms/p9545,79ms; cache0,00019ms. Alvo30ms frio não
cumprido neste run; não mascarar por cache ou medição anterior23,96ms. P6 pendente.


## RED válido — identidade e apresentação
Fonte32d24f630e1664af2b48193fc5be44fb1fb5e946, CI37110275413/job111166653375:
616 testes compilados/executados, exatamente2 falhas novas:
elapsedTimeAloneDoesNotRepublishVisibleDecision e
confirmedWritesHaveDistinctIdentityEvenWhenClockIsFrozen.
Correção mínima: sequência do experimento persistida compatível com v1 (calendário não
identifica sozinho); assinatura visual exclui somente onlineMs, que permanece no
diagnóstico e no tempo global. Sem mudar matemática/vereditos/oráculos.
Dois mutantes reais reintroduzem id por relógio e assinatura por tempo; baseline agora17.
RED Android entre ticks ainda pendente; listener de decisões NÃO implementado neste commit.

## Adendo D7 aceito — ordem e limite de prova
Pedido do dono às04:49/2026-10-03 (America/Sao_Paulo): AGORA fora da curva em3,5–4,3ms,
MAP0,40–0,65bar, "GNV OK", restauração8pontos e apagões2,5–3,0ms/0,26bar.
Isso é RELATO FÍSICO DO DONO, não medição física realizada pelo executor.
Manter objetivo anterior e ordem dos slices; depois executar D7: contrato COERENCIA.md
com campos/unidades/filtros/referências/precedência, regime estável explícito, resíduos
por MAP e por ms. Investigar H1visual/H2transiente/H3estado/H4proteção/H5projeção/
H6curva antiga/H7referência; cada conclusão precisa número e classe, aceita múltiplas causas.
Ainda INCONCLUSIVAS: nenhum replay D7 executado, nenhum resíduo desta região medido.
Não declarar "rico" somente por posição visual, nem remover proteção por opinião.
Aceites adicionais: S1–S8 do dono no gêmeo digital real fechado, mutantes de projeção/trava,
renders1280×720 de AGORA estável/transiente/divergência, relatório≤12linhas com limites.
Temperatura/corte/embreagem/frescor desconhecidos são lacunas de entrada; não inventar.

## Transições entre ticks — RED Android válido e correção mínima
- Fonte antiga `a4b3461d05d949abe0d7779d86d0b485588ef4c3`, CI `37110568906` verde; APK gate `37110569079`: 17 testes focados antes/depois, **10 mutantes mortos / 0 sobreviventes**, identidade exata verificada.
- Classe4: job `111168345115`, APK instalado no emulador1280×720. `refinoJournalTransitionsReachSessionBeforeNextTick` esperava quatro motivos em ordem e recebeu **[]**; 1 teste/1 falha comportamental. Não é falha de compilação.
- Correção: Journal publica cópia independente da transição; serviço enfileira decisão/veredito imediatamente no worker existente. Sem I/O no listener, sem espera por healthTick, sem escrita ECU. Histórico carregado não é repetido. Deduplicação dos encerramentos limitada aos40 experimentos do Journal preserva idempotência do fallback e do primeiro veredito.
- Contratos novos exercitam ordem, interrupção, substituição sem evento externo, cópia independente e desligamento do listener. Mutantes removem listener/cópia/evento de substituição; resultado ainda pendente até CI.
- Radar11: frase/ação existente preservada; pior caso perda de causa tratada por entrega imediata; nenhum novo estado de espera; números vêm do snapshot; mesma fonte Journal; IDs resistem relógio repetido; reinício/versão velha preservados pelos testes anteriores, SIGKILL após enqueue não equivale a garantia de durabilidade; classe2+4, nunca5; mudança justificada LC02/LC08; não cobrir falha de disco silenciosa permanece achado; nenhuma pergunta/chave nova; falsificação com callbacks em sequência e polling repetido, sem tocar oráculos congelados.
- GREEN deste ajuste, APK/digest e revisão independente **pendentes**; missão/D7/performance continuam abertos.


## Inspeção visual independente — gate incompleto
Fonte c3bdcac, gate37131229122, artifact11276967322: captura1280×720 de
refino-decisoes-entre-ticks coberta por ANR do Pixel Launcher apesar de PASS do DOM.
Isso é RED visual classe4 do pipeline, não diagnóstico de travamento do OMEGAS.
Receipt também dizia83c67d8 (merge de workflow), embora checkout logassec3bdcac.
Correção de prova: BuildConfig recebe a fonte exata; HOME do emulador efêmero é
isolado antes dos testes; saveEvidence exige janela ativa do próprio app, e rejeita
qualquer diálogo externo em vez de aceitá-lo como prova. Nenhum assert antigo removido.
APK anterior mantém identidade exata; render anterior não libera fechamento visual.
