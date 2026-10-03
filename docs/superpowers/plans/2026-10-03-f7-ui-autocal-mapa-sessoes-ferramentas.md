# Fatia 7 — UI AutoCal + Mapa K + Sessões + Ferramentas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar as abas 02 Mapa K, 04 AutoCal (`aquisicao · referencia · epocas`), 06 Sessões (`evolucao · lista`) e 07 Ferramentas sobre a ponte única `Omegas`, mais a faixa de status de uma linha e os estados de tela "Sem cabo"/"Conectando", com todo botão = um toque = um `Omegas.request`.

**Architecture:** Cada tela é uma classe com `constructor(host)`, `enter(context)` e `render(snapshot)`; desenha por `innerHTML` e trata toques por **delegação** num único componente novo, `IntentAction`, que chama `Omegas.request` uma vez, trava o botão até o recibo terminar e abre `OperationOverlay` nas mutações. O `autocal-cockpit.js` (1900 linhas) vira `screens/autocal/{model,aquisicao,referencia,epocas,index}.js`; `drawers.js` vira `screens/tools.js`; a lista de sessões muda para a nova `screens/sessions.js`. Tudo o que a F7 lê do snapshot está fixado num contrato de consumo (Task 7.1) e num fixture único de estados, usado igual pelos testes node e pelo androidTest.

**Tech Stack:** WebView + JS vanilla (`node --test`, `vm`), Kotlin/JUnit (paridade da prévia do Mapa K), androidTest (render 1280×720 com print), contratos Python, GitHub Actions via `tools/ci/remote-test.sh`.

**Spec:** docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md  ·  **Índice/contrato:** docs/superpowers/plans/2026-10-03-00-norte-unico-index.md

Branch: `work/platina-f7-autocal-mapa-sessoes-ferramentas` (base `OmegasPlatina` com F1–F6 mescladas). Todo commit termina com as duas linhas:

```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01YN94YqPpeRPSaeNkh2QkF7
```

> Linhas citadas em "Modify:" são da `OmegasPlatina` antes da F1. F1–F6 podem tê-las deslocado: localize sempre pelo **símbolo** citado entre parênteses, nunca só pelo número.


> **Antes de começar:** leia a seção **Reconciliação entre planos** do índice (`2026-10-03-00-norte-unico-index.md`). Onde este plano divergir dela (intents, payloads, formato do snapshot, comando do emulador), vale o índice.
## Global Constraints

Todas as do índice, mais:

- A F7 não toca Kotlin de produção. Se o `StateStore` da F5 não publicar o contrato de consumo da Task 7.1, a F7 **para** e escala ao dono (não implementa trabalho da F5 aqui).
- Telas da F7 não chamam `OmegasNative`, `OmegasV7`, `OmegasAutoCal`, `OmegasPower`, `OmegasCalibration`, `ns.AutoCalApi`, `scheduler.addHook` nem `setInterval`. Só `Omegas` (via `IntentAction.deps()`), `OperationOverlay`, `SubpageTabs`, `store`, `router`.
- Toque é tratado só por delegação no `host` (`[data-intent]`, `[data-select]`, `[data-nav]`): nenhuma tela faz `querySelector(...).addEventListener` por botão. É o que permite testar "um toque → um request" em node.
- Nenhum `confirm(`, nenhum overlay de revisão, nenhum "Tem certeza", nenhum "Executar agora"/"Cancelar" antes da ECU.
- Exceção de alvo declarada: as 144 células e os 24 cabeçalhos do Mapa K são matriz densa (12 × 12 em 720 px) com alvo ≥ 44 px; seleção grossa por linha/coluna/arraste. Todo o resto ≥ 76 px.
- Texto crítico é marcado `data-critical` e tem ≥ 24 px; o teste de render mede todo `[data-critical]` visível.
- Sem cor nova: CSS novo usa só `var(--…)` já existentes (tokens chegam na F8).
- Sessões e Ferramentas funcionam sem cabo (revisar, exportar, log): ali "Sem cabo"/"Conectando" é faixa compacta, não tela cheia.

## Review Focus

1. **Duplo toque em "Zerar gás" com o recibo ainda em `RECEBIDO`** → exatamente 1 `Omegas.request`; o 2º toque não faz nada (Task 7.1 `duplo toque com recibo pendente`, Task 7.4).
2. **"Reaprender 2 selecionados" termina em `FALHOU`** → a seleção da matriz continua marcada; em `CONCLUIDO` limpa; troca de sessão USB limpa (Task 7.3 `selectionAfterOperation`, Task 7.4).
3. **Cabo cai com o dono em Ferramentas/Sessões** → nada cobre a tela (faixa compacta); em AutoCal/Mapa K a tela cheia "Sem cabo" cobre só `.screen-host`, e ao voltar `CONECTADO` a subpágina e a seleção do Mapa estão intactas (Task 7.2 e Task 7.8).
4. **Apagar `autocal-cockpit.js` derruba o estilo do Refino** (o cockpit injetava `styles-autocal-cockpit.css` e há 52 seletores `.refino*` nele) → o CSS passa a vir por `<link>` no `index.html` e nenhum seletor `.refino*` some (Tasks 7.6/7.7).
5. **Mapa K relido automaticamente com seleção pendente** (`now.mapK.readAtMs` muda sem `MAP_WRITE`) → seleção e prévia preservadas; após `MAP_WRITE` `CONCLUIDO` → seleção limpa e a grade mostra os valores relidos (Task 7.8).

---

## Contrato de consumo do snapshot (exigência sobre o `StateStore` da F5)

Nomes de seção são os do índice. Dentro delas, a F7 lê **exatamente** isto (campos extras são ignorados):

```ts
connection: { state: 'SEM_CABO' | 'CONECTANDO' | 'CONECTADO', detail: string | null }
live:       { valid: boolean, ageMs: number | null, petrolMs: number | null, mapBar: number | null,
              rpm: number | null, fuel: 'GASOLINA' | 'GNV' | 'TRANSICAO' | 'CUTOFF' | 'DESLIGADO' | null }
now:        { mapK: MapKState, cell: { row: number, column: number } | null }
MapKState = { state: 'NAO_LIDO' | 'LENDO' | 'PRONTO' | 'FALHOU', readAtMs: number | null, error: string | null,
              rows: number[12][12], extraRow: number[], axes: { rpmBins: number[12], petrolBins: number[12] },
              hash: string, writableCells: number }        // = saída de TelemetryForegroundService.readKMap() + state/readAtMs
                                                         // lido sozinho ao conectar e depois de todo MAP_WRITE (readback)
reference:  { frozen: Reference | null,                  // tipo Reference da F4 (id, frozenAt, ecuAcquisitionFingerprint, points[RefPoint])
              ecuCurrent: RefPoint[],                    // gasolina que a ECU tem agora
              ecuDrift: number | null,                   // max |Δ petrolMs| / petrolMs entre frozen e ecuCurrent (fração)
              ecuChangedSinceFreeze: boolean, maturePoints: number, canFreeze: boolean, provisional: boolean }
operation:  { receiptId: string, intent: string /* Intent */, stage: string /* OpStage */, text: string,
              failureKind: 'TRANSPORTE' | 'ECU' | 'APP' | null, startedAtMs: number } | null
session:    { current: { id: string, recording: boolean, durationMs: number, megabytes: number | null,
                         limitMb: number | null, events: number | null, droppedEvents: number } | null,
              history: SessionHistoryItem[] }             // mais recente primeiro, até 50
SessionHistoryItem = {
  id: string, startedAtMs: number, durationMs: number, active: boolean, bytes: number,
  drivingMs: number | null,                              // tempo com RPM ≥ 1000
  stalls: { apagou: number, quaseApagou: number, before: number, during: number, after: number },
                                                         // before/during/after = antes do 1º recibo CONCLUIDO de ECU,
                                                         // dentro da janela [início, fim] de algum recibo, depois do último
  index: { start: number | null, end: number | null, coverage: number },   // fração 0..1 (F4 §1.3)
  fuel: { petrolTicks: number, cngTicks: number },
  phases: { atMs: number, phase: string, headline: string }[],
  receipts: { receiptId: string, intent: string, atMs: number, stage: 'CONCLUIDO' | 'FALHOU', text: string }[],
  autocal: { petrolZones: number | null, gasZones: number | null, correlatedRegions: number | null,
             calibrationEpochs: number, autoMatchExecuted: number | null },
  resumoMarkdown: string | null }                         // RESUMO.md da sessão (SessionResumo.markdown())
autocal:    { enabled: 0 | 1 | null, projection: object }  // projection = JSON de AutoCalUiProjection como hoje (getUiProjection), sem mudança
settings:   { retention: { telemetryEveryMs: number, maxSessionMb: number, keepSessions: number, captureRawUsb: boolean },
              overlay: { state: 'on' | 'off' | 'needs-permission' | 'unsupported', scale: number },
              battery: { supported: boolean, ignoringOptimizations: boolean },
              gps: { enabled: boolean, permission: boolean },
              diagnostics: { serviceRunning: boolean, engineRunning: boolean, engineStuck: boolean, usbConnected: boolean,
                             telemetryAgeMs: number | null,
                             logs: { time: string, level: string, category: string, message: string }[],   // últimos 200
                             selfTest: { ok: boolean, atMs: number, error: string | null } | null } }
```

**Pedidos sem intent próprio no índice** (ações de sistema de Ferramentas): a F7 usa intents existentes com payload fixo; o handler da F5 tem de aceitá-los.

| Botão | `Omegas.request` |
|---|---|
| Autorizar balão / Ativar | `{ intent:'OVERLAY_TOGGLE', payload:{ enabled:true } }` (handler pede a permissão se faltar) |
| Desativar balão | `{ intent:'OVERLAY_TOGGLE', payload:{ enabled:false } }` |
| Tamanho do balão | `{ intent:'SETTINGS_SET', payload:{ key:'overlay.scale', value: 1 \| 1.25 \| 1.6 } }` |
| Permitir bateria | `{ intent:'SETTINGS_SET', payload:{ key:'power.batteryExemption', value:true } }` |
| Ligar/Desligar GPS | `{ intent:'SETTINGS_SET', payload:{ key:'gps.enabled', value:true\|false } }` |
| Aplicar retenção | `{ intent:'SETTINGS_SET', payload:{ key:'session.retention', value:{…retention} } }` |
| Executar autoteste | `{ intent:'SETTINGS_SET', payload:{ key:'diagnostics.selfTest', value:true } }` |
| Exportar sessão | `{ intent:'SESSION_EXPORT', payload:{ sessionId } }` |
| Exportar logs | `{ intent:'SESSION_EXPORT', payload:{ scope:'logs' } }` |
| Exportar backup completo | `{ intent:'SESSION_EXPORT', payload:{ scope:'app-data' } }` (`DataArchiveManager.exportData`, verificado vivo) |

AutoCal: `AUTOCAL_PAUSE`/`AUTOCAL_RESUME`/`AUTOCAL_RESET_GAS`/`AUTOCAL_RESET_PETROL`/`REFERENCE_FREEZE` com `payload:{}`; `AUTOCAL_RELEARN` com `payload:{ points:[{ fuel:'PETROL'|'GAS', index:0..17 }] }`; Mapa: `MAP_WRITE` com `payload:{ cells:[{ row, column, current, target }] }`.

---

### Task 7.1: Fundação — fixture de estados, harness de teste, `IntentAction`, pré-condição do contrato

**Files:**
- Create: `app/src/main/assets/ui/components/intent-action.js`
- Create: `tests/fixtures/f7-snapshot-states-v1.json` (também vira asset do androidTest via `app/build.gradle.kts:119-123`)
- Create: `tests/ui/support/f7-harness.cjs`
- Test: `tests/ui/f7-foundation.test.cjs`, `tests/test_f7_snapshot_consumer_contract.py`

**Interfaces:**
- Consumes: `Omegas.request({intent,payload}) → receiptId|null` (F5), `OperationOverlay.open(receiptId)` (F6).
- Produces:

