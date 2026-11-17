# OMEGAS 8.2 — Fila operacional persistente (PR #195)

> Checkpoint OBS-COD de 2026-11-17. Esta fila **não** substitui as 19 Tasks nem as dependências C0→C5 do Programa Mestre. Números são estáveis; itens concluídos permanecem registrados. Não representa homologação física.

## Lote de verificação — cinco frentes

| Nº | Trabalho / aceite | Estado | Evidência e dependência |
|---|---|---|---|
| 1 | Confirmar CI do SHA anterior: seis jobs verdes, sem skips declarados como PASS | DONE | CI #1965, run 44090721778, SHA 79bf6441f89d9d21d857114a031e9cc6b72aadec; seis jobs success; não prova física |
| 2 | Auditar CTAs dinâmicos em `components/advanced.js`: registrar seletor, handler, efeito e testes | PARTIAL | OBS-COD: 17 templates/14 seletores com vínculo à matriz; falta contrato bridge/Kotlin e clique executável |
| 3 | Auditar CTAs dinâmicos em `components/drawers.js`: sessão, exportação, diagnóstico e configurações | PARTIAL | OBS-COD: 25 templates/19 seletores com vínculo à matriz; handler global identificado em `app.js`; falta prova de runtime |
| 4 | Expandir `matriz-funcional.json` a partir de ações efetivamente expostas, classificando origem, capacidade física, pedido/resposta, UX e teste | PARTIAL | 12→27 linhas, + rastreio 33 seletores em 2 componentes; ainda faltam demais JS, relação exata bridge e prova física |
| 5 | Criar testes RED/GREEN para cobertura de controles dinâmicos, regenerar grafo pelos geradores e validar CI do novo SHA | PARTIAL | CI #1971 RED por regex do teste; correção CI #1973 GREEN. CI #1981 verde após ampliar detecção e corrigir grafo; falta evidência de clique funcional para todos |

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
| 6 | Inventariar todas as interfaces publicadas e arquivos JS com testes de deriva | PARTIAL | 116 métodos nas 4 pontes, 82 sítios de botões nos 35 arquivos JS; não é cobertura funcional |
| 7 | Resolver exposição do reset geral `RESET_ALL` antes do APK | PARTIAL — correção P0 enviada, CI GREEN pendente | OBS-COD: o botão foi retirado e a bridge da WebView recusa prepare/execute; o manager interno/oráculo continua intacto; teste RED #1995 observado (3 falhas focadas); CI do SHA corrigido define o aceite |
| 8 | Revisar 5 telas nas duas orientações com evidência real | PARTIAL | CI #1969 SHA `b2ccc9a`, capturas inspecionadas; falha: Ferramentas quebra em duas linhas nas 4 telas técnicas em 360×640 DPR3; hardware não validado |
| 9 | Catalogar lixo/legado sem destruir histórico | DONE — triagem estrutural | Git tree completo SHA `17b2a79`: 1692 arquivos, 0 backups temporários/bytecode/artefatos binários; 633 arquivos de pesquisa/evidência preservados; 26 branches existentes, nenhuma removida |
| 10 | Mapear cada CTA dinâmico restante para seu evento, leitura/escrita, teste e resultado | PENDING | Depende de 6; manter Mapa/Curva/Refino/AutoCal sem reformas |
| 11 | Qualificar itens legados para remoção com prova de não uso + testes | PENDING | Nenhum candidato aprovado para exclusão; não apagar fontes ou branches fora da autorização |
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
| 15 | Expandir vínculo de cada CTA da matriz parcial a handler, ponte, classificação de capacidade e teste, sem inventar escrita | PENDING | Depende de 2–6 e 10; aceite = cada ação exposta rastreada ou marcada DESCONHECIDO com bloqueio |

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
| 16 | OP-16-DICIONARIO-REAL | PARTIAL; manuais + catálogo reverso + 6/10/15 | Por leitura: nome, unidade, sensor, escala, origem, confiabilidade e permissão por perfil; desconhecido explicitado |
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
| 20 | OP-20-ANTIAPAGAO-PERFIL-DONO | PENDING · P1, matriz de capacidades e relato de campo; não operar ECU | Separar funções que o dono confirma funcionarem, as que ele relata não funcionarem e as desconhecidas, registrar origem e limitar exposição conforme perfil comprovado; testes de fail-closed e regressão, sem novos comandos |
