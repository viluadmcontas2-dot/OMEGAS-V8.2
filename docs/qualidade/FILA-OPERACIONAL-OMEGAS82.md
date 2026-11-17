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
| 7 | Resolver exposição do reset geral `RESET_ALL` antes do APK | BLOCKED — decisão de proteção visual | OBS-COD `autocal-cockpit.js` + `AutoCalJavascriptBridge`: comando exposto; proibido no escopo do release; alteração de tela protegida exige OK do dono |
| 8 | Revisar 5 telas nas duas orientações com evidência real | PARTIAL | CI #1969 SHA `b2ccc9a`, capturas inspecionadas; falha: Ferramentas quebra em duas linhas nas 4 telas técnicas em 360×640 DPR3; hardware não validado |
| 9 | Catalogar lixo/legado sem destruir histórico | DONE — triagem estrutural | Git tree completo SHA `17b2a79`: 1692 arquivos, 0 backups temporários/bytecode/artefatos binários; 633 arquivos de pesquisa/evidência preservados; 26 branches existentes, nenhuma removida |
| 10 | Mapear cada CTA dinâmico restante para seu evento, leitura/escrita, teste e resultado | PENDING | Depende de 6; manter Mapa/Curva/Refino/AutoCal sem reformas |
| 11 | Qualificar itens legados para remoção com prova de não uso + testes | PENDING | Nenhum candidato aprovado para exclusão; não apagar fontes ou branches fora da autorização |
| 12 | Homologar Android físico/USB/ECU com consentimento | BLOCKED — hardware/consentimento | Evidência de Chromium não valida toque, bridge WebView nem efeito da central |

## Contagem das 19 Tasks (não confundir com a fila acima)

Conservadora, com base apenas nos marcos CI e nas evidências rastreadas: **1 DONE** (Task 1), **2 PARTIAL** (Tasks 2 e 3), **3 BLOCKED** (Tasks 5, 12 e 19 — decisões ou campo), **13 NÃO HOMOLOGADAS/NÃO RECONCILIADAS** (demais Tasks). Assim, **18 das 19 ainda NÃO estão DONE**. O estágio de tarefas não reconciliadas não afirma que nenhum código exista; afirma apenas que o aceite integral não foi demonstrado.

**Gate de APK atual: BLOCKED**, ainda que o CI da branch esteja verde: reset geral exposto, divergência ACK/readback, 4 telas técnicas com restrição de redesign, auditoria de funções incompleta, evidência de hardware pendente. Não gerar APK, não integrar PR e não acionar ECU.

**Higiene do ledger (OBS-COD):** na revisão histórica do KWriteManager, saída segura em timeout e concorrência de `busy` já têm cobertura no `KWriteManagerSafetyTest.kt` com CI #1983 verde. Pendências antigas foram reconciliadas no `PENDENTE.md`; conflito ACK/readback e risco do KFactorManager continuam abertos. Nenhuma alteração no writer.