```js
// OmegasUi.IntentAction
IntentAction.deps() → { omegas, overlay, tabs }      // ÚNICO lugar que resolve onde F5/F6 expõem Omegas/OperationOverlay/SubpageTabs
IntentAction.OVERLAY_INTENTS = ['CURVE_WRITE','CURVE_RESET','CURVE_RESTORE','MAP_WRITE','REFERENCE_FREEZE','AUTOCAL_RELEARN',
  'AUTOCAL_PAUSE','AUTOCAL_RESUME','AUTOCAL_RESET_GAS','AUTOCAL_RESET_PETROL','UNDO']
IntentAction.HUMAN: { CURVE_WRITE:'Aplicar ajuste', CURVE_RESET:'Resetar Curva K', CURVE_RESTORE:'Restaurar backup',
  MAP_WRITE:'Gravar Mapa K', REFERENCE_FREEZE:'Congelar referência', AUTOCAL_RELEARN:'Reaprender pontos',
  AUTOCAL_PAUSE:'Pausar aquisição', AUTOCAL_RESUME:'Retomar aquisição', AUTOCAL_RESET_GAS:'Zerar gás',
  AUTOCAL_RESET_PETROL:'Zerar gasolina', SESSION_EXPORT:'Exportar', OVERLAY_TOGGLE:'Balão flutuante',
  SETTINGS_SET:'Ajuste do app', UNDO:'Desfazer' }
IntentAction.STAGE: { RECEBIDO:'Recebido', PREPARANDO:'Tirando a foto', EXECUTANDO:'Executando',
  CONFERINDO:'Conferindo na ECU', CONCLUIDO:'Concluído', FALHOU:'Falhou' }
IntentAction.send(intent: string, payload?: object, button?: Element) → string | null
IntentAction.bind(host: Element) → void               // click delegado em [data-intent]; payload = JSON.parse(data-payload || '{}')
IntentAction.inFlight(operation) → boolean            // operation && stage ∉ {CONCLUIDO, FALHOU}
IntentAction.release(host: Element, operation) → void // tira aria-busy dos botões cujo data-receipt terminou
// tests/ui/support/f7-harness.cjs
load(files: string[], opts: { snapshot, store? }) → { window, requests, overlays, navigations, store, host(), button(attrs), tap(host, el), focus(host, el) }
```

- [ ] **Step 0: Fixar onde F5/F6 expõem as dependências**

Run: `grep -n "Omegas\b\|OperationOverlay\|SubpageTabs" app/src/main/assets/ui/core/omegas.js app/src/main/assets/ui/components/operation-overlay.js app/src/main/assets/ui/components/subpage-tabs.js | grep -n "root\.\|window\.\|ns\." `
Expected: a linha de export de cada um. `IntentAction.deps()` devolve exatamente esses objetos; nenhum outro arquivo da F7 os resolve. Confirme também que `Omegas.snapshot`/`Omegas.request` são propriedades graváveis de um objeto JS (o androidTest da Task 7.11 as substitui): `grep -n "Object.freeze\|defineProperty" app/src/main/assets/ui/core/omegas.js` → vazio.

- [ ] **Step 1: Write the failing tests**

`tests/test_f7_snapshot_consumer_contract.py` (pré-condição da F5):

```python
import pathlib
root = pathlib.Path(__file__).resolve().parents[1]
src = "\n".join(p.read_text("utf-8") for p in (root / "app/src/main/java/com/omegas/prohub/state").rglob("*.kt"))
for key in ('"SEM_CABO"', '"CONECTANDO"', '"CONECTADO"', '"mapK"', '"readAtMs"', '"ecuCurrent"', '"ecuDrift"',
            '"ecuChangedSinceFreeze"', '"canFreeze"', '"maturePoints"', '"receiptId"', '"failureKind"', '"history"',
            '"drivingMs"', '"quaseApagou"', '"before"', '"during"', '"after"', '"receipts"', '"resumoMarkdown"',
            '"projection"', '"retention"', '"overlay"', '"battery"', '"gps"', '"diagnostics"', '"selfTest"', '"logs"'):
    assert key in src, f"StateStore (F5) não publica {key}: contrato de consumo da F7"
for k in ('"overlay.scale"', '"power.batteryExemption"', '"gps.enabled"', '"session.retention"', '"diagnostics.selfTest"', '"app-data"', '"logs"'):
    assert k in src, f"handler de intent (F5) não aceita {k}"
print("F7_SNAPSHOT_CONSUMER_CONTRACT=PASS")
```

`tests/ui/f7-foundation.test.cjs`:

```js
const { load } = require('./support/f7-harness.cjs');
const states = require('../fixtures/f7-snapshot-states-v1.json');
const STATES = ['semCabo','conectando','ecuSemAquisicao','semReferencia','ecuReaprendeu','aprendendo','normal',
  'executando','falhaTransporte','falhaEcu','vazio','dadosLongos'];
const SECTIONS = ['revision','connection','live','now','reference','points','index','nextAction','operation','session','autocal','settings'];

test('fixture tem os 12 estados de spec §3.2, cada um com as 11 seções + revision', () => {
  assert.deepEqual(Object.keys(states.states), STATES);
  for (const s of STATES) assert.deepEqual(Object.keys(states.states[s]).sort(), [...SECTIONS].sort());
  assert.deepEqual(Object.keys(states.matrix), ['autocal','map','sessions','tools','global']);
});
test('um toque → exatamente um Omegas.request com intent e payload do botão', () => {
  const h = load(['components/intent-action.js'], { snapshot: states.states.normal });
  const host = h.host(); h.window.OmegasUi.IntentAction.bind(host);
  const b = h.button({ 'data-intent': 'AUTOCAL_RESET_GAS', 'data-payload': '{}' });
  h.tap(host, b);
  assert.deepEqual(h.requests, [{ intent: 'AUTOCAL_RESET_GAS', payload: {} }]);
  assert.deepEqual(h.overlays, ['r-1']);
});
test('duplo toque com recibo pendente → um request', () => {
  /* tap duas vezes no mesmo botão */ assert.equal(h.requests.length, 1);
  assert.equal(b.dataset.receipt, 'r-1'); assert.equal(b.getAttribute('aria-busy'), 'true');
});
test('intents verdes não abrem overlay', () => {
  for (const intent of ['SESSION_EXPORT','SETTINGS_SET','OVERLAY_TOGGLE']) /* tap */;
  assert.equal(h.requests.length, 3); assert.deepEqual(h.overlays, []);
});
test('ponte recusa (request → null): sem overlay, botão aceita novo toque, sem exceção', () => {
  /* harness com request que devolve null */ assert.equal(IntentAction.send('AUTOCAL_PAUSE', {}, b), null);
  assert.deepEqual(h.overlays, []); assert.equal(b.getAttribute('aria-busy'), null);
});
test('release solta o botão quando o recibo termina', () => {
  IntentAction.release(host, { receiptId: 'r-1', stage: 'CONCLUIDO' }); assert.equal(b.getAttribute('aria-busy'), null);
  assert.equal(IntentAction.inFlight({ receiptId: 'r-2', stage: 'CONFERINDO' }), true);
  assert.equal(IntentAction.inFlight(null), false);
});
test('nomes humanos', () => {
  assert.equal(IntentAction.STAGE.CONFERINDO, 'Conferindo na ECU');
  assert.equal(IntentAction.HUMAN.AUTOCAL_RESET_GAS, 'Zerar gás');
  assert.equal(IntentAction.HUMAN.AUTOCAL_RESET_PETROL, 'Zerar gasolina');
});
```

Valores fixados no fixture (os testes das próximas tasks dependem deles):
- `normal.live = { valid:true, ageMs:120, petrolMs:6.24, mapBar:0.62, rpm:2150, fuel:'GNV' }`; `normal.connection.state='CONECTADO'`.
- `normal.autocal.enabled=1`; `projection.ok=true`, `projection.referenceUsable=true`, `projection.snapshot.fields` (todos `status:'VALID'`): `CALIBRATION_VAL_1.rawValues=[0,0,6,0,0,4,0,0,6]`, `VECT_AUTOCAL_U8_1.rawValues=[4]`, `NUM_BUF_UPD_PETR.rawValues=[4,4,4,4,4,4,6,6,6,6,6,6,0,0,0,0,0,0]`, `NUM_BUF_UPD_GAS.rawValues=[4,4,4,4,4,4,3,3,3,0,0,0,0,0,0,0,0,0]`, `PETR_INJ_TBUF(_GAS).physicalValues[i]=2.0+0.5*i`, `MNFLD_PRESS_BUF(_GAS).physicalValues[i]=0.25+0.04*i`, `MNFLD_PRESS_THD.physicalValues[i]=0.20+0.05*i` (18, crescente), `AUTO_CAL_ENABLE.rawValues=[1]`, mais `PETR_INJ_TBP`/`PETR_MNFLD_PRESS_RV`/`GAS_MNFLD_PRESS_RV` com 30 valores.
- `ecuSemAquisicao`: `NUM_BUF_UPD_*` todos 0, `enabled=0`, `reference.frozen=null`, `canFreeze=false`.
- `semReferencia`: `reference.frozen=null, provisional=true, canFreeze=true, maturePoints=14`.
- `ecuReaprendeu`: `frozen.frozenAt=1790000000000`, `ecuDrift=0.032`, `ecuChangedSinceFreeze=true`, `maturePoints=14`. `normal.reference`: `ecuDrift=0.004`, `ecuChangedSinceFreeze=false`, `maturePoints=14`.
- `executando.operation = { receiptId:'r-9', intent:'AUTOCAL_RESET_GAS', stage:'EXECUTANDO', text:'Zerando gás', failureKind:null }`; `falhaTransporte.operation` = idem com `stage:'FALHOU', failureKind:'TRANSPORTE', text:'O cabo desconectou durante a operação'`; `falhaEcu` = `failureKind:'ECU', text:'A ECU não confirmou na releitura'`, `now.mapK.state='FALHOU', error:'A ECU não respondeu à leitura do Mapa K'`.
- `normal.now.mapK.rows[0][0]=120`; `normal.session.history` = 3 itens `s-1..s-3`; `s-2 = { startedAtMs: Date.UTC(2026,9,3,14,5), durationMs: 2520000, drivingMs: 5400000, stalls:{apagou:1, quaseApagou:3, before:0, during:1, after:0}, index:{start:0.62,end:0.71,coverage:21}, receipts:[{receiptId:'r-5', intent:'CURVE_WRITE', stage:'CONCLUIDO', text:'Concluído', atMs:…}], autocal:{petrolZones:4,gasZones:3,correlatedRegions:5,calibrationEpochs:2,autoMatchExecuted:1}, resumoMarkdown:'# Resumo da sessão s-2\n…' }`.
- `vazio.session.history=[]`, `vazio.settings.diagnostics.logs=[]`; `dadosLongos.session.history` = 50 itens, `dadosLongos.settings.diagnostics.logs` = 200 linhas com mensagens de 180 caracteres.
- `aprendendo.session.current.recording=true` e `history[0].active=true`.
- `matrix` = estados por aba (Task 7.11): `autocal`: todos os 12; `map`: `semCabo, conectando, vazio, normal, executando, falhaTransporte, falhaEcu, dadosLongos`; `sessions`: `semCabo, conectando, vazio, aprendendo, normal, executando, falhaTransporte, dadosLongos`; `tools`: `semCabo, conectando, vazio, normal, executando, falhaTransporte, dadosLongos`; `global`: `semCabo, conectando, normal, executando, falhaTransporte, falhaEcu`. `expect.<aba>.<estado>` = um texto que tem de aparecer na tela (preenchido nas Tasks 7.2–7.10 junto com cada tela).

- [ ] **Step 2: Run to see it fail**

Run: `git push -u origin work/platina-f7-autocal-mapa-sessoes-ferramentas && gh pr create --draft --base OmegasPlatina --title "F7: UI AutoCal + Mapa K + Sessões + Ferramentas" --body "WIP"` e depois `tools/ci/remote-test.sh node tests/ui/f7-foundation.test.cjs`
Expected: `REMOTE_TEST=FAIL` com `Cannot find module './support/f7-harness.cjs'`.
Run: `tools/ci/remote-test.sh python tests/test_f7_snapshot_consumer_contract.py`
Expected: `REMOTE_TEST=PASS` (F5 entregou). Se `FAIL`: **parar a fatia** e levar ao dono a mensagem do assert (é falta da F5, não da F7).

- [ ] **Step 3: Implement**

