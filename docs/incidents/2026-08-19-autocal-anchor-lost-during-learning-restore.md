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

A reauditoria posterior mostrou uma segunda causa estrutural: a decisão "esta operação ainda pode ser adiada?" estava espalhada entre caminhos de snapshot, ajuste e término do restore. Isso permitiu estados terminais diferentes receberem tratamentos inconsistentes. O Architecture Challenge classificou o caminho como `REDESIGN`: uma única autoridade de lifecycle deve governar a admissão de toda operação diferida.

## Por que os testes não detectaram

`DeferredLiveOnlyLearningStoreTest` cobria startup não bloqueante e ajuste de calibração confirmado durante restore, mas não exercitava `importNativeSnapshot()` durante restore. Os testes de `NativeLearningAnchor` validavam importação/deduplicação quando o store científico já estava disponível. Faltava o cruzamento entre lifecycle assíncrono e evento AutoCal.

Também faltavam falsificadores para os estados terminais `FAILED` e `CLOSED`, e para saturação com payload de ajuste não confirmado.

## Correção

`DeferredLiveOnlyLearningStore` passa a manter uma fila causal curta e limitada de operações materiais durante restore:

- snapshot AutoCal read-only;
- ajuste de calibração confirmado.

Snapshots são deduplicados por sessão + identidade material do snapshot. As operações são reproduzidas na ordem em que chegaram antes de o Learning restaurado ser exposto como READY. A fila tem hard bound de 64 operações; saturação é explícita em métricas/log e nunca é tratada silenciosamente como sucesso. Ajuste confirmado recebe prioridade sobre snapshot diagnóstico quando o bound é atingido.

Nenhum timer, thread serial, writer, protocolo MP48, matemática K/K*, UI ou OBD foi adicionado/alterado.

### Follow-up 117A.2 — prioridade só para ajuste realmente confirmado

A auditoria posterior encontrou uma segunda falha na primeira correção: o wrapper diferido aceitava qualquer payload de `onCalibrationAdjustment()` durante restore e só deixava o `LiveOnlyLearningStore` rejeitá-lo no replay. Assim, um payload sem confirmação/readback podia receber prioridade de segurança indevida e, com a fila cheia, expulsar uma âncora AutoCal válida. Além disso, retorno lógico `ok=false` no replay não era contabilizado como falha se não houvesse exception.

A correção 117A.2 replica no boundary diferido o mesmo gate já usado pelo `LiveOnlyLearningStore`: somente escrita manual com `humanConfirmed=true` + `readbackValid=true`, ou mudança `ECU_NATIVE_AUTOCAL` observada, sem app-write e com readback válido, pode entrar como `CalibrationAdjustment` prioritário. Payload não confirmado é recusado antes da fila. Replay de snapshot/ajuste que devolva `ok=false` incrementa `failedDeferredOperations` e gera log de erro explícito.

### Follow-up 117A.3 — falha terminal não pode fingir deferred

A auditoria seguinte encontrou outra fronteira: após o loader entrar em `LEARNING_RESTORE_FAILED`, a thread de restore já terminou, mas `delegate` continua nulo. Sem um gate terminal, novas operações ainda podiam ser adicionadas à fila e responder `deferred=true`, embora não existisse mais executor capaz de reproduzi-las.

A correção 117A.3 torna o estado terminal fail-closed: snapshot AutoCal ou calibration adjustment recebidos após `LEARNING_RESTORE_FAILED` retornam `ok=false`, `deferred=false` e `reasonCode=LEARNING_RESTORE_FAILED`. Nenhuma operação é adicionada à fila. A telemetria continua independente, conforme o contrato existente.

### Follow-up 117A.4 — uma única autoridade de lifecycle para a fila

O Architecture Challenge foi acionado porque o mesmo mecanismo exigiu correções sucessivas. A investigação mostrou uma classe comum de defeito: `FAILED` havia sido tratado, mas `close()` ainda podia deixar `restoreState` como `RESTORING`; chamadas posteriores poderiam voltar a enfileirar operações sem futuro replay e uma interrupção do loader causada pelo próprio fechamento poderia depois reclassificar o lifecycle como `FAILED`.

A correção 117A.4 introduz estado terminal explícito `LEARNING_CLOSED` e centraliza a decisão de admissão em `deferredAdmissionFailureLocked()`. A regra passa a ser única: somente store aberto em `LEARNING_RESTORING` pode aceitar `deferred=true`. `FAILED`, `CLOSED` ou qualquer estado não-restoring recusam material diferido. `close()` limpa a fila, fixa `STATE_CLOSED` e a captura de exception do loader preserva `CLOSED` quando a interrupção foi causada pelo fechamento.

Isso remove a classe de "fila órfã em estado terminal" em vez de adicionar mais uma exceção isolada.

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

`DeferredLearningRestoreSafetyTest` acrescenta falsificadores adicionais:

1. 64 snapshots distintos + ajuste sem confirmação/readback => os 64 snapshots permanecem, adjustment não entra e não há eviction;
2. 64 snapshots + ajuste manual confirmado/readback => o bound continua 64 e a retirada de um snapshot é explícita;
3. loader forçado a falhar => snapshot e ajuste confirmado posteriores retornam `deferred=false`, fila permanece vazia e o snapshot rejeitado aparece nas métricas;
4. `close()` durante restore => snapshot e ajuste posteriores retornam `LEARNING_CLOSED`, fila permanece vazia e o lifecycle não regressa para `FAILED` depois da interrupção do loader.

A política de fila também foi exercitada em harness Kotlin efêmero com resultado `OWNER_117A_DEFERRED_QUEUE_MODEL=PASS`. Esse harness prova a política causal/bounded isolada; não substitui execução do teste Android/JVM completo.

## Evidência

- Falha original confirmada por leitura remota de `DeferredLiveOnlyLearningStore.importNativeSnapshot()` e do callback `TelemetryForegroundService.onFreshSnapshot`.
- Correção original de produção: commit `06db52785f4b894c2e18c0c7ccac07e9e7e1ae1d`.
- Regressão original versionada: commit `299f97a943650bf92298aa5de7657d66cbe50d50`.
- Follow-up 117A.2 de produção: commit `fbd589a58ae885bc1c201cd2b41bb868d47630f0`.
- Falsificadores de saturação/confirmação: commit `cc7b202d5663aac954a825047c4b08355e4f802f`.
- Follow-up 117A.3 fail-closed após restore failure: commit `e590d2aeb52c34b0159dc67e7bf64dd7eddd70d2`.
- Falsificador de restore terminal: commit `b507686559b871fd574ce24ca74ca39bde1f64a7`.
- Falsificador de lifecycle CLOSED: commit `7baaf597aad74cef0ca42cdcaecf7f5706b6fef1`.
- Redesign de admissão/lifecycle: commit `709cde7666bc2d1d92e4f96a59d1bbcc31e2b310`.
- Harness efêmero: `OWNER_117A_DEFERRED_QUEUE_MODEL=PASS`.

## Risco residual

O teste completo do componente no SHA remoto ainda precisa ser executado antes de um auditor independente poder conceder PASS transversal ao owner 117/117A. A fila é bounded; em saturação extrema um snapshot pode ser rejeitado, mas a perda fica explícita e o recorder continua preservando a evidência bruta. Se a fila for composta apenas por ajustes confirmados, o mais antigo pode ser substituído pelo mais novo para manter boundedness; como nenhuma nova telemetria científica é aceita durante restore, isso preserva a invalidação final, mas o cenário extremo ainda merece teste específico antes do gate transversal. Performance física e comportamento no TayTech/RK3326 continuam pertencendo ao gate 122A.
