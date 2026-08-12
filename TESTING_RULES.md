# Regras globais de teste

## Princípio
Testes devem provar comportamento observável, não apenas presença de função, texto ou seletor. A estratégia completa está em `docs/TEST_STRATEGY.md`.

## Ordem mínima
1. reproduzir o defeito ou demonstrar o gap;
2. teste focado;
3. testes do componente;
4. contratos;
5. cenários e consequências;
6. propriedades e invariantes;
7. lint;
8. build;
9. integração;
10. emulador;
11. APK;
12. validação no celular;
13. validação física no veículo quando aplicável.

## Gates obrigatórios

### Gate rápido
Executar `python -B tools/run_checks.py`. Ele deve incluir governança, contratos Python e testes JavaScript ativos. Nenhum teste pode ser removido apenas para obter resultado verde; quando um componente é substituído, a cobertura antiga deve ser migrada para o novo contrato antes de o teste morto sair do gate.

### Gate Android
Executar, nesta ordem, quando a fronteira correspondente estiver autorizada:

- `./gradlew testDebugUnitTest`;
- `./gradlew lintDebug`;
- `./gradlew assembleDebug`.

Na CI, quando geração de APK estiver autorizada, o APK debug e os relatórios devem ser publicados como artifacts temporários com o commit no nome e SHA-256 do APK. Um bloco que não tenha autorização para APK pode executar `testDebugUnitTest` e `lintDebug` e permanece **PARCIAL** quanto ao gate Android completo.

### Gate de sessão, telemetria e uso contínuo
Toda mudança no shell, telemetria, conexão ou ciclo de vida deve provar:

- serviço indisponível, permissão USB pendente e ECU desconectada;
- telemetria fresca, atrasada e expirada;
- comunicação travada e erro recente;
- observação e condução provável sem depender de um modo oficina visual;
- gate nativo de escrita exige serviço ativo, USB conectado, engine pronta/não travada, telemetria atual e RPM abaixo do limiar preventivo;
- reconexão não inicia escrita;
- segundo plano e retomada reutilizam o mesmo scheduler visual, sem timer concorrente;
- nenhuma transição de sessão, sugestão, timer, leitura ou conexão chama writer;
- um único agendador permanece responsável pela atualização visual;
- 9" horizontal, 1024×600, celular vertical e celular estreito continuam utilizáveis.

Para desempenho de aprendizado sob telemetria contínua, provar adicionalmente:

- nenhum `writeText`/I/O físico de sidecar ocorre a cada quadro no caminho de `ingest`;
- snapshots substituíveis podem ser coalescidos, mas a fotografia mais nova deve chegar ao armazenamento em `flush`/fechamento;
- amostra aceita e comparação válida continuam chegando ao núcleo; coalescência de snapshot não pode virar descarte de evidência física;
- métricas de fila e persistência permanecem observáveis;
- no celular/multimídia, `learningPipeline.pending` não cresce continuamente em uma sessão prolongada e o atraso não pode escalar para minutos;
- a WebView não persegue pesos/células por Live Tracing visual; o caminho rápido mostra apenas RPM + Petrol Inj. + célula atual;
- a remoção do Live Tracing visual não altera a interpolação bilinear/trilinear executada no Kotlin.

A classificação `condução provável` é uma proteção preventiva por RPM e não pode ser apresentada como velocidade confirmada.

### Gate profundo de lógica
Toda mudança em aprendizado, comparação, sugestão, projeção, persistência ou mapa deve provar, conforme aplicável:

