# OMEGAS 8.2 — Fila operacional persistente (PR #195)

> Checkpoint OBS-COD de 2026-11-17. Esta fila **não** substitui as 19 Tasks nem as dependências C0→C5 do Programa Mestre. Números são estáveis; itens concluídos permanecem registrados. Não representa homologação física.

## Lote de verificação — cinco frentes

| Nº | Trabalho / aceite | Estado | Evidência e dependência |
|---|---|---|---|
| 1 | Confirmar CI do SHA anterior: seis jobs verdes, sem skips declarados como PASS | DONE | CI #1965, run 44090721778, SHA 79bf6441f89d9d21d857114a031e9cc6b72aadec; seis jobs success; não prova física |
| 2 | Auditar CTAs dinâmicos em `components/advanced.js`: registrar seletor, handler, efeito e testes | PARTIAL — 14 seletores auditados; runtime pendente | Handler do advanced.js e fluxos flag/vals/volta mapeados; escrita/efeito físico permanecem condicionados a prova da ECU. |
| 3 | Auditar CTAs dinâmicos em `components/drawers.js`: sessão, exportação, diagnóstico e configurações | PARTIAL — 19 seletores auditados; runtime pendente | Drawers vinculado a sessões, exportação, teste do app e bridge; clicar em browser/Android real ainda sem prova integral. |
| 4 | Expandir `matriz-funcional.json` a partir de ações efetivamente expostas, classificando origem, capacidade física, pedido/resposta, UX e teste | PARTIAL — 27→37 linhas e 8 fontes de botões | Matriz ampliada por origem e classes; registro adicional P1 cobre 6 fontes além de advanced/drawers; ainda sem total de capacidades físicas. |
| 5 | Criar testes RED/GREEN para cobertura de controles dinâmicos, regenerar grafo pelos geradores e validar CI do novo SHA | PARTIAL — contrato RED/GREEN enviado, resultado de CI pendente | Teste de rastreabilidade exige seis fontes, handlers, matriz e scanner de 36 JS; prova de clique/ECU ainda pendente. |

## Bloqueios vinculantes

- **BLOQUEIO_DE_DECISÃO:** divergência entre decisão histórica do dono (ACK e saída de K insertion podem exibir “Gravado”, com verificação posterior) e plano novo (somente readback). Não alterar `KWriteManager`, correção K ou semântica até autorização expressa. ACK não é prova de escrita física.
- **BLOCKED — hardware:** homologação real USB/ECU, nível do gás, writes existentes e comportamento no carro dependem de equipamento e autorização.
- **BLOCKED — design:** nenhuma reforma das quatro telas técnicas sem conjunto completo de mockups, mapa de fluxo e OK expresso.

## Próximo batch

Concluir 2 e 3 com mapeamento seletor→handler→bridge, executar 4, escrever teste que detecte omissões (5), regenerar artefatos e acompanhar CI do SHA. Manter a ordem contratual do Programa Mestre; não fazer merge, APK ou writes.

## Auditoria de controles dinâmicos — lote inline (OBS-COD)

Fonte: arquivos no HEAD `855b2a48baddeb87f3e52839056e4040025da916`. Classificação **preliminar de fluxo no JS**, não comprovação física nem fechamento da Task 3.

### Ferramentas / Motor — `components/advanced.js`

| Controle delegado | Handler / efeito observado no JS | Classe preliminar | Próxima prova |
|---|---|---|---|
| `data-adv-switch` | `flag.tapSwitch`, com `reason('flag')` e bloqueio durante gravação de valores | ECU_MANUAL_WRITE_EXISTING | Seguir `AntiStalloFlagWriter`, ACK/readback e teste |
| `data-adv-undo-flag` | `flag.tapUndo` | ECU_MANUAL_WRITE_EXISTING | Confirmar limites do Desfazer |
| `data-adv-undo-values` | `vals.ask('undo', ...)` após conferir disponibilidade | ECU_MANUAL_WRITE_EXISTING | Rastrear `AntiStalloParamsWriter` e falha parcial |
| `data-adv-factory` | `vals.ask('factory', changes, usb)` | ECU_MANUAL_WRITE_EXISTING | Verificar origem e limites dos valores de fábrica |
| `data-adv-step` | `stepField` (edição local antes de persistir) | APP_DRAFT | Provar que não grava sozinho |
| `data-adv-use-suggestion` | `vals.ask('edit', changes, usb)` | ECU_MANUAL_WRITE_EXISTING | Separar sugestão APP_ONLY da aplicação manual |
| `data-volta-step/toggle/undo` | `voltaTap` | MIXED_EXISTING | Abrir método e seguir cada chamada nativa; pressão tem prova física pendente |
| `data-adv-open`, `data-adv-open-view`, `data-adv-back`, `data-adv-back-valores`, `data-adv-open-info-tab`, `data-adv-info-tab` | Navegação interna e seleção de informação; abertura de valores pode solicitar leitura | APP_NAVIGATION_OR_ECU_READ | Diferenciar navegação de leitura sem escrita |

### Ferramentas / Sessões — `components/drawers.js`

