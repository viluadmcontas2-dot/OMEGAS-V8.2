# Incidente — backlog do aprendizado e células pouco didáticas

Data: 2026-08-09
Status: correção aplicada na branch; validação automatizada e física pendentes no momento deste registro.

## Sintoma e impacto

Durante uma sessão física real fornecida pelo proprietário, a telemetria continuou chegando em alta frequência, mas o aprendizado passou a processar o passado. A sessão observada acumulou milhares de tarefas pendentes e chegou a uma defasagem de ordem de minutos.

Na interface de Aprendizado, a grade agravava a percepção de que nada estava sendo aprendido:
- Gasolina e GNV exibiam principalmente contagem de amostras;
- comparação exibia apenas percentual;
- células sem comparação pronta permaneciam como ponto/reticências;
- Live Tracing escrevia percentuais de contribuição sobre as próprias células, podendo parecer dado aprendido mesmo quando representava apenas posição instantânea.

Impacto prático: aprendizado útil demorava a aparecer, sugestões e comparações pareciam inexistentes e o operador não conseguia conferir rapidamente o tempo médio armazenado em cada região.

## Causa imediata

1. `NativeRuntimeManager` mantém o aprendizado fora da thread ECU, porém cada quadro é enviado para um `OrderedBackgroundPipeline` de um único worker.
2. `SignalLearningStore.ingest()` fazia persistência síncrona do sidecar `learning_v6_evidence.json` em toda passagem pelo aprendizado, inclusive decisões intermediárias sem nova evidência.
3. O modelo JavaScript descartava as grandezas físicas `rpm`, `map_bar` e `petrol_ms` que já existiam na projeção Kotlin por célula.
4. `PhysicalGrid.setTrace()` escrevia a porcentagem de interpolação ao vivo no mesmo subtexto usado pela célula aprendida.
5. O full render do Aprendizado repetia `setTrace()` apesar de o scheduler rápido já ser a autoridade do tracing ao vivo.

## Causa estrutural

A arquitetura tinha separado a thread ECU do trabalho pesado, mas tratou uma fotografia substituível de estado como se cada versão precisasse ser persistida individualmente. A fila preservava ordem, porém não havia contrato de desempenho que proibisse I/O por quadro ou exigisse coalescência para snapshots.

Na UI, os testes validavam presença de estados e tracing, mas não o objetivo humano: olhar a célula e entender rapidamente o valor aprendido e a equivalência gasolina ↔ GNV.

## Por que os testes não detectaram

- O teste de `OrderedBackgroundPipeline` provava que todas as tarefas eram preservadas, mas não impunha limite para atraso acumulado.
- O contrato de backpressure apenas provava que o aprendizado estava fora da thread ECU e que métricas de fila existiam.
- Não existia teste impedindo `writeText()` no caminho quente de `SignalLearningStore.ingest()`.
- O teste do modelo visual não verificava preservação de RPM, MAP e tempo médio por combustível.
- O contrato UI aceitava percentuais de Live Tracing na célula sem verificar se eles substituíam o conteúdo aprendido.

## Correção aplicada

### Persistência leve

Criado `CoalescedSnapshotWriter` para arquivos que representam apenas o estado mais recente:
- gravação fora do caminho de telemetria;
- múltiplas fotografias intermediárias podem ser coalescidas;
- a fotografia mais nova é preservada;
- fechamento/flush força a última fotografia aceita ao armazenamento;
- métricas tornam pedidos, gravações e coalescência observáveis.

`SignalLearningStore` agora solicita persistência do sidecar somente quando o analisador produziu uma amostra ou em fronteiras explícitas como encerramento/ajuste. A memória principal e `delegate.ingest()` continuam recebendo o fluxo necessário; a otimização não altera equivalência RPM/MAP nem descarta amostra aceita.

### UI didática

- O modelo visual passa a preservar RPM médio, MAP médio e `petrol_ms` médio de Gasolina e GNV.
- Camada Gasolina mostra o tempo médio de referência em ms.
- Camada GNV mostra o `Petrol Inj.` médio observado enquanto o motor opera no GNV.
- Comparação mostra erro e, quando disponível, `referência → observado` em ms.
- O detalhe da célula mostra RPM, MAP, tempos, evidência e equivalência.
- Live Tracing deixa de escrever porcentagem sobre o conteúdo aprendido; permanece somente como posição/halo visual, com pesos no painel de detalhe.
- O tracing rápido continua sob o único Scheduler existente; o full render não o duplica.

## Invariantes preservados

- equivalência física continua selecionada pelo núcleo, com RPM + MAP como condição primária;
- nenhuma tolerância foi afrouxada neste bloco;
- gasolina continua referência;
- sugestão não escreve;
- writers, protocolo MP48, ACK/readback, Curva K, Mapa K e OBD não foram alterados por esta correção;
- nenhuma escrita automática na ECU foi adicionada.

## Testes de regressão

- `CoalescedSnapshotWriterTest`: armazenamento lento deve coalescer snapshots intermediários e persistir o estado mais novo.
- `test_multimedia_telemetry_backpressure_contract.py`: proíbe I/O direto no `ingest`, exige persistidor coalescido e preserva `delegate.ingest`.
- `learning-view.test.cjs`: prova que RPM, MAP e tempo médio reais chegam ao modelo visual.
- `test_ux_didactic_expansion_contract.py`: prova os rótulos/valores didáticos, a separação do Live Tracing e a existência de apenas um Scheduler.

## Evidência

Investigação correlacionou:
- sessão física de dirigibilidade fornecida pelo proprietário, na qual a fila do aprendizado acumulou milhares de tarefas e defasagem de ordem de minutos;
- PortMon de dirigibilidade fornecido pelo proprietário, usado como contexto de comportamento do motor, sem copiar limiares do software original;
- código remoto vivo da branch `fix/v8-consistency-safety`.

Os arquivos físicos usados na investigação não estão versionados neste repositório; portanto seus hashes não são declarados aqui como prova remota durável. A validação final desta correção deve usar CI do commit candidato e nova sessão no aparelho/veículo.

## Risco residual

A remoção do I/O síncrono ataca o gargalo objetivo observado, mas ainda é necessário confirmar no aparelho que:
- `learningPipeline.pending` não cresce continuamente durante condução;
- atraso de fila permanece baixo em sessão prolongada;
- UI horizontal 9" continua fluida;
- valores exibidos correspondem às regiões realmente aprendidas;
- comparação/sugestão aparecem com a velocidade esperada sem mudar os critérios físicos de equivalência.

Até essa validação: **AGUARDANDO CELULAR/VEÍCULO**.
