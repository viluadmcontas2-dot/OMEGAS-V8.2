# Contratos de arquitetura (blocos 1 a 6)

> Fundido em 2026-10-20 dos seis arquivos `BLOCK_*.md`; texto preservado. Visão geral das camadas: `ARCHITECTURE.md`.

---

## Bloco 1 — Sessão, telemetria e uso contínuo

### Resultado observável
A tela Agora passa a representar o estado real da sessão, e não apenas números instantâneos. A mesma autoridade de estado decide rótulos, alertas, modo de uso e permissão visual de escrita.

### Estados cobertos
- serviço indisponível;
- permissão USB pendente;
- ECU desconectada;
- telemetria atrasada;
- telemetria expirada;
- comunicação travada;
- sessão em observação;
- condução provável;
- modo oficina ativo;
- modo oficina suspenso;
- aplicativo em segundo plano.

### Regras de segurança
- modo oficina é ativado manualmente;
- conexão, timer, retomada ou sugestão nunca ativam modo oficina;
- telemetria acima de 2,5 s deixa de ser adequada para edição;
- telemetria acima de 8 s é tratada como expirada;
- RPM a partir de 1.200 indica condução provável e suspende escrita;
- desconexão desativa o modo oficina;
- somente modo oficina ativo, ECU conectada e telemetria fresca permitem confirmar escrita;
- checkpoint, ACK e readback permanecem obrigatórios;
- a inferência de condução é preventiva e não substitui velocidade real do veículo.

### Uso contínuo
Suspensão, retomada, foco e segundo plano atualizam a apresentação sem criar outro timer. O aplicativo preserva um único `setInterval`, controlado pelo shell principal.

### Evidência automatizada
- testes determinísticos de todos os estados;
- 500 combinações pseudoaleatórias de conexão, idade da telemetria, RPM, serviço e modo oficina;
- desconexão e reconexão;
- suspensão e retomada visual;
- garantia de que nenhuma transição chama o writer;
- responsividade horizontal, 1024×600 e celular vertical.

### Limites
Sem velocidade do veículo, `condução provável` é inferida por RPM e telemetria. A validação de uso real ainda exige celular, multimídia, USB e veículo.

---

## Bloco 2 — Aprendizado, cobertura e comparação

### Status
Contrato técnico preparado. Este documento não autoriza implementação, merge, release, publicação, Netlify, instalação, escrita na ECU ou alteração da `main`.

### Origem e destino
- Origem: estado atual da branch `rebuild/ux-9in-dual-layout` após o Bloco 1.
- Destino observável: uma área de Aprendizado que explique, sem ambiguidade, o que já foi aprendido, onde há cobertura, onde faltam dados e quais comparações gasolina × GNV são válidas na época atual.

### Resultado observável
Ao abrir Aprendizado, o usuário deve conseguir responder:
1. Quais regiões do mapa possuem evidência suficiente?
2. Quais regiões têm apenas gasolina, apenas GNV ou ambos?
3. Quais células estão prontas para comparação?
4. Por que uma célula ainda não está pronta?
5. A evidência pertence à época atual do Mapa K?
6. O que mudou entre gasolina e GNV sem transformar isso automaticamente em escrita?

### Escopo autorizado somente após aprovação de implementação
- visualização de cobertura na grade física do Mapa K;
- distinção clara entre gasolina, GNV e região comparável;
- contagem e qualidade das amostras por célula/região;
- explicação de prontidão e insuficiência;
- separação por época do Mapa K;
- exclusão de evidência antiga das decisões da época atual, sem apagar o histórico;
- preservação do contexto ao abrir uma região;
- testes de consequências do aprendizado;
- documentação e incidente quando houver defeito confirmado.

### Fora de escopo
- alterar matemática central do aprendizado;
- alterar protocolo MP48, USB, ACK ou readback;
- alterar escrita do Mapa K;
- reconstruir Curva K;
- aceitar ou aplicar sugestões automaticamente;
- alterar OBD;
- migrar persistência sem contrato separado;
- refatoração geral da interface;
- merge, release, publicação ou Netlify.

### Invariantes
- nenhuma escrita automática na ECU;
- abrir Aprendizado, tocar célula, receber sugestão ou mudar época nunca inicia escrita;
- OBD permanece observacional;
- Curva K e Mapa K mantêm responsabilidades separadas;
- gasolina é preservada como referência;
- UI apenas apresenta dados produzidos pelo núcleo Kotlin;
- linha técnica do Mapa K permanece protegida;
- histórico antigo pode ser consultado, mas não pode contaminar decisão da época atual;
- mesma autoridade de estado deve controlar seleção, detalhe, filtros e renderização.