`intent-action.js`: IIFE padrão do repo (`(function (root) { … })(typeof window !== 'undefined' ? window : globalThis)`), exporta em `root.OmegasUi.IntentAction`. `send`: ignora se `button?.getAttribute('aria-busy') === 'true'`; chama `deps().omegas.request({ intent, payload })` dentro de `try` (exceção ⇒ `null`); com recibo: `button.dataset.receipt = id`, `aria-busy="true"`, e `deps().overlay.open(id)` se `OVERLAY_INTENTS.includes(intent)`. Harness: `vm.runInNewContext` dos arquivos pedidos com `window` falso; `Omegas.request` registra e devolve `'r-' + n`; `OperationOverlay.open` registra em `overlays`; `SubpageTabs.mount(host, opts)` registra e chama `opts.onChange(store.get()['subpage.' + opts.route] || opts.items[0].id)`; `router.navigate(route, ctx)` registra em `navigations`; `host()` guarda `innerHTML` e listeners; `button(attrs)` devolve elemento falso com `dataset`, `getAttribute/setAttribute/removeAttribute`, `closest(sel)` (casa `[data-x]`); `tap` despacha `click` com esse `target`.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/f7-foundation.test.cjs`
Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `test(ui): fundação F7 — fixture de estados, harness e IntentAction (um toque = um request)`

---

### Task 7.2: Faixa de status de uma linha + "Sem cabo"/"Conectando"

**Files:**
- Modify (reescrever): `app/src/main/assets/ui/components/vehicle-status-strip.js:1-106` (`VehicleStatusStrip`)
- Create: `app/src/main/assets/ui/components/connection-gate.js`
- Modify: `app/src/main/assets/ui/styles-shell-status.css` (regras do strip, `:1-93`) — e acrescentar `.connection-gate`, `.connection-banner`
- Test: `tests/ui/global-vehicle-status.test.cjs` (reescrito), `tests/ui/connection-gate.test.cjs`; repoint `tests/ui/telemetry-truth-contract.test.cjs:14` e `tests/ui/display-rules.test.cjs:126-128`

**Interfaces:**
- Consumes: `connection.state`, `live`, `operation`, `IntentAction.HUMAN/STAGE`, `OperationOverlay.open`.
- Produces:

```js
OmegasUi.VehicleStatusStrip: class { constructor(headerEl: Element); render(snapshot): void }   // 4 fatos: connection · fuel · agora · operation
OmegasUi.ConnectionGate = { GATED_ROUTES: ['dashboard','map','curve','autocal','refino'],
  mount(screenHost: Element): void, render(snapshot, route: string): 'gate' | 'banner' | 'none' }
```

- [ ] **Step 1: Write the failing tests**

`global-vehicle-status.test.cjs`:

```js
test('uma linha, quatro fatos na ordem', () => {
  strip.render(states.normal);
  assert.deepEqual(facts(host), ['connection','fuel','agora','operation']);
  assert.equal(text(host,'connection'), 'Conectado'); assert.equal(text(host,'fuel'), 'GNV');
  assert.equal(text(host,'agora'), '6,24 ms · 0,62 bar'); assert.equal(attr(host,'operation','data-empty'), 'true');
});
test('operação em andamento aparece e reabre o overlay num toque', () => {
  strip.render(states.executando);
  assert.equal(text(host,'operation'), 'Zerar gás · Executando');
  h.tap(host, factEl(host,'operation')); assert.deepEqual(h.overlays, ['r-9']); assert.equal(h.requests.length, 0);
});
test('falha mostra Falhou em vermelho', () => { strip.render(states.falhaTransporte);
  assert.equal(text(host,'operation'), 'Zerar gás · Falhou'); assert.equal(attr(host,'operation','data-level'), 'danger'); });
test('telemetria inválida nunca vira zero', () => { strip.render({ ...states.normal, live: { ...states.normal.live, valid:false } });
  assert.equal(text(host,'agora'), '—'); assert.equal(text(host,'fuel'), '—'); });
test('fonte sem polling e sem ponte antiga', () => {
  for (const bad of ['setInterval','OmegasNative','store.subscribe','telemetryAgeMs']) assert.equal(src.includes(bad), false, bad);
  assert.match(css, /\.vehicle-status-strip\s*\{[^}]*flex-wrap:\s*nowrap[^}]*max-height:\s*64px/s);
});
```

`connection-gate.test.cjs`:

```js
test('AutoCal sem cabo → tela cheia sobre .screen-host', () => {
  assert.equal(Gate.render(states.semCabo, 'autocal'), 'gate'); assert.match(screenHost.innerHTML, /data-gate="sem-cabo"/);
  assert.match(screenHost.innerHTML, /Sem cabo/); assert.match(screenHost.innerHTML, /Ligue o cabo USB da ECU/);
});
test('conectando → "Conectando à ECU"', () => { assert.equal(Gate.render(states.conectando, 'map'), 'gate'); assert.match(screenHost.innerHTML, /Conectando à ECU/); });
test('Ferramentas e Sessões sem cabo → só faixa', () => {
  assert.equal(Gate.render(states.semCabo, 'tools'), 'banner'); assert.equal(Gate.render(states.semCabo, 'sessions'), 'banner');
  assert.doesNotMatch(screenHost.innerHTML, /data-gate=/);
});
test('conectado → nada, e o gate não re-renderiza a tela de baixo', () => {
  assert.equal(Gate.render(states.normal, 'autocal'), 'none'); assert.equal(h.renderCalls.autocal, 0);
});
test('CSS cobre só a área de conteúdo', () => assert.match(css, /\.connection-gate\s*\{[^}]*position:\s*absolute[^}]*inset:\s*0/s));
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh node tests/ui/global-vehicle-status.test.cjs`
Expected: `REMOTE_TEST=FAIL` — `facts` devolve `['service','ecu','freshness','fuel','rpm','petrol']`.

- [ ] **Step 3: Implement**

Strip: `<section id="vehicleStatusStrip" class="vehicle-status-strip">` com 4 `<button|div data-fact=…>`; valores com `data-critical` (24 px). Rótulos de conexão: `SEM_CABO→'Sem cabo'`, `CONECTANDO→'Conectando'`, `CONECTADO→'Conectado'`. `operation` vazio ⇒ `data-empty="true"` (oculto por CSS, sem reflow). Gate: um `<div class="connection-gate" data-gate="sem-cabo|conectando">` filho de `.screen-host` (`styles.css:44` já é `position:relative`), título `data-critical` 32 px e uma linha: "Ligue o cabo USB da ECU. O app reconecta sozinho." / "Lendo a ECU. Isso leva alguns segundos."; sem botão. Banner: `<div class="connection-banner">` de uma linha no topo da tela de Sessões/Ferramentas. Ambos chamados pelo handler de revisão do `app.js` (o mesmo ponto onde a F6 redesenha por `Omegas.onRevision`), uma vez por revisão de `connection`/`live`/`operation`. `router.js:50` continua carregando o strip; `ConnectionGate` entra em `index.html` como `<script defer>` antes de `app.js`.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/global-vehicle-status.test.cjs` e `tools/ci/remote-test.sh node tests/ui/connection-gate.test.cjs` e `tools/ci/remote-test.sh node tests/ui/telemetry-truth-contract.test.cjs`
Expected: `REMOTE_TEST=PASS` (3×).

- [ ] **Step 5: Commit** `feat(ui): faixa de status em uma linha e telas Sem cabo/Conectando`

---

### Task 7.3: `AutoCalUxModel` sai do cockpit para `screens/autocal/model.js`

**Files:**
- Create: `app/src/main/assets/ui/screens/autocal/model.js` (de `autocal-cockpit.js:8-603`, `AutoCalUxModel` e helpers puros)
- Modify: `app/src/main/assets/ui/screens/autocal-cockpit.js:8-603` (remove o modelo; usa `const AutoCalUxModel = ns.AutoCalUxModel`) — transitório até a Task 7.7
- Test: `tests/ui/autocal-model.test.cjs` (novo); repoint para `screens/autocal/model.js`: `autocal-chart-semantics`, `autocal-current-band-parity`, `autocal-live-levels-parity`, `autocal-live-epoch-gate` (só asserts de modelo), `autocal-didactic-cockpit` (só asserts de `humanState`/zonas), `autocal-blueprint-context` (só `referenceTransition`/geometria)

**Interfaces:**
- Produces (em `OmegasUi.AutoCalUxModel`; o resto do modelo mantém nome e assinatura):

```js
matrix(snapshot) → Array<{ band: 0..17, zone: 1..4,
  PETROL: { state: 'acquired'|'collecting'|'empty', counter: number, threshold: number|null, progress: number|null, petrolMs: number|null, mapBar: number|null },
  GAS:    { …mesmo } }>                                    // sempre 18 linhas
toggleIntent(enabled) → 'AUTOCAL_PAUSE' | 'AUTOCAL_RESUME' | null     // substitui toggleAction
selectionAfterOperation(pendingKeys: string[], operation, { sessionChanged: boolean })
  → { clear: boolean, restore: string[], pending: string[], reason: 'SESSION_CHANGED'|'IDLE'|'CONCLUIDO'|'FALHOU_RETEM'|'EM_VOO' }   // substitui pointSelectionTransition
relearnPayload(keys: string[]) → { points: { fuel: 'PETROL'|'GAS', index: number }[] }   // chave = 'GAS:6'
livePoint(live) → { petrolMs, mapBar, rpm, fuel, ageMs } | null      // agora lê snapshot.live (valid, ageMs ≤ 7500)
```

Saem do modelo: `actionLabel` (`:24-36`), `toggleAction` (`:556-561`), `pointSelectionTransition` (`:563-600`), `readNarrative` (`:173-189`, leitura manual não existe mais), `sessionNarrative` (`:191-219`, sessão foi para Sessões).

- [ ] **Step 1: Write the failing test** `tests/ui/autocal-model.test.cjs`

```js
const m = load(['screens/autocal/model.js'], { snapshot: states.normal }).window.OmegasUi.AutoCalUxModel;
const snap = states.states.normal.autocal.projection.snapshot;
test('matriz 18 × gasolina/GNV com maturidade', () => {
  const rows = m.matrix(snap); assert.equal(rows.length, 18);
  assert.equal(rows.filter(r => r.PETROL.state === 'acquired').length, 12);
  assert.equal(rows.filter(r => r.GAS.state === 'acquired').length, 6);
  assert.deepEqual(rows.filter(r => r.GAS.state === 'collecting').map(r => r.band), [6, 7, 8]);
  assert.equal(rows[6].GAS.progress, 0.5); assert.equal(rows[6].zone, 2);
  assert.equal(rows.filter(r => r.GAS.state === 'empty').length, 9);
  assert.equal(m.matrix(states.states.ecuSemAquisicao.autocal.projection.snapshot).every(r => r.PETROL.state === 'empty' && r.GAS.state === 'empty'), true);
});
test('pausar/retomar', () => { assert.equal(m.toggleIntent(1), 'AUTOCAL_PAUSE'); assert.equal(m.toggleIntent(0), 'AUTOCAL_RESUME'); assert.equal(m.toggleIntent(null), null); });
test('seleção só limpa com CONCLUIDO; FALHOU retém; sessão nova limpa', () => {
  const keys = ['GAS:6', 'GAS:7'];
  assert.deepEqual(m.selectionAfterOperation(keys, { receiptId:'r-1', stage:'FALHOU' }, { sessionChanged:false }), { clear:false, restore:keys, pending:[], reason:'FALHOU_RETEM' });
  assert.equal(m.selectionAfterOperation(keys, { receiptId:'r-1', stage:'CONCLUIDO' }, { sessionChanged:false }).clear, true);
  assert.deepEqual(m.selectionAfterOperation(keys, { receiptId:'r-1', stage:'CONFERINDO' }, { sessionChanged:false }).pending, keys);
  assert.equal(m.selectionAfterOperation(keys, null, { sessionChanged:true }).reason, 'SESSION_CHANGED');
});
test('payload de reaprender', () => assert.deepEqual(m.relearnPayload(['GAS:6','PETROL:2']), { points: [{ fuel:'GAS', index:6 }, { fuel:'PETROL', index:2 }] }));
test('AGORA vem de snapshot.live e some quando velho', () => {
  assert.deepEqual(m.livePoint(states.states.normal.live), { petrolMs:6.24, mapBar:0.62, rpm:2150, fuel:'GNV', ageMs:120 });
  assert.equal(m.livePoint({ ...states.states.normal.live, ageMs: 8000 }), null);
});
test('o que saiu do modelo', () => { for (const gone of ['actionLabel','toggleAction','pointSelectionTransition','readNarrative','sessionNarrative','MANUAL_AUTOMATCH','RESET_ALL']) assert.equal(src.includes(gone), false, gone); });
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh node tests/ui/autocal-model.test.cjs`
Expected: `REMOTE_TEST=FAIL` — `ENOENT … screens/autocal/model.js`.