- conservação de peso nas interpolações;
- limites e índices válidos em toda a grade;
- preenchimento das células esperadas;
- coerência entre memória, interface e exportação;
- invariância à ordem das amostras;
- rejeição de dados inválidos;
- separação entre tendência global da Curva K e resíduo local do Mapa K;
- sugestão com direção coerente e magnitude limitada pelo erro observado;
- dados contraditórios não viram correção por volume;
- época antiga de GNV não influencia decisão/prontidão atual e aparece somente como histórico consultável;
- gasolina permanece referência preservada;
- nenhuma sugestão, conexão, timer ou leitura chama writer;
- resultado final de escrita vem de ACK e readback reais;
- um `visitId` já registrado é evidência científica imutável: snapshot posterior não reescreve RPM, MAP, Petrol Inj., qualidade ou timestamp daquela visita;
- comparações usam a cronologia física da visita e permanecem determinísticas após reinício/reprocessamento;
- evidência repetível promove `CONSOLIDATED`;
- um outlier isolado depois da consolidação não desloca o valor consolidado e entra como `REVALIDATING`;
- tendência contraditória repetível pode promover novo consolidado em nova geração, evitando congelamento eterno;
- a mesma coleção de evidências em ordem diferente produz o mesmo consolidado;
- peso bilinear não transforma uma visita física em quatro visitas independentes;
- sugestão pendente permanece com a mesma magnitude enquanto a mesma geração consolidada estiver válida;
- durante `REVALIDATING`, sugestão permanece visível mas não acionável;
- Curva K global exige consolidação e cobertura independente em mais de uma condição de RPM/MAP; evidência localizada continua pertencendo ao Mapa K;
- schema novo de persistência lê versões anteriores sem apagar ou reinterpretar destrutivamente o arquivo existente.

Preferir testes determinísticos de propriedades com centenas de combinações no JVM em vez de depender apenas de exemplos manuais.

### Gate de emulador
O workflow `OMEGAS Emulator Smoke` é executado sob demanda para preservar minutos do plano gratuito. Deve:

- gerar e instalar o APK real somente quando a fronteira de APK estiver autorizada;
- abrir a `MainActivity`;
- confirmar que a atividade permanece ativa;
- capturar hierarquia visual, screenshot e logcat;
- falhar em crash, `FATAL EXCEPTION` e `ForegroundServiceDidNotStopInTimeException`.

O emulador prova inicialização Android/WebView e ausência de crashes básicos. Não prova USB, ECU, OBD físico, ACK ou readback.

### Gate físico
Android, USB, ECU, OBD, serviço, WebView, suspensão, retomada, ACK e readback exigem validação no aparelho. Até isso acontecer, usar `AGUARDANDO CELULAR` ou `AGUARDANDO VEÍCULO`.

Para estabilidade física do aprendizado, provar adicionalmente:
- célula consolidada permanece estável sob passagens normais e ruído casual;
- mudança real e persistente aparece primeiro como `REVALIDATING` e depois pode promover novo consolidado;
- reiniciar o aplicativo preserva a mesma memória consolidada;
- sessão prolongada não volta a criar backlog de minutos nem degrada progressivamente CPU/RAM.

## UI clean-slate
Provar:

- exatamente cinco destinos principais: Dashboard, Aprendizado, Mapa K, Curva K e OBD;
- uma única Store, um Router e um Scheduler;
- nenhum shell antigo ativo, `MutationObserver` reorganizador ou timer concorrente;
- modo oficina removido da UI ativa;
- nenhum checkbox redundante de confirmação;
- botão `Gravar ... na ECU` só existe após revisão explícita;
- modo navegador/simulado nunca transmite escrita;
- telas inativas não fazem render pesado contínuo;
- Live Tracing visual permanece removido do DOM da grade; nenhum halo/trail/peso bilinear é perseguido pela WebView;
- a interpolação contínua permanece matemática Kotlin e não é recalculada pela UI;
- o contexto rápido mostra somente RPM + Petrol Inj. + célula atual, com atualização quantizada/leve;
- histórico GNV de época anterior não aparece como evidência atual;
- Curva K usa prévia do Kotlin para `currentRaw/targetRaw`, sem conversão Q14 no JavaScript;
- OBD permanece somente observação.

## Mapa K
Provar:

