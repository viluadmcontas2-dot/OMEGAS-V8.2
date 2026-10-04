# Defeitos reais encontrados pela suíte de USO (Lote W)

Estes defeitos estão no APP, não nos testes. Nenhum foi corrigido aqui (outros agentes são donos do código).
Cada um tem um teste que o reproduz, marcado `todo` enquanto o defeito existir.

Como funciona o `todo`: `tests/ui/wiring/registry.cjs` registra cada defeito com uma *probe* independente que o
reproduz. Enquanto a probe reproduz, os testes ligados a ele rodam como `todo` do `node:test` (não falham o CI, mas
continuam aparecendo como `not ok ... # TODO DEFECT-n`). Quando o app é corrigido a probe deixa de reproduzir e os
MESMOS testes passam a valer sozinhos. Para o lote final de integração: depois de corrigir o defeito, rodar
`node --test tests/ui/wiring-*.test.cjs`, conferir que não há mais `# TODO DEFECT-n` e apagar o `defect('DEFECT-n', ...)`
do registro. Os defeitos de grafo (1-8) ficam em `tests/wiring/allowlist.json` (`defects` / `consumer_defects`): o
`tests/test_wiring_graph.py` exige que CONTINUEM violando e manda remover a entrada quando forem corrigidos.

Ordem = impacto para o dono (maior primeiro).