### Áreas e consumidores a inspecionar antes da implementação
- produtores Kotlin de regiões, células projetadas, comparações, épocas e sugestões;
- exportação/importação `.omegas` relacionada ao aprendizado;
- WebView e adaptador nativo;
- tela atual de Aprendizado e seus consumidores JavaScript;
- `LearningGridProjection`;
- `AssistedCalibrationAdvisor`;
- persistência das épocas e relação com alterações do Mapa K;
- workflows e testes Android aplicáveis.

### Critérios de aceite
#### Cobertura
- grade física mantém 12 × 12 células visuais;
- célula sem evidência não aparece como pronta;
- gasolina, GNV e comparável usam estados diferentes e explicados;
- cobertura não é confundida com sugestão ou autorização de escrita.

#### Prontidão
- o motivo de não prontidão é mostrado em linguagem simples;
- quantidade, qualidade, estabilidade, sessões/visitas e época são consideradas conforme o contrato existente do núcleo;
- a interface não inventa limites próprios.

#### Épocas
- após alteração confirmada do Mapa K, nova evidência GNV pertence à nova época;
- evidência GNV de época antiga não influencia sugestão da época atual;
- referência de gasolina continua preservada;
- histórico antigo permanece acessível como histórico, não como decisão atual.

#### Comparação
- comparação só ocorre quando gasolina e GNV são compatíveis na mesma região;
- ordem dos dados não altera a decisão;
- valores inválidos são ignorados de forma auditável;
- tendência global e erro residual local permanecem separados;
- nenhuma comparação inicia escrita.

#### Interface
- tocar uma célula abre detalhe visível sem trocar para uma área escondida;
- seleção e contexto são preservados ao voltar;
- horizontal 9" é a experiência principal;
- celular vertical continua funcional e concentrado;
- não criar shell, timer ou autoridade concorrente.

### Testes obrigatórios
1. célula vazia, apenas gasolina, apenas GNV e comparável;
2. preenchimento entre eixos conserva toda a evidência;
3. evidência insuficiente mostra motivo correto;
4. evidência antiga não contamina época atual;
5. gasolina permanece disponível após mudança de época;
6. ordem das comparações não muda decisões;
7. dados inválidos não geram prontidão ou sugestão;
8. erro global não vira correção residual local falsa;
9. anomalia local permanece local após remover tendência global;
10. abrir detalhe não escreve e não altera o Mapa K;
11. UI usa os eixos e valores produzidos pelo Kotlin;
12. estado e seleção sobrevivem à navegação e renderização;
13. gate rápido, testes Kotlin/JVM, lint e build Android;
14. APK e SHA-256 ligados ao commit final;
15. validação no celular e, quando envolver ECU, validação física separada.

### Riscos principais
- misturar histórico antigo com época atual;
- UI recalcular prontidão de forma diferente do Kotlin;
- cores sugerirem prontidão inexistente;
- confundir cobertura com recomendação de ajuste;
- perder referência de gasolina ao mudar época;
- introduzir segunda autoridade de seleção/renderização;
- alterar matemática enquanto se corrige apresentação.

### Rollback
- reverter apenas os commits do Bloco 2 na branch de trabalho;
- preservar integralmente o commit validado do Bloco 1 `411a7188f4b4858edb379d7bf7f037087c646709`;
- não migrar ou apagar dados sem plano específico de rollback.

### Evidência necessária para conclusão
- diff remoto restrito ao Bloco 2;
- testes focados e de consequências verdes;
- gate rápido verde;
- Kotlin/JVM, lint e assembleDebug verdes;
- artifact e SHA-256 do commit final;
- validação no celular;
- qualquer teste com USB/MP48/ECU declarado separadamente como validação física.

---

## Bloco 03 — contratos arquiteturais V8.2 (021–030)

Este bloco congela as fronteiras arquiteturais antes dos próximos trabalhos de lifecycle, integração e UI. Ele não altera protocolo MP48, matemática científica, writers, telas ou comportamento físico da ECU.

### Contratos