- [ ] **Step 3: Implement** — mover `:8-603` sem mudar as funções que ficam (`humanState`, `currentBand`, `currentZone`, `zoneSurface`, `referencePoints`, `acquiredPoints`, `referenceDomain`, `projectLive`, `liveLabelAnchor`, `referenceComparison`, `referenceFingerprint`, `referenceTransition`, `bandStrip`, `referenceSourceLabel`, `bandNarrative`, `liveFuelState`); `matrix` = `acquiredPoints(snap,'petrol')` + `acquiredPoints(snap,'gas')` indexados por banda, banda ausente = `empty`. `model.js` entra no `index.html` antes do cockpit. Repoint dos 6 testes listados: trocar o caminho lido e remover só os asserts que olham markup do cockpit.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/autocal-model.test.cjs`, depois o mesmo para cada um dos 6 testes repontados
Expected: `REMOTE_TEST=PASS` em todos.

- [ ] **Step 5: Commit** `refactor(ui): AutoCalUxModel em screens/autocal/model.js com matriz e seleção por recibo`

---

### Task 7.4: AutoCal › Aquisição

**Files:**
- Create: `app/src/main/assets/ui/screens/autocal/aquisicao.js` (gráfico de `autocal-cockpit.js:1289-1629` sem a camada "anterior"; inspector de `:1656-1697`; técnicos de `:779-797`, `:1778-1859`)
- Modify: `app/src/main/assets/ui/styles-autocal-cockpit.css` (acrescentar `.autocal-actions`, `.autocal-matrix`, `.autocal-matrix-cell`)
- Test: `tests/ui/autocal-aquisicao.test.cjs`

**Interfaces:**
- Consumes: `AutoCalUxModel` (7.3), `IntentAction` (7.1), `snapshot.autocal`, `snapshot.live`, `snapshot.operation`.
- Produces: `OmegasUi.AutoCalAquisicao: class { constructor(host); render(snapshot); renderLive(live); select(key) }`. Marcação: `[data-intent]` nos 5 botões, `[data-select="GAS:6"]` nas 36 células, `[data-matrix-cell][data-fuel][data-band][data-state]`.

Tela (ordem de leitura): linha de estado `data-critical` (`humanState.title`) + progresso → linha de ações [Pausar aquisição | Retomar aquisição] · [Zerar gás] · [Zerar gasolina] · [Reaprender N selecionados] → gráfico (curva de aquisição + AGORA) → matriz 18 faixas em 6 colunas × 3 linhas, cada faixa dividida Gasolina | GNV (cada metade ≥ 76×76) → inspector do ponto → `<details class="tech"><summary>Detalhes técnicos</summary>` (estado raw, habilitação, hash, fonte da referência, AutoMatch nativo e evidência, 18 regiões, eventos).

- [ ] **Step 1: Write the failing test**

```js
const screen = new h.window.OmegasUi.AutoCalAquisicao(host); h.window.OmegasUi.IntentAction.bind(host);
test('matriz 36 alvos com estado', () => {
  screen.render(states.normal);
  assert.equal(count(host.innerHTML, /data-matrix-cell/g), 36);
  assert.equal(count(host.innerHTML, /data-fuel="PETROL"[^>]*data-state="acquired"/g), 12);
  assert.equal(count(host.innerHTML, /data-fuel="GAS"[^>]*data-state="collecting"/g), 3);
});
test('Pausar quando ativa; Retomar quando pausada', () => {
  screen.render(states.normal); assert.match(host.innerHTML, /data-intent="AUTOCAL_PAUSE"[^>]*>Pausar aquisição</);
  screen.render(states.ecuSemAquisicao); assert.match(host.innerHTML, /data-intent="AUTOCAL_RESUME"[^>]*>Retomar aquisição</);
});
for (const [label, intent] of [['Zerar gás','AUTOCAL_RESET_GAS'], ['Zerar gasolina','AUTOCAL_RESET_PETROL'], ['Pausar aquisição','AUTOCAL_PAUSE']])
  test(`${label}: um toque → um request, sem revisão`, () => {
    const h2 = fresh(states.normal); h2.tap(h2.host, h2.byText(label));
    assert.deepEqual(h2.requests, [{ intent, payload: {} }]); assert.deepEqual(h2.overlays, ['r-1']);
    assert.doesNotMatch(h2.host.innerHTML, /autocalReview|Executar agora|Cancelar|REVISÃO ANTES DA ECU|Tem certeza/);
  });
