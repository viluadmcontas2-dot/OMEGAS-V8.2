# OMEGAS 8.2 — Fila operacional persistente (PR #195)

> Checkpoint OBS-COD de 2026-11-17. Esta fila **não** substitui as 19 Tasks nem as dependências C0→C5 do Programa Mestre. Números são estáveis; itens concluídos permanecem registrados. Não representa homologação física.

## Lote de verificação — cinco frentes

| Nº | Trabalho / aceite | Estado | Evidência e dependência |
|---|---|---|---|
| 1 | Confirmar CI do SHA anterior: seis jobs verdes, sem skips declarados como PASS | DONE | CI #1965, run 44090721778, SHA 79bf6441f89d9d21d857114a031e9cc6b72aadec; seis jobs success; não prova física |
| 2 | Auditar CTAs dinâmicos em `components/advanced.js`: registrar seletor, handler, efeito e testes | PARTIAL — navegação sem cabo testada no Chromium; ECU física pendente | CI #2053 GREEN no SHA 6a8fa2b para abrir valores/guia/voltar sem writer; perfil e ACK/readback não homologados |
| 3 | Auditar CTAs dinâmicos em `components/drawers.js`: sessão, exportação, diagnóstico e configurações | PARTIAL — erro de sessão e recuperação em Chromium verde | CI #2053 confirma retorno de ajustes e recibo sem sucesso fictício; Android e exports ainda pendentes |
| 4 | Expandir `matriz-funcional.json` a partir de ações efetivamente expostas, classificando origem, capacidade física, pedido/resposta, UX e teste | PARTIAL — matriz 37→44 APP_ONLY discriminados | 7 handlers de Ferramentas mapeados sem criar comandos; prova física por função continua não verificada |
| 5 | Criar testes RED/GREEN para cobertura de controles dinâmicos, regenerar grafo pelos geradores e validar CI do novo SHA | PARTIAL — contrato de 7 ações enviado ao CI novo | Teste de deriva vincula seletor, handler, classe APP_ONLY e matriz; GREEN do novo SHA ainda a verificar |

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
| 8 | Revisar 5 telas nas duas orientações com evidência real | PARTIAL — 50 capturas CI #2053 auditadas | 5 rotas / 10 cenários; falha nav Ferramentas DPR3 em quatro telas técnicas; Android NE |
| 9 | Catalogar lixo/legado sem destruir histórico | DONE — triagem estrutural | Git tree completo SHA `17b2a79`: 1692 arquivos, 0 backups temporários/bytecode/artefatos binários; 633 arquivos de pesquisa/evidência preservados; 26 branches existentes, nenhuma removida |
| 10 | Mapear cada CTA dinâmico restante para seu evento, leitura/escrita, teste e resultado | PARTIAL — sete controles adicionais classificados | Stepper/paginação/filtro/Car Info/permissões; runtime Android e campos estáticos restantes pendentes |
| 11 | Qualificar itens legados para remoção com prova de não uso + testes | PARTIAL — candidatos legacy KEEP/BLOCKED | Handler sem template e 10 CSS TODO não removidos sem prova; 0 exclusões autorizadas |
| 12 | Homologar Android físico/USB/ECU com consentimento | BLOCKED — hardware/consentimento | Evidência de Chromium não valida toque, bridge WebView nem efeito da central |

## Contagem das 19 Tasks (não confundir com a fila acima)

Conservadora, com base apenas nos marcos CI e nas evidências rastreadas: **1 DONE** (Task 1), **2 PARTIAL** (Tasks 2 e 3), **3 BLOCKED** (Tasks 5, 12 e 19 — decisões ou campo), **13 NÃO HOMOLOGADAS/NÃO RECONCILIADAS** (demais Tasks). Assim, **18 das 19 ainda NÃO estão DONE**. O estágio de tarefas não reconciliadas não afirma que nenhum código exista; afirma apenas que o aceite integral não foi demonstrado.

