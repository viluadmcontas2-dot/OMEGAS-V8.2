# A1 — Auditoria dos fluxos de gravar, apagar e desfazer (UI real)

Base: branch `claude/omega-aura`. Só leitura. Caminhos relativos a `app/src/main/assets/ui/` (UI) e `tests/ui/`. "inferido" = não confirmado lendo o trecho.
Regras R1–R8 = `docs/aura/CONTRATO-DE-FLUXO.md`.

## Resumo em 5 achados
1. **Refino esconde e troca botões por estado** (`screens/refino.js:297-300` nascem `hidden`; `:621-634` liga/desliga com `show.*`). Em "feito" o "Ver alterações" some e o "Desfazer"+"Pronto" aparecem (`:613`). Quebra R1, R2, R4. É a queixa do dono.
2. **Desfazer do Mapa K no Refino vive só na memória da tela** (`core/map-fix.js:77,94`). "Pronto" chama `dismiss()` (`refino.js:323`) e o antes→depois some; reiniciar o app também. Quebra R2/R6/R8.
3. **Ajuste local (`screens/map.js`) não tem Desfazer nenhum** e o botão troca de texto/estado (`:567-572`). Não existe "Última gravação". Quebra R2, R3, R8.
4. **Ajuste global troca a função do botão principal** para "Toque em Restaurar, no rodapé" desabilitado (`screens/curve.js:690-692`). Quebra R1/R4; o Desfazer real é "Restaurar backup" (fluxo à parte, `:230-300`).
5. **Ferramentas: Desfazer aparece/some** (`components/advanced.js:393,525,700` — `${undo.available ? ... : ''}`) e só vive na sessão da central (Kotlin `AntiStallo*Writer.kt`, `undoSession`). Quebra R4. Ainda: "Recomeçar tudo" (RESET_ALL) está exposto em `autocal-cockpit.js:768`, contra o invariante do AGENTS.md (testes `autocal-cockpit.test.cjs:50`, `autocal-reacquisition-ux.test.cjs:34`, `textos-leigo.test.cjs:91` o exigem).

## Porta e selo de hoje (base do I0)
- `core/write-gate.js:19` `check(autopilot)` → `{ok,message}`; só fecha quando `ecuDone===false`. Chamado na proposta e no toque (`refino.js:writeBlocked`).
- `core/write-verify.js` `Tracker.begin()/tick()/resolve()`; estado `idle|checking|fixing|failed`; falha é "sticky". Instância em `app.writeVerify` (`components/live-bar.js:70-72`), desenhada no topo (`:118-127`). Quem chama `begin()`: `map.js:645` (Ajuste local), `curve.js` (~`:454`); Refino só chama `resolve()` (`refino.js:~597`, `:512`) — **o Refino nunca mostra "conferindo"**. `operationStatus.update/fail/clear(owner,...)` (`live-bar.js:66-67`) é o selo de atividade já existente.

## Fluxos