test('selecionar não pede nada; Reaprender selecionados manda os pontos', () => {
  const h2 = fresh(states.normal); h2.tap(h2.host, h2.bySelect('GAS:6')); h2.tap(h2.host, h2.bySelect('GAS:7'));
  assert.equal(h2.requests.length, 0); assert.match(h2.host.innerHTML, />Reaprender 2 selecionados</);
  h2.tap(h2.host, h2.byText('Reaprender 2 selecionados'));
  assert.deepEqual(h2.requests, [{ intent:'AUTOCAL_RELEARN', payload:{ points:[{ fuel:'GAS', index:6 }, { fuel:'GAS', index:7 }] } }]);
});
test('FALHOU mantém a seleção; CONCLUIDO limpa', () => {
  /* após o request r-1 */ screen.render({ ...states.normal, operation: { receiptId:'r-1', intent:'AUTOCAL_RELEARN', stage:'FALHOU', failureKind:'ECU', text:'…' } });
  assert.match(host.innerHTML, />Reaprender 2 selecionados</);
  screen.render({ ...states.normal, operation: { receiptId:'r-1', intent:'AUTOCAL_RELEARN', stage:'CONCLUIDO', text:'Concluído' } });
  assert.match(host.innerHTML, /disabled[^>]*>Reaprender selecionados</);
});
test('outra mutação em voo → ações da ECU desabilitadas', () => {
  screen.render(states.executando); assert.equal(count(host.innerHTML, /data-intent="AUTOCAL_[A-Z_]+"[^>]*disabled/g), 4);
});
test('inspector do ponto', () => { /* tap GAS:6 */ assert.match(host.innerHTML, /GNV · faixa 7 · Z2 · coletando · 50%/); });
test('o que saiu', () => {
  screen.render(states.normal);
  for (const gone of ['Ações avançadas','AutoMatch manual','Nova aquisição completa','Resetar Curva K','Readquirir','Contexto da aquisição','Ver sessões','Leitura anterior'])
    assert.equal(host.innerHTML.includes(gone), false, gone);
  assert.ok(host.innerHTML.lastIndexOf('<details class="tech"') > host.innerHTML.lastIndexOf('data-matrix-cell'));
  assert.equal(/\bconfirm\(/.test(src), false);
});
test('AGORA nunca vira ponto adquirido', () => { screen.render(states.normal); assert.match(host.innerHTML, /class="autocal-live-point"/); assert.equal(count(host.innerHTML, /data-matrix-cell[^>]*data-live/g), 0); });
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh node tests/ui/autocal-aquisicao.test.cjs`
Expected: `REMOTE_TEST=FAIL` — `AutoCalAquisicao is not a constructor`.

- [ ] **Step 3: Implement** — `render` recalcula seleção com `selectionAfterOperation` sempre que `operation.receiptId === this.pendingReceipt`; `renderLive` só move o cursor AGORA (`[data-autocal-live-point]`) e a faixa corrente, sem redesenhar o SVG (mesma regra de `:1181-1239`). CSS: `.autocal-matrix{display:grid;grid-template-columns:repeat(6,minmax(0,1fr))}`, `.autocal-matrix-cell{min-height:76px;min-width:76px}`, `.autocal-actions button{min-height:76px}`; cores de estado reaproveitam as classes `.petrol/.gas/.acquired/.collecting` já existentes no arquivo.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/autocal-aquisicao.test.cjs`
Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(ui): AutoCal › Aquisição — matriz 18×2, ações de um toque, reaprender por seleção`

---

### Task 7.5: AutoCal › Referência e › Épocas

**Files:**
- Create: `app/src/main/assets/ui/screens/autocal/referencia.js`
- Create: `app/src/main/assets/ui/screens/autocal/epocas.js` (lista de `autocal-cockpit.js:1103-1142`; "Leitura anterior" de `:730`, `:826-831`, `:1631-1654`)
- Test: `tests/ui/autocal-referencia.test.cjs`, `tests/ui/autocal-epocas.test.cjs`

**Interfaces:**
- Produces:

```js
OmegasUi.AutoCalReferencia: class { constructor(host); render(snapshot) }
OmegasUi.AutoCalEpocas: class { constructor(host, history: AutoCalHistory); render(snapshot) }
// AutoCalHistory (criado no index.js, Task 7.6): { previousPoints: object[], visible: boolean, comparison(points) }
```

- [ ] **Step 1: Write the failing tests**

`autocal-referencia.test.cjs`:

```js
test('sem Referência → provisório e "Congelar esta curva"', () => {
  r.render(states.semReferencia);
  assert.match(host.innerHTML, /Sem referência · modo provisório/);
  assert.match(host.innerHTML, /data-intent="REFERENCE_FREEZE"[^>]*>Congelar esta curva</);
  h.tap(host, h.byText('Congelar esta curva'));
  assert.deepEqual(h.requests, [{ intent:'REFERENCE_FREEZE', payload:{} }]); assert.deepEqual(h.overlays, ['r-1']);
});
test('ECU reaprendeu → âmbar, diferença e "Congelar de novo"', () => {
  r.render(states.ecuReaprendeu);
  assert.match(host.innerHTML, /A ECU reaprendeu gasolina desde o congelamento \(diferença máx\. 3,2%\)/);
  assert.match(host.innerHTML, /data-level="warn"/); assert.match(host.innerHTML, />Congelar de novo</);
});
test('normal → data e maturidade', () => {
  r.render(states.normal);
  assert.match(host.innerHTML, /Congelada em \d{2}\/\d{2}\/\d{4} \d{2}:\d{2}/);
  assert.match(host.innerHTML, /14 de 18 faixas maduras/);
  assert.match(host.innerHTML, /A ECU não mudou a gasolina desde o congelamento/);
});
test('não maduro → botão desabilitado com motivo', () => {
  r.render(states.ecuSemAquisicao);
  assert.match(host.innerHTML, /disabled[^>]*>Congelar esta curva</);
  assert.match(host.innerHTML, /A gasolina da ECU ainda não está madura para congelar\./);
});
test('gráfico: Referência tracejada × ECU agora', () => {
  r.render(states.ecuReaprendeu);
  assert.match(host.innerHTML, /class="ref-line reference"/); assert.match(host.innerHTML, /class="ref-line ecu"/);
  assert.match(host.innerHTML, /Referência \(congelada\)/); assert.match(host.innerHTML, /ECU agora/);
});
```

`autocal-epocas.test.cjs`:

```js
test('vazio', () => { e.render(states.vazio); assert.match(host.innerHTML, /Nenhuma sessão gravada ainda\./); });
test('linha por sessão', () => {
  e.render(states.normal); assert.equal(count(host.innerHTML, /data-epoch-row/g), 3);
  assert.match(host.innerHTML, /42 min · GNV 3\/4 · gasolina 4\/4 · 2 épocas/);
});
test('"Ver na lista" navega sem pedir nada', () => {
  h.tap(host, h.byNav('s-2'));
  assert.deepEqual(h.navigations, [{ route:'sessions', context:{ subpage:'lista', sessionId:'s-2' } }]); assert.equal(h.requests.length, 0);
});
test('Leitura anterior é toggle no lugar', () => {
  history.previousPoints = [/* 30 pontos */]; e.render(states.normal);
  h.tap(host, h.byText('Leitura anterior')); assert.match(host.innerHTML, /aria-pressed="true"[^>]*>Ocultar anterior</);
  assert.match(host.innerHTML, /ANTES × DEPOIS/); assert.equal(h.requests.length, 0);
  history.previousPoints = []; e.render(states.normal); assert.match(host.innerHTML, /disabled[^>]*>Leitura anterior</);
});
test('dados longos sem lixo', () => { e.render(states.dadosLongos); assert.equal(count(host.innerHTML, /data-epoch-row/g), 50); assert.doesNotMatch(host.innerHTML, /undefined|NaN/); });
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh node tests/ui/autocal-referencia.test.cjs`
Expected: `REMOTE_TEST=FAIL` — `ENOENT … referencia.js`.

- [ ] **Step 3: Implement** — `referencia.js`: um card (`data-level` `ok`/`warn`), linha de estado `data-critical`, botão único, SVG 1000×400 com o mesmo domínio de eixo da Aquisição (x = Petrol Inj. ms, y = MAP bar), `<details class="tech">` com `id`, `ecuAcquisitionFingerprint`, `frozenAt` cru e a tabela de pontos. Data com `toLocaleString('pt-BR', {day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'})`; percentual com 1 casa e vírgula. `epocas.js`: linhas ≥ 76 px; `[data-nav="s-2"]` chama `router.navigate('sessions', { subpage:'lista', sessionId })`; o toggle e a comparação reutilizam `AutoCalUxModel.referenceComparison`.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/autocal-referencia.test.cjs` e `tools/ci/remote-test.sh node tests/ui/autocal-epocas.test.cjs`
Expected: `REMOTE_TEST=PASS` (2×).

- [ ] **Step 5: Commit** `feat(ui): AutoCal › Referência (congelar) e › Épocas (sessões e leitura anterior)`

---

### Task 7.6: AutoCal › montagem por subpáginas e troca do cockpit

**Files:**
- Create: `app/src/main/assets/ui/screens/autocal/index.js`
- Modify: `app/src/main/assets/ui/index.html:8-9` (link `styles-autocal-cockpit.css`), `:158-160` (`#autocalScreenHost`), `:185-198` (scripts `screens/autocal/{model,aquisicao,referencia,epocas,index}.js`, `components/intent-action.js`, `components/connection-gate.js`)
- Modify: `app/src/main/assets/ui/core/router.js:54-55` (`loadOptionalScript('screens/autocal-cockpit.js' …)` sai; `screens/refino.js` continua carregado)
- Modify: `app/src/main/assets/ui/app.js:479`, `:482`, `:538` (`autoCalCockpit` → `autoCalScreen`) — três linhas
- Test: `tests/ui/autocal-screen.test.cjs`

**Interfaces:**
- Consumes: `SubpageTabs.mount(host,{route,items,onChange})` e chave `subpage.autocal` (F6).
- Produces: `OmegasUi.AutoCalScreen: class { constructor(host); enter(context?: { subpage?: 'aquisicao'|'referencia'|'epocas' }); render(snapshot) }`; registrado em `OmegasApp.autoCalScreen`.

- [ ] **Step 1: Write the failing test**

```js
test('três subpáginas na ordem do índice', () => {
  assert.deepEqual(h.tabs[0].route, 'autocal');
  assert.deepEqual(h.tabs[0].items, [{ id:'aquisicao', label:'Aquisição' }, { id:'referencia', label:'Referência' }, { id:'epocas', label:'Épocas' }]);
});
test('uma linha didática por subpágina', () => {
  const lines = { aquisicao:'O que a ECU já aprendeu, faixa por faixa, na gasolina e no GNV.',
    referencia:'A curva de gasolina congelada que o app usa como régua.', epocas:'Como a aquisição da ECU mudou de uma sessão para outra.' };
  for (const [id, line] of Object.entries(lines)) { screen.enter({ subpage:id }); screen.render(states.normal); assert.match(host.innerHTML, new RegExp(line)); }
});
test('Agora leva a Referência já posicionada', () => {
  screen.enter({ subpage:'referencia' }); screen.render(states.semReferencia);
  assert.equal(h.store.get()['subpage.autocal'], 'referencia'); assert.match(host.innerHTML, /Congelar esta curva/);
});
test('Leitura anterior é capturada quando a referência da ECU muda', () => {
  screen.render(states.normal); screen.render(states.ecuReaprendeu); screen.enter({ subpage:'epocas' }); screen.render(states.ecuReaprendeu);
  assert.doesNotMatch(host.innerHTML, /disabled[^>]*>Leitura anterior</);
});
test('fiação', () => {
  assert.match(html, /<link rel="stylesheet" href="styles-autocal-cockpit.css">/);
  assert.ok(html.indexOf('screens/autocal/model.js') < html.indexOf('screens/autocal/index.js'));
  assert.equal(router.includes('autocal-cockpit'), false); assert.equal(app.includes('autoCalCockpit'), false);
  for (const f of autocalFiles) for (const bad of ['addHook(','setInterval','AutoCalApi','OmegasAutoCal']) assert.equal(f.src.includes(bad), false, f.name + ' ' + bad);
});
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh node tests/ui/autocal-screen.test.cjs`
Expected: `REMOTE_TEST=FAIL` — `AutoCalScreen is not a constructor`.

- [ ] **Step 3: Implement** — `index.js`: `IntentAction.bind(host)` uma vez; `SubpageTabs.mount` no topo do host; um contêiner por subpágina, só o ativo recebe `render`; `AutoCalHistory` guarda `referencePoints` anteriores quando `AutoCalUxModel.referenceTransition(...).referenceChanged` (e zera em `clearHistory`); `enter(context)` grava `store.patch({ 'subpage.autocal': context.subpage })` e remonta as tabs; `boot()` espera `OmegasApp.store` e cria `OmegasApp.autoCalScreen = new AutoCalScreen(document.getElementById('autocalScreenHost'))`. A renderização vem do mesmo handler de revisão que a F6 usa para as telas dela.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/autocal-screen.test.cjs` e `tools/ci/remote-test.sh node tests/ui/refino-screen.test.cjs`
Expected: `REMOTE_TEST=PASS` (2×).

- [ ] **Step 5: Commit** `feat(ui): AutoCal com subpáginas Aquisição · Referência · Épocas`

---

### Task 7.7: Apagar o cockpit, encolher o CSS e classificar os testes antigos

**Files:**
- Delete: `app/src/main/assets/ui/screens/autocal-cockpit.js`
- Modify: `app/src/main/assets/ui/styles-autocal-cockpit.css` (2324 → ≤ 1400 linhas)
- Delete (condicional, Step 3): `app/src/main/assets/ui/core/autocal-api.js`
- Test: `tests/test_f7_autocal_cockpit_removed.py`; testes antigos conforme a tabela abaixo

**Interfaces:** Produces: nada novo. Remove `OmegasUi.AutoCalCockpit`.

Classificação dos testes que leem o cockpit:

| Teste | Destino |
|---|---|
| `tests/ui/autocal-automatic-live-surface.test.cjs` | **apagar** (coberto por `autocal-aquisicao`: sem leitura manual, ação sempre visível) |
| `tests/ui/autocal-batch-recovery-intent.test.cjs` | **apagar** (reescrito como `selectionAfterOperation` em `autocal-model`) |
| `tests/ui/autocal-blueprint-context.test.cjs` | **manter+repontar** (Task 7.3); apagar o caso "malformed execution response does not dismiss confirmation" (não há confirmação) |
| `tests/ui/autocal-chart-semantics.test.cjs` | **manter+repontar** → `model.js` + `aquisicao.js` |
| `tests/ui/autocal-cockpit.test.cjs` | **reescrever** como contrato da tela: arquivos `screens/autocal/*.js` existem, intents fechados (`AUTOCAL_PAUSE/RESUME/RESET_GAS/RESET_PETROL/RELEARN`, `REFERENCE_FREEZE`), nenhum `FINISH_AUTOCAL`/`FINISH_AUTOMATCH`/`MANUAL_AUTOMATCH`/`RESET_ALL`/`RESET_K_FACTOR` |
| `tests/ui/autocal-consumer-graph.test.cjs` | **reescrever**: grafo `index.html → screens/autocal/{model,aquisicao,referencia,epocas,index}.js → IntentAction → Omegas` |
| `tests/ui/autocal-current-band-parity.test.cjs` | **manter+repontar** → `model.js` |
| `tests/ui/autocal-didactic-cockpit.test.cjs` | **manter+repontar** só `humanState`/zonas → `model.js`; asserts de markup/CSS do cockpit saem |
| `tests/ui/autocal-durable-hmi.test.cjs` | **apagar** (persistência em Downloads/Omegas agora é texto da retenção em Ferramentas, testado na Task 7.10) |
| `tests/ui/autocal-final-product.test.cjs` + `tests/ui/autocal-test-sensitivity.test.cjs` | **apagar juntos** (meta-teste depende do anterior; substituídos por `autocal-screen` + `autocal-consumer-graph`) |
| `tests/ui/autocal-instrument-hierarchy.test.cjs` | **manter+repontar** → `aquisicao.js` + CSS; sai o caso "secondary state stays below the graph" |
| `tests/ui/autocal-levels-scope.test.cjs` | **manter+repontar** → glob `screens/autocal/*.js` |
| `tests/ui/autocal-live-epoch-gate.test.cjs` | **manter+repontar** → `model.js` + `aquisicao.js` |
| `tests/ui/autocal-live-levels-parity.test.cjs` | **manter+repontar** → `model.js` |
| `tests/ui/autocal-multimedia-sophistication.test.cjs` | **reescrever**: só "antes × depois compacto" (agora em `epocas.js`) e âncora do rótulo AGORA (`model.js`) |
| `tests/ui/autocal-reacquisition-ux.test.cjs` | **apagar** (fixava "Ações avançadas"; substituído por `autocal-aquisicao`) |
| `tests/ui/autocal-realtime-cadence.test.cjs` | **apagar** e remover o ramo `setCadenceMs(route === 'autocal' ? 50 : 200)` do `app.js` se a F6 o deixou (AutoCal redesenha por revisão; AGORA por `live`) |
| `tests/ui/autocal-session-experience.test.cjs` | **apagar** (sessões saíram do AutoCal; Task 7.9) |
| `tests/ui/autocal-vertical-flow.test.cjs` | **manter+repontar** → `aquisicao.js` + CSS (a matriz fica no fluxo vertical, sem rolagem interna) |
| `tests/ui/autocal-ci-no-apk.test.cjs`, `autocal-ci-proof.test.cjs` | intocados (não leem o cockpit) |
| `tests/test_autocal_canonical_session_contract.py` | **reescrever**: as frases de persistência passam a ser exigidas em `screens/tools.js` (Task 7.10) |
| `tests/test_autocal_levels_scope_contract.py` | **manter+repontar** → `screens/autocal/*.js` |
| `tests/test_autocal_operational_toggle_contract.py` | **reescrever** a parte UI: `data-intent="AUTOCAL_PAUSE"`/`AUTOCAL_RESUME` em `aquisicao.js`; asserts de Kotlin intocados |
| `tests/test_autocal_point_delete_contract.py` | **reescrever** a parte UI: `AUTOCAL_RELEARN` + `relearnPayload` em `aquisicao.js`/`model.js`; Kotlin intocado |
| `tests/test_autocal_reacquisition_scope_contract.py` | **reescrever** a parte UI: saem `salve manualmente`/`Backup não é requisito`; exige "Zerar gás"/"Zerar gasolina" em `aquisicao.js` |
| `tests/test_autocal_reset_safety_gate.py` | **reescrever** a parte UI: nenhum `prepare(`/`autocalReview`; `AUTOCAL_RESET_*` por `IntentAction` |
| `tests/test_omegas_autocal_progbase_parity_matrix.py` | **manter+repontar**: `addHook('fast'` → `renderLive(` em `aquisicao.js` |
| `tests/test_platina_progbase_host_contract_v2.py` | **manter+repontar** → `screens/autocal/*.js` (sem FINISH_*; `autoMatchQuotaReached` em `model.js`; texto "O AutoMatch nativo é automático e decidido pela ECU." nos Detalhes técnicos de `aquisicao.js`) |
| `app/src/androidTest/.../DashboardLevelsRenderTest.kt` `autocal*` (5 métodos, `:905-1092`) | **apagar** na Task 7.11 (substituídos por `F7SurfacesRenderTest.autocalTodosOsEstados`) |

- [ ] **Step 1: Write the failing test** `tests/test_f7_autocal_cockpit_removed.py`

```python
import pathlib, re, subprocess
root = pathlib.Path(__file__).resolve().parents[1]
ui = root / "app/src/main/assets/ui"
assert not (ui / "screens/autocal-cockpit.js").exists()
hits = subprocess.run(["git", "grep", "-l", "autocal-cockpit.js", "--", "app", "tests", "tools", ".github"], cwd=root, capture_output=True, text=True).stdout.split()
assert hits == [], hits
css = (ui / "styles-autocal-cockpit.css").read_text("utf-8")
assert len(css.splitlines()) <= 1400, len(css.splitlines())
for gone in ("autocal-reset-menu", "autocal-reset-popover", "autocal-review", "autocal-secondary-details", "autocal-secondary-card",
             "autocal-session-drawer", "autocal-session-strip", "autocal-session-item", "autocal-command-bar", "autocal-hero",
             "autocal-zone-card", "autocal-zone-meter", "autocal-history-float", "autocal-automatch-evidence"):
    assert "." + gone not in css, gone
assert len(re.findall(r"\.refino", css)) == 52, "seletores do Refino não podem sumir"
print("F7_AUTOCAL_COCKPIT_REMOVED=PASS")
```

(52 = contagem de `.refino` no arquivo antes desta task; reconte no Step 0 com `grep -o "\.refino" styles-autocal-cockpit.css | wc -l` e fixe o número que aparecer.)

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh python tests/test_f7_autocal_cockpit_removed.py`
Expected: `REMOTE_TEST=FAIL` — `AssertionError` no primeiro assert.

- [ ] **Step 3: Delete, shrink, classify**

Apagar o cockpit; no CSS apagar os blocos dos seletores listados e os de `.autocal-focus-actions`/`.autocal-reacquire-action`/`.autocal-zone-*` que a matriz substituiu. Aplicar a tabela. Depois: `git grep -n "AutoCalApi\|autocal-api.js" -- app/src/main/assets` — se só sobrar a própria definição e o `loadOptionalScript` do `router.js`, apagar `core/autocal-api.js` e essa carga; se o Refino (F6) ainda consome, manter e anotar no PR.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh python tests/test_f7_autocal_cockpit_removed.py`, depois `tools/ci/remote-test.sh checks ""`
Expected: `REMOTE_TEST=PASS` (2×) — o `checks` roda todos os `tests/ui/*.test.cjs` e Python repontados.

- [ ] **Step 5: Commit** `refactor(ui)!: remove autocal-cockpit.js, CSS do AutoCal −900 linhas, testes reclassificados`

---

### Task 7.8: Mapa K por `MAP_WRITE` + `OperationOverlay`

**Files:**
- Modify: `app/src/main/assets/ui/screens/map.js` (`MapScreen`): `:12-32` (ctor → `constructor(host)`), `:36-53` e `:57` (`ensureContextChrome`/`mapBackToLearning` saem), `:87-143` (`settleReadFailure`/`startRead`/`poll` → `render(snapshot)` lê `now.mapK`), `:255-271` (`applyAdjustment` usa prévia local), `:327-393` (`writePrepared` → `IntentAction.send('MAP_WRITE', …)`; `dismissResult`/`pollWrite` saem), `:395-428` (`applyContext` só aceita `{ cell }`)
- Modify: `app/src/main/assets/ui/map-editor.js:231-247` (acrescentar `previewItems`; `applyNativePreview` fica)
- Modify: `app/src/main/assets/ui/index.html:72-95` (saem `#mapReadButton` `:75` e as duas `.operation-layer` `:93-94`; `safety-copy` `:88` vira "Um toque grava. Foto antes, Desfazer depois.")
- Test: `tests/ui/map-screen-intent.test.cjs`, `tests/fixtures/map-k-preview-parity-v1.json`, `tests/ui/map-preview-parity.test.cjs`, `app/src/test/java/com/omegas/prohub/calibration/MapKPreviewParityTest.kt`; repoint `tests/ui/map-workflow-e2e.test.cjs`, `tests/ui/curve-map-editor-coherence.test.cjs` (só linhas do mapa), `tests/ui/automotive-hmi-legibility.test.cjs:15-21,29`, `tests/test_clean_ui_contract.py:124-135`, `tests/test_mp48_k_map_axes_contract.py:40-48`

**Interfaces:**
- Consumes: `now.mapK`, `now.cell`, `operation`, `IntentAction`.
- Produces:

```js
OmegasMapEditor.previewTarget(current: int, mode: 'percent'|'delta'|'target', adjustment: number) → int   // clamp(Math.round(raw), 100, 255)
OmegasMapEditor.previewItems(cells: {row,column,current}[], mode, adjustment) → { ok: true, items: {row,column,current,target,changed}[] } | { ok: false, error: string }
OmegasUi.MapScreen: class { constructor(host); enter(context?: { cell?: {row,column} }); render(snapshot) }
```

- [ ] **Step 1: Write the failing tests**

`tests/fixtures/map-k-preview-parity-v1.json`:

```json
{ "format": "omegas-map-k-preview-parity-v1", "cases": [
  { "current": 120, "mode": "percent", "adjustment": 5,    "target": 126 },
  { "current": 200, "mode": "percent", "adjustment": -2.5, "target": 195 },
  { "current": 250, "mode": "delta",   "adjustment": 10,   "target": 255 },
  { "current": 120, "mode": "target",  "adjustment": 90,   "target": 100 },
  { "current": 101, "mode": "percent", "adjustment": 0.5,  "target": 102 },
  { "current": 150, "mode": "delta",   "adjustment": -0.5, "target": 150 },
  { "current": 151, "mode": "delta",   "adjustment": -0.5, "target": 151 } ] }
```

`MapKPreviewParityTest.kt`: `@Test fun jsPreviewCasesMatchKotlinPlanner()` lê `../tests/fixtures/map-k-preview-parity-v1.json` (fallback `tests/fixtures/…`) e `assertEquals(case.target, MapKManualPlanner.target(case.current, case.mode, case.adjustment))` para cada caso. `map-preview-parity.test.cjs`: mesmo arquivo, `assert.equal(previewTarget(c.current, c.mode, c.adjustment), c.target)`.

`map-screen-intent.test.cjs`:

```js
test('grade de 144 a partir de now.mapK, sem "Voltar ao aprendizado"', () => {
  screen.render(states.normal); assert.equal(count(host.innerHTML, /class="map-k-cell/g), 144);
  for (const gone of ['Voltar ao aprendizado','mapBackToLearning',"navigate('learning'"]) assert.equal((host.innerHTML + src).includes(gone), false, gone);
});
test('selecionar e ajustar não pede nada; gravar = um MAP_WRITE com overlay', () => {
  h.tap(host, cell(0,0)); setInput(host, 'mapAdjustmentValue', '5');
  assert.equal(h.requests.length, 0); assert.match(host.innerHTML, /120→126/);
  h.tap(host, h.byText('Gravar 1 alteração na ECU')); h.tap(host, h.byText('Gravar 1 alteração na ECU'));
  assert.deepEqual(h.requests, [{ intent:'MAP_WRITE', payload:{ cells:[{ row:0, column:0, current:120, target:126 }] } }]);
  assert.deepEqual(h.overlays, ['r-1']);
});
test('releitura automática sem gravação preserva a seleção', () => {
  /* seleção (0,0) pendente */ screen.render(withMapK(states.normal, { readAtMs: 2 }));
  assert.match(host.innerHTML, /1 selecionada/);
});
test('MAP_WRITE concluído → seleção limpa e grade relida', () => {
  screen.render(withMapK({ ...states.normal, operation:{ receiptId:'r-1', intent:'MAP_WRITE', stage:'CONCLUIDO', text:'Concluído' } }, { readAtMs: 3, row0col0: 126 }));
  assert.match(host.innerHTML, /0 selecionadas/); assert.match(cellHtml(host,0,0), /<b>126<\/b>/);
});
test('leitura falhou → mensagem humana', () => { screen.render(states.falhaEcu); assert.match(host.innerHTML, /Mapa indisponível/); assert.match(host.innerHTML, /A ECU não respondeu à leitura do Mapa K/); });
test('lendo → estado de leitura, sem botão de releitura', () => { screen.render(states.vazio /* mapK LENDO */); assert.match(host.innerHTML, /Lendo o Mapa K da ECU/); assert.doesNotMatch(host.innerHTML + html, /mapReadButton|Reler ECU/); });
test('nenhuma ponte antiga, nenhuma camada própria de operação', () => {
  for (const bad of ['startMapRead','mapReadResult','previewMapAdjustment','writeMap(','mapWriteOperation','operation-layer','is-writing','has-result']) assert.equal(src.includes(bad), false, bad);
  assert.doesNotMatch(mapSection(html), /operation-layer/);
});
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh node tests/ui/map-screen-intent.test.cjs`
Expected: `REMOTE_TEST=FAIL` — `MapScreen` exige `store, api` e chama `api.startMapRead`.

- [ ] **Step 3: Implement** — `render` só recarrega o editor quando `mapK.hash` mudou **e** (não há seleção **ou** chegou `CONCLUIDO` do `MAP_WRITE` pendente); grade, nudges e botão como hoje; botões de nudge e `#mapReviewButton` ≥ 76 px; célula/cabeçalho ≥ 44 px. `now.cell` destaca a célula AGORA (substitui `renderLiveContext`). Repoints: `map-workflow-e2e` passa a exigir `IntentAction.send('MAP_WRITE'` e a frase nova; `automotive-hmi-legibility` troca `44px` por `76px` em `.nudge-row button`, mantém o resto; `test_clean_ui_contract` troca `this.api.writeMap(this.review.items` por `IntentAction.send('MAP_WRITE'`; `test_mp48_k_map_axes_contract` troca `this.editor.load(result)` por `this.editor.load(mapK)`.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/map-screen-intent.test.cjs`, `tools/ci/remote-test.sh node tests/ui/map-preview-parity.test.cjs`, `tools/ci/remote-test.sh gradle "com.omegas.prohub.calibration.MapKPreviewParityTest"`, `tools/ci/remote-test.sh node tests/ui/map-editor-flow.test.cjs`
Expected: `REMOTE_TEST=PASS` (4×).

- [ ] **Step 5: Commit** `feat(ui): Mapa K grava por MAP_WRITE com overlay único; prévia local com paridade Kotlin`

---

### Task 7.9: Sessões (`evolucao` · `lista`)

**Files:**
- Create: `app/src/main/assets/ui/screens/sessions.js`
- Create: `app/src/main/assets/ui/styles-sessions-tools.css` (Sessões + Ferramentas; só `var(--…)`)
- Modify: `app/src/main/assets/ui/index.html` (seção `data-screen="sessions"` que a F6 deixou como placeholder → `<div id="sessionsScreenHost">`; `<link>` do CSS; `<script defer src="screens/sessions.js">`), `app/src/main/assets/ui/app.js` (`ensureScreen`, `:113-120`: `sessions`)
- Test: `tests/ui/sessions-screen.test.cjs`

**Interfaces:**
- Consumes: `session.current`, `session.history`, `operation`, `SubpageTabs`, `IntentAction`, `store` chave `sessions.expanded`.
- Produces:

```js
OmegasUi.SessionsModel = {
  nearStallsPerHour(item) → number | null,          // drivingMs ≥ 600000 ? round1(quaseApagou / (drivingMs/3600000)) : null
  evolutionSeries(history, limit = 20) → { id, label: 'dd/MM', index: number|null /*0..100*/, nearStallsPerHour: number|null, marker: 'ajuste'|null }[],   // mais antiga → mais nova
  rowText(item) → string,                           // 'dd/MM HH:mm · N min · A apagão(ões) · Q quase · 62% → 71%'
}
OmegasUi.SessionsScreen: class { constructor(host); enter(context?: { subpage?: 'evolucao'|'lista', sessionId?: string }); render(snapshot) }
```

- [ ] **Step 1: Write the failing test** (topo do arquivo: `process.env.TZ = 'UTC'`)

```js
test('subpáginas', () => assert.deepEqual(h.tabs[0].items, [{ id:'evolucao', label:'Evolução' }, { id:'lista', label:'Lista' }]));
test('apagões por hora de condução', () => {
  assert.equal(M.nearStallsPerHour({ stalls:{ quaseApagou:3 }, drivingMs:5400000 }), 2);
  assert.equal(M.nearStallsPerHour({ stalls:{ quaseApagou:3 }, drivingMs:300000 }), null);
});
test('série da evolução: antiga→nova, % inteiro, ▲ onde houve gravação', () => {
  const s = M.evolutionSeries(states.normal.session.history);
  assert.deepEqual(s.map(p => p.id), ['s-3','s-2','s-1']); assert.equal(s[1].index, 71); assert.equal(s[1].marker, 'ajuste');
  assert.equal(M.evolutionSeries(states.dadosLongos.session.history).length, 20);
});
test('evolução com < 2 sessões medidas', () => { screen.enter({ subpage:'evolucao' }); screen.render(states.vazio);
  assert.match(host.innerHTML, /Rode mais uma sessão para ver a evolução\./); });
test('linha da lista', () => { screen.enter({ subpage:'lista' }); screen.render(states.normal);
  assert.match(host.innerHTML, /03\/10 14:05 · 42 min · 1 apagão · 3 quase · 62% → 71%/); });
test('tocar expande no lugar, sem pedir nada', () => {
  h.tap(host, h.byExpand('s-2')); assert.equal(h.requests.length, 0); assert.equal(h.store.get()['sessions.expanded'], 's-2');
  assert.match(host.innerHTML, /Apagões: antes 0 · durante 1 · depois 0/); assert.match(host.innerHTML, /Aplicar ajuste · Concluído/);
  assert.match(host.innerHTML, /<details class="tech"><summary>Detalhes técnicos<\/summary>[\s\S]*# Resumo da sessão s-2/);
});
test('Exportar = um SESSION_EXPORT, sem overlay', () => { h.tap(host, h.byText('Exportar'));
  assert.deepEqual(h.requests, [{ intent:'SESSION_EXPORT', payload:{ sessionId:'s-2' } }]); assert.deepEqual(h.overlays, []); });
test('Épocas leva direto à sessão', () => { screen.enter({ subpage:'lista', sessionId:'s-2' }); screen.render(states.normal); assert.match(host.innerHTML, /data-expanded="s-2"/); });
test('sessão gravando', () => { screen.render(states.aprendendo); assert.match(host.innerHTML, /em andamento/); });
test('vazio e longos', () => {
  screen.render(states.vazio); assert.match(host.innerHTML, /Nenhuma sessão gravada ainda\. Ela começa sozinha ao conectar a ECU\./);
  screen.render(states.dadosLongos); assert.equal(count(host.innerHTML, /data-session-row/g), 50); assert.doesNotMatch(host.innerHTML, /undefined|NaN/);
});
test('didática', () => assert.match(host.innerHTML, /O que aconteceu, e estou chegando lá\?/));
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh node tests/ui/sessions-screen.test.cjs`
Expected: `REMOTE_TEST=FAIL` — `ENOENT … screens/sessions.js`.

- [ ] **Step 3: Implement** — gráfico SVG (sem canvas, sem lib): linha do índice 0–100 %, barras discretas de quase-apagões/h no eixo direito, ▲ nas sessões com recibo `CONCLUIDO` de `CURVE_WRITE|CURVE_RESET|CURVE_RESTORE|MAP_WRITE|UNDO`; ponto com `index=null` vira lacuna. Linhas da lista ≥ 76 px, uma expandida por vez, expansão sobrevive a revisões e troca de aba (chave `sessions.expanded`). Fases em português com o mesmo mapa de `SessionResumo.PHASE_WORDS` (`SessionResumo.kt:39-47`). `resumoMarkdown` sempre escapado e em `<pre>`.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/sessions-screen.test.cjs`
Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(ui): aba Sessões — evolução do índice e lista com fases, apagões e recibos`

---

### Task 7.10: Ferramentas (`drawers.js` → `screens/tools.js`)

**Files:**
- Create: `app/src/main/assets/ui/screens/tools.js` (de `drawers.js:193-346`, sem sessões e sem aprendizado)
- Delete: `app/src/main/assets/ui/components/drawers.js`
- Modify: `app/src/main/assets/ui/index.html:171-178` (`#toolsDrawer` → `<section class="screen" data-screen="tools"><div id="toolsScreenHost"></div></section>`; saem `#toolExportLearning`/`#toolImportLearning`; script de `drawers.js` → `screens/tools.js`), `app/src/main/assets/ui/app.js:19` (`ui.Drawers` sai) e `ensureScreen` (`tools`)
- Test: `tests/ui/tools-screen.test.cjs`; repoint `tests/ui/display-rules.test.cjs:86`, `tests/test_background_power_overlay_contract.py:17-76`, `tests/test_session_recording_always_automatic_contract.py:6-18`, `tests/test_tools_session_retention_contract.py`, `tests/test_autocal_recovery_export_surface.py`, `tests/test_autocal_canonical_session_contract.py:36-37`

**Interfaces:**
- Consumes: `settings.*`, `session.current`, `IntentAction`.
- Produces: `OmegasUi.ToolsScreen: class { constructor(host); render(snapshot) }` (render ignorado enquanto um campo do formulário de retenção tem foco).

- [ ] **Step 1: Write the failing test**

```js
const taps = [
  ['Autorizar', 'needs-permission', { intent:'OVERLAY_TOGGLE', payload:{ enabled:true } }],
  ['Desativar', 'on',               { intent:'OVERLAY_TOGGLE', payload:{ enabled:false } }],
  ['Grande', 'on',                  { intent:'SETTINGS_SET', payload:{ key:'overlay.scale', value:1.6 } }],
  ['Permitir', null,                { intent:'SETTINGS_SET', payload:{ key:'power.batteryExemption', value:true } }],
  ['Ligar GPS', null,               { intent:'SETTINGS_SET', payload:{ key:'gps.enabled', value:true } }],
  ['Exportar logs', null,           { intent:'SESSION_EXPORT', payload:{ scope:'logs' } }],
  ['Executar autoteste', null,      { intent:'SETTINGS_SET', payload:{ key:'diagnostics.selfTest', value:true } }],
  ['Exportar backup completo', null,{ intent:'SESSION_EXPORT', payload:{ scope:'app-data' } }],
];
for (const [label, overlay, expected] of taps) test(`${label}: um toque → um request, sem overlay`, () => {
  const h2 = fresh(withOverlay(states.normal, overlay)); h2.tap(h2.host, h2.byText(label));
  assert.deepEqual(h2.requests, [expected]); assert.deepEqual(h2.overlays, []);
});
test('retenção: Aplicar manda o formulário, mínimo 20 sessões', () => {
  setInput(host, 'data-retention-keep', '5'); h.tap(host, h.byText('Aplicar'));
  assert.deepEqual(h.requests.at(-1), { intent:'SETTINGS_SET', payload:{ key:'session.retention', value:{ telemetryEveryMs:500, maxSessionMb:256, keepSessions:20, captureRawUsb:false } } });
  assert.match(host.innerHTML, /um só arquivo ZIP/); assert.match(host.innerHTML, /Download\/Omegas/);
});
test('não redesenha com o dono digitando', () => { h.focus(host, input(host,'data-retention-keep')); const before = host.innerHTML; screen.render(states.dadosLongos); assert.equal(host.innerHTML, before); });
test('o que saiu', () => { screen.render(states.normal);
  for (const gone of ['recorded-session','Exportar ZIP','Exportar aprendizado','Importar aprendizado','.omegas','regiões gasolina'])
    assert.equal(host.innerHTML.includes(gone), false, gone); });
test('logs: filtro local, últimas 24, longos sem quebrar', () => {
  screen.render(states.dadosLongos); assert.equal(count(host.innerHTML, /data-log-line/g), 24);
  change(host, 'data-log-level', 'ERROR'); assert.equal(h.requests.length, 0);
  screen.render(states.vazio); assert.match(host.innerHTML, /Nenhum evento neste filtro\./);
});
test('log e autoteste dentro de Detalhes técnicos, último bloco', () => assert.ok(host.innerHTML.lastIndexOf('<details class="tech"') > host.innerHTML.lastIndexOf('Exportar backup completo')));
test('didática', () => assert.match(host.innerHTML, /Sistema: sessões gravadas, balão, GPS e diagnóstico\./));
test('drawers.js não existe mais', () => assert.equal(fs.existsSync(UI('components/drawers.js')), false));
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh node tests/ui/tools-screen.test.cjs`
Expected: `REMOTE_TEST=FAIL` — `ENOENT … screens/tools.js`.

- [ ] **Step 3: Implement** — blocos (unidades semânticas): Saúde do app · Bateria · Balão flutuante (estado + Autorizar/Ativar/Desativar + Pequeno/Médio/Grande) · GPS · `<details>` "Tamanho e retenção das sessões" (texto de `drawers.js:320-321` mantido) · "Exportar backup completo" · `<details class="tech"><summary>Detalhes técnicos · log do sistema (N)</summary>` com Exportar logs, Executar autoteste (resultado de `settings.diagnostics.selfTest`), filtros e linhas. Linhas e botões ≥ 76 px. Foco: `focusin`/`focusout` delegados ligam `this.editing`.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/tools-screen.test.cjs`, `tools/ci/remote-test.sh python tests/test_background_power_overlay_contract.py`, `tools/ci/remote-test.sh python tests/test_tools_session_retention_contract.py`
Expected: `REMOTE_TEST=PASS` (3×).

- [ ] **Step 5: Commit** `feat(ui): aba Ferramentas em screens/tools.js; sessões e aprendizado saem dela`

---

### Task 7.11: Portões globais — sem confirmação, legibilidade, render de cada aba × estado

**Files:**
- Create: `tests/ui/f7-no-confirm.test.cjs`, `tests/ui/f7-hmi-legibility.test.cjs`
- Create: `app/src/androidTest/java/com/omegas/prohub/F7SurfacesRenderTest.kt`
- Modify: `app/src/androidTest/java/com/omegas/prohub/DashboardLevelsRenderTest.kt` (apagar `mapFreshMp48ContextRender` `:704-725` e os 5 `autocal*` `:905-1092` com seus helpers que ficarem sem uso: `activateAutocal`, `autocalDom`, `autocalReferenceDom`, `install*AutoCal*Fixture`, `publishAutoCalSnapshot`)
- Modify: `app/src/androidTest/java/com/omegas/prohub/RefinoRenderTest.kt:572-643` (apagar `ferramentasEBalaoFlutuante`)
- Modify: `.github/workflows/verde-android-render-evidence.yml` (matriz `:47-100`: saem `autocal-fresh-control`, `autocal-visible-actions`, `autocal-reference-curves`, `autocal-sparse-zone-map`, `autocal-equivalence-shifted`, `map-fresh-context`, `ferramentas-balao-prompt`; entram os 5 abaixo com `class: F7SurfacesRenderTest`), `tools/ci/run_android_render_evidence.sh:81-84,93`

**Interfaces:**
- Produces: `F7SurfacesRenderTest` com `@Test fun autocalTodosOsEstados()`, `mapaKTodosOsEstados()`, `sessoesTodosOsEstados()`, `ferramentasTodosOsEstados()`, `faixaEPortaoDeConexao()`; evidência `omegas-evidence/f7-<aba>-<estado>.{json,png}`.

- [ ] **Step 1: Write the failing tests**

`f7-no-confirm.test.cjs`:

```js
const files = ['screens/autocal/model.js','screens/autocal/aquisicao.js','screens/autocal/referencia.js','screens/autocal/epocas.js',
  'screens/autocal/index.js','screens/map.js','screens/sessions.js','screens/tools.js','components/intent-action.js',
  'components/connection-gate.js','components/vehicle-status-strip.js','index.html'];
for (const f of files) test(`${f} sem confirmação`, () => {
  const s = read(f);
  for (const bad of [/\bconfirm\(/, /autocalReview/, /data-autocal-confirm/, /data-autocal-cancel/, /review-layer/, /operation-layer/,
                     /Tem certeza/, /Executar agora/, /prepare\(/, /preparationId/]) assert.doesNotMatch(s, bad, `${f} ${bad}`);
});
test('cada intent usado pela F7 está no enum do índice', () => {
  const used = new Set(files.flatMap(f => [...read(f).matchAll(/(?:data-intent="|send\(')([A-Z_]+)/g)].map(m => m[1])));
  const allowed = ['CURVE_WRITE','CURVE_RESET','CURVE_RESTORE','MAP_WRITE','REFERENCE_FREEZE','AUTOCAL_RELEARN','AUTOCAL_PAUSE','AUTOCAL_RESUME',
    'AUTOCAL_RESET_GAS','AUTOCAL_RESET_PETROL','SESSION_EXPORT','OVERLAY_TOGGLE','SETTINGS_SET','UNDO'];
  for (const i of used) assert.ok(allowed.includes(i), i);
});
```

`f7-hmi-legibility.test.cjs` (padrão de `automotive-hmi-legibility.test.cjs`, regex sobre CSS):

```js
const px = (css, sel, prop) => Number((css.match(new RegExp(esc(sel) + '\\s*\\{[^}]*' + prop + ':\\s*(\\d+)px', 's')) || [])[1]);
for (const [file, sel] of [['styles-autocal-cockpit.css','.autocal-actions button'], ['styles-autocal-cockpit.css','.autocal-matrix-cell'],
  ['styles-autocal-cockpit.css','.autocal-epoch-row'], ['styles-sessions-tools.css','.session-row'], ['styles-sessions-tools.css','.tools-row button'],
  ['styles.css','.nudge-row button'], ['styles.css','#mapReviewButton'], ['styles-shell-status.css','.vehicle-status-strip [data-fact]']])
  test(`${sel} ≥ 76px`, () => assert.ok(px(read(file), sel, 'min-height') >= 76));
test('[data-critical] ≥ 24px', () => assert.ok(px(read('styles.css'), '[data-critical]', 'font-size') >= 24));
test('exceção declarada: matriz do Mapa K ≥ 44px', () => assert.ok(px(read('styles-refine.css'), '.map-k-cell', 'min-height') >= 44));
test('sem cor nova no CSS da F7', () => assert.doesNotMatch(read('styles-sessions-tools.css'), /#[0-9a-fA-F]{3,8}\b|rgba?\(/));
```

`F7SurfacesRenderTest.kt` (infra copiada de `RefinoRenderTest.kt:48-60, 645-694`: `launch`, `saveEvidence`, `evalJson`, `evalRaw`, `waitFor`):

```kotlin
private val fixture: JSONObject by lazy { JSONObject(instrumentation.context.assets.open("f7-snapshot-states-v1.json").bufferedReader().readText()) }

/** Troca snapshot/request do Omegas da página pelo fixture; nada chega à ECU. */
private fun install(scenario: ActivityScenario<MainActivity>, state: String, route: String, subpage: String?) {
    val snap = fixture.getJSONObject("states").getJSONObject(state).toString()
    evalRaw(scenario, """(() => { const d = OmegasUi.IntentAction.deps(); const f = $snap; window.__f7Requests = [];
      d.omegas.snapshot = () => f; d.omegas.request = r => { window.__f7Requests.push(r); return 'test-' + window.__f7Requests.length; };
      OmegasApp.router.navigate('$route', ${if (subpage == null) "undefined" else "{ subpage: '$subpage' }"}); window.OmegasOnRevision(f.revision); return 'ok'; })()""")
    SystemClock.sleep(400L)
}

private fun measure(scenario: ActivityScenario<MainActivity>): JSONObject = evalJson(scenario, MEASURE_JS)
// MEASURE_JS devolve: text (innerText de .screen-host + #vehicleStatusStrip), smallTargets[] (button/[data-intent]/[data-select]/[data-nav]
// visíveis com altura ou largura < 76, exceto .map-k-cell/.map-axis-header com < 44), smallCritical[] ([data-critical] visível com
// font-size < 24), hScroll (scrollWidth > clientWidth em .screen-host), requests (window.__f7Requests.length), viewport w/h.

private fun assertState(tab: String, state: String, dom: JSONObject) {
    val expect = fixture.getJSONObject("expect").getJSONObject(tab).getString(state)
    assertTrue("$tab/$state mostra '$expect'", dom.getString("text").contains(expect))
    assertFalse("$tab/$state sem NaN/undefined", Regex("NaN|undefined").containsMatchIn(dom.getString("text")))
    assertEquals("$tab/$state alvos ≥ 76 px: ${dom.getJSONArray("smallTargets")}", 0, dom.getJSONArray("smallTargets").length())
    assertEquals("$tab/$state texto crítico ≥ 24 px: ${dom.getJSONArray("smallCritical")}", 0, dom.getJSONArray("smallCritical").length())
    assertFalse("$tab/$state sem rolagem horizontal", dom.getBoolean("hScroll"))
    assertEquals("render nunca pede nada à ECU", 0, dom.getInt("requests"))
    assertEquals(1280, dom.getInt("viewportWidth")); assertEquals(720, dom.getInt("viewportHeight"))
}

@Test fun autocalTodosOsEstados() = runMatrix("autocal") { state -> when (state) {
    "semReferencia", "ecuReaprendeu" -> "referencia"; "vazio", "dadosLongos" -> "epocas"; else -> "aquisicao" } }
@Test fun mapaKTodosOsEstados() = runMatrix("map") { null }
@Test fun sessoesTodosOsEstados() = runMatrix("sessions") { state -> if (state == "normal") "evolucao" else "lista" }
@Test fun ferramentasTodosOsEstados() = runMatrix("tools") { null }
@Test fun faixaEPortaoDeConexao() = runMatrix("global") { null }   // rota autocal; mede strip + gate
// runMatrix(tab, subpageFor): para cada estado em fixture.matrix[tab]: install → measure → saveEvidence("f7-$tab-$state", …) → assertState
```

`expect` no fixture (preenchido nesta task): autocal: `semCabo:'Sem cabo'`, `conectando:'Conectando à ECU'`, `ecuSemAquisicao:'Retomar aquisição'`, `semReferencia:'Sem referência · modo provisório'`, `ecuReaprendeu:'A ECU reaprendeu gasolina'`, `aprendendo:'coletando'`, `normal:'Pausar aquisição'`, `executando:'Zerar gás · Executando'`, `falhaTransporte:'Zerar gás · Falhou'`, `falhaEcu:'Zerar gás · Falhou'`, `vazio:'Nenhuma sessão gravada ainda.'`, `dadosLongos:'2 épocas'`; map: `vazio:'Lendo o Mapa K da ECU'`, `normal:'Selecione células'`, `falhaEcu:'Mapa indisponível'`, …; sessions: `normal:'Evolução'`, `aprendendo:'em andamento'`, `vazio:'Nenhuma sessão gravada ainda. Ela começa sozinha ao conectar a ECU.'`, `semCabo:'Sem cabo'` (faixa), …; tools: `normal:'Exportar backup completo'`, `vazio:'Nenhum evento neste filtro.'`, `semCabo:'Sem cabo'` (faixa), …; global: `semCabo:'Sem cabo'`, `conectando:'Conectando'`, `normal:'Conectado'`, `executando:'Executando'`, `falhaTransporte:'Falhou'`, `falhaEcu:'Falhou'`.

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh node tests/ui/f7-hmi-legibility.test.cjs`
Expected: `REMOTE_TEST=FAIL` — falta a regra `[data-critical]` em `styles.css` (ou um seletor < 76).
Run (androidTest não tem `kind` no laço remoto; usa o workflow de render já existente): `git push && gh workflow run verde-android-render-evidence.yml --ref work/platina-f7-autocal-mapa-sessoes-ferramentas && sleep 15 && gh run watch "$(gh run list --workflow verde-android-render-evidence.yml --branch work/platina-f7-autocal-mapa-sessoes-ferramentas -L1 --json databaseId -q '.[0].databaseId')" --exit-status`
Expected: FAIL nos jobs `f7-*` (`No tests found` antes do arquivo existir, ou o primeiro `assertState` que não casar).

- [ ] **Step 3: Implement** — `styles.css`: `[data-critical]{font-size:24px}` (e 32 px no título do gate); ajustar o que o render apontar (é a medição real que manda). Remover os métodos e linhas de matriz/script listados.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/f7-no-confirm.test.cjs`, `tools/ci/remote-test.sh node tests/ui/f7-hmi-legibility.test.cjs`, e de novo o `gh workflow run verde-android-render-evidence.yml …` acima; depois `gh run download <id> -n <artefato f7-autocal>` e abrir 3 prints (`f7-autocal-normal.png`, `f7-map-executando.png`, `f7-sessions-dadosLongos.png`) para conferir à vista.
Expected: `REMOTE_TEST=PASS` (2×); render run verde com os 5 jobs `f7-*` e os demais (Agora/Curva/Refino da F6) intocados e verdes.

- [ ] **Step 5: Commit** `test(render): F7 — cada aba × estado obrigatório em 1280×720, alvos ≥76 px e texto ≥24 px`

---

### Task 7.12: Portão final e PR

**Files:** nenhum novo.

- [ ] **Step 1: Varredura de consumidores mortos**

Run: `git grep -nE "autocal-cockpit|AutoCalCockpit|components/drawers\.js|ui\.Drawers|toolExportLearning|toolImportLearning|mapBackToLearning|autocalReview|data-autocal-action|operation-layer" -- app tests tools .github`
Expected: vazio.

- [ ] **Step 2: Gate completo**

Run: `tools/ci/remote-test.sh checks ""`
Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 3: Descrição do PR**

`gh pr edit --body-file -` com: o que mudou (abas 02/04/06/07, faixa, Sem cabo/Conectando, cockpit −1900 linhas, CSS −900, `drawers.js` apagado) · classe de prova: 1 (contratos), 2 (fixture sintético em node), 4 (render no emulador com print) · **não provado**: toque real na multimídia, balão sobre outros apps, cabo USB real, ECU física; intents de Ferramentas por `SETTINGS_SET`/`SESSION_EXPORT` dependem do handler da F5 (contrato da Task 7.1). Terminar com:

```
🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01YN94YqPpeRPSaeNkh2QkF7
```

- [ ] **Step 4: Pronto e mescla**

Run: `gh pr ready && gh pr checks --watch && gh pr merge --merge`
Expected: `OMEGAS PLATINA CI` verde no SHA do PR; PR mesclado em `OmegasPlatina`.

---

## Cobertura da spec

| Requisito (spec) | Task |
|---|---|
| §3.1 linha 02 Mapa K: ajuste por célula, overlay de gravação como hoje → overlay único | 7.8 |
| §3.1 (bugs de navegação) "Voltar ao aprendizado" → rota `learning` (`map.js:57`) | 7.8 |
| §3.1 linha 04 AutoCal `Aquisição` (matriz 18 × gasolina/GNV, Pausar/Retomar, Reaprender, Zerar gás/gasolina, inspector) | 7.3, 7.4 |
| §3.1 linha 04 `Referência` (card, Congelar / Congelar de novo, diferença contra a ECU agora) | 7.5 |
| §3.1 linha 04 `Épocas` ("Ver sessões" de hoje) + "Leitura anterior" no lugar | 7.5 |
| §3.1 AutoCal "sem AutoMatch manual e sem Nova aquisição completa"; "Ações avançadas" sai | 7.4, 7.7 |
| §3.1 linha 06 Sessões `Evolução` (índice sessão a sessão, apagões/hora) · `Lista` (duração, apagões, índice início→fim, expande: fases, recibos; Exportar) | 7.9 |
| §3.1 linha 07 Ferramentas: retenção, log, autoteste, balão e autorização, GPS; sem exportar/importar aprendizado | 7.10 |
| §3.1 subpágina = tablist na mesma posição, última lembrada | 7.6, 7.9 |
| §3.1 toda mutação abre o mesmo overlay (`Desfazer`/`Voltar`) | 7.1, 7.4, 7.5, 7.8 |
| §3.1 "Detalhes técnicos" como último bloco | 7.4, 7.5, 7.9, 7.10 |
| §3.1 uma linha didática por aba | 7.6, 7.9, 7.10 |
| §0.2-2 todo botão é um toque, sem confirmação | 7.1, 7.4, 7.11 |
| §2.1 tela só pela ponte `Omegas`; redesenho por revisão, sem polling | 7.1, 7.6, 7.7 |
| §2.2 intents `MAP_WRITE`, `REFERENCE_FREEZE`, `AUTOCAL_*`, `SESSION_EXPORT`, `OVERLAY_TOGGLE`, `SETTINGS_SET` | 7.4, 7.5, 7.8, 7.9, 7.10 |
| §3.2 faixa de status global (conexão · combustível · AGORA · operação) | 7.2 |
| §3.2 estados sem cabo · conectando (tela cheia sobre o conteúdo) | 7.2 |
| §3.2 alvo ≥ 76 px, texto crítico ≥ 24 px, verificado no render do CI | 7.11 |
| §3.2 estados obrigatórios por superfície renderizados no CI (matriz aba × estado; N/A: Mapa/Sessões/Ferramentas não têm "ECU sem aquisição", "sem Referência", "ECU reaprendeu"; Ferramentas não tem "aprendendo") | 7.1, 7.11 |
| §3.2 contexto preservado ao trocar de aba (seleção, linha expandida) | 7.4, 7.8, 7.9 |
| §4.3 `screens/autocal-cockpit.js`/`map.js` reescritos e seu CSS | 7.6, 7.7, 7.8 |
| §4.6 testes editados `autocal-cockpit`, `autocal-reacquisition-ux` | 7.7 |
| §8-5 abas, subpáginas, faixa e estados sem cabo/conectando em 1280×720 | 7.11 |