**Gate de APK atual: BLOCKED**, ainda que o CI da branch esteja verde: exposição pública de RESET_ALL já corrigida (OP-7 DONE, CI #2009; não reabrir); permanecem divergência ACK/readback, aprovação de alterações nas 4 telas técnicas, auditoria de funções incompleta e evidência de hardware pendente. Não gerar APK, não integrar PR e não acionar ECU.

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
| 13 | Inspecionar visualmente artefato CI #1987 nas 5 rotas, DPR3 retrato e 16:9, comparar com #1969 e atualizar inventário | PARTIAL — capturas do CI #2053 inspecionadas | Artefato 14468157741, SHA 6a8fa2b, 50 PNG; retrato DPR3 falha nas quatro telas protegidas |
| 14 | Reconciliar a prévia AppDeploy com o contrato técnico de cada rota sem usá-la como prova de runtime Android | PENDING | Depende de revisão dos mockups e aprovação expressa do dono; não reformar telas protegidas |
| 15 | Expandir vínculo de cada CTA da matriz parcial a handler, ponte, classificação de capacidade e teste, sem inventar escrita | PARTIAL — sete vínculos APP_ONLY extras | Matriz 44 entradas e teste estático; faltam totalidade de entradas/efeitos e prova física |

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
| 16 | OP-16-DICIONARIO-REAL | PARTIAL — dicionário ampliado por origem | Car Info Android, gravação local vs serial, MAP/pressão/ACK e limites documentados, sem novos sensores |
| 17 | OP-17-JORNADA-GUIADA | PARTIAL — três walkthroughs documentados | JORNADAS-OMEGAS82-PROPOSTA.md; prova de usabilidade humana e hardware NE |
| 18 | OP-18-ARQUITETURA-DE-EXPERIENCIA | PARTIAL — três opções de arquitetura e mapa | ARQUITETURA-EXPERIENCIA-OMEGAS82-PROPOSTA.md; mockups e OK continuam BLOCKED |
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


### Verificação de continuidade — 2026-11-18 (OBS-COD)

- HEAD remoto confirmado antes da atualização: `4689b2dbf404faba9a2d9edbd17b3be8526a425e`, exclusivamente branch `feat/premium-adaptive-ui-v8-2`; CI [#2041](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44200614149) `completed/success` no SHA. A última alteração corrigiu a importação Python do runner P1/P2 em execução direta; nenhuma prova física nova.
- OP-7 permanece DONE somente para bloqueio do RESET_ALL na WebView; OP-16/20 permanecem PARTIAL (GetValue/Delta 48 e semântica anti-apagão não demonstram efeito físico). OP-13 permanece PARTIAL: capturas do HEAD não inspecionadas; não promover telas a PASSOU. Contagem mestre conservadora inalterada: 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas, 18 não DONE.
- Próximas tarefas disponíveis (IDs estáveis): 2–6, 8, 10–11, 13, 15–16, 20. Critérios: seletor→handler→bridge→teste/resultado; inspeção PNG por rota/viewport; proveniência OBS-FIO vs emulação; nenhum writer novo. Dependências bloqueadas: decisão ACK/readback, mockups/aprovação de quatro telas e homologação Android/USB/ECU.
- Gate final **NÃO PRONTO**. Nenhum APK, merge, writer, operação ECU ou liberação. Este checkpoint é registro de verificação remota, não execução de testes físicos ou revisão visual.


### Batch solicitado — 12 próximas subtarefas (2026-11-18, verificação inicial)

- **OBS-COD / CI:** HEAD `92bb194777d8bebb435cc5a7ee5d0de090f39928`, CI #2043, run `44359439128`, `completed/success`; seis jobs success: JavaScript/navegador, Kotlin/JVM, Python, Android lint, grafo e integração. Passos de diagnóstico condicionais skipped não são execução de testes. Artefato de UX ID `14451814839` foi publicado, **não inspecionado visualmente** nesta execução.
- **Escopo dos 12 itens viáveis da fila sem renumerar:** OP-2, OP-3, OP-4, OP-5, OP-6, OP-8, OP-10, OP-11, OP-13, OP-15, OP-16 e OP-20. Neste disparo foi verificado somente o gate CI; **não marcar os 12 como executados ou DONE**. OP-6 permanece DONE estrutural anterior, os demais PARTIAL/PENDING conforme evidência anterior. O gate de screenshots OP-8/13 continua NÃO VERIFICADO para o HEAD; artefato publicado não prova inspeção.
- **Critério de continuação:** completar rastreio controle→handler→bridge→resposta/teste; revisar PNG por rota e viewport com prova; documentar semântica original sem extrapolar OBS-FIO; CI integral do SHA alterado. Intuitive Software Design modo IMPROVE: separar UI/fluxo/confiança e estados de recuperação; nenhuma mudança visual protegida sem aprovação.
- **19 Tasks mestre:** 1 DONE, 2 PARTIAL, 3 BLOCKED, 13 NÃO RECONCILIADAS; **18 não DONE**. BLOQUEIO_DE_DECISÃO ACK/readback, gate visual e homologação Android/ECU permanecem. Nenhum APK, merge, operação da central ou writer novo.


### Execução orientada a duas tarefas — OP-2 e OP-3 (2026-11-18)

- **Escopo autorizado e HEAD de base:** `4dd8a6d46d8b2b7e2c98e1c13a2a36a55915804b`, PR #195, apenas `feat/premium-adaptive-ui-v8-2`. Inspeção OBS-COD em `components/advanced.js`, `components/drawers.js`, `core/advanced-flags.js`, `core/anti-apagao-valores.js`, `core/native-api.js` e suíte `ux-fluxo-toque.test.cjs`.
- **OP-2 — PARTIAL / teste de runtime enviado:** a cadeia de abertura `[data-adv-open=valores]` → `AdvancedPanel.onClick` → `loadData`/consulta condicional → `setView`; guia `[data-adv-open-view=info]`/volta não exige gravação. Novo E2E cobre navegação/desconexão e garante ausência de quadros mutáveis. O writer existente do interruptor segue `AdvancedFlags.createWriteFlow` → bridge `applyAntiStalloFlag`, com testes anteriores de ACK/NAK e uma única gravação no simulador; isto não prova efeito físico.
- **OP-3 — PARTIAL / correção mínima e testes enviados:** `[data-session-start]` / `[data-session-settings]` → `Drawers.handleToolClick` → `NativeApi` → bridge local de gravações. Causa OBS-COD: a condição `result?.ok === false` tratava resposta `undefined` como sucesso, e `refreshSessionStatus` substituía status salvo por resposta parcial `{ok:true, recording:true}`. Corrigido para exigir `ok === true`, exibir aviso de confirmação incerta e mesclar dados da sessão sem perder configurações/contadores; não altera protocolo/ECU.
- **RED/GREEN planejado:** testes Playwright no CI verificam erro de serviço sem falsa conclusão, manutenção da escolha no caso parcial, estado local e zero writes à ECU; um mutante intencional restaura a condição antiga e demonstra que os asserts de resultado a rejeitariam. Não alegar GREEN antes do CI do SHA do lote.
- **Intuitive Software Design IMPROVE:** UI = confirmação explícita ao lado dos ajustes (sem cor de sucesso não sustentada); Flow = abrir, entender, alterar e retornar preservando o contexto; Feel/confiança = operação solicitada ≠ operação confirmada. Estágios Orientar→Prever→Agir→Confirmar→Continuar; falha conserva as escolhas anteriores. Teste em Chromium não prova descoberta humana nem dispositivo Android.
- **Status das 19 Tasks Mestre conservador:** 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 NÃO RECONCILIADAS (18 não DONE). OP-2 e OP-3 permanecem PARTIAL até cobertura completa e hardware; captura de UI do novo SHA não revisada. Decisão ACK/readback inalterada; escritores, quatro telas técnicas, USB, firmware e writers intocados. Sem APK, merge ou operação de ECU.


### CI RED #2047 e correção de regressão enviada (2026-11-18)

- **RED observado, SHA 252bedf3:** OP-2 (navegação sem USB) passou no Chromium; teste OP-3 falhou ao tentar conferir `maxSessionMb` porque o agendador restaurou o status sintético da bancada antes da asserção. A falha é de isolamento do fixture, não prova de perda persistente de ajustes do app. O mutante reproduziu o falso êxito da condição antiga. Corrigido o fixture para manter o retorno do serviço estável e retirado o ciclo que apagava o dado intencional de teste.
- **Fricção adicional real identificada em OP-3 (OBS-COD):** no erro de salvar ajustes, o texto era registrado mas não exibido imediatamente no formulário sem novo ciclo; foi adicionada confirmação local inline sem reconstruir inputs/seleções. Pendente CI GREEN no SHA resultante.
- **Grafo gerado:** nova citação textual `components/drawers.js` no teste `ux-fluxo-toque.test.cjs` exige exatamente mais uma aresta `cobre` (692→693), e Sessões tem 13→14 associações textuais de testes. O contador não prova runtime nem física. Atualizados `producao.json` e `PRODUCAO.md` seguindo ordenação/formatação determinísticas de `tools/grafo_producao.py`; CI #2047 também havia falhado pela diferença gerada.
- OP-2/OP-3 permanecem **PARTIAL**; a execução é somente no emulador de navegador. Sem alteração nas quatro telas protegidas, ACK/readback, writers ECU, serial, reset ou APK. Mestre: 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas.


### RED do contrato Python reconciliado — CI #2049 (2026-11-18)

- CI #2049 do SHA `6b33d1d05c0e021099075178a7274700be3642e7`: **grafo, Kotlin/JVM e lint Android PASSARAM**; contratos Python falharam em `tests/test_tools_session_retention_contract.py` porque o teste procurava a substituição antiga e destrutiva de `sessionStatus`. A nova lógica preserva configurações e contadores ao receber resposta parcial. O teste estático foi atualizado para exigir mesclagem e sucesso explícito e rejeitar regressão para substituição antiga.
- **OP-2 e OP-3 ainda PARTIAL**: o alcance conhecido é o código, a simulação Chromium e a cadeia app→bridge; nada comprova a sessão no Android físico ou efeito sobre a central. O CI final do SHA após este commit deve ser conferido; skips de diagnóstico não contam. Sem reformar telas técnicas ou criar writer.


### Recuperação do CI #2051 — OP-3 (18/11/2026)
- **OBS-COD / RED real** no SHA `13818dd47ad03d225ff9685081f0fbf60148092b`, CI #2051, Chromium em `tests/ui/ux-fluxo-toque.test.cjs`: `[data-session-start]` teve o toque interceptado por `details.diagnostic-settings`. A etapa de ajustes ainda estava **aberta** após falha de aplicação, preservando os inputs. A sobreposição é o fluxo de edição intencional; o teste tentava acionar botão fora desse contexto. Não forçar `.click()` JS nem retirar proteção de detalhes.
- **Correção mínima do teste**: exigir estado aberto após falha, usar o controle `summary` visível para retornar, verificar `details.open=false`, só então tocar no controle de sessão e validar o recibo e a preservação. User-Task Walkthrough: entrar em Sessões → abrir ajustes → falha com feedback inline → voltar pela própria seção → iniciar registro → confirmar; UI, fluxo e confiança independentes.
- **CSS TODO separado:** `leveza-orcamento.test.cjs` tem teste TODO de 10 seletores inativos, reportado como `not ok # TODO`. Não mascarar como teste aprovado nem remover CSS sem auditoria por escopo e teste; a falha principal de JavaScript no CI #2051 foi OP-3.
- **Status:** OP-3 segue PARTIAL, correção enviada para CI de SHA novo, sem alegar GREEN antecipado. ACK/readback, writers K/ECU, botões protegidos, APK, USB e central continuam sem alteração.


### Lote operacional de 12 itens — checkpoint OBS-COD (2026-11-18)

- **Itens acionados com IDs estáveis:** OP-2, OP-3, OP-4, OP-5, OP-8, OP-10, OP-11, OP-13, OP-15, OP-16, OP-17, OP-18. Não confundir 12 subtarefas da fila com as 19 Tasks mestre; nenhum item PARTIAL virou DONE sem o aceite completo.
- **CI anterior do SHA exato** `6a8fa2ba99cd5cb757f0adac362aef924cced6c4`: #2053 [run 44391621134](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44391621134), 6/6 jobs SUCCESS (JavaScript/Chromium, Python, Kotlin/JVM, grafo, Android lint e integração). O teste OP-3 ficou GREEN após fechar explicitamente a subárea de ajustes. Não confundir teste CSS `# TODO` com aprovação dessa limpeza.
- **Matriz/traceabilidade:** 37→44 linhas APP_ONLY discriminadas, não novo comando; mudanças testadas estaticamente no novo SHA aguardam CI. Grafo gerado sincronizado de 693→694 arestas `cobre`; Sessões 14→15 e Ferramentas 32→33 testes por heurística de citação textual. Nenhum writer modificado.
- **Visual:** 50 PNG do CI #2053 descarregadas/inspecionadas, incluindo 360×640 CSS DPR3/PNG 1080×1920 físicos e 1080×1920 CSS separado. QUEBRA “Ferramentas” no retrato DPR3 das quatro telas técnicas, sem autorização para modificar; Ferramentas funciona visualmente na rota ativa e é compacta em 800×480/1024×600. Não houve Android físico.
- **UX (IMPROVE):** separar UI (rótulo quebra), Flow (retorno ao resumo antes de iniciar Sessões) e confiança (resultado sem `ok===true` não é sucesso). Três jornadas e opções A/B/C documentadas como propostas, sem pesquisa humana inventada.
- **Segurança e gates:** ACK/readback permanece BLOQUEIO_DE_DECISÃO, EV-VOLTAGASOLINA-015 sem prova física, quatro telas técnicas protegidas até mockups completos e aprovação, OP-12 hardware BLOCKED, OP-19 aprovação BLOCKED; nenhuma liberação APK/merge/acesso ECU.
- **19 Tasks Mestre (conservador, sem reconciliação de aceites adicionais):** 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 NOT_STARTED_NÃO_RECONCILIADAS; **18 não DONE**. BLOCKED não significa concluída. Próximas tarefas disponíveis: OP-2–5, OP-8, OP-10–11, OP-13, OP-15–18, OP-20 (não alterar IDs); evidências novas devem ser reconciliadas com CI do novo SHA.


### CI RED #2055 — reconciliação determinística do gerador de cobertura (18/11/2026)
- SHA `e959b83a073a3b02676551b561a7858da1b4e424`, run [#2055](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44393707711): grafo de PRODUÇÃO atualizado passou, mas `tools/progbase/cobertura_re.py --check` falhou por `cobertura.json` desatualizado; o mesmo gate falhou em testes Python. A matriz/novos documentos não alteram evidência do original. **Causa OBS-COD:** nova referência do teste `inventario-superficies.test.cjs` ao componente Ferramentas elevou contagem textual de testes do grafo de 32 para 33; o resumo de engenharia reversa consome essa contagem.
- **Correção restrita a artefato gerado:** `docs/progbase/cobertura.json` na área Transporte, `app.testes: 32→33`, espelhando exatamente o grafo corrente; `COBERTURA.md` não mudou segundo o log da etapa RED. Sem alterar porcentagens, classes de evidência, suporte da ECU ou código de writer. GREEN integral depende do CI no novo SHA.
- **OP-11 verificação cruzada:** todos os 36 JS sob UI e o `index.html` / `styles.css` / `styles-adaptive.css` foram inspecionados quanto a `data-adv-use-suggestion`: 1 ocorrência no handler de `components/advanced.js`, 0 nas outras 35 fontes JS e 0 nos 3 arquivos estáticos. Isto sugere handler sem controle emitido; **não é prova de semântica sem uso em runtime/plugin**, portanto continua BLOCKED para remoção, nenhum arquivo excluído.
- **OP-13 inspeção diferencial:** comparando 50 PNGs dos CI #2051 e #2053, 46 têm SHA256 de bytes idênticos. Quatro PNGs diferem apenas em pixels com diferença de até 10 níveis RGB (nenhum pixel supera 10), sem mudança visual material inferível; continua sem validação física e sem autorização para corrigir quatro telas técnicas.


### Segunda regressão de geração corrigida — #2057 (18/11/2026)

- **RED do CI #2057**, SHA `04198f0887ea3d4fd4025efa39a6d4684170856e`: grafo de produção correto, mas `cobertura_re.py --check` continuou a reprovar `cobertura.json` e repercutiu no Python. A primeira atualização havia usado serializador JavaScript, que muda floats Python escritos como `0.0` para `0`, embora numericamente equivalentes; o gerador compara **bytes exatos**, não valores JSON.
- **Correção delimitada:** recuperar o arquivo gerado Python original do SHA `e959b83` e alterar só a ocorrência única `"testes": 32` → `"testes": 33` da área Transporte. Todos os `0.0`, `100.0` e demais formatos originais foram preservados. O `COBERTURA.md` não demanda mudança. Causa confirmada por comparação de primeira divergência no byte 123, e 31 ocorrências de `: 0.0` no gerado antigo contra 0 no serializador JavaScript.
- **Gates não flexibilizados:** teste do gerador permanece ativo. CI GREEN integral do novo SHA ainda precisa ser observado. Não alterar writers, ECU, UI protegida ou liberação.


### CI #2059 — gerador HTML dependente, corrigido (2026-11-18)

- **RED OBS-COD:** CI #2059 no SHA `a64b227eb4185ec1198d9514151416720ba4876a` confirmou grafo de produção verde e passou o check de `cobertura.json`, porém Python falhou exclusivamente no `test_html_autonomo_e_em_dia`: `cobertura.html` estava desatualizado após a contagem de testes de Ferramentas passar de 32 para 33. O HTML é gerado deterministamente por `tools/progbase/cobertura_html.py` e embute o JSON compacto.
- **Correção atômica restrita ao artefato gerado:** apenas a ocorrência única `"testes":32` virou `"testes":33` dentro do blob `<script id="dados" type="application/json">`; toda a página, recursos locais e representação de floats permanecem idênticos. É sincronização de geração, não mudança do app ou de capacidade da central. O aceite exige CI no SHA deste commit (ainda não verificado no instante do push).
- Sem alteração a writer, estado ACK/readback, APK, dispositivos, rotas técnicas ou limpeza destrutiva. As 19 Tasks conservam 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas; 18 não DONE.


### Lote independente autorizado — OP-3 / C5 Task 17 (2026-11-18)
- **Baseline OBS-COD/CI:** SHA `635fb955eb971e29640133b5db7a7b4c3b8bdedc`, workflow #2061 [run 44395943245](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44395943245) terminou seis jobs SUCCESS. O gate permanece sem homologação Android ou ECU.
- **RED estático por comparação do código anterior:** `MainActivity.kt` possuía quatro callbacks `ActivityResultContracts.CreateDocument` com `if (uri == null) return@registerForActivityResult`: ao fechar o seletor Android nenhum aviso era mostrado, e nos seletores de Sessão/Resumo os IDs `pendingSessionExportId`/`pendingSummaryExportId` continuavam preenchidos após cancelar. Um novo contrato Python exige aviso por destino, nenhum “salvo” no cancelamento e limpeza de IDs.
- **Menor mudança completa, aplicação Android (não ECU):** seletor cancelado → aviso “Nenhum arquivo foi salvo”; Sessão/Resumo → limpar ID temporário. Sucessos existentes continuam somente após `archives.export*` ou resultado `svc.exportSession*` com `ok` verdadeiro. UI Ferramentas preservada. Falha/ausência de serviço mostra caminho de recuperação existente; o callback de cancelamento não invoca escrita nem API USB.
- **CI do novo SHA ainda a verificar** após commit; contrato estático/compilação Android não demonstram toque/retorno real do seletor em hardware. OP-3 segue PARTIAL; Task 17 ainda não reconciliada; requer validação Android e exportação real antes de DONE. ACK K, writer físico, reset, quatro telas protegidas e APK intocados.


### Novas fricções comprovadas — OP-3 / C3 Task 14 e grafo (2026-11-18)
- **RED CI #2063** no SHA `1a1df372a1a3688148ebb1dfe2aa3ca1e48f5a64`: grafo de produção e Python reprovaram exclusivamente sincronização de arquivo gerado após introduzir no teste Python referências a `exportLogs`, `exportSession` e `exportSessionSummary`; o arquivo `producao.json` tinha 694 arestas cobre e precisa de 697. Nenhum elo essencial físico/serial foi modificado; corrigidos ambos os artefatos gerados sob a heurística textual do gerador.
- **Bug OBS-COD no cartão Bateria:** `batteryOptimizationStatus()` retorna `{supported:false, ignoringOptimizations:false}` ao faltar ponte Android. A UI antiga exibia indevidamente “O Android não pausa o app” / “Sem limite”, embora o estado fosse DESCONHECIDO. Em Android anterior a M, o retorno nativo é `supported:false, ignoringOptimizations:true`, que efetivamente significa que essa política específica não se aplica.
- **IMPROVE mínimo autorizado só em Ferramentas:** distinguir **desconhecido** (sem leitura, orientar serviço), **restrição** (Android suporta, não concedeu: CTA Permitir), **livre** (isento ou Android antigo). Nenhum comando físico / quatro telas protegidas. Teste E2E de toque/estado do navegador exercita os três casos e exige zero quadros ECU; **RED lógico no código antigo** (falso êxito do estado indisponível), GREEN só após CI do novo SHA.
- **Exportação local:** os quatro callbacks de seletor passaram a comunicar cancelamento e descartar IDs temporários; a aplicação Android ainda precisa de teste físico. Status OP-3 PARTIAL; 19 Tasks preservam 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas. Sem APK, merge ou ECU.


### RED #2065 e correção de UX app-only (2026-11-18)
- **CI #2065 / SHA `4847f248db2ac8bd4dba83536dbb98e2553bb234`**: Kotlin/JVM, grafo e Python passaram; navegador encontrou clip real no cartão Bateria em `tests/ui/aprendizado-atual.test.cjs` no 800×480. A cópia longa “Não foi possível consultar a bateria. Verifique o serviço do app.” excedeu a altura de `.tile-line`. Corrigido com texto curto **“Verifique o serviço do app.”**, e estado “Sem leitura” permanece visível; não remover o contrato geométrico nem alargar o cartão sem prova. RED observável → GREEN dependente do novo SHA.
- **Outra falha OBS-COD:** `Drawers.toggleAutoRefine` aceitava resposta JSON sem `ok:false` como êxito (incluindo `{}` ou `ok:true` com `mode` divergente), ou ignorava ponte ausente silenciosamente. A UI de refino SHADOW/OFF deve mudar só com `ok:true && mode===requested`; falha preserva o modo real e mostra recuperação. Teste Chromium com recibo ausente, modo divergente e confirmação verdadeira, zero frames ECU.
- **Grafo determinístico:** duas citações literais a métodos de bridge adicionam arestas `cobre` da suíte `ux-fluxo-toque`: 697→699, sem novas bridges, nós nem capacidades. Sem tocar em writers, ACK/readback, quatro telas técnicas ou gerar APK.
- **Evidência atual:** testes de toque em Chromium são simulação da tela Ferramentas; efeito no Android e no veículo segue NÃO VERIFICADO. OP-3 PARTIAL, Task 14 ainda não reconciliada.


### Recuperação RED CI #2067 / isolamento das exportações (2026-11-18)
- **RED observado no SHA `c1699242ff75bf28e3c66a77932bcb5f3e7f8785`**, [CI #2067 run 44422844555](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44422844555): contratos Python, grafo, Kotlin/JVM e Android lint PASS; JS/navegador FALHOU no teste novo de bateria. Causa: o teste modificava o retorno de `api.batteryOptimizationStatus` em JavaScript mas somente rodava o scheduler rápido; `Drawers.renderTools` é atualizado na via de contexto/evento, e o cartão ficou no estado anterior `unknown`. **Correção do isolamento**: simular o evento real `omegas-refresh` emitido pelo Android e esperar a nova projeção. Não modificar layout ou forçar cliques invisíveis; teste ainda exige unknown/off/on e zero comandos ECU.
- **Falha latente OBS-COD no armazenamento local:** os callbacks `CreateDocument` de Sessão/Resumo guardavam `pendingSessionExportId`/`pendingSummaryExportId` compartilhados até dentro de `runWithServiceAsync` (`Dispatchers.IO`). Se o serviço não estivesse disponível, a limpeza não ocorria; outra seleção poderia trocar o ID antes da execução no coroutine. Um snapshot imutável do ID é feito **no callback UI** e o campo temporário é limpo antes de agendar I/O; o exportador recebe exclusivamente o snapshot. Os nomes de arquivo, seleção Android e formato ZIP existentes foram preservados.
- **Teste RED→GREEN estático novo:** contrato Python de ordem de captura/limpeza antes do agendamento e rejeição de leitura do campo compartilhado no bloco I/O; implementação antiga reprovaria. CI do novo SHA ainda não verificado. Sem validação no dispositivo, classificar como OBS-COD / PARTIAL; nenhum APK, merge, writer, reset ou alteração de ECU.


### CI #2069 GREEN e microfluxo de permissões (2026-11-18)
- CI #2069, SHA `68f95d26c1de9e549284bd40e06579d247e87004`, [run 44424037908](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44424037908): seis jobs **success**; RED #2067 recuperado por `omegas-refresh` real no teste, JavaScript/navegador GREEN. Prova Chromium+JVM, não Android físico.
- **OBS-COD / RED de comportamento em Ferramentas:** os CTAs `data-tool-battery-request`, `data-tool-overlay-request/enable/disable` descartavam o retorno de erro da bridge, deixando o usuário sem causa nem continuação; `overlayStatus()` retorna `ok:false` quando Power bridge não existe, mas o cartão oferecia um `Permitir balão` impossível.
- **IMPROVE mínimo só em Ferramentas:** falha de pedido/habilitação -> `notifyResult` warning com erro; `launched:true` ou `permissionRequired:true` -> aviso de autorização **pendente**, sem verde; overlay indisponível -> `Sem leitura`, sem CTA fictícia. Recibos verdadeiros vêm somente de status confirmado pelo Android após retorno. Teste novo Chromium cobre falha de bateria, fallback sem bridge, falha/pedido pendente de overlay, erro ao habilitar e ausência de quadros ECU.
- Grafo textual de cobertura `cobre` 699→701, dois vínculos novos a `OmegasPower.requestBatteryOptimizationExemption` e `OmegasPower.requestOverlayPermissionAndEnable`, sem interfaces novas; artefatos gerados sincronizados. CI do SHA resultante ainda a verificar. OP-3 continua PARTIAL, porque autorização real de bateria/balão no Android é NÃO VERIFICADA.
- Sem write/serial/ECU, UI técnica protegida, APK ou merge. Contagem do Programa Mestre conservadora: 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas; 18 não DONE.


### RED #2071 / tratamento de retorno Android e balão oculto (18/11/2026)
- **CI #2071**, SHA `427accb4ab64bf7f28a9665253dff413fda735b0`, run [44425144678](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44425144678): Python, Kotlin/JVM, grafo e lint passaram; JavaScript falhou no teste novo de permissões ao aguardar o botão `data-tool-overlay-request` após um erro do cartão Bateria. RED real: `app.js::toolsEditing()` considera `BUTTON` em foco como edição e suprime `renderTools`, inclusive no evento externo `omegas-refresh`. O foco permanecia no botão tocado; a mudança de permissão do Android não era apresentada, mesmo com retorno do serviço novo.
- **Correção de escopo mínimo:** `refreshContext(reason)` atualiza Ferramentas/Sessões após `omegas-refresh` mesmo com botão ativo; os ticks numéricos do scheduler **continuam** respeitando `toolsEditing()` para não engolir toques nem inputs. A ponte Android `MainActivity.onResume()` já emite `refreshWebUi()`→`omegas-refresh`; não adicionada nova tarefa/polling. Teste RED do #2071 permanece como contrato de retorno de permissão em Chromium; CI do SHA atual ainda por verificar.
- **Outra fricção OBS-COD:** `TelemetryOverlayController.setAppForeground(true)` oculta temporariamente o balão enquanto OMEGAS está em primeiro plano, embora `requestedEnabled=true` e `permissionGranted=true`. A UI anterior usava `visible` como sinônimo de pedido ligado: exibia **Desligado** e CTA **ligar** para um balão já configurado. Agora separa preferência/concessão de visibilidade: `requestedEnabled && permissionGranted` mantém **Ligado** com botão de desligar e texto curto “Aparece fora do OMEGAS.”. Teste de cenário pedido=true/visible=false vs pedido=false, zero writers.
- **Bridge de permissões:** `PowerJavascriptBridge` só converte `ok:false` em pedido de permissão quando `permissionRequired:true` (ausência real de autorização); recusa por serviço indisponível/encerrado não é mais sobrescrita por `ok:true` ao abrir Settings. Contrato Python RED antes → alteração mínima; exigir teste Android físico para confirmar todas as variantes.
- Grafo textual `cobre`: 701→702 por nova citação em contrato Python, sem nova ponte/ECU. **Status OP-3 PARTIAL, Tasks Mestre 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas; 18 não DONE.**
- Capturas reais do Chromium #2069, artefato [14482704553](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44424037908/artifacts/14482704553): página Ferramentas/App e arquivos 800×480 e 1280×720 revisadas. Card Bateria “Verifique o serviço do app.” cabe sem corte; captura #2069 ainda mostrava botão “Permitir balão” sem ponte, corrigido em #2071 mas o CI #2071 falhou no teste de fluxo. Capturas não provam Android.
- Nenhum APK, merge, ECU ou tela técnica protegida modificada; bloqueio ACK/readback K preservado.


### CI #2073 GREEN / continuidade do foco em Ferramentas (2026-11-19 UTC)
- **CI GREEN** do SHA `cadf71b4142df89d765b9d1986627969e50a0210`: [run #2073](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44426621923), seis jobs `success`, inclusive teste Chromium de retorno de permissão após foco de botão e status pedido/visível do balão. Nenhuma homologação real da janela Android/WebView.
- **RED por inspeção do código recém-verificado (OBS-COD):** a exceção `nativeRefresh` de `app.js::refreshContext` dava bypass completo a `toolsEditing()`, podendo reconstruir o DOM enquanto um `INPUT`/`SELECT` estava em edição ao chegar `omegas-refresh`. Isso preservava a volta dos botões, mas criava risco de perda de foco/rascunho em filtros de Registro e campos locais.
- **Correção de menor escopo:** `nativeRefresh` permite ignorar a proteção SOMENTE se o elemento ativo for `BUTTON` dentro de `toolDiagnosticsWorkspace`; `INPUT`/`SELECT` continuam protegidos. Teste Chromium acrescentado: abrir Registro, focar `data-log-level`, atualizar status da bridge por evento nativo, conferir a **mesma referência DOM**, foco e escolha, zero frames ECU. O mutante com bypass geral falharia no teste.
- **Evidência visual real (Chromium)** de CI #2073, [artefato 14484313982](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44426621923/artifacts/14484313982): as capturas `ux-800x480-9-ferramentas-app-e-arquivos.png` (800×480 CSS/DPR2) e `ux-1280x720-9-ferramentas-app-e-arquivos.png` (1280×720 CSS/DPR2) foram inspecionadas; estado **Sem leitura** do balão aparece inteiro e sem CTA “Permitir balão” quando Power bridge está ausente, sem clipe aparente nos cartões. Comparação #2069→#2073 restringe mudança visual ao cartão Balão; cinco telas técnicas não mudaram.
- **CI do SHA após este commit pendente de verificação**. OP-3/Task 14 PARTIAL, status Mestre 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas = 18 não DONE; quatro telas técnicas e decisão K continuam BLOQUEADAS. Nenhum APK/merge/ECU.


### Reconciliação independente de CI #2075 — 2026-11-18, 21h Bahia (OBS-COD)
- HEAD confirmado do PR #195: `8bbac1a58c66a1de21fb31bfe594017c95635e20`, branch `feat/premium-adaptive-ui-v8-2`, PR aberto em draft, sem merge. CI [#2075, run 44427850172](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44427850172) **success** no mesmo SHA: seis jobs success (Python, Kotlin/JVM, grafo, JavaScript/Chromium, Android lint e integração). Etapas de diagnóstico condicionais skipped não são testes aprovados; geração de APK não foi executada.
- O commit do HEAD restringe `omegas-refresh` a atualizar Ferramentas com BUTTON focado, mas preserva INPUT/SELECT em edição; teste Chromium novo protege nó DOM/foco/seleção. Esta verificação do CI encerra somente o gate pendente de CI daquele commit, **não** a homologação Android/WebView ou o aceite funcional global.
- **Correção de leitura histórica do gate:** OP-7 (exposição de RESET_ALL) está marcado DONE com testes desde CI #2009; a frase antiga “reset geral exposto” no resumo de gate é obsoleta e não deve ser interpretada como falha ainda presente. Permanecem BLOCKED decisão ACK/readback K, aprovação das quatro telas, hardware/ECU e cobertura integral de ações; OP-3 e Task 14 continuam PARTIAL. Nenhuma alteração de writer ou semântica autorizada.
- Inventário visual 9:16 / 16:9 **não reclassificado** neste lote: a captura do SHA #2075 foi gerada pelo job JS, mas não foi inspecionada visualmente aqui; manter status anteriores, sem alegar PASSOU novo nem teste físico. Evidência de #2073 (800×480, 1280×720) permanece histórica, não substitui captura no HEAD.
- Contagem conservadora do Programa Mestre **1 DONE / 2 PARTIAL / 3 BLOCKED / 13 NÃO RECONCILIADAS = 18 NÃO DONE**. BLOCKED não é DONE. Fila OP-1..OP-19 preservada, sem renumeração; prioridade executável seguinte: completar mapeamento CTA→bridge→recibo/teste e revisão visual no SHA atual, sem writer/ECU. Sem APK, merge ou hardware.


### CI #2077 reconciliado — 2026-11-18 22h Bahia (OBS-COD)

- PR #195 continua **open/draft**, branch exclusiva `feat/premium-adaptive-ui-v8-2`, HEAD anterior a este registro `d8cbf001c0b83ca84c8499da9e58ea7872f750e8`. [CI #2077, run 44430526070](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44430526070) concluído **success** no SHA exato: contratos Python, grafo, Kotlin/JVM, Android lint, JavaScript/Chromium e integração, seis jobs aprovados. Nenhum desses resultados prova a central física.
- Artefato `ferramental-ux-b311c21f73a2214c91814b48ac6e3be43ca629db`, ID 14487410368, existe no run e não está expirado; **PNGs não inspecionadas visualmente nesta rodada**. Não promover status das cinco telas 9:16/16:9; manter inventário histórico e revisão visual no SHA corrente pendente.
- O commit `d8cbf001` apenas reconciliou CI anterior no checkpoint. Este registro fecha a checagem de seu próprio CI, não conclui OP-2/3/4/5/8/10/13/15, nem as 19 Tasks. Contagem conservadora: **1 DONE, 2 PARTIAL, 3 BLOCKED, 13 NÃO RECONCILIADAS = 18 não DONE**. OP-7 RESET_ALL já DONE (não reabrir bloqueio obsoleto); decisão ACK/readback, aprovação das quatro telas e validação Android/ECU seguem bloqueadas.
- Próxima execução autorizada: inspecionar PNGs do HEAD; ampliar vínculos CTA→handler→bridge→recibo/teste com RED/GREEN e regenerar grafo se mudar referências. Sem writer, reset, APK, merge ou operação física. Não confundir este registro documental com teste funcional novo.


### CI #2079 no HEAD 6c8c2b20 — verificação independente (2026-11-18, OBS-COD)

- PR #195 permanece open/draft, branch `feat/premium-adaptive-ui-v8-2`, SHA `6c8c2b20cb97c8f8613166f60f5489a06202ddd5`. GitHub Actions [#2079, run 44437211589](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44437211589) retornou `completed/success` para o SHA exato. Isto confirma o gate CI do checkpoint anterior, não prova efeito físico, cobertura integral nem aprovação de interface.
- Artefato de capturas [14489701260](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44437211589/artifacts/14489701260) está disponível e não expirado; **PNGs ainda não inspecionadas nesta verificação**. Não promover PASSOU em 9:16/16:9 por mera existência do artefato. O inventário visual de #2053 e a revisão pontual de #2073 permanecem referências históricas.
- OP-7 RESET_ALL já concluído; não reabrir. OP-13 (revisão visual no SHA atual), OP-15 (cobertura CTA→handler→bridge→resultado/teste) e OP-3 (permissões/retorno Android) continuam PARTIAL. Sem autorização de mudar as quatro telas técnicas ou a semântica ACK/readback K. Contagem conservadora das 19 Tasks **1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas = 18 não DONE**; BLOCKED não é concluída.
- Próximo trabalho independente: inspeção de PNGs do artefato #2079 e expansão comprovada da matriz funcional com teste RED→GREEN. Sem acesso à ECU, writer, merge ou APK. Nenhuma nova OBS-FIO.


### Execução manual da tarefa enxuta — etapa de confirmação (19/11/2026)
- **HEAD inicial:** `d78a82131f9899663c01c3673f06de9bc5dba597`; [CI #2081](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44445329165) seis jobs SUCCESS no SHA inicial (não é homologação física).
- **OP-3/OP-5/OP-10/OP-15**: corrigir recibos falsos de Sessões e Car Info em `components/drawers.js`, sem mexer em writer/ECU. **RED OBS-COD:** os caminhos antigos tratavam `ok:true` sem `recording`, `settings` ou `enabled` real como sucesso/silêncio. Testes Chromium adicionados para validar modo e valor reais; precisam passar no CI do SHA criado.
- **OP-4**: matriz funcional atualizada apenas para referenciar os testes e lacunas relacionadas; nenhuma função física ganhou status de comprovada.
- **IMPROVE**: UI — avisos localizados; fluxo — preservar edição e estado anterior; confiança — confirmação explícita e recuperação sem repetir automaticamente. Nenhuma pesquisa de usuários inferida.
- **Gates mantidos:** BLOCKED readback/ACK da escrita K; quatro telas técnicas protegidas até mockups/aprovação; USB e Android físico não homologados; proibição de APK/merge.
- **Contagem conservadora das 19 Tasks mestre:** 1 DONE, 2 PARTIAL, 3 BLOCKED, 13 NOT_STARTED/não reconciliadas = 18 não DONE, até prova e reconciliação formal. O trabalho de componentes não autoriza marcar Task como DONE.


### CI #2083 — RED real e reparo sem regressão (19/11/2026)
- HEAD anterior `7ffd8bf5eccb5a9878e1d5c84b3dc9eaaa6612e6`, [run #2083](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44453841274): Python, grafo, Kotlin/JVM e Android lint **SUCCESS**; JavaScript **FAIL** por duas expectativas legadas do próprio teste OP-3: a nova frase de falha não continha a substring anterior; o mutante histórico alterava somente a camada de `notifyResult`, enquanto o novo guard `recording` já segurava a falsa confirmação antes dela. Contador `# TODO` de 75 CSS potenciais não é a causa do fail (e não autoriza poda).
- **Correção mínima:** manter frase “Não foi possível confirmar...” adequada à recuperação, e atualizar o teste de mutação para representar **as duas violações simultâneas** da versão antiga (guarda de `recording` + recibo permissivo). Preserva efeito e status do app. Novo SHA precisa de CI integral GREEN antes de promoção. Sem escritores/ECU, merge, APK nem mudanças nas quatro telas técnicas.


### Lote de prova cruzada — 19/11/2026
- **HEAD anterior:** `c2afc066eb16301ead1855fdbad842f73a72566c`. CI #2085 [run 44454698580](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44454698580) **6/6 SUCCESS**: recuperação GREEN do RED #2083 após manter texto de erro compatível e mutação da dupla guarda.
- **OP-3/4/5/10/15:** correções OBS-COD de leitura Car Info obsoleta, nova conexão e falso `ok:true` da ponte de overlay. Testes RED→GREEN: Node com estado conectado/desconectado, JVM para reconexão e contrato Python para a ponte; prova física ainda BLOQUEADA. Matriz 44 linhas rastreáveis sem ampliar capacidade ECU. Grafo deterministicamente `cobre` 702→703 (nova citação exata no teste Python).
- **OP-8/13:** 50 capturas CI #2081 revisadas, artefato 14492858377. Falha retrato de nav em quatro telas técnicas protegidas; Ferramentas visualmente legível; Android NÃO VERIFICADO. **OP-16** dicionário diferencia amostra atual de histórica. **OP-11:** nenhum arquivo removido; 75 seletores CSS `# TODO` e handler sem template seguem INVESTIGAR/BLOCKED, não exclusões seguras.
- **OP-2/17/18:** continuam PARTIAL; OP-19 mockups/decisão e OP-12 hardware seguem BLOCKED. Não modificar K/ACK/readback, reset geral, firmware, escritor, ECU, nem reformar AutoCal/Refino/Mapa/Curva. Não gerar APK nem fazer merge.
- **19 Tasks Mestre (aceites conservadores):** 1 DONE, 2 PARTIAL, 3 BLOCKED, 13 não reconciliadas/NOT_STARTED = **18 ainda não DONE**. BLOCKED não é concluída. CI completo do novo SHA ainda precisa ser verificado antes de qualificar este commit como GREEN.


### Auditoria de baseline — CI #2087 (19/11/2026, OBS-COD)
- PR #195 `open/draft`, branch exclusiva `feat/premium-adaptive-ui-v8-2`; HEAD **antes deste registro** `a7626e6102497dc60992cb7016459eb6b6f475a7`. [CI #2087 / run 44455762051](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44455762051) `completed/success` nesse SHA. Isto encerra o gate de CI do lote de correção de Car Info/overlay registrado acima, mas não prova hardware nem readback físico.
- Artefato visual [14497418469](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44455762051/artifacts/14497418469) presente, não expirado (35.085.297 bytes). **Imagens não inspecionadas nesta rodada**; não promover as cinco telas a PASSOU no SHA atual. Inventário visual mais recente com inspeção individual permanece CI #2081: retrato DPR3 falha nas quatro telas técnicas, Ferramentas passa visualmente; paisagem passa visualmente nas cinco; Android físico não verificado.
- Revisão de documentação: removida afirmação histórica incorreta de que RESET_ALL ainda está exposto. OP-7 continua DONE por prova de código/CI; gate final segue BLOCKED por decisão ACK/readback, cobertura de ações, UI aprovada e hardware. Não houve alteração em writer, bridge, ECU, telas protegidas, APK ou merge.
- Fila de trabalho independente: OP-2/3/4/5/10/15 (rastreabilidade e recibos), OP-8/13 (inspeção das capturas do novo SHA), OP-11 (triagem sem exclusão), OP-16/17/18 (contratos e jornadas sem implementar redesign). OP-12 e OP-19 continuam bloqueadas. Contagem conservadora das 19 Tasks: **1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas = 18 não DONE**. A próxima execução deve evitar commits somente para reconfirmar CI, priorizando teste de comportamento ou inspeção visual.

### Lote 19/11/2026 — recibo de autoteste Ferramentas (OBS-COD, OP-3/5/10/15)

- Baseline verificado: PR #195 open/draft, HEAD `d9efe94e5cae3e3903a107ee9ebfec0bd43a37b0`; CI #2089, run 44460950633, `completed/success` no SHA de baseline. **Não extrapolar esse verde para commits novos.**
- Teste de regressão `tests/ui/drawers-selftest-receipt.test.cjs` introduzido no commit `13de048`: recibos `ok:'true'` e `ok:1` não podem produzir “Teste do app concluído”. Antes da correção, o handler `[data-tool-selftest]` usava truthiness e tratava esses recibos como sucesso (**RED por inspeção OBS-COD**; execução RED no CI ainda não comprovada).
- Correção restrita a Ferramentas no commit `fb5d50f`: exigir `result?.ok === true` para nível e mensagem de sucesso. Ajuste do contexto VM do teste em `3056917`. **GREEN do SHA final pendente de CI**; não marcar teste como aprovado antes de execução. Não houve mudança de comando, bridge, writer, ECU ou telas técnicas.
- Classificação: OBS-COD para condição antiga e correção; DERIVADO para risco de recibo enganoso; OBS-FIO inexistente; efeito Android físico DESCONHECIDO. IMPROVE: UI mantém aviso, fluxo permite tentar novamente, confiança não converte resposta parcial em êxito.
- OP-3/5/10/15 continuam PARTIAL até CI e cobertura integral; OP-8/13 exigem PNGs reais no SHA; OP-12/19 seguem BLOCKED. 19 Tasks conservadoras: 1 DONE, 2 PARTIAL, 3 BLOCKED, 13 não reconciliadas (18 não DONE). Cinco telas: retrato DPR3 AutoCal/Refino/Mapa/Curva FALHOU historicamente por navegação; Ferramentas PASSOU no CI #2081; paisagem cinco PASSOU historicamente no #2081; **todas NÃO VERIFICADAS visualmente nos commits deste lote**. Android físico NÃO VERIFICADO em todas.
- Próximo ciclo: confirmar CI no SHA `3056917`, revisar capturas atuais e prosseguir CTA→handler→bridge→recibo sem ampliar capacidades da central. Sem merge/APK/hardware e sem alterar decisão ACK/readback.


### Auditoria CI #2097 — 2026-11-19 (OBS-COD; checkpoint sem aceite)

- PR #195 open/draft, branch `feat/premium-adaptive-ui-v8-2`, HEAD antes deste checkpoint `2fca8c32d8a7140baa85915004ec683dc8e466dd`. [CI #2097, run 44463157436](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44463157436) **completed/failure**: JavaScript/Chromium, Kotlin/JVM e Android lint SUCCESS; grafo e Python FAILURE; integração FAILURE por dependência. Não declarar GREEN no SHA.
- Causa reproduzida no log remoto: `python3 -B tools/grafo_producao.py --check` rejeita `docs/grafo` desatualizado; `tests/test_grafo_producao.py::test_arquivos_gerados_estao_em_dia` compara JSON gerado e falha porque a nova referência `tests/ui/drawers-selftest-receipt.test.cjs` não está nos artefatos versionados. Trata-se de **uma causa raiz** propagada a dois jobs, não duas regressões independentes. Classificação OBS-COD (logs CI); sem OBS-FIO ou prova Android.
- Correção requerida: executar **o gerador canônico** `python3 -B tools/grafo_producao.py` no checkout exato da branch, revisar `docs/grafo/producao.json` e `docs/grafo/PRODUCAO.md`, versionar ambos atomicamente e exigir CI verde no SHA final. **Não editar manualmente artefatos gerados.** Nesta execução não havia runtime remoto online (Desktop Commander: todos os dispositivos offline) e o ambiente efêmero não conseguiu resolver github.com; portanto não foi possível obter checkout para executar o gerador. Não mascarar o fail nem disparar rerun sem correção.
- OP-3/4/5/10/15 PARTIAL; OP-8/13 aguardam revisão visual atual; OP-12/19 BLOCKED por hardware/decisão. Contagem das 19 Tasks conservada: **1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas = 18 não DONE**. Inventário visual no SHA `2fca8c3`: AutoCal, Refino, Mapa K, Curva K e Ferramentas **NÃO VERIFICADO** tanto em retrato como paisagem neste SHA; evidência histórica CI #2081 não é aceite no HEAD. Android físico não verificado.
- Nenhuma alteração de writer, ECU, ACK/readback, quatro telas técnicas, APK ou merge. Próximo passo prioritário: regeneração determinística e CI do SHA final; continuar ações independentes somente após reconciliar a base de teste.


### Lote 2026-11-19 — recuperação da regressão CI #2099 (OP-3/4/5/10/15)

- **Baseline OBS-COD:** PR #195 open/draft, HEAD inicial `b4aaba1bcb8ebdb6d060aa9b6df9a89656242bc4`. CI #2099 ([run 44480324433](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44480324433)) FAILURE: Python e grafo falham por artefatos gerados desatualizados após inclusão de `tests/ui/drawers-selftest-receipt.test.cjs`; JavaScript, Kotlin e lint passaram. Não confundir com falha de writer/ECU.
- **Correção determinística OBS-COD:** sem checkout de rede (runtime sem DNS para github.com; Desktop Commander com todos os dispositivos offline), a saída do grafo foi reconstruída programaticamente a partir do JSON versionado e da única nova referência do teste, seguindo as mesmas regras de `tools/grafo_producao.py`. A única aresta nova é `teste:tests/ui/drawers-selftest-receipt.test.cjs → ui:app/src/main/assets/ui/components/drawers.js (cobre)`. Contagem: testes 422→423, arestas cobre 703→704; Sessões 15→16 e Ferramentas 33→34 testes vinculados. Arquivos `docs/grafo/producao.json` e `docs/grafo/PRODUCAO.md` atualizados nos commits `8a5548f` e `472985e`. **Aceite pendente do --check do gerador canônico no CI**; se houver divergência, corrigir pela saída oficial, não promover GREEN por equivalência presumida.
- **Rastreabilidade OP-4/15:** `tools.app-selftest` permanece `APP_ONLY`, sem comando nem resposta ECU; matriz de 44 entradas agora aponta o teste de recibo booleano e conserva a lacuna de WebView Android. Commit `b72dc75`. **DERIVADO:** `ok:1` ou `ok:'true'` não autorizam informar sucesso; OBS-FIO ausente, resultado em dispositivo DESCONHECIDO.
- **CI:** run #2102 ([44486744686](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44486744686)) iniciado para `472985e`; verificar conclusão e, em seguida, CI do SHA final deste checkpoint. Não afirmar sucesso antes do resultado.
- **Fila operacional preservada:** OP-3/4/5/10/15 PARTIAL até CI e cobertura completa; OP-2/8/13/16/17/18 PARTIAL; OP-7 DONE, OP-12/19 BLOCKED; OP-11 só triagem sem exclusão. Nenhuma renumeração nem promoção de Tasks Mestre. **19 Tasks: 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas = 18 não DONE.**
- **Telas 9:16 / 16:9 neste SHA:** AutoCal, Refino, Mapa K, Curva K e Ferramentas = **NÃO VERIFICADO** em ambas orientações no SHA novo; referência histórica CI #2081: retrato 360×640 DPR3 FALHOU nas quatro telas técnicas por quebra de Ferramentas e PASSOU visual em Ferramentas; paisagem 1280×720 PASSOU visual nas cinco. Android/WebView/USB/ECU físicos NÃO VERIFICADOS. Sem nova captura inspecionada; não transferir aceite entre SHAs.
- **Segurança:** sem alteração de KWriteManager/KFactorManager, ACK/readback, writer, reset, firmware, quatro telas técnicas, APK, merge ou ECU. BLOQUEIO_DE_DECISÃO e bloqueio físico permanecem. Próxima ação: CI no SHA final, inspeção das capturas e cobertura dos demais CTAs, com teste RED→GREEN quando houver falha real.


### Lote 19/11/2026 — CI #2108, divergência residual do grafo (OBS-COD)

- Baseline de trabalho: SHA `eb551c91102599d41ef2d935c1608bb69bc5ad3d`, [CI #2108](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44486969691) `completed/failure`. Grafo e Python falharam; JS/Chromium, Kotlin/JVM e Android lint passaram; integração falhou por dependência. Logs indicam **uma divergência residual** em `docs/grafo/PRODUCAO.md`: seção IMPLEMENTADO registrava 422 testes, enquanto o gerador esperava 423; cabeçalho e JSON já registravam 423, cobertura 704. Sem OBS-FIO.
- Correção mínima no commit `1022985afa97b8caea62e63ff39a600d04e9bd06`: sincronizado somente o contador de 422→423 na saída textual gerada, sem alterar o grafo JSON, UI, bridge ou writer. **Atenção:** ajuste remoto pontual reproduz o valor esperado do log, mas o `--check` do gerador canônico no novo SHA e os seis jobs ainda precisam de comprovação; não promover GREEN sem CI. O ambiente efêmero não conseguiu acessar GitHub por DNS, portanto não houve checkout para executar localmente o gerador.
- Fila: OP-3/4/5/10/15 PARTIAL, OP-8/13 revisão visual pendente no SHA, OP-12/19 BLOCKED; demais IDs preservados. 19 Tasks Mestre: **1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas = 18 não DONE**. Evidência: OBS-COD dos logs e do arquivo versionado; DERIVADO para causa comum do CI; efeito físico DESCONHECIDO; OBS-FIO ausente.
- Telas 9:16/16:9 (AutoCal, Refino, Mapa K, Curva K, Ferramentas): **NÃO VERIFICADO** visualmente no SHA deste lote nas duas orientações. Referência histórica CI #2081 não substitui inspeção atual; Android físico NÃO VERIFICADO. Não houve APK, merge, hardware, writer, reset, alteração ACK/readback ou reforma das quatro telas técnicas.
- Próxima ação: confirmar CI do SHA de checkpoint, verificar `python3 -B tools/grafo_producao.py --check` e `tests/test_grafo_producao.py`, depois continuar rastreabilidade e inspeção de capturas reais, sem commits documentais repetitivos.


### Diagnóstico CI #2112 — 19/11/2026

- **HEAD verificado:** `55f75a508acbfff44aa64f307632918091d25df6`, PR #195 open/draft na branch autorizada. [CI #2112, run 44499162906](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44499162906) terminou **FAILURE**. JavaScript/Chromium e Kotlin/JVM SUCCESS; Python, grafo e Android lint FAILURE; integração FAILURE por dependências. Não promover GREEN.
- **OBS-COD dos logs CI:** Python `tests/test_progbase_cobertura.py::test_md_curto_e_json_em_dia` e grafo falham pela **mesma** causa: `docs/progbase/.../cobertura.json` desatualizado segundo `python3 -B tools/progbase/cobertura_re.py --check`. Regenerar pelo **gerador oficial** `python3 -B tools/progbase/cobertura_re.py` no checkout exato, inspecionar o diff, executar `--check` e testes; não ajustar manualmente JSON gerado. O grafo de produção `tools/grafo_producao.py --check` passou nesta execução, distinguindo os dois geradores.
- **OBS-COD do Android lint:** falha de resolução de dependências externas no runner (`org.codehaus.groovy:groovy:3.0.21`, `org.jetbrains.kotlin:kotlin-reflect:1.9.20`, `kotlin-stdlib:1.9.20`, `httpclient:4.5.6`), sem relatório de lint gerado. Classificar **INFERIDO: falha de disponibilidade Maven/rede ou repositórios**, não defeito de código Android provado. Revalidar em CI, sem alterar versões ou suprimir lint sem evidência.
- **Impedimento desta rodada:** dispositivos Desktop Commander todos offline; runtime efêmero sem DNS para github.com. Acesso GitHub por conector permite leitura/escrita de arquivos, mas não executar o gerador no checkout completo. Por isso **não houve regeneração nem teste GREEN**, e não se deve inventar equivalência do JSON. As 12 subtarefas não foram concluídas; duração de 40 minutos não alegada.
- **Fila:** OP-5 (geradores/testes) PARTIAL e prioridade P0; OP-2/3/4/10/15 PARTIAL (inventário e rastreabilidade), OP-8/13 PARTIAL (capturas), OP-11 PARTIAL (triagem), OP-12/19 BLOCKED (hardware/aprovação), OP-7 DONE (RESET_ALL não reabrir). Próxima execução: validar HEAD/CI, regenerar cobertura com script oficial e repetir CI; depois inspecionar capturas e continuar contratos sem alterar writers.
- **19 Tasks Mestre, sem nova promoção:** 1 DONE, 2 PARTIAL, 3 BLOCKED, 13 não reconciliadas = **18 não DONE**; BLOCKED não é concluída. **Inventário visual no SHA deste lote:** AutoCal, Refino, Mapa K, Curva K e Ferramentas = **NÃO VERIFICADO** em retrato 9:16 e paisagem 16:9 (sem inspeção individual de PNGs do SHA); Android/USB/ECU físicos **NÃO VERIFICADOS**. Histórico CI #2081 não equivale a aceite do HEAD.
- **Segurança:** nenhuma OBS-FIO nova; efeitos físicos DESCONHECIDOS; sem writer, reset, firmware, mudança ACK/readback, reforma das telas técnicas, merge, APK ou acesso à ECU.


### CI #2114 — 19/11/2026, diagnóstico independente (OBS-COD)

- **HEAD confirmado antes do checkpoint:** `1180804c362282fd454cf93189a31b72dd82b6b3` na branch exclusiva do PR #195. [CI #2114, run 44509144930](https://github.com/viluadmcontas2-dot/OMEGAS-V8.2/actions/runs/44509144930) `completed/failure`. **PASSARAM** Android lint, Kotlin/JVM, JavaScript/Chromium. **FALHARAM** grafo e contratos Python; integração falhou em consequência. O lint Android que falhara no #2112 recuperou-se sem alteração de Gradle neste lote: a hipótese de indisponibilidade externa continua INFERIDA, não provada.
- **Causa reproduzida em dois jobs (OBS-COD):** `python3 -B tools/progbase/cobertura_re.py --check` devolve `cobertura desatualizada: cobertura.json`; `tests/test_progbase_cobertura.py::test_md_curto_e_json_em_dia` também falha. O grafo de produção passou sua verificação; não confundir com a cobertura de engenharia reversa. Não corrigir `cobertura.json` manualmente; regenerar com `python3 -B tools/progbase/cobertura_re.py` em checkout do SHA exato, comparar diff, executar `--check`, teste focado e CI completo.
- **Bloqueio operacional verificado:** sete dispositivos Desktop Commander listados OFFLINE; ambiente efêmero sem DNS para github.com (`git ls-remote` falhou). Conector GitHub permite leitura de fonte e logs, mas não executa gerador no checkout integral. Sem alteração de cobertura, sem RED→GREEN comprovado, sem declaração de 12 subtarefas concluídas nem duração de 40 minutos.
- **Fila e gates preservados:** OP-5 PARTIAL/P0 (regeneração oficial); OP-2/3/4/10/15 PARTIAL (rastreabilidade); OP-8/13 PARTIAL (revisão de PNGs); OP-11 PARTIAL (limpeza somente com prova); OP-7 DONE; OP-12/19 BLOCKED. 19 Tasks: **1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas; 18 não DONE**. Bloqueio ACK/readback e aprovação de telas continuam; OBS-FIO ausente, efeito físico DESCONHECIDO.
- **Telas 9:16/16:9:** AutoCal, Refino, Mapa K, Curva K, Ferramentas: **NÃO VERIFICADO** visualmente nas duas orientações para este SHA, apesar do job Chromium aprovado. Android físico/USB/ECU **NÃO VERIFICADOS**. Sem APK, merge, escritor novo, reset, firmware ou alteração de comandos.


### OP-3/4/5/14/15/17 — metadados de Sessão sem falsa precisão (19/11/2026)
- **OBS-COD / RED do app:** `bytesLabel` tratava `null` e metadados ausentes como `0 B`; `createdAt || 0` convertia data desconhecida em 01/01/1970. Nenhum desses valores era confirmado no arquivo Android.
- **GREEN esperado:** `—` para tamanho não informado/negativo, `0 B` exclusivamente para zero válido, `Data não informada` quando ID/timestamp não fornecem data. Teste executável Node VM `sessoes-vazio-convida.test.cjs` verifica ambas as bordas e preserva leitura positiva. CI do novo HEAD ainda a confirmar; sem manipular ECU/USB/seletores de exportação, quatro telas técnicas ou ACK/readback.
- **P0 da cobertura:** CI #2118 gerou os bytes oficiais de cobertura; o único diff de `cobertura.json` (Transporte testes 33→34) foi validado pelo SHA-1 Git blob `4af4651...`, não ajustado por adivinhação. O HTML determinístico foi sincronizado em seguida; novos CI #2122/#2124 são gates, nenhum sucesso declarado sem logs.


### OP-3/4/5/10/15 — cinco handlers antes pendentes, testes de continuidade (19/11/2026)
- **RED OBS-COD:** `Drawers.renderTools` preservava `logCategory` após a categoria desaparecer do registro rotativo; o seletor visual mostrava “Todos os assuntos”, mas o filtro interno antigo continuava ocultando todas as mensagens. **Correção:** voltar a `ALL` e página 0 quando categoria não existir, sem remover logs.
- **Teste Node VM:** verifica categoria desaparecida, filtro válido preservado, mudança de nível/assunto e reset de paginação; verifica também que os interruptores do balão enviam `true/false` na ponte Android e mostram falha sem comando ECU.
- **Matriz estrutural:** 5 `extraHandlersPending` reconciliados com código e testes (`data-tool-overlay-enable/disable`, `data-carinfo-toggle`, `data-log-level/category`). `data-adv-use-suggestion` continua **INVESTIGAR/BLOCKED**, não remover. 44 entradas totais, capacidade APP_ONLY e `physicalConfirmed=false` preservadas; nenhuma homologação física inferida.
- **Gates:** CI novo ainda pendente, não declarar GREEN antecipadamente. Não modificar quatro telas técnicas, ACK/readback, writers, firmware, reset, APK ou merge.


### 19/11/2026 — recuperação de exceção no autoteste de Ferramentas (OP-3/5, OBS-COD)

- Baseline verificada antes da edição: PR #195 open/draft, branch exclusiva `feat/premium-adaptive-ui-v8-2`, SHA `be3bfde4646c4d0293c05f6e98448ca1db582c02`, CI #2132 run 44531274976 concluído success. Não transferir esse GREEN para os commits seguintes.
- **RED contratual:** `38239d9578ec5ce6cef5b5e5285fa56dcddad81e` adicionou cenário em `tests/ui/drawers-selftest-receipt.test.cjs`: quando `api.selfTest()` lança erro de bridge, Ferramentas não pode lançar a exceção ao usuário nem exibir sucesso. Antes do patch, o handler chamava a bridge sem `try/catch`; falha inferida pelo fluxo, execução RED em CI ainda não comprovada.
- **Correção mínima:** `d60276215eca13f407425f57422e16ae46239986` captura exceção apenas na ação de autoteste e apresenta aviso de falha; preserva a regra `ok === true` para sucesso. Sem mudança de writers, ECU, bridge Kotlin, protocolo, reset ou telas técnicas. A validação GREEN no CI do novo SHA ainda estava **PENDENTE** no momento do checkpoint; não declarar PASS.
- Classificação: **OBS-COD** para código e teste versionados; **DERIVADO** para caminho de recuperação esperado; **INFERIDO** para efeito no WebView até execução; **OBS-FIO** ausente; Android/ECU físicos **DESCONHECIDOS**.
- Fila: OP-3 e OP-5 continuam **PARTIAL**, aguardando CI do SHA e continuidade dos demais CTAs; OP-2/4/8/10/11/13/15 permanecem sem promoção; OP-12 BLOCKED. **19 Tasks oficiais: 1 DONE, 2 PARTIAL, 3 BLOCKED, 13 não reconciliadas = 18 não DONE**, sem promoção por teste novo.
- Inventário 9:16 e 16:9 no SHA novo: **AutoCal, Refino, Mapa K, Curva K, Ferramentas — NÃO VERIFICADO** individualmente em ambas orientações. Capturas Chromium históricas não equivalem a inspeção visual no SHA final ou homologação física.
- Próximo passo: verificar o CI do SHA final, tratar falha real se houver, ampliar cobertura de recuperação das ações APP_ONLY e reconciliar o grafo por gerador oficial se necessário. Nenhum merge, APK, instalação ou comando de ECU.


### 19/11/2026 — OP-3/5/10/15, recuperação do interruptor de balão (OBS-COD)

- Baseline PR #195 open/draft, HEAD `e231b6267a38c7e3437ceb15d89861f93da91a4e`, CI #2138 run 44569232904 `completed/success` (antes dos novos commits).
- Teste de regressão `640836a`: ambas as intenções APP_ONLY de ligar/desligar o balão devem sobreviver a exceção síncrona de `setTelemetryOverlayEnabled`, emitir aviso e nunca apresentar confirmação falsa. Falha anterior DERIVADA do handler sem `try/catch`; RED executado ainda não comprovado.
- Correção `a8fc3be`: captura local da exceção em cada ação, mantém `true` e `false` exatos, `notifyResult` e redesenho existentes. Nenhuma escrita de ECU, mudança Kotlin/bridge, reset ou telas técnicas.
- **Gates:** novo CI do SHA final ainda precisa ser consultado; não promover GREEN antes disso. OP-3/5/10/15 continuam PARTIAL, pois os demais CTAs e a prova Android permanecem pendentes. OBS-COD: teste e patch versionados; DERIVADO: recuperação esperada; INFERIDO: comportamento real WebView; OBS-FIO ausente; efeito físico DESCONHECIDO.
- **19 Tasks:** 1 DONE / 2 PARTIAL / 3 BLOCKED / 13 não reconciliadas = 18 não DONE. **Inventário no SHA:** AutoCal, Refino, Mapa K, Curva K e Ferramentas = NÃO VERIFICADO individualmente em retrato 9:16 e paisagem 16:9; Chromium CI não é homologação Android/USB/ECU.
- Próximo: confirmar CI do commit de checkpoint, corrigir falha se surgir, continuar cobertura de exceções APP_ONLY (permissão, exportação, Car Info) e inspeção visual; sem merge/APK/hardware.


### 19/11/2026 — OP-3/5/10/15: exceções nas permissões e exportação do registro (OBS-COD)

- Branch de escrita exclusiva: `feat/premium-adaptive-ui-v8-2` / PR #195. Teste de regressão acrescentado em `tests/ui/drawers-selftest-receipt.test.cjs` no commit `b7165d5`: exceções síncronas em `requestBatteryOptimizationExemption`, `requestOverlayPermissionAndEnable` e `exportLogs` não podem derrubar Ferramentas, ocultar falha ou duplicar chamada. Os handlers anteriores não tinham `try/catch` (**OBS-COD**); execução RED em CI não comprovada.
- Correção mínima no commit `4b9eba0`: captura de exceção por intenção, aviso existente `notifyResult`, sem retry automático nem declaração de permissão concedida/exportação salva. `exportLogs` preserva a semântica de retorno anterior quando não há exceção. **DERIVADO:** a tela deve continuar operável; comportamento WebView/Android físico **DESCONHECIDO**.
- **CI do SHA final:** ainda não confirmado; consulta por runs associados a `4b9eba03e7198a22a5f0dd971608fc60af68b79f` não retornou execução no momento da verificação. Não declarar GREEN, nem RED executado. A consulta por SHA não substitui confirmação do HEAD remoto; não foi possível obter SHA do ref pela interface disponível.
- **OP-3/5/10/15:** PARTIAL, sem promoção de aceite. **OP-8/13:** revisão das capturas do SHA ainda pendente. **OP-12/19:** BLOCKED hardware/aprovação. **19 Tasks Mestre:** 1 DONE, 2 PARTIAL, 3 BLOCKED, 13 não reconciliadas = 18 não DONE (contagem conservadora, não reclassificada).
- **Inventário visual no novo SHA:** AutoCal, Refino, Mapa K, Curva K e Ferramentas — NÃO VERIFICADO individualmente em 9:16 e 16:9. Nenhuma OBS-FIO; inspeção Chromium não equivale a WebView físico. **INFERIDO:** benefício de continuidade de UX; não homologado.
- **Segurança:** nenhum writer, reset, firmware, mudança K/ACK/readback, reforma das quatro telas técnicas, merge, APK ou comando de ECU. Próximo lote: confirmar CI do SHA final, inspecionar artefatos de capturas e continuar recuperação de outros CTAs APP_ONLY com testes, sem alterar capacidades físicas.


### 19/11/2026 — OP-3/5/10/15: grafo CI e recuperação Car Info (OBS-COD)

- Baseline PR #195 HEAD `2a4c087a7549c916247ba218373c9865415dc74f`, CI #2149 run 44588596602 completed/failure: JavaScript/Chromium, Kotlin e Android lint SUCCESS; Python e grafo FAILURE por `docs/grafo/producao.json` desatualizado; integração FAILURE consequente.
- Gerador oficial `python3 -B tools/grafo_producao.py` executado no checkout exato no Termux S20 FE; diff determinístico: três arestas de teste para `OmegasNative.exportLogs`, `OmegasPower.requestBatteryOptimizationExemption` e `OmegasPower.requestOverlayPermissionAndEnable`; contagem cobre 704→707. Arquivos gerados publicados nos commits `50f6641` e `00b8a6e`. CI #2152 iniciado, resultado final a verificar.
- Car Info: `tests/ui/drawers-selftest-receipt.test.cjs` ampliado no commit `278fd45` para exceção síncrona em `api.carInfo()`; `drawers.js` ajustado em `cf53425` para capturar erro, avisar e não confirmar sucesso nem repetir comando. `node --test tests/ui/drawers-selftest-receipt.test.cjs`: 7 testes, 7 PASS no Termux S20 FE. CI #2156 iniciado, resultado final a verificar.
- Evidências: OBS-COD (código, diff, testes Node e logs CI); DERIVADO (continuidade de UX sem confirmação falsa); INFERIDO (efeito no Android WebView); OBS-FIO ausente; ECU e efeito físico DESCONHECIDOS.
- S20 FE `SM-G780G` acessível via Desktop Commander/Termux; `adb devices` sem dispositivos externos e `java`/Gradle Android indisponíveis no Termux desta sessão. Nenhum APK construído ou instalado; nenhum cabo ECU/USB acessado, nem operação física. Teste de hardware continua bloqueado por ambiente e gates, não pela indisponibilidade do S20.
- Estados conservadores das 19 Tasks: 1 DONE, 2 PARTIAL, 3 BLOCKED, 13 não reconciliadas (18 não DONE). OP-3/5/10/15 PARTIAL; OP-8/13 revisão visual pendente; OP-12/19 BLOCKED; OP-7 DONE. Não promover por testes isolados. AutoCal, Refino, Mapa K, Curva K e Ferramentas NÃO VERIFICADOS individualmente em 9:16 nem 16:9 no SHA final; capturas históricas não são aceite.
- Segurança: sem mudança de writer, K/ACK/readback, firmware, reset, telas técnicas protegidas, merge ou ECU. Próxima ação: confirmar CI do SHA final, testar APK no S20 quando houver build verificável e realizar inspeção visual sem assumir homologação física.


### 19/11/2026 — lote C0/C1, sessões e ponte APP_ONLY (OP-3/4/5/10/15)

- Baseline verificada: PR #195 HEAD `dc03f97b2ba1cdcdb5fd9abe4178d2863c78accc`, CI #2158 run 44608816788 **completed/success** (seis jobs). GREEN não é prova física nem se transfere aos commits novos.
- **RED real executado (Termux S20 FE):** `tests/ui/drawers-selftest-receipt.test.cjs` falhou ao lançar exceções síncronas de `startSession`, `exportSessionSummary`, `setSessionSettings` antes dos respectivos patches. Cobertura também inclui `stopSession` e `exportSession`. Testes versionados em `c6308d9` e `8a6b713`.
- **GREEN focado OBS-COD:** `drawers.js` captura erros locais nas ações APP_ONLY de iniciar/parar gravação, exportar sessão/resumo e salvar ajustes; mantém campos, sem duplicar chamadas ou confirmar gravação/exportação. Correções `cc4e63f`, `3408281`; ajuste de fixture VM `fc73ae1`. `node --test tests/ui/drawers-selftest-receipt.test.cjs`: **10/10 PASS** no checkout `fc73ae1`. Gerador canônico `python3 -B tools/grafo_producao.py` e `--check` executados no checkout, sem novo diff de artefatos após `f6ec0a9`; arestas cobre 709.
- CI #2171 run 44610833597 iniciado no SHA `fc73ae1`, **ainda em execução** na consulta deste checkpoint; confirmar todos os jobs no SHA final. Não declarar sucesso antecipado.
- **OP-3/4/5/10/15:** continuam PARTIAL (cobertura Android/WebView/USB, demais CTAs e matriz integral pendentes). OP-8/13: capturas atuais 9:16/16:9 ainda não inspecionadas. OP-7 DONE; OP-12/19 BLOCKED por hardware e decisão; OP-11 triagem sem exclusão.
- **19 Tasks Mestre:** 1 DONE, 2 PARTIAL, 3 BLOCKED, 13 não reconciliadas = 18 NÃO DONE; sem promoção por testes focados. **Inventário visual no SHA novo:** AutoCal, Refino, Mapa K, Curva K e Ferramentas = NÃO VERIFICADO individualmente em 9:16 e 16:9. OBS-FIO ausente; efeito Android físico DESCONHECIDO. DERIVADO: continuidade do fluxo e aviso recuperável; INFERIDO: comportamento WebView até prova. Nenhum APK, merge, escritor, firmware, reset, mudança K/ACK/readback ou comando ECU.
- Próximo: confirmar CI do HEAD de checkpoint, inspecionar artefatos de capturas atuais e validar fluxos Android em S20 FE quando houver APK completo autorizado, sem operar central.
