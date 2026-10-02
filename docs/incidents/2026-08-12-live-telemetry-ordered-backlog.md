# Incidente — entrega visual tratava estado ao vivo como histórico ordenado

## Sintoma e impacto
Em consumidor visual mais lento que a aquisição MP48, cada quadro aceito permanecia em uma fila ordered sem capacidade máxima. A UI podia receber estados antigos com atraso crescente e o fechamento podia esperar backlog que já não tinha valor operacional.

## Causa imediata
`NativeRuntimeManager` entregava cada evento de telemetria por `OrderedBackgroundPipeline`, cuja semântica preservava todas as tarefas aceitas.

## Causa estrutural
Estado visual ao vivo foi modelado como histórico. Para telemetria de painel, quadros intermediários ainda não consumidos são substituíveis pelo estado mais recente.

## Por que os testes não detectaram
O JUnit anterior exigia explicitamente que 100 de 100 tarefas fossem preservadas em ordem. O contrato Python verificava apenas presença de executor e métrica `pending`, portanto protegia o comportamento contrário ao requisito de fluidez.

## Correção
A produção passa a usar `LatestOnlyBackgroundPipeline`: uma tarefa pode estar ativa e existe no máximo um estado pendente. Novo `submit` substitui o pendente anterior; a transação MP48 e o quadro já em processamento nunca são interrompidos.

## Regressão
- consumidor bloqueado + 99 novos quadros: ao liberar, observa o quadro ativo e depois somente o quadro 100;
- `pending <= 1`;
- coalescência contabilizada;
- falha de consumidor não impede o estado mais recente posterior.

## Evidência
Contrato local `test_multimedia_telemetry_backpressure_contract.py` e gate rápido. Validação física em Android/multimídia permanece pendente.

## Risco residual
A correção elimina backlog visual em RAM, mas não substitui o scheduler serial único. Operações Mapa K/Curva K/AutoCal ainda precisam ser migradas para a autoridade MP48 central em bloco separado.