| # | Modo | Elemento / chave | Como falha | Causa (arquivo:linha) | Correção mínima proposta |
|---|------|------------------|------------|------------------------|--------------------------|
| DEFECT-9 | M4 Refino (toda fase) | `RefinoScreen.render` | A aba Refino lança `ReferenceError: Cannot access 'agreed' before initialization` a CADA renderização: título, passos, botão principal, gráfico e diário nunca atualizam depois do primeiro desenho; o cartão REFINO do Agora e o botão "Ir para Refino" levam a uma tela morta. | `app/src/main/assets/ui/screens/refino.js:488` usa `agreed` (declarada em `:491`) | Mover `const agreed = ...` (linha 491) para antes de `const headline = ...` (linha 488) |
| DEFECT-14 | M5 Curva K | `#curveReviewButton` (Gravar) | Toque duplo com a ECU ocupada chama `startCurveBatchWrite` DUAS vezes (a segunda volta como falha "Outra operação V8…"). | `screens/curve.js:675` `writePrepared()` não tem guarda de ocupado (`reading/writing/backupTask`) | Começar com `if (this.reading \|\| this.writing \|\| this.backupTask) return;` |
| DEFECT-17 | M6 Mapa K | `#mapReviewButton` (Gravar) | Idem: toque duplo envia `startMapBatchWrite` duas vezes. | `screens/map.js:337` `writePrepared()` sem guarda | `if (this.store.get().map?.state === 'writing') return;` no início |
| DEFECT-11 | M5 Curva K | `#curveReviewButton`, `#curveProposalList` | Depois de gravar e conferir, o botão continua "Gravar 1 ponto na ECU" (ativo) e a lista mostra o ponto antigo; tocar nele não faz nada (botão ativo morto). | `screens/curve.js:389-410` ramo de sucesso faz `proposals.clear()` mas não chama `renderProposalList()` | Chamar `this.renderProposalList()` (e `renderChart()`) depois de `proposals.clear()` no ramo de sucesso |
| DEFECT-13 | M5 Curva K | `circle[data-curve-index]` | Depois de gravação com falha/parcial o gráfico segue desenhando a curva ANTIGA, com 30 pontos que não respondem ao toque (`data=null`), e "ECU confirmada · 30 pontos" continua na tela, embora a ECU possa ter sido alterada. | `screens/curve.js:425-433` ramo de falha zera `this.data` sem redesenhar nem reler | No ramo de falha: `renderChart()` vazio + texto "Curva não confirmada" e/ou `startRead(true)` |
| DEFECT-12 | M5 Curva K | `[data-curve-nudge]`, `#curvePreparePoint` | Sem curva lida (leitura falhou, ou após falha de gravação) os botões −0,05/−0,01/+0,01/+0,05 e "Preparar este ponto" ficam ativos e não fazem nada. | `screens/curve.js:449` `nudgeActive` / `:483` `prepareActivePoint` retornam em silêncio; o HTML não os desativa | Desativar os botões (com motivo) enquanto `this.points().length === 0` |
| DEFECT-10 | M1 conexão | `#dashHeroStatus`, `#dashHealth` | ECU conectada mas muda (nenhum quadro, idade desconhecida): o Agora diz "Leitura em tempo real · operação estável" e "ECU e telemetria principal atualizadas". | `screens/dashboard.js:157` (`else if (connected)`) e `:161` (`level = "ok"`) tratam idade desconhecida (`null`/`< 0`) como fresca | Tratar `age === null \|\| age < 0` com `connected` como "Sem telemetria da ECU" (nível `warning`) |
| DEFECT-1 | M1 conexão | `getStatus.usbPermissionPending`, `usbDevice`, `ecuState` | "Aguardando permissão USB" é visualmente IGUAL a "sem cabo": o dono não sabe que falta tocar em Permitir. | Nenhum JS lê as chaves (grafo produtor/consumidor); `app.js:136`/`dashboard.js:136` só usam `usbConnected` | Mostrar "Autorize o USB" quando `usbPermissionPending === true` (rail + Agora) |
| DEFECT-19 | M7 Sessões | listagem | Se `listRecordedSessions` falha (ponte lança/erro), a aba fica em "Lendo as sessões salvas…" para sempre, sem erro nem próxima ação. | `app.js:302-304` (`Array.isArray(listed) ? listed : null`) + `screens/sessions.js` (`loading = !Array.isArray(...)`) | Distinguir `null` (lendo) de falha (`{ok:false}`): mostrar "Não consegui ler as sessões · tentar de novo" |
| DEFECT-5 | M4 Refino | `autopilot.canDisconnect`, `timeoutReason`, `watchdogExpired` | O Kotlin decide "pode desconectar" e o motivo do prazo, mas a tela usa texto fixo e nunca mostra o motivo do prazo vencido. | Chaves emitidas por `EquivalencePhases.kt`, nunca lidas (grafo) | Ler `canDisconnect` no botão "Estável" e `timeoutReason` na frase de prazo |
| DEFECT-3 | M4 Refino | `relearnSuggested`, `ecuDrift` | O Kotlin sugere reaprender / avisa que a ECU mexeu na curva e nada aparece. | `EquivalenceJson.kt` emite; nenhum JS lê | Mostrar uma linha "A ECU mudou a curva por fora" no Refino/Agora |
| DEFECT-2 | M5/M6 | `safetyBlocked`, `writerState` | Escrita bloqueada por segurança só mostra o texto de erro genérico (a UI não distingue bloqueio de segurança de falha). | `CalibrationOperationsBridge.kt` emite; nenhum JS lê | Mostrar "Bloqueado por segurança" quando `safetyBlocked === true` |
| DEFECT-15 | M5 Curva K | `#curveBackupStatus` | Ao salvar a foto, o caminho/hash do arquivo é escrito e logo apagado por `refreshBackups()`: o dono nunca vê onde ficou. | `screens/curve.js:303` seguido de `:304` | Chamar `refreshBackups()` ANTES do `text('curveBackupStatus', ...)` |
| DEFECT-16 | M5 Curva K | `#curveCurrentFactor` | Fator desconhecido (`null`) aparece como "0,0000" (e o gráfico o desenha em 0). | `screens/curve.js:10` `finite(null) === 0` (`Number(null)`) | `finite` com `value == null \|\| value === '' → null` (igual a `display-rules.js`) |
| DEFECT-18 | M6 Mapa K | `[data-select-row] b` | Bin de Petrol Inj. desconhecido aparece como "0,0 ms". | `screens/map.js:8` mesmo `finite(null) === 0` | Idem DEFECT-16 |
| DEFECT-20 | M7 Sessões | linha da sessão | Singular errado: "1 apagõo". | `screens/sessions.js:77` (`'apagõ' + (n === 1 ? 'o' : 'es')`) | `n === 1 ? '1 apagão' : n + ' apagões'` |
| DEFECT-21 | M8 Ferramentas | `[data-session-keep]`, `[data-session-maxmb]`, texto da retenção | Valor não numérico da ponte vira "NaN"/"Infinity" no campo e na frase. | `components/drawers.js:205-208` (`Number(settings.keepSessions \|\| 20)` sem checar finito) | Usar `Number.isFinite` com padrão 20/256 |
| DEFECT-22 | M4 Refino | `getRefinedAnalysis.points` | `points` que não é lista derruba a renderização (`TypeError: ….map is not a function`). | `screens/refino.js:482` `(this.analysis?.points \|\| []).map(...)` | `Array.isArray(this.analysis?.points) ? ... : []` |
| DEFECT-4 | M8 Ferramentas | `requestOverlayPermissionAndEnable` | A resposta (`permissionRequired`/`launched`) é ignorada: o botão "Autorizar" não dá retorno se o Android não abrir a tela. | `components/drawers.js:57` | Mostrar alerta quando `launched !== true` |
| DEFECT-6 | M4 Refino | `EquivalenceView.typicalBands` | Faixas típicas de Petrol Inj. calculadas no Kotlin e nunca usadas. | Chave sem leitor (grafo) | Ligar ao gráfico do Refino ou parar de emitir |
| DEFECT-7 | M5/M6 | `suggestion.curveChanges/mapChanges/rationale/explanation` | Caminho morto: a UI lê sugestões que nenhum produtor Kotlin emite (a aba Sugestões foi removida). | `screens/curve.js:459,505,666`; `screens/map.js:498` | Apagar o caminho de sugestão da Curva K/Mapa K |
| DEFECT-8 | M7 Sessões | `semanticSummary.indexStart/indexEnd` | "Índice X → Y" por sessão nunca aparece: o resumo do Kotlin não traz o índice. | `screens/sessions.js:18-19` lê; `SessionSemanticLedger.kt` não emite | Emitir `indexStart/indexEnd` no resumo ou remover a linha da UI |