### F1. Refino — gravar na Curva K (apply / restore) e desfazer
1. Estados (`refino.js:render`): ocioso → botão "Ver alterações" (só com evidência; `:621`) + "Refinar por minha conta" (se oferecido) + "Desfazer última gravação" (só se Kotlin der `secondary undo enabled`, `:622`); cartão de revisão (`#refinoReview`, `:472-487`) com "Gravar na Curva K"/"Gravar no Mapa K"/cancelar; lendo/gravando (`:603-606`, sem botões); falhou → só "Voltar ao refino"; fim da curva: volta a `idle` na hora (`:525-529`), **sem bloco de resultado e sem selo "Gravado"**.
2. Violações: R1 (o principal é "Ver alterações", não "Gravar correção"; some sem evidência, `refino-botoes-persistem.test.cjs:74` trava isso); R2 (existe cartão intermediário com 2 botões "Gravar"; Desfazer só depois e sem descrição); R3 (não aponta próxima mudança; sem "Nada mais para gravar agora"); R4 (4 botões `hidden` por estado); R5 (falha só tem "Voltar"; sem Tentar de novo); R7 (o ritual tem cartão + `confirm()` com 3 conferências, ok, mas o resultado não mostra selo/Última gravação).
3. Desfazer: lê `refinement.latest.undoPoints` do diário Kotlin (`refino.js:87-100`). Guarda antes→depois por ponto em `RefinementJournal` (arquivo via `AtomicJsonFile`, `RefinementJournal.kt:751`) → **sobrevive a reinício** (inferido pelo arquivo); some quando `undoable=false` (último registro já é um desfazer, `:699`). Só desfaz a ÚLTIMA; não há lista.
4. Testes que travam: `refino-botoes-persistem.test.cjs:51-74` (some sem proposta); `refino-screen.test.cjs:221-235` (barra com "Ver alterações" antes de Desfazer; `doesNotMatch /Revisar e gravar/`, sem `.disabled =`); `:105-129` (Desfazer some depois de usado); `:41` undoPoints; `refino-automatico-interruptor.test.cjs` (inferido).
5. Mudança mínima: trocar a barra por UM botão fixo "Gravar correção" (cinza + motivo quando não há proposta; ao tocar abre o antes→depois no próprio painel e um segundo toque grava, ou grava direto se R7 aceitar o cartão como "ver o que muda"); bloco fixo "Última gravação" (texto vindo de `latest`) com Desfazer cinza/ativo; trocar `.disabled` proibido em `refino-screen.test.cjs:235` por `aria-disabled` + classe; falha ganha "Tentar de novo / Deixar assim".
6. Risco: **alto** (753 linhas, 5 testes presos a texto/hidden).

### F2. Refino — gravar no Mapa K e desfazer (MapFix)
1. Estados (`core/map-fix.js`): idle → reading → ready → writing → done | failed; `undone`. UI: `done` mostra "Desfazer"+"Pronto" e esconde "Ver alterações" (`refino.js:612-613`); `undone` só "Pronto"; `failed` só "Entendi".
2. Violações: R1/R2/R4 (Desfazer+Pronto trocam o lugar do principal); R2 (a próxima gravação só é possível depois de tocar "Pronto", que apaga o Desfazer — **exatamente a dúvida do dono**); R6 (cabo solto vira `fail` "Releia o Mapa K", `map-fix.js:118`, mas não guarda o que faltou); R8.
3. Desfazer: `undoItems` (antes/depois por célula) só em `this.state` (memória, `map-fix.js:77`). Morre no "Pronto", em `start()` novo, ao trocar de tela (nova instância, inferido) e no reinício. Grava de volta com `reason` "Refino: desfazer correção no Mapa K" (conferido por ACK+readback `:93`).
4. Testes: `map-fix.test.cjs:45-53` (fases `done`/`undone`), `tropecos-para-mapa.test.cjs`, `refino-screen.test.cjs:221`.
5. Mínima: `MapFix` passa a manter `last = {items, undoItems, quando-contagem}` fora de `state.phase` (sobrevive a `dismiss`/nova gravação; nova gravação substitui); persistir via diário (I7) ou `localStorage` por ora (marcar como provisório); remover "Pronto"/"Entendi" (nada a "entender": bloco Última gravação já mostra).
6. Risco: médio.