| Controle delegado | Handler / efeito observado no JS | Classe preliminar | Próxima prova |
|---|---|---|---|
| `toolExportData` | `api.exportData()` | APP_EXPORT | Conferir seletor Android e recuperação |
| `toolAutoRefine` | `toggleAutoRefine()`, `OmegasAutoCal.setAutoRefineMode` OFF/SHADOW | APP_SETTING | Testar que SHADOW só sugere, nunca grava K |
| `data-session-start/stop` | `api.startSession/stopSession` | APP_SESSION | Verificar resultado parcial e retomada |
| `data-session-step` | Altera somente `input.value` | APP_DRAFT | Testar limites min/max |
| `data-session-settings` | `applySessionSettings()` | APP_SETTING | Conferir ponte e persistência |
| `data-export-summary/session` | `api.exportSessionSummary/exportSession` | APP_EXPORT | Verificar sessão ativa e falha do seletor |
| `data-tool-battery-request` | `api.requestBatteryOptimizationExemption?.()` | ANDROID_PERMISSION | Verificar retorno da permissão |
| `data-tool-overlay-request/enable/disable` | `api.requestOverlayPermissionAndEnable?.()` / `setTelemetryOverlayEnabled?.()` | ANDROID_PERMISSION_OR_SETTING | Verificar ausência de bridge e recuperação |
| `data-tool-export-logs` | `api.exportLogs()` | APP_EXPORT | Validar retorno e arquivo |
| `data-tool-selftest` | `api.selfTest()` com alerta `ok/warning` | APP_DIAGNOSTIC | Não confundir autoteste do app com ECU |
| `data-tool-open-sessions/log/carinfo`, `data-tool-back`, `data-session-page`, `data-log-page` | Navegação e paginação local | APP_NAVIGATION | Conferir foco, paginação e persistência |
| `data-carinfo-toggle` | `CarInfoView.toggle(api,...)` | ANDROID_OR_APP_SETTING | Rastrear origem e resultado |
| `data-log-level/category` | Filtra visualização do registro | APP_FILTER | Testar estado vazio e retorno |

**Lacuna:** estas linhas ainda não provam cobertura de todos os CTAs, dos controles das outras telas ou dos métodos expostos das bridges Kotlin. A matriz estruturada permanece `PARTIAL_SEED_NOT_COMPLETE`. Ações de navegação/edição não devem ser contadas como novos comandos da central.

**Segurança:** sem alterações no app, na semântica ACK/readback, no K insertion, no writer, no perfil físico, nas quatro telas protegidas ou no APK.

## Critério de prontidão para APK (AINDA NÃO SATISFEITO)

Não gerar APK nem merge enquanto: CI do HEAD não estiver verde; ações expostas não estiverem totalmente mapeadas/testadas; capturas atuais não forem revisadas em 9:16 e 16:9; bloqueios físicos e decisões críticas de escrita persistirem. Limpeza de legado será inventariada por arquivo, com testes e prova de não uso, sem apagar histórico de engenharia reversa ou branches fora do escopo autorizado.

## Fila adicional — IDs fixos, não renumerar

| Nº | Entrega verificável | Estado | Evidência / dependência |
|---|---|---|---|
| 6 | Inventariar todas as interfaces publicadas e arquivos JS com testes de deriva | DONE — inventário estrutural JS + 4 bridges | 36 arquivos JS com detector de deriva (inclui map-editor.js), 81 sítios dinâmicos; inventário das 4 bridges com 116 métodos. Não é certificação funcional. |
| 7 | Resolver exposição do reset geral `RESET_ALL` antes do APK | DONE — código/testes verificados | OBS-COD: botão removido; WebView recusa preparar/executar; modo original preservado internamente. Teste RED #1995 e aprovação integral #2009 no código. Não comprova efeito físico nem libera todas as outras funções. |
| 8 | Revisar 5 telas nas duas orientações com evidência real | PARTIAL | CI #1969 SHA `b2ccc9a`, capturas inspecionadas; falha: Ferramentas quebra em duas linhas nas 4 telas técnicas em 360×640 DPR3; hardware não validado |
| 9 | Catalogar lixo/legado sem destruir histórico | DONE — triagem estrutural | Git tree completo SHA `17b2a79`: 1692 arquivos, 0 backups temporários/bytecode/artefatos binários; 633 arquivos de pesquisa/evidência preservados; 26 branches existentes, nenhuma removida |
| 10 | Mapear cada CTA dinâmico restante para seu evento, leitura/escrita, teste e resultado | PARTIAL — 6 fontes adicionais rastreadas | 33 seletores/fábricas complementares ligados a handler; ainda restam inputs, controles estáticos e runtime. |
| 11 | Qualificar itens legados para remoção com prova de não uso + testes | PARTIAL — triagem sem remoção arriscada | Nenhum arquivo legado foi excluído; handler data-adv-use-suggestion sem botão no template é hipótese de órfão, não exclusão aprovada. |
| 12 | Homologar Android físico/USB/ECU com consentimento | BLOCKED — hardware/consentimento | Evidência de Chromium não valida toque, bridge WebView nem efeito da central |

## Contagem das 19 Tasks (não confundir com a fila acima)

Conservadora, com base apenas nos marcos CI e nas evidências rastreadas: **1 DONE** (Task 1), **2 PARTIAL** (Tasks 2 e 3), **3 BLOCKED** (Tasks 5, 12 e 19 — decisões ou campo), **13 NÃO HOMOLOGADAS/NÃO RECONCILIADAS** (demais Tasks). Assim, **18 das 19 ainda NÃO estão DONE**. O estágio de tarefas não reconciliadas não afirma que nenhum código exista; afirma apenas que o aceite integral não foi demonstrado.

**Gate de APK atual: BLOCKED**, ainda que o CI da branch esteja verde: reset geral exposto, divergência ACK/readback, 4 telas técnicas com restrição de redesign, auditoria de funções incompleta, evidência de hardware pendente. Não gerar APK, não integrar PR e não acionar ECU.

**Higiene do ledger (OBS-COD):** na revisão histórica do KWriteManager, saída segura em timeout e concorrência de `busy` já têm cobertura no `KWriteManagerSafetyTest.kt` com CI #1983 verde. Pendências antigas foram reconciliadas no `PENDENTE.md`; conflito ACK/readback e risco do KFactorManager continuam abertos. Nenhuma alteração no writer.

