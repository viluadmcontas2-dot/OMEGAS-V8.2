# Incidente — checkpoint portátil automático no caminho quente

## Sintoma e impacto
A primeira telemetria de uma sessão disparava `LearningArchiveManager.saveInternalCheckpoint("Primeira telemetria da sessão")` dentro do callback da fila de entrega ao vivo. O encerramento do Foreground Service também tentava um checkpoint portátil completo antes de parar o runtime.

Um checkpoint portátil chama `runtime.exportLearning()`, que drena a fila do Learning e monta Learning + OBD + histórico K. Portanto uma operação de conveniência podia bloquear a fila de telemetria logo no primeiro quadro e alongar o encerramento do serviço.

## Causa imediata
- `consumeEngineEvent()` chamava `saveInternalCheckpoint()` quando `sequence == 1`.
- `onDestroy()` chamava `saveInternalCheckpoint("Serviço encerrado")` antes de `runtime.stop()`/`runtime.close()`.

## Causa estrutural
Checkpoint portátil de continuidade estava sendo tratado como requisito de qualquer início/fim de sessão, embora a memória científica principal já possua persistência própria e os checkpoints realmente críticos sejam os ligados a escrita manual.

## Correção preparada
- Removido checkpoint portátil da primeira telemetria.
- Removido checkpoint portátil automático do `onDestroy()`.
- Preservados checkpoints manuais/críticos:
  - antes de ajustar célula K;
  - antes de lote do Mapa K;
  - antes de K factor;
  - após escrita K confirmada;
  - após escrita K factor confirmada;
  - checkpoints ligados a confirmações nativas já existentes.
- `runtime.close()` continua drenando pipelines e `MotorLearningMemory` continua persistindo seu estado científico por executor dedicado.

## Por que os testes anteriores não detectaram
Os testes validavam presença de checkpoints de segurança, mas não proibiam checkpoint completo dentro da fila de primeira telemetria nem durante a janela crítica de destruição do serviço.

## Teste de regressão
`tests/test_checkpoint_hot_path_contract.py` prova:
- nenhum checkpoint portátil em `consumeEngineEvent()`;
- nenhum checkpoint portátil no corpo de `onDestroy()`;
- checkpoints antes/depois de writers continuam presentes;
- fechamento do runtime continua drenando pipelines;
- persistência científica principal continua assíncrona.

`CHECKPOINT_HOT_PATH_CONTRACT=PASS` e `QUALITY_GATE_FAST=PASS`.

## Evidência
O callback `onTelemetryEvent` é executado pela `telemetryDeliveryPipeline`. Antes da correção, o primeiro callback podia entrar em `exportLearning()`, que chama `flushLearning()` com janela de até 10 s. A remoção corta essa dependência circular do caminho ao vivo.

## Risco residual
- Checkpoint manual antes de writer permanece deliberadamente síncrono porque faz parte da segurança de escrita; sua latência deve ser medida no celular, mas não pode ser removido por otimização.
- Encerramento físico ainda depende de `runtime.stop/close`; precisa validação Android real para confirmar eliminação do histórico `ForegroundServiceDidNotStopInTimeException`.
- APK/Gradle e teste prolongado continuam pendentes.