### F3. Ajuste local (Mapa K manual) — `screens/map.js`, `map-editor.js`
1. Estados: sem seleção → botão `#mapReviewButton` desabilitado "Gravar na central"; com seleção sem mudança → idem; com mudança → "Gravar N alterações na central" (`:567-572`); gravando (`is-writing`); sucesso → `editor.reset()` + releitura (`:648-653`) — **seleção e valores somem, nenhum resultado fica na tela**; falha → só alerta/`operationStatus.fail`. "Concluir curva" (`#mapFinishCurve`) nasce `hidden` (`:368`); "Curva K → Mapa K" (`#mapFromCurve`) fica desabilitado durante a leitura (`:228`).
2. Violações: R2/R8 (não existe Desfazer nem "Última gravação" — um erro de digitação só se desfaz regravando à mão); R3 (sem próxima ação); R4 ("Concluir curva" some/aparece); R5 (falha sem "Tentar de novo"); texto do botão muda de forma mas estrutura é a mesma (aceitável: o contrato pede "Gravar N células").
3. Desfazer: não existe. O antes está em `editor.buildReview()` (items com `current`) — dá para guardar.
4. Testes: `mapa-k-ajuste-local.test.cjs`, `map-editor-flow.test.cjs`, `map-workflow-e2e.test.cjs`, `map-ecu-simulator-e2e.test.cjs`, `mapa-k-so-propria-operacao.test.cjs`, `porta-de-gravacao-no-toque.test.cjs` (inferido: travam o texto "Gravar … na central" e `reset()` após gravar). Rótulo "Gravar N alterações" → contrato diz "Gravar N células": decidir um só (sugestão: "células", cobrir no teste).
5. Mínima: antes de `writePrepared()` guardar `{items, undoItems}`; após `BATCH_CONFIRMED` renderizar "Última gravação (N células, ms/RPM)" + Desfazer reusando `MapFix.write('undo')` (mesma ponte `writeMap`); "Concluir curva" sempre presente, cinza com motivo.
6. Risco: médio.

### F4. Curva K → Mapa K (2 etapas) — `map.js:218-370`, `core/curve-to-map.js`, `core/balance-runner.js`
1. Estados: proposta nas células → Gravar → mapa gravado → etapa 2 (curva neutra, `runCurveStage`) → ok, ou etapa 2 pendente (`curvePlan.failed`) → "Concluir curva".
2. Violações: R6 **parcial**: o app sabe que falta a curva (`curvePlan`) mas só em memória (`this.curvePlan`); fechar o app perde o aviso e deixa a correção "em dobro" (o próprio texto `map.js:358-359` admite). Sem "Voltar à curva", sem Desfazer, sem "Última gravação". R4 (botão aparece/some).
3. Desfazer: nenhum. Backup da curva fica em arquivo (`startCurveBackup`, `curve.js:206`) mas só é usado pelo reset da curva, não por este fluxo (inferido).
4. Testes: `curva-k-para-mapa-k.test.cjs`, `curva-para-mapa-concluir-curva.test.cjs`, `curve-map-editor-coherence.test.cjs`.
5. Mínima: persistir `curvePlan` (mapa gravado + nós da curva) em armazenamento durável e, ao abrir/reconectar, mostrar "Mapa K gravado. Curva K ainda não → Concluir" (R6); botão fixo "Passar para o Mapa K" cinza quando "Curva já passada".
6. Risco: **alto** (grava duas coisas; envolve `curve-to-map.js` e `balance-runner.js`).

### F5. Ajuste global (Curva K) — `screens/curve.js`
1. Estados: sem pontos preparados → `#curveReviewButton` cinza "Gravar na central"; N pontos → "Gravar N pontos na central" (`:693-697`); **em restauração → desabilitado "Toque em Restaurar, no rodapé"** (`:690-692`); gravar com "Gravado na hora" (`:454`); Restaurar backup (`#curveBackupRestore`, texto muda em 3 estados `:183-184,237-238,412`); "Guardar backup"; "Curva K toda em 1,0" (reset em um toque após backup automático, `:205-231`).
2. Violações: R1/R4 (botão principal troca de função e aponta para outro botão); R2/R8 (Desfazer = restaurar backup, mas não há "Última gravação" nem backup automático antes de cada gravação — só antes do reset); R3; R5 (alerta sem passo seguinte, ex. `:568`).
3. Desfazer: arquivo de backup da curva (persistente; `api.curveBackups()` `:173`) — sobrevive a reinício. Só existe quando o dono "Guarda backup" ou reseta; **gravação comum da curva não gera backup** (inferido por ausência de chamada de `startCurveBackup` fora de `:195,206`).
4. Testes: `curva-k-arrasto.test.cjs`, `curva-k-reset-um-toque.test.cjs`, `curva-k-releitura-e-rodape.test.cjs`, `curva-k-suavizacao.test.cjs`, `gravacao-conferencia-segundo-plano.test.cjs` (travam textos de botão e o rodapé; sem linha verificada).
5. Mínima: guardar `{antes,depois}` dos nós ao gravar (a revisão já tem `currentFactor→targetFactor`, `:683-690`), mostrar "Última gravação" + Desfazer que usa o mesmo `writeCurve`; "Restaurar" vira item do Histórico (R8), o botão principal nunca é desabilitado por restauração.
6. Risco: médio-alto.