- leitura antes da seleção e leitura automática ao abrir sem iniciar escrita;
- editor visível no mesmo contexto do mapa;
- eixos e valor corretos;
- releitura da ECU;
- limites K;
- seleção de **1 a 144** células graváveis;
- seleção por célula, faixa, linha, coluna e grade completa quando aplicável;
- linha técnica protegida e fora das 144 células;
- prévia antes/depois;
- cancelar sem escrever;
- uma única intenção humana de lote enviada ao adaptador V7;
- particionamento interno, se necessário, acontece no Kotlin e não no JavaScript;
- falha antes do primeiro bloco não é sucesso;
- falha depois de blocos confirmados é `BATCH_PARTIAL_FAILED` e informa quantidade confirmada;
- falha de ACK não é sucesso;
- readback divergente não é sucesso;
- atualização visual vem do readback/releitura real;
- contexto é preservado quando seguro.

## Curva K
Provar:

- leitura dos 30 pontos antes da edição;
- curva atual e proposta visualmente separadas;
- seleção de ponto e prévia pelo planner Kotlin;
- revisão antes da escrita;
- cancelar sem escrever;
- gate nativo de condição segura antes da escrita;
- backup, ACK e readback permanecem no Kotlin;
- sucesso somente com `BATCH_CONFIRMED` e `readbackValid=true`;
- sugestão global abre Curva K para revisão e nunca escreve diretamente;
- uma única condição física localizada não libera proposta global madura.

## Aprendizado
Provar:

- mesma grade física para Gasolina, GNV, Comparação e Sugestão;
- Gasolina e GNV atual distinguidos por época;
- GNV histórico não contamina prontidão atual;
- comparação só é apresentada como pronta quando o núcleo possui comparação válida;
- camada Gasolina apresenta o `Petrol Inj.` médio real fornecido pela projeção nativa, não apenas contagem;
- camada GNV apresenta o `Petrol Inj.` médio observado enquanto roda no GNV, sem rotulá-lo como pulso do bico de gás;
- detalhe da célula preserva RPM médio, MAP médio e tempos usados para explicar a equivalência;
- comparação permite entender `referência → observado` em ms e o erro correspondente;
- acelerar processamento não altera RPM/MAP, tolerâncias ou o seletor de referência física;
- a grade mostra `Aprendendo`, `Consolidado` ou `Revalidando` usando a decisão nativa; JavaScript não inventa critérios científicos;
- quando houver consolidado, o número principal da comparação é o valor consolidado, e a estimativa recente contraditória aparece apenas como revalidação no detalhe;
- sugestão local abre Mapa K; sugestão global abre Curva K; abrir nunca escreve.

## UI adaptativa
Provar em pelo menos:

- 9" 16:9 horizontal como experiência principal;
- 1024×600 como altura automotiva crítica;
- celular vertical como modo rápido;
- celular estreito;
- sem editor escondido;
- sem navegação duplicada;
- sem rolagem horizontal acidental fora da região do mapa/grade quando essa rolagem for necessária;
- mesma autoridade de estado em todos os formatos.

## Economia de CI
Para repositório privado no plano gratuito:

- gate rápido em toda mudança relevante;
- gate Android somente quando código Android, Gradle, configuração ou workflow mudar e dentro da autorização vigente;
- emulador apenas sob demanda ou antes de um checkpoint importante;
- cancelar execução antiga da mesma branch;
- artifacts por 7 dias, comprimidos;
- não repetir workflow verde no mesmo commit sem evidência nova.

## Governança executável
`tests/test_governance_contract.py` deve validar documentos obrigatórios, regra remoto primeiro, autorização por bloco, invariantes de escrita, workflows completos, arquitetura clean-slate e ausência de material de assinatura.

## Resultado
Usar somente estes estados: `PASSOU AUTOMATIZADO`, `PARCIAL`, `FALHOU`, `AGUARDANDO CELULAR` e `AGUARDANDO VEÍCULO`.

CI verde prova somente o comportamento realmente exercitado. APK gerado não prova comunicação com ECU.
