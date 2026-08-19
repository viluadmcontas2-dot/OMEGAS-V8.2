# Incidente — âncora AutoCal perdida durante restauração do Learning

Data: 2026-08-19
Owner relacionado: 117 / 117A

## Sintoma e impacto

Um snapshot AutoCal nativo válido podia chegar enquanto `DeferredLiveOnlyLearningStore` ainda estava em `LEARNING_RESTORING`. O recorder preservava o snapshot bruto, mas a importação científica devolvia indisponível e não existia retry. Na prática, uma âncora nativa correlacionada podia desaparecer do ledger científico usado pelo Learning.

Impacto: perda silenciosa de evidência contextual válida, maior tempo até informação útil e divergência entre o que foi observado/gravado e o que entrou na memória científica. Não há evidência de escrita automática na ECU causada por este incidente.

## Causa imediata

`DeferredLiveOnlyLearningStore.importNativeSnapshot()` encaminhava para `LiveOnlyLearningStore` somente quando o delegate já estava pronto. Durante restore retornava `unavailable(...)` sem reter a operação.

`TelemetryForegroundService` chamava `runtime.importNativeAutoCalSnapshot(snapshot)` e em seguida gravava o snapshot no recorder, mas não existia uma segunda tentativa de importação após o Learning ficar READY.

## Causa estrutural

A restauração assíncrona já possuía tratamento diferido para ajuste de calibração confirmado, porém snapshot AutoCal e ajuste não compartilhavam uma fronteira causal única. O lifecycle `RESTORING → READY` tratava telemetria como descartável para ciência durante restore, mas não distinguia eventos materiais raros que precisam sobreviver à janela de inicialização.

## Por que os testes não detectaram

`DeferredLiveOnlyLearningStoreTest` cobria startup não bloqueante e ajuste de calibração confirmado durante restore, mas não exercitava `importNativeSnapshot()` durante restore. Os testes de `NativeLearningAnchor` validavam importação/deduplicação quando o store científico já estava disponível. Faltava o cruzamento entre lifecycle assíncrono e evento AutoCal.

## Correção

`DeferredLiveOnlyLearningStore` passa a manter uma fila causal curta e limitada de operações materiais durante restore:

- snapshot AutoCal read-only;
- ajuste de calibração confirmado.

Snapshots são deduplicados por sessão + identidade material do snapshot. As operações são reproduzidas na ordem em que chegaram antes de o Learning restaurado ser exposto como READY. A fila tem hard bound de 64 operações; saturação é explícita em métricas/log e nunca é tratada silenciosamente como sucesso. Ajuste confirmado recebe prioridade sobre snapshot diagnóstico quando o bound é atingido.

Nenhum timer, thread serial, writer, protocolo MP48, matemática K/K*, UI ou OBD foi adicionado/alterado.

## Teste de regressão

`DeferredLiveOnlyLearningStoreTest` ganhou cenário concorrente controlado por latch:

1. segura o restore aberto;
2. envia snapshot A;
3. repete snapshot A e exige deduplicação;
4. envia ajuste de calibração confirmado;
5. envia snapshot B;
6. libera restore;
7. exige replay causal `A → ajuste → B`;
8. exige que A seja supersedido pelo ajuste e somente B permaneça como âncora;
9. exige fila vazia, dois snapshots reproduzidos, uma duplicata e zero falhas de replay.

A política de fila também foi exercitada em harness Kotlin efêmero com resultado `OWNER_117A_DEFERRED_QUEUE_MODEL=PASS`. Esse harness prova a política causal/bounded isolada; não substitui execução do teste Android/JVM completo.

## Evidência

- Falha anterior confirmada por leitura remota de `DeferredLiveOnlyLearningStore.importNativeSnapshot()` e do callback `TelemetryForegroundService.onFreshSnapshot`.
- Correção de produção: commit `06db52785f4b894c2e18c0c7ccac07e9e7e1ae1d`.
- Regressão versionada: commit `299f97a943650bf92298aa5de7657d66cbe50d50`.
- Harness efêmero: `OWNER_117A_DEFERRED_QUEUE_MODEL=PASS`.

## Risco residual

O teste completo do componente no SHA remoto ainda precisa ser executado antes de um auditor independente poder conceder PASS transversal ao owner 117/117A. A fila é bounded; em saturação extrema um snapshot pode ser rejeitado, mas a perda fica explícita e o recorder continua preservando a evidência bruta. Performance física e comportamento no TayTech/RK3326 continuam pertencendo ao gate 122A.