### F6. AutoCal — apagar pontos / reaprender — `screens/autocal-cockpit.js`, `core/ponto-limpeza.js`, `core/low-rpm-filter.js`
1. Estados: botão por zona (`requestZoneReacquisition`, `:2061`) limpa em 1 toque, fila + conferência ("N pontos limpos", `ponto-limpeza.js:157`); inspetor de ponto: sem marca → "Marcar mais pontos"+"Reaprender este ponto"; com marcas → "Desmarcar"+"Reaprender N marcados"+"Limpar" (`:1993-2007`); "Reaprender GNV/gasolina (apaga o atual)" (`:756-757`); "AutoMatch manual" nasce `hidden` (`:755,1257`); menu "Ações avançadas" (`:763-770`).
2. Violações: R4 (botão único troca por lote; manual some); R2 (apagar não tem Desfazer — **por contrato é aceito**: só a matriz tem toque que apaga; mas deve dizer "não dá para desfazer"); naming: "Limpar" no lote (`:1998`) significa "limpar a seleção", mas "limpar" noutros lugares = apagar pontos — confunde; R5 (`details`/popover fecha por clicar de novo? inferido); R7 (limpeza sem antes→depois; usa `operationStatus`, sem selo "Gravado e conferido"); **invariante**: `RESET_ALL` exposto como "Recomeçar tudo" (`:768`).
3. Desfazer: nenhum. Fila de limpeza só em memória (`ponto-limpeza.js` `queue`); cabo solto no meio: tenta de novo uma vez (`retried`) e conta `LOST_LIMIT=3` (`:15`).
4. Testes: `limpar-pontos.test.cjs`, `autocal-botao-por-zona.test.cjs`, `filtro-rpm-baixo.test.cjs`, `autocal-um-toque.test.cjs`, `autocal-reacquisition-ux.test.cjs:34` (exige "Recomeçar tudo"), `autocal-cockpit.test.cjs:50`, `autocal-consumer-graph.test.cjs:53`, `autocal-didactic-cockpit.test.cjs:240`, `textos-leigo.test.cjs:91`.
5. Mínima: manter "Reaprender N marcados" sempre presente (texto "Reaprender 0 marcados" cinza com motivo) em vez de trocar de botão; renomear lote "Limpar" → "Desmarcar todos"; texto "não dá para desfazer" nas ações; **decisão do dono** sobre `RESET_ALL`: fica ou sai (trocar teste junto, RED primeiro). Não tocar `AutoCalNativeActionManager`.
6. Risco: **alto** (2.251 linhas; muitos testes de texto). Dividir: só o inspetor e os rótulos agora.

### F7. Ferramentas — cartões (interruptores, anti-apagão 5 valores, volta à gasolina) — `components/advanced.js`, `core/advanced-flags.js`, `core/anti-apagao-valores.js`, `core/volta-gasolina.js`
1. Estados: interruptor (`switchCardHtml :385-394`) com "Desfazer" só se `fv.undo.available && fase∈{done,idle}`; Anti-apagão: − / + gravam na hora, "Restaurar fábrica" (desabilitado se já é fábrica), "Desfazer" só com `undo.available` (`:525`), "Como funciona"; Volta: "Desfazer" só com `st.undo.available && !busy` (`:700`).
2. Violações: R4 (Desfazer aparece/some, desloca o layout); R2 está OK (Desfazer não toma o lugar do interruptor); R3/R7 ok para o toque único (`anti-apagao-toque-real.test.cjs` garante sem janela); R5: resultado "parcial" diz "Toque em Desfazer" (`anti-apagao-valores.js:151`) — correto; "incerto" precisa de saída (inferido).
3. Desfazer: Kotlin guarda o "antes" **em memória por sessão da central** (`AntiStalloFlagWriter.kt:41-52`, `AntiStalloParamsWriter.kt:45-56`, `VoltaGasolinaWriter.kt:38-46`: `undoSession == session`). Some ao reconectar/reiniciar o app. Não é o histórico do contrato (R8).
4. Testes que mudam: `ferramentas-grupos.test.cjs:140,152` (esperam `count()==1` só quando há; passar a esperar sempre 1, desabilitado), `ferramentas-contrato-coerencia.test.cjs:136`, `anti-apagao-valores-didatico.test.cjs:124`, `e2e-avancado-logica.test.cjs:48,117` (lógica; não muda), `ferramentas-sem-rolagem.test.cjs` (altura fixa ajuda).
5. Mínima: render sempre o `<button data-adv-undo-*>` com `disabled`+`aria-disabled` e `title` do motivo ("Nada para desfazer nesta conexão"); mesma coisa para `tile-undo`. Manter as caixas atuais.
6. Risco: baixo.