**Higiene de pendências:** a investigação de liberação concorrente em `KFactorManager` passou em `KFactorManagerBusyTest.kt` (CI #1985). O ledger deixa como bloqueada somente a decisão de status ACK/readback para Mapa K e Curva K; não tratar ACK como prova física.

## Checkpoint de CI e prévia web — 2026-11-17 (OBS-COD)

- PR #195 na branch autorizada, HEAD antes desta atualização: `0a45a30aedf87d89badc06b9d4f58955c66b0064`. CI **#1987** [run 44105850664](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44105850664) terminou **success**, seis jobs verdes: grafo, Python, Kotlin/JVM, Android lint, JavaScript/navegador, integração. Os passos de diagnóstico condicionais foram SKIPPED por ausência de falha, não foram contados como testes executados. Artefato de capturas [14339515720](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44105850664/artifacts/14339515720), SHA da captura = `0a45a30`; ainda **não houve inspeção visual manual nova** destas imagens neste lote, portanto o inventário visual anterior não muda para PASSOU.
- Rechecagem independente de `screens/autocal-cockpit.js`: linha 768 mantém `data-autocal-action="RESET_ALL"` no menu avançado, e a linha 864 vincula ações. A matriz estruturada também registra o bloqueio. **P0 continua BLOCKED** por autorização de modificação da tela protegida. Não remover nem ativar outra ação sem aprovação.
- Prévia de design publicada em AppDeploy: https://omegas-8-2-previa-ux-r5qbc2.v2.appdeploy.ai/ — **produto separado, dados ilustrativos**, não é deploy do código Android do PR, não comprova USB, bridge, protocolo ou aprovação de mockups. O gate de UI das quatro telas técnicas permanece BLOQUEADO até OK explícito do dono.
- A divergência histórica entre exibir “Gravado” após ACK/saída K insertion e exigir readback antes continua **BLOQUEIO_DE_DECISÃO**; não modificar KWriteManager nem correção K existente. Sem OBS-FIO novo.
- A contagem conservadora permanece **1 DONE / 2 PARTIAL / 3 BLOCKED / 13 NÃO RECONCILIADAS = 18 não DONE**. Nenhum status de Task foi promovido só porque o CI está verde.

### Extensão da fila, preservando IDs

| Nº | Entrega verificável | Estado | Dependência / critério de aceite |
|---|---|---|---|
| 13 | Inspecionar visualmente artefato CI #1987 nas 5 rotas, DPR3 retrato e 16:9, comparar com #1969 e atualizar inventário | PENDING | Depende do download/inspeção das PNG reais; PASSOU somente com geometria e captura por rota no SHA |
| 14 | Reconciliar a prévia AppDeploy com o contrato técnico de cada rota sem usá-la como prova de runtime Android | PENDING | Depende de revisão dos mockups e aprovação expressa do dono; não reformar telas protegidas |
| 15 | Expandir vínculo de cada CTA da matriz parcial a handler, ponte, classificação de capacidade e teste, sem inventar escrita | PARTIAL — nova cadeia JS→função/bridge classificada | P1 registra 10 linhas novas, eventos manuais existentes e bloqueios; não confundir handler com efeito físico. |

**Próximas tarefas disponíveis:** 2–6, 8, 10–11, 13, 15 (observação, inventário, testes e documentação). Os itens 7, 12 e 14 possuem gates explícitos; não interpretar BLOCKED como concluído. Próxima execução deve confirmar novo HEAD/CI antes de promover evidências.

## Reorientação estratégica de UX — pedido direto do dono, 2026-11-17

**OBS-RETORNO-DO-DONO:** o protótipo HTML vertical ficou carregado, difícil de rolar e afastado do produto; a organização moderna de Ferramentas foi bem avaliada. Em instrução posterior, o dono deixou explícito que **não há obrigação de preservar telas, nomes, quantidade de destinos, layout nem orientação**. O objetivo é que alguém leigo em GNV e/ou mecânico sem prática com celular consiga regular e diagnosticar pelo fluxo mais seguro, guiado, limpo e compreensível possível. Isto supera restrições antigas **de design e exploração**; não equivale a aprovação de solução específica nem cancela limites físicos ou de segurança. Nesta rodada não foi alterada a interface.

**Pesquisa MANUAL_EXTERNO — não OBS-FIO na MP48:** 
- Software OMEGAS 2.16.4 C, pp. 10, 18–21, 23, 38–49: https://landi.pl/assets/files/OMEGAS/MANUALE_SW_2164C_gb1.pdf
- Manual OMEGAS histórico, capítulo K / calibração: https://lpgtech.ua/assets/uploads/files/c94f3-Instruktsiya-po-programmnomu-obespecheniyu.pdf
- Manual de componentes GNV: https://manualzz.com/doc/en/26787903/landirenzo-cng-system-installation-handbook
- Fabricante OMEGAS 4.0 GNV (geração diferente, referência explicativa apenas): https://landi.it/us/omegas-multipoint-natural-gas-injection-system

### Primeiras conclusões com proveniência e limites

| Pergunta de uso | Evidência do manual (MANUAL_EXTERNO) | Repositório / lacuna | Efeito sobre a UX a explorar |
|---|---|---|---|
| Qual pressão? | MAP = pressão absoluta da admissão; diferencial do gás difere da absoluta na linha, calculada no contexto do manual por diferencial + MAP. Não é pressão do cilindro. | components/live-bar.js exibe MAP; EV-VOLTAGASOLINA-015 é DESCONHECIDO para efeito físico do write da pressão. | Rotular grandeza, origem, unidade e atualização; não chamar tudo de pressão. |
| Como se calcula a dosagem do gás? | Tempo de injeção gasolina, rotação, coeficientes K e compensações de pressão/temperatura influenciam injeção de gás. | docs/progbase/semantica.md e matriz-funcional mostram funções distintas. | Não deduzir correção de K de amostra isolada; explicar evidência e condições comparáveis. |
| O que é AutoCal? | O software histórico inclui calibração guiada inicial e refinamento posterior. | docs/progbase/semantica.md separa TFormCalibra (roteiro) e TAutoCalUI (zonas/pontos); screens/autocal-cockpit.js trata quatro zonas por combustível e AutoMatch nativo. | Não misturar procedimento guiado de outra geração com aquisição nativa nem inventar progresso em %. |
| Eixos do mapa? | K principal = rotações × tempo de injeção gasolina; célula contém fator K. | screens/autocal-original.js usa MAP × tempo gasolina no gráfico de aquisição; Mapa K e Curva K são distintos. | Nunca rotular o Mapa K como rotações × MAP. |
| Quais dados faltam? | Temperatura água/gás, injeção gás, pressão diferencial/absoluta, nível e OBD podem ser úteis, variando com sensores/perfil. | components/live-bar.js mostra tensão, MAP, injeção gasolina, RPM e nível RAW interpretado; tensão com escala provisória. | Mostrar dado extra só com prova de suporte e origem; indisponível = travessão, não zero ou dado fictício. |
| Quando orientar uma mudança? | Manual GNV recomenda verificar primeiro funcionamento a gasolina, falhas e condições mecânicas; pressão baixa pode indicar filtro/regulador, não necessariamente K. | PENDENTE.md descreve apagões e transições, mas sem velocidade/chave não determina toda a causa. | Criar jornada primeiro diagnosticar, depois sugerir, sem escritas automáticas nem testes manuais dirigindo. |

**Prova por função:** manual de outro perfil nunca autoriza portar writer, OBD, atuadores ou firmware. Para cada recurso classificar OBS-FIO / OBS-COD / DERIVADO / INFERIDO / DESCONHECIDO; unir original → perfil → comando/resposta → efeito real → testes. Matriz atual: PARTIAL_SEED_NOT_COMPLETE, 27 linhas, cobertura total não comprovada.

**Alternativas de produto, ainda sem escolha aprovada:**
A. Oficina existente refinada: eficiente para técnicos, potencialmente pouco guiada.
B. Assistente linear rígido: bom para introdução, mas esconderia instrumentos e confundiria eventos da ECU com etapas do app.
C. **Jornada orientada ao objetivo com bancada técnica contextual (hipótese preferida):** entrar por aprender, investigar apagão, avaliar mistura ou ajustar; mostrar só dados que mudam a decisão; abrir gráfico/Mapa/Curva completos sob demanda; preservar acesso técnico direto. Sem fixar quantidade de abas ou forçar paisagem/retrato antes de medir.

**Segurança não revogada:** RESET_ALL exposto bloqueia release; não alterar KWriteManager/KFactorManager, ACK/readback, autocorreção existente, escrita automática, USB nem operar ECU sem autorização; nenhum merge ou APK. A divergência histórica de rótulo após ACK é BLOQUEIO_DE_DECISÃO, não confundir ACK com readback físico. Esta pesquisa não valida Android físico.

### Fila operacional adicional, números antigos intactos

| Nº | ID estável | Estado e dependência | Aceite |
|---|---|---|---|
| 16 | OP-16-DICIONARIO-REAL | PARTIAL — dicionário OBS-COD inicial documentado | DICIONARIO-LEITURAS-MP48.md identifica MAP, pressão do gás indisponível, RPM, inj., tensão provisória, nível RAW e anti-apagão. |
| 17 | OP-17-JORNADA-GUIADA | PENDING; OP-16 + PENDENTE.md | Três walkthroughs (primeiro uso, motor apagando, ajuste K), com ação, consequência, falha/parcial e recuperação |
| 18 | OP-18-ARQUITETURA-DE-EXPERIENCIA | PENDING; OP-16/17 | Comparar as três alternativas, fluxos e retrato/paisagem com evidências, sem congelar navegação antiga |
| 19 | OP-19-MOCKUPS-E-VALIDACAO | BLOCKED por aprovação da arquitetura; OP-18 e CI | Conjunto coeso de telas e flow, testes de toque/rolagem/clip/legibilidade, aprovação antes de implementações de risco |

**Contagem das 19 Tasks mestre permanece conservadora: 1 DONE, 2 PARTIAL, 3 BLOCKED, 13 não reconciliadas; 18 não DONE.** Números da fila operacional são independentes das 19 Tasks. Este checkpoint é de pesquisa/decisão, sem código, ECU, APK ou merge.

## Prioridade atual do dono — motor e verdade primeiro, interface por último (2026-11-17)

**OBS-DONO:** a arquitetura de assistência guiada + bancada técnica foi aprovada **como direção de produto, não como redesign pronto**. Primeiro lapidar motor, protocolo, evidências, classificação do que é suportado e testes; depois lapidar UX. Valor principal: uma pessoa leiga deve conseguir regular GNV com fluidez e segurança, sem ambiguidade de informação. Trabalhar rapidamente por lotes verificáveis e comandáveis por prioridade. A última prioridade **de desenvolvimento visual** é a interface; preparar contratos de comportamento durante a auditoria não viola esta ordem.

### Como o dono comanda sem administrar arquivos

O **mesmo PR #195** e **esta fila numerada (1–19, imutável)** são a fonte de checkpoint. Os rótulos P0–P5 abaixo são **lotes operacionais**, NÃO renumeram as 19 Tasks do Programa Mestre e NÃO representam porcentagem de progresso. O dono pode dizer **"Execute o próximo lote"** (seleção automática do menor P viável), **"Execute P1"**, **"Execute 2 a 6"** ou **"Status"**. Cada lote verifica HEAD + CI, cumpre etapas autorizadas em RED→GREEN, registra evidência e CI, bloqueia apenas o que depender de decisão/hardware, e avança para outras etapas do mesmo lote. Ao final, relatar resultados, número da fila, 19 Tasks por estado, bloqueios e próxima ação, sem exigir revisão de arquivo pelo dono.

| Prioridade | Objetivo e subfila relacionada | Dependência e condição para avançar | Estado de planejamento |
|---|---|---|---|
| **P0 — Segurança e baseline** | Corrigir regressão real de CI; identificar/remover exposição de RESET_ALL em AutoCal sem criar qualquer comando; registrar conflito ACK/readback e não tocar na correção K. **OP-7; Tasks Mestre 1–3, 4 e 5 (decisão isolada)** | Verificar ramo autorizado/CI HEAD; antes de editar writer ou semântica bloqueada exigir autorização específica; testes de não exposição e recuperação, CI no SHA | PENDING / BLOQUEIO_DE_DECISÃO isolado |
| **P1 — Inventário sem ambiguidade** | Rastrear **todas** as ações, sensores, origens, escalas, capacidades por perfil, resposta observável, efeito esperado, origem do código/ProgBase, teste e ausência de prova. **OP-2,3,4,5,6,10,15,16; Tasks Mestre 3,4,6,8** | Começar de 27 linhas parciais; preservar e expandir a matriz canônica, nunca declarar 100% antes de fechar denominador de ações e pontes | PARTIAL |
| **P2 — Motor, protocolo e estados** | Auditar bridge, serial, engine, ACK/NAK, readback, limites, timeout, concorrência, auto recuperação, AutoCal nativo, Mapa/Curva, anti-apagão e pressão, usando original e replay. **Tasks Mestre 4,6,7,8,9,10,11; OP-5,10,15** | Por capacidade: observação → teste que falha → correção mínima → teste verde → CI; nenhuma escrita nova/automática, nenhum ECU real | PENDING, dependente P1 por recurso |
| **P3 — Qualidade da lógica** | Auditoria end-to-end de fluxos por sessão e motor, dados ausentes, desconexão, proposta sem gravação, replay determinístico e contratos de segurança; separar funções da central das análises APP_ONLY. **Tasks Mestre 10,11,15,16,17,18 em seus pré-requisitos não visuais** | CI e corpus de fixtures por caso, relatório de efeito comprovado ou DESCONHECIDO; não declarar readback físico | PENDING |
| **P4 — Interface orientada ao objetivo (ÚLTIMA implementação visual)** | User-Task Walkthrough de conectar/aprender, motor apagando e regular K; mockups completos, alternativa retrato/paisagem, navegabilidade real e componentes acessíveis. **OP-8,13,14,17,18,19; Tasks Mestre 12,13,14 e integração visual 15–18** | Só após base confiável; antes de refazer as telas técnicas, conjunto completo de mockups + fluxos + aprovação específica do dono. A pesquisa de UX pode ocorrer antes, mas implementação visual não concorre com motor | PENDING / gate de aprovação |
| **P5 — Homologação e pronto para APK** | CI atual, screenshot completo por SHA, QA de recuperação, Android WebView/USB em dispositivo físico com consentimento, verificação de perfil e resultados. **OP-12; Tasks Mestre 17–19** | Requer hardware e autorização; NÃO gerar APK, fazer merge ou acionar ECU sem consentimento | BLOCKED hardware/consentimento |

**Restrições não negociadas:** OBS-FIO / OBS-COD / DERIVADO / INFERIDO / DESCONHECIDO jamais misturadas; um manual de outra geração não prova o perfil instalado. ACK não é readback. `RESET_ALL` acessível continua P0, independentemente da antiga proteção visual. Divergência do status "Gravado" após ACK fica BLOQUEIO_DE_DECISÃO; não alterar KWriteManager, KFactorManager ou correção K sem autorização expressa. Não portar tudo do ProgBase. A arquitetura aprovada NÃO autoriza writer novo, comandos de firmware, reset geral, SetByAddress ou SetDuty.

### Gatilho dos próximos disparos

**Primeiro lote recomendado: P0, seguido de P1** sempre que executável; ao terminar subtarefas independentes, não parar pelo CI em andamento: prosseguir investigação e documentação sem risco. O gatilho "Execute o próximo lote" seleciona o menor P com trabalho desbloqueado no checkpoint vivo. Caso a versão de interface de coordenação em chat mostre botões, eles **apenas enviam a instrução para a próxima execução**; não executam comandos da ECU nem ação de GitHub sem o novo turno.

**Checkpoint conservador neste momento:** total mestre 19 = 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 NÃO RECONCILIADAS, faltam 18 NÃO DONE. O total não é estimativa de tempo nem progresso percentual. Antes de cada rodada consultar o novo HEAD e atualizar esta contagem somente por critério de aceite verificado. Nenhuma alteração técnica foi feita neste documento.


## Checkpoint de P0 — TESTE RED reproduzido, correção sob validação (2026-11-17)

- **RED/OBS-COD:** commit `5454fe2` adicionou `tests/ui/autocal-reset-all-webview-gate.test.cjs`; CI #1995 (`44131457656`) reproduziu **3 falhas esperadas**: botão `RESET_ALL` no HTML, `prepareNativeAction` aceitando o modo e `executeNativeAction` permitindo executar a preparação. Python e grafo também falharam por artefatos gerados não atualizados com novo teste, não por defeito de protocolo. Não confundir CI RED proposital com regressão não compreendida.
- **GREEN a conferir no SHA:** remoção cirúrgica do botão e sua explicação; negação explícita e fail-closed nos dois métodos da bridge; `AutoCalNativeActionManager.Action.RESET_ALL` **mantido como evidência do original**, não acionável via WebView; artefatos de grafo atualizados deterministicamente. Nenhum opcode, writer K, sessão serial ou AutoMatch modificado. OP-7 só vira DONE se JavaScript/Kotlin/Python/grafo/integração estiverem verdes no SHA final.
- **RELATO DO DONO (não é log OBS-FIO novo):** escritas e leitura de confirmação da versão de uso pessoal já funcionaram na central do proprietário; algumas funções de anti-apagão específicas **não** são suportadas na central dele. Não apagar provas existentes nem presumir que todas as demais capacidades tenham sido fisicamente homologadas. Registrar por função/perfil em P1; função relatada não suportada deve ser classificada como tal, sem inventar novo writer.
- **Uso pessoal:** a fase P5 preserva testes de segurança e recuperação para o próprio dono, não exige empacotamento comercial. Sem APK/merge/ECU real sem autorização.

### Fila acrescida sem renumerar anteriores

| Nº | ID estável | Dependência, estado | Aceite |
|---|---|---|---|
| 20 | OP-20-ANTIAPAGAO-PERFIL-DONO | PARTIAL — perfil do proprietário classificado preliminarmente | Relato de escrita/readback anterior funcional e extras anti-apagão não suportados sem enumeração por função; não inferir qual campo falha. |


### CI #1997 — contratos legados reconciliados (OBS-COD)

- No HEAD `5a179ce`, o teste P0 de ausência de `RESET_ALL` passou no escopo focado da fonte, mas os contratos históricos `tests/test_autocal_operational_toggle_contract.py`, `tests/ui/autocal-cockpit.test.cjs` e `tests/ui/autocal-reacquisition-ux.test.cjs` ainda exigiam indevidamente o botão visível. RED de regressão nos jobs Python e JS confirmou a divergência; os contratos foram atualizados para exigir a ausência, preservando as asserções das funções autorizadas.
- `tools/grafo_producao.py --check` passou no #1997. `tools/progbase/cobertura_re.py --check` apontou `docs/progbase/cobertura.json` desatualizado pelo novo teste AutoCal: `app.testes` 54 → 55; atualização determinística de valor derivado, sem alteração de evidência nem de porcentagem, `COBERTURA.md` inalterado.
- Aceite P0/OP-7 ainda condicionado a CI integral GREEN no SHA corrigido; não declarar DONE antes de verificar. O relato do dono sobre escritas/readback funcionais e funções de anti-apagão não suportadas em sua central continua marcado como relato de campo por capacidade a reconciliar em P1.


### P0: revisão de contrato histórico #1999 e acesso ao painel

- No CI #1999 (`44133637781`), Python `tests/test_autocal_reset_safety_gate.py` ainda exigia `RESET_ALL` como botão embora o comando permaneça no `AutoCalNativeActionManager` apenas para documentação/paridade. A condição foi ajustada: **UI não expõe; bridge nega; manager interno é preservado**. `tests/ui/autocal-consumer-graph.test.cjs` e `tests/ui/autocal-didactic-cockpit.test.cjs` passam a explicitar a mesma regra. Sem alteração do protocolo nem do writer.
- **Painel persistente de comandos P0–P5:** https://omegas-8-2-centro-de-comando-n1cqxk.v2.appdeploy.ai/ (AppDeploy, frontend-only). Botões apenas copiam prompts para o ChatGPT; **não** executam tarefas, não acompanham o GitHub automaticamente e não acessam USB/central. O checkpoint real continua no PR #195 e nesta fila.


### P0 — último teste legado de cópia identificado (OBS-COD)

- Varredura de textos localizou `tests/ui/textos-leigo.test.cjs` ainda exigindo o rótulo `Recomeçar tudo`, removido do HTML por risco de RESET_ALL. Teste atualizado para recusar exposição dessa ação, mantendo a checagem dos rótulos permitidos. Este ajuste é preventivo enquanto o CI #2001 processa as alterações anteriores; não altera telemetria, ECU ou K writers.


### P0 — correção da documentação de usuário e HTML gerado

- O CI #2005 apontou `docs/progbase/cobertura.html` desatualizado após adicionar um teste AutoCal (54→55); o arquivo autônomo foi sincronizado com a projeção de `cobertura.json`, sem alteração de evidência física.
- O guia de uso (`docs/manual/OMEGAS-guia-do-app.md` e espelho `.html`) deixou de apresentar `Recomeçar tudo` como botão do OMEGAS. O reset geral é mantido somente como referência histórica do original, não é recomendado para corrigir apagão e não está disponível na WebView. Não houve operação real na central.
- P0/OP-7 continua PARCIAL até CI integral GREEN no SHA corrigido. O relato do dono sobre comandos previamente funcionais e funções de anti-apagão indisponíveis será reconciliado por perfil, sem desmentir o uso de campo nem concluir capacidade universal.

## P0 — inventário reconciliado após retirada de RESET_ALL

- CI #2007 (44135501412) confirmou grafo, Kotlin, Python e Android lint verdes; JavaScript RED: tests/ui/inventario-superficies.test.cjs encontrou 12 botões AutoCal, contra 13 inventariados. Causa: remoção intencional de RESET_ALL.
- Matriz atualizada (OBS-COD): AutoCal 13→12, total de sítios JS 82→81. O bloqueio de exposição foi alterado para FIXED_IN_CODE_PENDING_CI e unsafeRelease=false apenas quanto ao botão geral. Matriz global permanece PARTIAL_SEED_NOT_COMPLETE, sem alegar prova física.
- Contrato do inventário revisado; OP-7 somente DONE quando CI integral do HEAD corrigido ficar verde. Preservado código nativo dos modos originais, sem escrita automática, firmware, merge nem APK.


## Checkpoint remoto reconciliado — CI #2009 (2026-11-17)

- **OBS-COD / CI:** PR #195, branch `feat/premium-adaptive-ui-v8-2`, HEAD verificado `1638fd82c4e90158a0f0a05a56e27a92387a47b0`; run [#2009](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44136883598) concluído com **6/6 jobs SUCCESS**: JavaScript e navegador, Android lint, grafo, Contratos Python, Kotlin/JVM e build_and_test. A verificação não prova USB, WebView Android físico ou efeito na central.
- **OP-7 — DONE no escopo de código e CI:** remoção da exposição de `RESET_ALL` na UI AutoCal, negação na bridge, contratos reconciliados e CI verde no SHA acima. O enum/manager histórico não foi apagado; não reabrir o mesmo P0 sem nova regressão comprovada. Não implica release liberado: outras capacidades e bloqueios permanecem.
- **P1 — próximo trabalho independente:** continuar OP-2, OP-3, OP-4, OP-5, OP-6, OP-10, OP-15, OP-16 e OP-20, com inventário por seletor→handler→bridge→efeito→teste, e separação de capacidades do perfil do dono; matriz com 27 fluxos continua **PARTIAL**, sem denominador total comprovado. Não inventar suporte físico das funções extras do anti-apagão.
- **Dívida documental:** `docs/qualidade/INVENTARIO-TELAS-9x16-16x9.md` ainda descreve `RESET_ALL` como exposto com base em SHA antigo. É registro histórico de capturas, não estado do HEAD; reconciliar na próxima atualização visual sem apagar a proveniência. Não declarar novas capturas do SHA atual: artefato #2009 `ferramental-ux-7a3c7296c8b7eb1b0180bdc593ab5f3723e2bd97` (ID 14353900355) existe, mas suas imagens ainda não foram inspecionadas.
- **Bloqueios preservados:** `BLOQUEIO_DE_DECISÃO` de "Gravado" após ACK versus readback e correção K; telas técnicas exigem mockups de conjunto + OK; hardware/USB/ECU exige autorização. Nenhum writer, ECU, APK ou merge alterado neste checkpoint.
- **Contagem conservadora do Programa Mestre (não recontada por aceite de todas as 19 Tasks):** 1 DONE, 2 PARTIAL, 3 BLOCKED, 13 NÃO RECONCILIADAS; 18 NÃO DONE. OP-7 é subtarefa operacional e não altera automaticamente o status da Task Mestre correspondente.


## APK pessoal de teste — entrega isolada (2026-11-17)

- **Solicitação expressa do dono:** geração de APK Android de teste para uso pessoal, sem merge/operar ECU. Compilação do commit `454c4075dbc00f0bf9bfb13523befcb91c5c6cdb` produziu `OMEGAS-uso-pessoal.apk` (5.217.191 bytes, SHA256 `75558d9eb12515e409a5cabaa7450e001df8eb0bfd594ae3cbf7852c8142d3a3`), verificado como arquivo ZIP/APK íntegro. Artefato GitHub #14358313381 (run 44150818470) é **candidato**, não release homologado.
- O workflow temporário foi removido imediatamente após a compilação para preservar os dois pipelines oficiais originais. Árvores do diretório `app/` do commit de build e do commit após limpeza `b5ec5868b02e024ba8d8b3a16916bc1127b3b4d3` foram verificadas idênticas. O workflow temporário provocou falha no contrato que exige exatamente 2 arquivos em `.github/workflows/` e foi retirado; não mudar aquele contrato só para criar outro pipeline.
- **Instalabilidade testada:** compilação Android debug e integridade do pacote, não instalação física. Por usar assinatura debug efêmera, não substituir APK existente sem backup nem presumir upgrade; USB/ECU/anti-apagão continuam dependentes de validação física autorizada. Não chamar o produto de 100% funcional sem prova.
- O Centro de Comando AppDeploy, acessível em https://omegas-8-2-centro-de-comando-n1cqxk.v2.appdeploy.ai/, agora consulta a fila GitHub atual ao abrir/a cada 5 min e apresenta apenas progresso leigo (Pronto, Fazendo, Falta) e link de APK de teste. Não incorpora credenciais ou opera a ECU; quando a consulta falha, informa atualização indisponível.


## Bloqueio absoluto de APK — instrução mais recente do dono (2026-11-17)

- **NÃO ENVIAR, GERAR NEM OFERECER APK de teste, candidato, debug, incompleto ou com lacunas.** O proprietário recusou versões intermediárias e solicita **apenas o APK final, completo e funcional**, sem nenhum requisito pendente. A entrega anterior de `OMEGAS-uso-pessoal.apk` foi prematura e NÃO deve ser repetida, promovida a release, exibida como pronta ou usada como atalho à homologação.
- **Portão de entrega:** todas as funções pretendidas para o perfil do proprietário precisam estar inventariadas e classificadas, testes automatizados integrais aprovados, bloqueios de decisão resolvidos ou explicitamente removidos do escopo aprovado, fluxo UX validado, nenhum risco P0/P1 em aberto e **homologação real Android/USB/ECU com consentimento quando necessária**. Enquanto qualquer condição faltar, estado geral = **NÃO PRONTO PARA ENTREGA**. Não confundir APK compilável com APK completo.
- Manter o programa de desenvolvimento nas tarefas independentes do PR #195; **sem relatórios/interrupções/notificações intermediárias ao usuário**, registrar progresso neste checkpoint. A automação foi atualizada para monitoramento condicional e só deve notificar em caso de conclusão integral comprovada, preservando a tarefa ativa.
- O Centro de Comando pode continuar mostrando progresso se o dono abrir voluntariamente, mas **não deve oferecer botão/link de APK** enquanto faltarem requisitos. Não habilitar entrega automaticamente apenas por um workflow de build verde.


## Batch P1 de dez itens — OP-2,3,4,5,6,10,11,15,16,20 (2026-11-17)

- **Prioridade:** dez tarefas executadas no sentido de auditoria/entrega verificável, NÃO dez funcionalidades homologadas. OP-6 pode concluir no escopo exclusivamente ESTRUTURAL (36 JS, 81 sítios e 116 métodos nas quatro bridges). As outras nove continuam parciais por testes de clique em runtime, denominador completo, decisão ou prova física; nenhuma é marcada DONE sem aceite.
- **OBS-COD:** oito fontes efetivas de criação de botões no conjunto atual. Novo registro `CONTROLES-P1.json` rastreia seis além de Ferramentas/Sessões (33 seletores/fábricas), com origem, handler, classe de escrita/leitura/local, linha na matriz, marca de não homologação física. A matriz passa 27→37 linhas, ainda `PARTIAL_SEED_NOT_COMPLETE`.
- **RED/GREEN:** detector do registro P1 introduzido antes do inventário; pós-correção verificar na CI integral (JavaScript, Python, Kotlin, lint, grafo). Nenhum writer, protocolo, tela ou ECU real foi alterado. Se CI falhar por arquivos gerados, corrigir pelo gerador e registrar a causa, nunca dispensar teste.
- **Dicionário OP-16:** separa MAP de pressão diferencial/absoluta do gás, inj. gasolina de gás, nível RAW e tensão provisória, com `—` quando desconhecido. **OP-20:** uso pessoal relatado, subset exato de anti-apagão não suportado continua DESCONHECIDO, sem expor função nova.
- **OP-11:** limpeza conservadora: handler `[data-adv-use-suggestion]` presente sem template de botão local, marcado INVESTIGAR; não apagar oráculo ou remover código sem prova de ausência de consumidores.
- **Próximo gate:** observação de CI GREEN desta revisão, testes reais de eventos/ponte, classificação por perfil e confirmação Android/USB/ECU quando autorizada. NÃO GERAR/ENVIAR APK enquanto houver qualquer lacuna.

### P1 — correção dos contratos existentes na rodada RED/GREEN

- CI #2027 apontou falha legítima do contrato `adaptive-premium-layout.test.cjs`: toda nova linha ECU_MANUAL_WRITE exige referência ao catálogo original. As cinco novas linhas de intenção manual foram vinculadas a entradas existentes (AutoCal preferências com observação/oráculo; AutoCal geral com incerteza explícita; Refino/Curva com aceite físico desconhecido). **Essas referências são contextuais e NÃO provam suporte do comando específico**, como descrito no campo `gap`.
- `tools/grafo_producao.py --check` encontrou apenas o arquivo gerado desatualizado pelo teste expandido; grafo foi recomputado por sua mesma regra determinística de citações (`cita_arquivo`) e os contadores 686→692 ligações de teste; nenhum elo essencial novo foi permitido. O CI integral desta revisão continua sendo o gate.

### P1 — cobertura gerada reconciliada

- Após expansão do teste de rastreio, o verificador de cobertura encontrou somente contagens `app.testes` atrasadas por área. Atualizadas no JSON e HTML embutido, com os mesmos números da lista de teste do grafo de produção (AutoCal 56, Mapa 27, Curva 19, Ajuste GNV 17). A cobertura de engenharia reversa, percentuais, origem e suporte físico não foram alterados; não equivaler contagem de ligações textuais com execução testada.

### P1 — semântica anti-apagão DFM × manual × código (2026-11-17)

- Fonte: `docs/qualidade/SEMANTICA-ANTIAPAGAO-PESQUISA-2026-11-17.md`. Cruzados cinco campos reais do DFM, `ANTI_STALLO` 0x0133, original emulado (valor 1601 permitido no índice 0), passos do JS 50/5/100/5/5 e manual OMVL externo como analogia conceitual, **não paridade de firmware**.
- Problema de semântica confirmado em OBS-COD: índices 1,3,4 têm unidade original **rpm/ciclo**; o catálogo JS registra **rpm**; índice 3 tem referência 2 e incremento JS 5. O original não registra incrementos automáticos 5/50; precisão de 1 não define o passo correto. Atualmente o HEAD expõe só índice 0 nos cartões; extras continuam ocultos.
- **OP-16 e OP-20 continuam PARTIAL, prioridade P1**: resolver semântica, suporte por perfil e incrementos com teste original/bridge, antes de reexpor controles. P2 dependente de comparar delta por ciclo e estados de firmware; sem writer novo, sem mudança de código nesta pesquisa. P0 de exposição de RESET_ALL permanece DONE; conflito ACK/readback continua BLOQUEIO_DE_DECISÃO.
- **19 Tasks Mestre não promovidas automaticamente**: 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas (18 não DONE). Entrega APK final permanece bloqueada até a conclusão e homologação real. CI desta mudança documental ainda deve ser verificado no HEAD.

### Pesquisa P1/P2: GetValue, Enter e 48 xx por emulacao (2026-11-17)

- Novo teste deterministico em Python: 11 cenarios de fronteira, 7 quadros identicos aos ja gerados pelo Unicorn historico, Enter com mesmo valor, truncamento de quadros invalidos e delta s16 sintetico. Pesquisa documentada em docs/progbase/protocolo/antiapagao-fronteiras-getvalue-delta48-p1p2.md.
- Real OBS-FIO: 1189 respostas validas de 48 01 em 3 capturas do Drive, todas com offset +0x1E = 0, inclusive RPM bruto 1478/1017. Sem paridade provada entre o objeto da tela e subtipo 48 01, nao afirmar zero fisico ou escala de ciclo.
- Arredondamento fracionario e candidato da nota RE, nao teste recente da CPU original. OP-16 e OP-20 seguem PARTIAL. Apenas ferramentas, testes e documentacao; motores/writers/ECU/APK intocados. Verificar CI do SHA resultante.

### CI #2037 — atualização determinística do inventário de testes

- O teste novo foi executado pela CI Python, mas a verificação do grafo detectou contador gerado obsoleto: 421→422 arquivos de teste. Foi inserido o nó novo do teste no JSON gerado e sincronizado o resumo Markdown (mesmo número de ligações: 692); sem adicionar falsas provas de comunicação ou efeito físico.
- RED do grafo/contratos Python confirmado em #2037; o GREEN integral depende de novo CI. Nenhum arquivo de produção, writer, ECU, branch ou APK alterado.