- **021 — autoridade serial MP48 única:** `Mp48SerialScheduler` permanece a porta contratual fora da engine; managers não recebem autoridade USB paralela.
- **022 — estado de aplicação:** novas superfícies devem consumir projeções do estado nativo existente; não podem criar uma segunda verdade operacional/científica na WebView.
- **023 — domínios de estado:** `AppStateDomain` separa explicitamente `SCIENTIFIC`, `OPERATIONAL` e `VISUAL`.
- **024 — eventos canônicos:** `ProductEvent` define eventos de telemetria, Learning, AutoCal, Predictor e calibração sem acoplar a camada visual aos produtores.
- **025 — freshness:** `Freshness` produz `AVAILABLE`, `STALE` ou `UNAVAILABLE` a partir do instante real de produção, nunca do instante em que a UI consultou o dado.
- **026 — indisponibilidade:** ausência e stale são estados explícitos; fallback silencioso para valor antigo não satisfaz este contrato.
- **027 — revisão científica:** `ScientificRevision` só acompanha mudança científica/semântica. Refresh visual não cria revisão. `AdvisorRevisionGate` já segue este princípio.
- **028 — intenção humana:** `HumanIntent.requireConfirmed()` torna confirmação explícita pré-condição do contrato para mutação de ECU. A `CalibrationWriteSafetyPolicy` continua sendo a política operacional de segurança complementar.
- **029 — erro em dois níveis:** `HumanFacingError` exige resumo humano e detalhe técnico, além de código estável.
- **030 — fronteira da UI:** `UiBoundaryContract` permite renderizar estado e emitir intenção; proíbe USB direto, parser MP48 direto, writer próprio e matemática científica na UI.

### Evidência já existente preservada

- `TelemetryStateStore` mantém estado nativo thread-safe, `sessionId`, validade e idade real de telemetria.
- `Mp48SerialScheduler` modela a autoridade serial e unidades transacionais.
- `AdvisorRevisionGate` avança revisão por token semântico, não por timer/frame.
- `CalibrationWriteSafetyPolicy` bloqueia escrita por estado operacional inseguro e declara confirmação humana obrigatória como etapa separada.

### Delta deste bloco

- `app/src/main/java/com/omegas/prohub/model/ArchitectureContracts.kt`
- `app/src/test/java/com/omegas/prohub/model/ArchitectureContractsTest.kt`
- este documento de rastreabilidade.

Os próximos blocos devem **usar** estes contratos; este bloco deliberadamente não migra consumidores nem redesenha UI.

---

## Bloco 04 — Startup, lifecycle e restauração (031–040)

Base remota investigada: `work/v8.2-clean` em `8eeb1ce25d3269ed12691aafd9a4b1804f1f092f`.

### Evidência de implementação existente preservada

- `DeferredLiveOnlyLearningStore` restaura Learning em executor dedicado, publica `LEARNING_RESTORING`, `LEARNING_READY` e `LEARNING_RESTORE_FAILED` e mantém telemetria independente durante restore/falha.
- Ajuste confirmado durante restore fica pendente e é aplicado antes de expor o Learning como READY, evitando reutilizar evidência GNV antiga.
- `MainActivity` inicia/binda o `TelemetryForegroundService`, carrega a WebView e não realiza leitura completa de histórico nem checkpoint pesado em `onCreate`, `onResume` ou `onDestroy`.
- `TelemetryForegroundService` mantém o runtime fora da Activity; destruição da Activity apenas desfaz bind/bridges/WebView, sem encerrar automaticamente o serviço.

### Delta deste bloco

`StartupLifecyclePolicy.kt` centraliza os contratos que faltavam para 031–040:

- budgets explícitos: cold start 1500 ms, warm start 750 ms, reopen 1000 ms, primeira telemetria válida 3000 ms;
- Learning fora do critical path;
- telemetria independente de RESTORING/READY/FAILED;
- nenhuma leitura integral de histórico na primeira tela;
- nenhum checkpoint pesado no primeiro frame, troca de tela ou `Activity.onDestroy`;
- custo de abertura proibido de crescer com a contagem histórica acumulada.

`StartupLifecyclePolicyTest.kt` cobre essas invariantes e inclui restore corrompido como cenário fail-open apenas para telemetria, sem promover Learning inválido.

### Estado de prova

Código e testes foram adicionados remotamente. GitHub Actions não foi executado. APK não foi gerado. Portanto os budgets temporais são contrato/teste preparado, mas medição física cold/warm/reopen em dispositivo continua pendente para a fase de validação física prevista no Programa Mestre.

---

## Bloco 05 — USB, MP48 e árbitro serial — micropassos 041–050

Branch operacional: `work/v8.2-clean`.

### Evidência de implementação