### F8. Barra do topo — `components/live-bar.js`, `core/write-verify.js`
Selo `#verify` usa `node.hidden = idle` (`live-bar.js:118`): aparece/some (R4 — aqui aceitável se o espaço for reservado). Não há selo "Gravado e conferido": só "conferindo/corrigindo/não confere"; após OK volta a vazio. Testes: `gravacao-conferencia-segundo-plano.test.cjs`, `global-live-bar.test.cjs`, `eventos-sem-tempo.test.cjs`. Mínima (I1/I0): manter o nó sempre ocupando lugar; adicionar fase `ok` curta ("Gravado e conferido") ligada por evento, não por tempo (R9). Risco: baixo-médio.

## Quadro rápido
| Fluxo | Principal fixo | Última gravação | Desfazer persiste | Recupera interrupção |
|---|---|---|---|---|
| Refino curva | não | não | sim (diário) | parcial |
| Refino Mapa K | não | não | não | não |
| Ajuste local | quase | não | não existe | não |
| Curva→Mapa K | não | não | não existe | só memória |
| Ajuste global | não | não | só backup manual | não |
| AutoCal | quase | n/a | n/a | refaz 1 vez |
| Ferramentas | sim | não | só sessão da central | n/a |

## (a) Ordem recomendada
1. **I0** núcleo (`fluxo.js`, `acao-principal.js`, teste do contrato) — RED: teste varre telas e hoje falha.
2. **I5** Ferramentas (baixo risco, valida a API do I0).
3. **I1** barra/selo (usa a mesma API de atividade).
4. **I4** Ajuste local → Ajuste global → Curva→Mapa K (nessa ordem; o último só após persistência do plano).
5. **I3** Refino (maior queixa, mas depende de `last` do MapFix e do I0 estável).
6. **I2** AutoCal por último e em fatias (inspetor/rótulos primeiro; `RESET_ALL` só com decisão do dono).
Persistência do Desfazer entre reinícios (Mapa K, Ajuste local/global) depende do Diário (I7): até lá, guardar em memória e dizer na tela "vale até fechar o app".