## Não são defeitos (observados e deixados como comportamento aceito)

- Curva K/Mapa K não limitam a seleção em 16 células: o limite do código é 144 (`MapBatchPlan.MAX_USER_CELLS`); o teste M6 cobre 1, 16 e 144.
- `DESCONHECIDO` (combustível) aparece como a palavra "DESCONHECIDO", não "—": legível e sem 0 (aceito).
- Botões "Limpar" sem nada para limpar e a aba já ativa são no-ops legítimos (listados em `allow` dos testes com motivo).

## Situação na integração (Guardião, 2026-10-04)

Corrigidos no app (probes não reproduzem mais; testes de uso estritos, 0 `todo`): 1, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22 e os mortos de 7 (leituras de sugestão removidas).
Abertos de propósito (mudam o que o dono vê; decisão de produto), seguem em `allowlist.json` e o teste de grafo exige que continuem violando: 2 (`safetyBlocked`/`writerState`), 3 (`relearnSuggested`/`ecuDrift`), 4 (resposta do pedido de overlay), 5 (`canDisconnect`/`timeoutReason`/`watchdogExpired`), 6 (`typicalBands`), 8 (`indexStart`/`indexEnd`).
Mutantes: 34, 32 mortos (94%). Sobreviventes equivalentes: `curve-listener-registered-twice` (a guarda de ocupado de `writePrepared` absorve o ouvinte duplicado) e `curve-learning-chart-empty-array` (sem fatores nada é desenhado).

## Final (2026-10-04)

Decididos e corrigidos (allowlist sem `defects`/`consumer_defects` de 2,3,4,5,6,8): 2 = motivo humano de bloqueio em `DisplayRules.failureText`; 3 = linha discreta no AutoCal; 4 e 5 = Ferramentas > Detalhes técnicos; 6 = `typicalBands` deixou de ser emitido; 8 = `indexStart/indexEnd` emitidos pelo livro semântico da sessão.