- **041** — `ResponseDrivenEcuEngine` permanece a autoridade única da sessão MP48; `Mp48SerialScheduler` é a fronteira entregue aos clientes.
- **042** — classes semânticas preservadas: `SAFETY`, `MANUAL_WRITE`, `READ_ONLY`.
- **043** — cada trabalho serial roda em executor único; `unit()` mantém sequências inseparáveis como write + readback sem preempção.
- **044 / 050** — leituras secundárias devolvem oportunidade à telemetria por `telemetryAfter`; o harness `test_mp48_serial_scheduler_behavior.py` cobre leitura/leitura e write+readback atômico.
- **045 / 046** — `connectionSessionId` é geração física. `true → true` com ID novo é `GENERATION_CHANGED` e o serviço invalida runtime, AutoCal e telemetria antes de iniciar Store/writers/AutoCal na geração nova. Falha de transporte agora é fail-closed: não é permitido reabrir fisicamente a porta preservando a geração lógica antiga.
- **047** — timeout/frame inválido/ACK divergente/sessão trocada continuam retornando falha; sessão física trocada é rejeitada por `expectedSessionId` antes e depois da transação.
- **048** — purge permanece restrito a fronteiras de sincronização controladas (abertura física/handshake/recuperação), não como rotina observacional por frame.
- **049** — a arquitetura já expõe `elapsedMs` por transação e métricas da engine, mas a ocupação serial real sob AutoCal + Mapa K + Curva K + telemetria exige medição física posterior; não é declarada como provada neste bloco.

### Delta deste bloco

Foi corrigida uma inconsistência concreta: a recuperação transitória podia fechar e reabrir a porta física mantendo `connected=true` e o mesmo `connectionSessionId`. Isso contrariava a regra de geração USB. A política passou a exigir hard disconnect; o auto-reconnect normal volta por `open()`, que cria um ID novo e aciona a invalidação integral já existente no serviço.

### Validação

- Provas estruturais existentes: `tests/test_mp48_serial_scheduler_contract.py`, `tests/test_mp48_serial_scheduler_behavior.py`, `UsbSessionTransitionPolicyTest.kt`.
- Regressão atualizada: `UsbPoliciesTest.kt` e `tests/test_usb_permission_identity_contract.py`.
- GitHub Actions não executadas.
- APK não gerado.
- Testes não executados nesta superfície; estado: `TEST_NOT_AVAILABLE` para execução, com evidência estática remota fresca.
- Medição física do micropasso 049 permanece para a fase de validação física prevista no Programa Mestre.

---

## Bloco 06 — Telemetria ao vivo e backpressure — micropassos 051–060

Base revalidada antes do bloco: `work/v8.2-clean` em `e38f5d59361b1c69d2273fd0dea691b7b3672642`.

### Evidência de implementação

- **051** — `LatestOnlyBackgroundPipeline` já mantém exatamente um quadro ativo e no máximo um pendente substituível. Não existe `ArrayDeque` nem executor com fila histórica na entrega visual.
- **052** — Learning usa `RealtimeLearningBuffer`, separado da entrega visual `LatestOnlyBackgroundPipeline`; descarte/coalescência visual não apaga a evidência científica.
- **053** — falhas e eventos críticos seguem `RingLog`/callbacks próprios; `TelemetryVisualLifecyclePolicy` congela explicitamente que eventos críticos não usam a fila visual descartável.
- **054** — `TelemetryStateStore` mantém uma única `AtomicLong sequence` monotônica e publica a sequência no snapshot entregue à UI.
- **055** — `TelemetryStateStore.liveJson()` publica `updatedAt` e `ageMs`, calculados nativamente; a tela não precisa inferir frescor.
- **056** — o pipeline visual substitui o pendente antigo pelo snapshot mais recente quando o consumidor está lento.
- **057** — `TelemetryVisualLifecyclePolicy.primaryTelemetryFields` congela RPM, Petrol Inj. e combustível como caminho primário; gráficos/Predictor ficam classificados como trabalho visual secundário. Resposta ao toque não depende de replay de backlog visual.
- **058** — `LatestOnlyBackgroundPipeline.metricsJson()` expõe contadores bounded (`pending`, `active`, `coalesced`, delays e tempos máximos) sem log por frame.
- **059** — política explícita: `HIDDEN` não renderiza telemetria visual, mas o Store continua latest-only; `SPLIT_SCREEN` mantém telemetria primária e suspende visual secundário; ao voltar não há replay histórico.
- **060** — `LatestOnlyBackgroundPipelineTest.slowConsumerKeepsOnlyNewestPendingState` bloqueia artificialmente o consumidor, injeta sequências 2..100 e prova que somente 1 e 100 são processadas, com backlog pendente máximo de 1.

### Limites deste bloco

Este bloco não reconstrói a UI legada. A nova UI, quando chegar à fase própria, deverá consumir estes contratos sem criar fila paralela em JavaScript. Nenhuma mudança automática de ECU foi adicionada.

### Validação

A evidência comportamental relevante já existe no teste Kotlin de saturação e foi complementada pelo teste da política de lifecycle. Neste bloco não foi executado GitHub Actions nem gerado APK. Qualquer medição física de frame-time/CPU em multimídia permanece para a fase de validação física prevista no Programa Mestre.