## (b) Arquivos por unidade (sem sobreposição)
- **I0:** `ui/core/fluxo.js` (novo), `ui/components/acao-principal.js` (novo), `ui/core/write-gate.js`, `ui/core/write-verify.js`, `ui/styles-fluxo.css` (novo, ou bloco em arquivo próprio), `tests/ui/contrato-de-fluxo.test.cjs` (novo), registro de scripts em `ui/index.html` e `ui/app.js` (só as linhas `<script>`).
- **I1:** `ui/components/live-bar.js`, `tests/ui/global-live-bar.test.cjs`, `tests/ui/gravacao-conferencia-segundo-plano.test.cjs`.
- **I2:** `ui/screens/autocal-cockpit.js`, `ui/screens/autocal-original.js`, `ui/core/ponto-limpeza.js`, `ui/core/low-rpm-filter.js`, `ui/styles-autocal-cockpit.css`, testes `limpar-pontos`, `autocal-botao-por-zona`, `filtro-rpm-baixo`, `autocal-um-toque`, `autocal-reacquisition-ux`, `autocal-cockpit`, `autocal-didactic-cockpit`, `autocal-consumer-graph`, `textos-leigo` (só as linhas afetadas).
- **I3:** `ui/screens/refino.js`, `ui/core/map-fix.js`, `ui/core/stall-region.js`, testes `refino-botoes-persistem`, `refino-screen`, `refino-automatico-interruptor`, `map-fix`, `tropecos-para-mapa`, `refino-mesmo-grafico`.
- **I4:** `ui/screens/map.js`, `ui/map-editor.js`, `ui/screens/curve.js`, `ui/core/curve-to-map.js`, `ui/core/balance-runner.js`, `ui/index.html` (só blocos `#map*` e `#curve*`; combinar com I0 que só toca `<script>`), testes `mapa-k-*`, `map-*`, `curva-k-*`, `curva-para-mapa-concluir-curva`, `curve-map-editor-coherence`, `porta-de-gravacao-no-toque`.
- **I5:** `ui/components/advanced.js`, `ui/components/drawers.js`, `ui/core/ferramentas-grupos.js`, `ui/core/anti-apagao-valores.js`, `ui/core/volta-gasolina.js`, `ui/core/advanced-flags.js`, `ui/styles-ux-leigo.css` (`.tile-undo`), testes `ferramentas-*`, `anti-apagao-*`, `avancado-*`, `e2e-avancado-logica`.
Risco de choque: `ui/index.html` (I0 e I4) e `tests/ui/textos-leigo.test.cjs` (I2 e I5): cada um edita só o seu trecho; integração serializada pelo Claude principal.

## (c) API mínima do I0 (`OmegasFluxo`, UMD como `write-gate.js`)
- `OmegasFluxo.mountPrimary(host, {id, base:'Gravar correção'})` → `{ set({count?, enabled, reason, busy, onTap}), el }`. Um `<button>` fixo no fim do painel; nunca `hidden`; desabilitado = `aria-disabled="true"` + `reason` dentro do botão (texto "Gravar correção · Escolha um trecho"); `busy` troca só o texto ("Gravando…").
- `OmegasFluxo.mountLast(host, {id})` → `{ record({what, count, undo:{run(), label}, by:'você|Refino|AutoCal'}), clear(), view() }`. Bloco fixo "Última gravação" (vazio: "Nenhuma gravação ainda") com botão Desfazer sempre presente (cinza se `undo` ausente/`undoable=false`). `record()` substitui a anterior e empurra a velha para `history` (lista em memória, ponto de entrada do Diário I7).
- `OmegasFluxo.history()` → lista `{id, by, what, at:contagem, undo}`; `OmegasFluxo.undoById(id)` (R8, Histórico em Mais).
- `OmegasFluxo.run({id, owner, check, write, verify})`: orquestra o ritual R7: `WriteGate.check(autopilot)` → se bloqueado, `set({enabled:false, reason})`; `write()` devolve Promise/evento; ao BATCH_CONFIRMED chama `app.writeVerify.begin()` + `operationStatus.clear(owner)`, grava `record(...)` e emite selo `ok`; em falha entrega `{retry, leave, details}` (R5).
- Selo: `OmegasApp.operationStatus.update/fail/clear` (já existe) + novo `OmegasApp.operationStatus.done(owner, label)` que mostra "Gravado e conferido" até o próximo evento da ECU (nunca por tempo, R9); `OmegasWriteVerify.view()` ganha fase `ok`.
- `OmegasFluxo.pending.save/get/clear(key, payload)` para R6 (plano em etapas); backend `localStorage` com try/catch hoje, trocado pelo Diário no I7.
- Contrato do teste: `OmegasFluxo.registry()` lista botões principais registrados; `contrato-de-fluxo.test.cjs` percorre estados antes/gravando/gravado/desfeito e compara `getBoundingClientRect` e `hidden`.
- Regra de adoção: cada tela chama `mountPrimary`+`mountLast` e entrega callbacks (`write`, `undo`); sem reescrever leituras, planos nem ponte.
