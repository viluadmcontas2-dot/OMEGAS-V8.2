# Fatia 6 — UI Agora + Curva K + Refino Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refazer o conteúdo das abas 01 Agora, 03 Curva K e 05 Refino sobre o `snapshot()` único, com subpáginas por tablist, overlay de operação único e "Detalhes técnicos", e tirar Sugestões do trilho.

**Architecture:** Um alimentador (`core/snapshot-feed.js`) puxa `Omegas.snapshot` só quando chega revisão nova, calcula quais seções mudaram e entrega ao `Store`; cada tela redesenha só o pedaço da seção que mudou. Três componentes compartilhados (`SubpageTabs`, `OperationOverlay`, `EquivalenceCanvas`) dão a mesma lógica às três abas; toda mutação sai por `Omegas.request` e abre o mesmo overlay. Agora só mostra e navega.

**Tech Stack:** WebView + JS vanilla (testes `node --test` com `vm`), CSS só com `var(--…)` existentes, androidTest Kotlin (WebView real 1280×720), contratos Python.

**Spec:** docs/superpowers/specs/2026-10-03-omegas-platina-norte-unico-design.md  ·  **Índice/contrato:** docs/superpowers/plans/2026-10-03-00-norte-unico-index.md

Branch: `work/platina-f6-ui-agora-curva-refino` a partir de `OmegasPlatina` (com F1–F5 mesclados). Todo commit termina com as duas linhas:

```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01YN94YqPpeRPSaeNkh2QkF7
```


> **Antes de começar:** leia a seção **Reconciliação entre planos** do índice (`2026-10-03-00-norte-unico-index.md`). Onde este plano divergir dela (intents, payloads, formato do snapshot, comando do emulador), vale o índice.
## Global Constraints

Todas as do índice, mais:

- Linhas citadas foram medidas em `OmegasPlatina` antes da F1; F2–F5 podem tê-las deslocado: localize sempre pelo símbolo citado, nunca só pelo número.
- **Agora nunca chama `Omegas.request`**; só lê o snapshot e navega (`router.navigate(route, { subpage })`).
- Telas e componentes da F6 não usam `scheduler`, `addHook`, `setInterval` nem `setTimeout` de polling; redesenham só pelas seções em `state.snapChanged`; o AGORA do canvas usa `requestAnimationFrame` (no máximo 1 desenho por quadro).
- Componentes (`SubpageTabs`, `OperationOverlay`, `EquivalenceCanvas`) montam DOM com `createElement`, nunca com `innerHTML` (testáveis no harness e sem injeção).
- Todo acesso à ponte passa por um único acessor `OmegasUi.omegas()` (Task 6.1); nenhum arquivo da F6 toca `NativeApi`/`AutoCalApi`.
- Payloads pinados: `CURVE_WRITE {points:[{index,currentRaw,targetRaw}], reason}` (mesmo formato do `startCurveBatchWrite(pointsJson, reason)` de hoje, `V7JavascriptBridge.kt:226`), `CURVE_RESTORE {backupId}`, `CURVE_RESET {}`, `UNDO {receiptId}` (overlay) ou `UNDO {}` (= última operação desfazível da sessão). Raw Q14 = `Math.round(k * 16384)`, aceito só em `0,6 ≤ k ≤ 4,0`.
- Strings que outros testes procuram e ficam: `const ROUTES = ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools']` (com espaços: `autocal-test-sensitivity.test.cjs:46` muta `'curve', 'autocal', 'refino'`); botões `autocal`→`refino` adjacentes e no mesmo formato no `index.html` (`autocal-test-sensitivity.test.cjs:48-49`); `dashboard.js` mantém `LEVELS RAW`, `id="dashLevelsRaw"`, `level_raw`; `refino.js` mantém `function finite(value)` que rejeita `null`/`undefined`.
- CSS novo só em `app/src/main/assets/ui/styles-norte.css`, sem cor literal (`#hex`, `rgb(`, `hsl(`); o canvas lê as cores de `getComputedStyle(document.documentElement)` (`--ok --warn --danger --muted --dim --accent --text --petrol`).
- Mapa K e AutoCal ficam com a F7; aqui só sai o "Voltar ao aprendizado" do `map.js` (bug de navegação da spec §3.1).
- Testes Android rodam no PR (`autocal-mission-evidence.yml` → `verde-android-render-evidence.yml`); o "Run" deles é `git push && gh pr checks --watch` lendo a linha `SCENARIO_RESULT=<cenário>:PASS|FAIL`.

### Formato do snapshot que a F6 lê

`OmegasUi.omegas().snapshot(sinceRevision)` devolve objeto JS `{ revision, <seções> }`. Seção ausente na resposta = não mudou.

| Seção | Campos lidos | Origem |
|---|---|---|
| `points` | array de 30 `EquivalencePoint` com os nomes do contrato (`index, axisMs, kCurrent, kTarget, mixture, tolerance, roughnessRatio, nearStallRatio, slope, usage, samples, sources, state`) | contrato |
| `nextAction` | `{kind, text, route, subpage, pointIndexes}` + **`proposal: {currentRaw:int[30], refinedRaw:int[30]} \| null`** quando `kind=APPLY` | contrato + **lacuna** |
| `index` | `{value: fração 0–1 \| null, coverage: int, provisional: bool, trend: number[]}` (`trend` = índice por sessão anterior, antigo→recente) | assumido |
| `reference` | `null \| {id, frozenAt, ecuDrift: fração \| null, points:[{mapBar,petrolMs,maturity}], ownPetrol:[{mapBar,petrolMs,samples,dispersion,source,divergence}]}` | parcial |
| `now` | `{valid: bool, petrolMs, mapBar, rpm}` | assumido |
| `connection` | `{state: 'SEM_CABO' \| 'CONECTANDO' \| 'ONLINE'}` | assumido |
| `live` | `{fuel, level_raw}` | assumido |
| `operation` | `null \| {receiptId, intent, stage: OpStage, failureKind: FailureKind \| null, reason: string \| null, undoable: bool, detail:{sent, readback, durationMs}}` | parcial |
| `session` | `{phase: string (de EquivalencePhases.json().phase), curveBackups:[{id,label,createdAt,kind:'FOTO'\|'MANUAL'}]}` | assumido |

Linhas "assumido/lacuna" precisam bater com o serializador da F5 antes da Task 6.1; se a F5 usou outro nome, troca-se aqui (um lugar por campo: os modelos puros de cada tela).

## Review Focus

1. **Agora com `nextAction.kind=APPLY` e o dono toca o botão** → só `navigate('curve', {subpage:'equivalencia'})`, zero chamadas a `request`. Teste: Task 6.8 `Agora nunca envia intent`.
2. **`now` muda a cada 80 ms com `points` parado** → a camada base do canvas não redesenha; a camada AGORA desenha ≤ 1 vez por quadro. Teste: Task 6.7 `AGORA redesenha só a própria camada`.
3. **Operação termina em `FALHOU` por `TRANSPORTE` vs `ECU`** → textos distintos (`✗ A comunicação com a ECU caiu` / `✗ A ECU não confirmou o valor`), `Desfazer` visível só se `undoable`. Teste: Task 6.6.
4. **Entrar em Curva K via Agora com `subpage` diferente da última lembrada** → abre a pedida; trocar de aba e voltar reabre a última. Teste: Tasks 6.2 e 6.5.
5. **Ponto sem dado (`mixture`/`usage`/`index.value` nulos, ECU recém-instalada)** → "—", nunca `NaN`, `0%` falso ou `undefined`. Testes: Tasks 6.8 e 6.11 (modelos) e 6.12 (DOM real, 9 estados).

---

### Task 6.1: Alimentador por revisão (`SnapshotFeed`) e harness de teste

**Files:**
- Create: `app/src/main/assets/ui/core/snapshot-feed.js`
- Modify: `app/src/main/assets/ui/app.js` (`initialize` :543-557; novo `renderActiveScreen` ao lado de `ensureScreen` :113-119)
- Modify: `app/src/main/assets/ui/index.html` (scripts :185-198: `core/snapshot-feed.js` logo após `core/omegas.js`)
- Create: `tests/ui/_norte-harness.cjs` (não casa `*.test.cjs`; não é descoberto)
- Test: `tests/ui/snapshot-feed.test.cjs`

**Interfaces:**
- Consumes: `core/omegas.js` (F5) — `snapshot(since)`, `request({intent,payload}) → receiptId`, `onRevision(cb)`.
- Produces:
```js
OmegasUi.omegas()                                   // → o objeto exportado por core/omegas.js (OmegasUi.Omegas) ou null
OmegasUi.SnapshotFeed.SECTIONS                      // ['connection','live','now','reference','points','index','nextAction','operation','session','autocal','settings']
OmegasUi.SnapshotFeed.changedSections(prev, next)   // → string[] (ordem de SECTIONS), compara JSON.stringify por seção
OmegasUi.SnapshotFeed.start(store, omegas)          // → stop(); patch: { snap, snapChanged: string[], snapRevision }
// Contrato de tela: instances[route].renderSnapshot(snap, changed: string[])
```
- Harness: `loadUi(relPaths, extras) → { window, document, flushFrames() }`; `makeElement(tag)` com `dataset`, `classList`, `hidden`, `disabled`, `textContent`, `value`, `children`, `appendChild`, `replaceChildren`, `setAttribute/getAttribute`, `addEventListener`, `click()`, `querySelector(All)` para `#id`, `.classe`, `tag`, `[data-x]`, `[data-x="v"]` (mapeia para `dataset` camelCase), `getContext('2d')` que grava `calls` (nome do método); `requestAnimationFrame` enfileira e `flushFrames()` executa; `getComputedStyle()` devolve `getPropertyValue: () => '#000'`; `window.OmegasApp` livre para o teste preencher.

- [ ] **Step 1: Write the failing test** `tests/ui/snapshot-feed.test.cjs`

```js
const { loadUi } = require('./_norte-harness.cjs');
test('changedSections compara por seção', () => {
  const F = loadUi(['core/store.js', 'core/snapshot-feed.js']).window.OmegasUi.SnapshotFeed;
  assert.deepEqual(F.changedSections({ now: { petrolMs: 4 }, points: [1] }, { now: { petrolMs: 5 }, points: [1] }), ['now']);
  assert.deepEqual(F.changedSections(null, { revision: 1, index: { value: 0.5 } }), ['index']);
});
test('várias revisões no mesmo quadro = 1 leitura; revisão repetida não repinta', () => {
  const { window, flushFrames } = loadUi(['core/store.js', 'core/snapshot-feed.js']);
  let rev = 1, calls = 0, cb = null;
  const omegas = { onRevision: f => { cb = f; }, snapshot: () => { calls += 1; return { revision: rev, now: { petrolMs: rev } }; } };
  const store = new window.OmegasUi.Store({}); let patches = 0; store.subscribe(() => { patches += 1; });
  window.OmegasUi.SnapshotFeed.start(store, omegas);
  assert.equal(calls, 1); assert.equal(patches, 1); assert.deepEqual([...store.get().snapChanged], ['now']);
  rev = 2; cb(2); cb(2); cb(2); flushFrames();
  assert.equal(calls, 2); assert.equal(patches, 2); assert.equal(store.get().snapRevision, 2);
  cb(2); flushFrames(); assert.equal(patches, 2);
});
test('seção ausente na resposta mantém a anterior', () => { /* snapshot devolve só {revision:3, now} → store.get().snap.points continua o de antes */ });
```

- [ ] **Step 2: Run to see it fail** — primeiro push abre o PR rascunho:

Run: `git push -u origin work/platina-f6-ui-agora-curva-refino && gh pr create --draft --base OmegasPlatina --title "F6: UI Agora + Curva K + Refino" --body "WIP" && tools/ci/remote-test.sh node tests/ui/snapshot-feed.test.cjs`
Expected: `REMOTE_TEST=FAIL` com `ENOENT ... core/snapshot-feed.js`.

- [ ] **Step 3: Implement** `snapshot-feed.js` conforme Interfaces (pull inicial em `start`; `onRevision` só agenda um `requestAnimationFrame`; pull ignora `revision <= snapRevision`; merge seção a seção; `patch` só se `changed.length`). `app.js`: em `initialize`, `ui.SnapshotFeed.start(store, ui.omegas())`; `store.subscribe(renderActiveScreen)` onde `renderActiveScreen(state)` chama `instances[state.route]?.renderSnapshot?.(state.snap, state.snapChanged)` só quando `state.snapChanged` mudou de referência; `activateRoute` chama `renderSnapshot(snap, SECTIONS)` (primeira pintura completa).

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/snapshot-feed.test.cjs`
Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(ui): alimentador de snapshot por revisão e harness de teste da F6`

---

### Task 6.2: Roteador, trilho de 7 itens, cabeçalho global e Sessões provisória

**Files:**
- Modify: `app/src/main/assets/ui/core/router.js` (`ROUTES` :4; `navigate` :27-35; novo `ROUTE_META`)
- Modify: `app/src/main/assets/ui/index.html` (trilho :19-28; cabeçalho :37-40 — `<p>` :39 ganha `id="routeQuestion"`; tela Sugestões :166-169 sai; nova tela `sessions`)
- Modify: `app/src/main/assets/ui/app.js` (`routeMeta` :22-31 sai → `ui.ROUTE_META`; `renderShell` :121-138: escreve `routeQuestion`; `autocal-focus` :125 deixa de incluir `refino`)
- Modify (testes existentes): `tests/ui/app-shell-runtime.test.cjs:57-65`, `tests/test_clean_ui_contract.py` (`test_seven_static_human_destinations_with_refino_below_autocal`; apagar `test_suggestions_route_is_review_navigation_not_auto_apply`)
- Test: `tests/ui/norte-router.test.cjs`

**Interfaces:**
- Produces:
```js
OmegasUi.ROUTES = ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools']
OmegasUi.ROUTE_META = { dashboard: ['01 · AGORA','Agora','Como estou e o que faço agora?'],
  map: ['02 · MAPA K','Mapa K','Ajuste fino por célula RPM × MAP.'],
  curve: ['03 · CURVA K','Curva K','Onde o GNV difere da gasolina e o que gravo?'],
  autocal: ['04 · AUTOCAL','AutoCal','O que a ECU já aprendeu?'],
  refino: ['05 · REFINO','Refino','Em que fase estou e o que falta?'],
  sessions: ['06 · SESSÕES','Sessões','O que aconteceu, e estou chegando lá?'],
  tools: ['07 · FERRAMENTAS','Ferramentas','Sistema: retenção, log, autoteste, balão e GPS.'] }
Router.navigate(route, { subpage }?)   // grava store['subpage.<route>'] = subpage no mesmo patch da rota
```
- Tela `sessions` (F7 preenche): `<section class="screen" data-screen="sessions"><div class="detail-empty" data-sessions-empty><b>Sessões</b><span>Nenhuma sessão para mostrar ainda.</span></div></section>`.

- [ ] **Step 1: Write the failing test** `tests/ui/norte-router.test.cjs`

```js
const html = read('app/src/main/assets/ui/index.html');
test('rótulos e ordem do trilho', () => {
  const rail = [...html.matchAll(/data-route="([^"]+)"[^>]*><i>(\d\d)<\/i><span>([^<]+)<\/span>/g)];
  assert.deepEqual(rail.map(m => m[1]), ['dashboard','map','curve','autocal','refino','sessions','tools']);
  assert.equal(rail.map(m => `${m[2]} ${m[3]}`).join(' · '), '01 Agora · 02 Mapa K · 03 Curva K · 04 AutoCal · 05 Refino · 06 Sessões · 07 Ferramentas');
  assert.doesNotMatch(html, /suggestionCount|data-screen="suggestions"|data-route="suggestions"/);
  assert.match(html, /data-screen="sessions"[\s\S]*Nenhuma sessão para mostrar ainda\./);
  assert.match(html, /<p id="routeQuestion">/);
});
test('ROUTES e ROUTE_META', () => {
  const ui = loadUi(['core/store.js','core/router.js']).window.OmegasUi;
  assert.deepEqual([...ui.ROUTES], ['dashboard','map','curve','autocal','refino','sessions','tools']);
  assert.deepEqual(Object.keys(ui.ROUTE_META), [...ui.ROUTES]);
  assert.equal(ui.ROUTE_META.curve[2], 'Onde o GNV difere da gasolina e o que gravo?');
});
test('navigate com subpágina lembra por rota', () => {
  const { window } = loadUi(['core/store.js','core/router.js']);
  const store = new window.OmegasUi.Store(window.OmegasUi.createInitialState());
  const router = new window.OmegasUi.Router(store);
  assert.equal(router.navigate('curve', { subpage: 'backups' }), true);
  assert.equal(store.get().route, 'curve'); assert.equal(store.get()['subpage.curve'], 'backups');
  router.navigate('refino', { subpage: 'pontos' }); router.navigate('dashboard');
  assert.equal(store.get()['subpage.refino'], 'pontos');
  assert.equal(router.navigate('suggestions'), false); assert.equal(router.navigate('learning'), false);
});
test('cabeçalho é global: refino não esconde o header', () => {
  assert.doesNotMatch(read('app/src/main/assets/ui/app.js'), /autocal-focus', state\.route === 'autocal' \|\| state\.route === 'refino'/);
});
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh node tests/ui/norte-router.test.cjs`
Expected: `REMOTE_TEST=FAIL` no primeiro `deepEqual` (`'suggestions'` no lugar de `'sessions'`).

- [ ] **Step 3: Implement** conforme Interfaces. Rótulos: `<span>Mapa K</span>`, `<span>Curva K</span>`, `data-route="sessions"><i>06</i><span>Sessões</span>`; o `<em id="suggestionCount">` sai. `app-shell-runtime.test.cjs`: `navigate('suggestions')` passa a esperar `false` e entra `assert.equal(router.navigate('sessions'), true)`. `test_clean_ui_contract.py`: lista esperada e rótulos `('Agora','Mapa K','Curva K','AutoCal','Refino','Sessões','Ferramentas')`, literal `const ROUTES` novo.

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh node tests/ui/norte-router.test.cjs` → `REMOTE_TEST=PASS`; depois `tools/ci/remote-test.sh python tests/test_clean_ui_contract.py` → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh node tests/ui/app-shell-runtime.test.cjs` → `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `feat(ui): trilho 01–07 do Norte Único, cabeçalho global e Sessões provisória`

---

### Task 6.3: Sugestões sai do app (código de tela e contador)

**Files:**
- Modify: `app/src/main/assets/ui/app.js` (o que restar após a F2 de: `updateSuggestionBadge` chamada :158 e função :356-364; `refinementPhaseCached` :346-354; `refinementSuggestion` :365-371; `renderPersistentSuggestions` :372-448; `suggestionTargetLabel`/`suggestionMagnitude` :324-342; `selectedSuggestionIds` :20; rota `predictor`/`suggestions` em `refreshContext` :284-285, :317-320 e `activateRoute` :453, :460-464, :490-494)
- Modify: `app/src/main/assets/ui/components/drawers.js` (:27 `this.suggestions`; :45-53 `#suggestionsButton`/`#toolsButton`/`[data-close-drawer]`; :149, :151 toggles de sugestão; :159-184 `renderSuggestions`)
- Modify: `app/src/main/assets/ui/core/display-rules.js` (`pendingSuggestionCount` :93-107 e export :153)
- Modify (teste existente): `tests/ui/display-rules.test.cjs:75-89` (sai o bloco de `pendingSuggestionCount`/`suggestionCount`)
- Test: `tests/test_norte_ui_poda_contract.py`

**Interfaces:** Produces nada; remove símbolos.

- [ ] **Step 1: Write the failing test** `tests/test_norte_ui_poda_contract.py`

```python
import pathlib
root = pathlib.Path(__file__).resolve().parents[1]
ui = root / "app/src/main/assets/ui"
app = (ui / "app.js").read_text(encoding="utf-8")
drawers = (ui / "components/drawers.js").read_text(encoding="utf-8")
rules = (ui / "core/display-rules.js").read_text(encoding="utf-8")
for gone in ("renderPersistentSuggestions", "updateSuggestionBadge", "refinementSuggestion", "refinementPhaseCached",
             "suggestionTargetLabel", "suggestionMagnitude", "selectedSuggestionIds", "suggestionCount",
             "'suggestions'", "suggestionsOpen", "'predictor'"):
    assert gone not in app, f"app.js: {gone}"
for gone in ("suggestionsButton", "toolsButton", "data-close-drawer", "renderSuggestions", "suggestionDrawer",
             "OmegasSuggestionModel", "suggestionsOpen"):
    assert gone not in drawers, f"drawers.js: {gone}"
assert "pendingSuggestionCount" not in rules
print("NORTE_UI_PODA=PASS")
```

- [ ] **Step 2: Run to see it fail**

Run: `tools/ci/remote-test.sh python tests/test_norte_ui_poda_contract.py`
Expected: `REMOTE_TEST=FAIL` com `AssertionError: app.js: renderPersistentSuggestions` (ou o primeiro símbolo que a F2 deixou).

- [ ] **Step 3: Remove** os trechos listados. `toolsOpen` fica (Ferramentas usa em `drawers.js:150,154`).

- [ ] **Step 4: Run to verify**

Run: `tools/ci/remote-test.sh python tests/test_norte_ui_poda_contract.py` → `REMOTE_TEST=PASS` (`NORTE_UI_PODA=PASS`); `tools/ci/remote-test.sh node tests/ui/display-rules.test.cjs` → `REMOTE_TEST=PASS`.

- [ ] **Step 5: Commit** `refactor(ui): remove a aba Sugestões e o contador do trilho`

---

### Task 6.4: Rota `learning` inexistente e chave morta do Store

**Files:**
- Modify: `app/src/main/assets/ui/screens/map.js` (:45-51 cria `#mapBackToLearning`; :57 `navigate('learning')`)
- Modify: `app/src/main/assets/ui/core/store.js` (:58 `suggestionsOpen`)
- Modify (teste): `tests/test_norte_ui_poda_contract.py` (acrescentar)

- [ ] **Step 1: Write the failing test** — acrescentar antes do `print`:

```python
map_js = (ui / "screens/map.js").read_text(encoding="utf-8")
store = (ui / "core/store.js").read_text(encoding="utf-8")
assert "mapBackToLearning" not in map_js and "navigate('learning')" not in map_js
assert "Voltar ao aprendizado" not in map_js
assert "suggestionsOpen" not in store
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh python tests/test_norte_ui_poda_contract.py` → `REMOTE_TEST=FAIL` (`mapBackToLearning`).
- [ ] **Step 3: Remove** o bloco `if (actions && !document.getElementById('mapBackToLearning')) {…}` e o listener; a chave do `createInitialState`.
- [ ] **Step 4: Run** o mesmo comando → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh node tests/ui/map-workflow-e2e.test.cjs` → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `fix(ui): Mapa K não navega mais para a rota inexistente learning`

---

### Task 6.5: `SubpageTabs`, `styles-norte.css` e pisos de toque/texto

**Files:**
- Create: `app/src/main/assets/ui/components/subpage-tabs.js`
- Create: `app/src/main/assets/ui/styles-norte.css`
- Modify: `app/src/main/assets/ui/index.html` (`<link rel="stylesheet" href="styles-norte.css">` após :9; script `components/subpage-tabs.js` antes de `screens/dashboard.js`)
- Test: `tests/ui/subpage-tabs.test.cjs`, `tests/ui/norte-hmi-floors.test.cjs`

**Interfaces:**
- Consumes: `root.OmegasApp.store`.
- Produces:
```js
OmegasUi.SubpageTabs.mount(host, { route, items: [{ id, label, hint? }], onChange(id) })
  // → { select(id), current(), destroy() }; lança Error('no máximo 3 subpáginas') se items.length > 3
  // DOM: div.view-switch.subpage-tabs[role=tablist] > button[role=tab][data-subpage=id][aria-selected] ; p.subpage-hint
  // inicial = store['subpage.<route>'] se for id válido, senão items[0].id; onChange(inicial) na montagem
  // select grava store['subpage.<route>'] (só se mudou) e chama onChange; mudança externa da chave (Router) → select
```
- CSS (classes que F7/F8 reutilizam): `.subpage-tabs button{min-height:76px;font-size:20px}`, `.side-nav button{min-height:76px}`, `details.tech>summary{min-height:76px}` (texto "Detalhes técnicos", sempre o último bloco), `[data-critical]{font-size:28px}`, `.norte-action{min-height:76px;min-width:220px;font-size:22px}`, `.norte-chip`, `.subpage-hint{font-size:18px;color:var(--muted)}`, `#routeQuestion{font-size:18px}`.

- [ ] **Step 1: Write the failing tests**

`tests/ui/subpage-tabs.test.cjs`:
```js
const ITEMS = [{ id: 'equivalencia', label: 'Equivalência' }, { id: 'editar', label: 'Editar' }, { id: 'backups', label: 'Backups' }];
test('lembra a última subpágina por rota e obedece o roteador', () => {
  const { window, document } = loadUi(['core/store.js', 'components/subpage-tabs.js']);
  const store = new window.OmegasUi.Store({}); window.OmegasApp = { store };
  const seen = []; const host = document.createElement('div');
  const tabs = window.OmegasUi.SubpageTabs.mount(host, { route: 'curve', items: ITEMS, onChange: id => seen.push(id) });
  assert.equal(tabs.current(), 'equivalencia'); assert.deepEqual(seen, ['equivalencia']);
  host.querySelector('[data-subpage="backups"]').click();
  assert.equal(store.get()['subpage.curve'], 'backups');
  assert.equal(host.querySelector('[data-subpage="backups"]').getAttribute('aria-selected'), 'true');
  const again = window.OmegasUi.SubpageTabs.mount(document.createElement('div'), { route: 'curve', items: ITEMS, onChange() {} });
  assert.equal(again.current(), 'backups');
  store.patch({ 'subpage.curve': 'editar' }); assert.equal(tabs.current(), 'editar');
  store.patch({ 'subpage.curve': 'inexistente' }); assert.equal(tabs.current(), 'editar');
});
test('no máximo 3 itens', () => { /* 4 itens → assert.throws(..., /no máximo 3/) */ });
```

`tests/ui/norte-hmi-floors.test.cjs`:
```js
const css = read('app/src/main/assets/ui/styles-norte.css');
assert.match(css, /\.subpage-tabs button\s*\{[^}]*min-height:\s*76px/);
assert.match(css, /\.side-nav button\s*\{[^}]*min-height:\s*76px/);
assert.match(css, /details\.tech\s*>\s*summary\s*\{[^}]*min-height:\s*76px/);
assert.match(css, /\.norte-action\s*\{[^}]*min-height:\s*76px/);
assert.match(css, /\[data-critical\]\s*\{[^}]*font-size:\s*(2[4-9]|[3-9]\d)px/);
assert.doesNotMatch(css, /:[^;{}]*#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(/, 'sem cor literal: só var(--…)');
assert.match(read('app/src/main/assets/ui/index.html'), /href="styles-norte\.css"/);
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh node tests/ui/subpage-tabs.test.cjs` → `REMOTE_TEST=FAIL` (`ENOENT components/subpage-tabs.js`).
- [ ] **Step 3: Implement** conforme Interfaces (visual = `.view-switch` de `styles-calibration-obd.css:2`, com os pisos acima).
- [ ] **Step 4: Run** `tools/ci/remote-test.sh node tests/ui/subpage-tabs.test.cjs` → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh node tests/ui/norte-hmi-floors.test.cjs` → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(ui): SubpageTabs com memória por aba e pisos 76/24 px`

---

### Task 6.6: `OperationOverlay` (etapas em português, Desfazer, Voltar, Detalhes técnicos)

**Files:**
- Create: `app/src/main/assets/ui/components/operation-overlay.js`
- Modify: `app/src/main/assets/ui/index.html` (após `</main>` :179: `<div id="operationOverlay" class="operation-layer norte-overlay" role="dialog" aria-live="polite" hidden></div>`; script antes de `app.js`)
- Test: `tests/ui/operation-overlay.test.cjs`

**Interfaces:**
- Consumes: `OmegasUi.omegas().request`, `store.get().snap.operation`, `state.snapChanged`.
- Produces:
```js
OmegasUi.OperationOverlay = {
  open(receiptId), close(), isOpen() -> boolean, current() -> receiptId|null,
  stageText(op) -> string, nextStepText(op) -> string,     // puros
  INTENT_LABEL: { CURVE_WRITE:'Aplicar ajuste na Curva K', CURVE_RESET:'Voltar a Curva K para 1,0', CURVE_RESTORE:'Restaurar Curva K',
                  UNDO:'Desfazer', MAP_WRITE:'Gravar Mapa K', REFERENCE_FREEZE:'Congelar referência' } }
```
- `stageText`: `RECEBIDO→'Recebido'`, `PREPARANDO→'Preparando'`, `EXECUTANDO→'Gravando'`, `CONFERINDO→'Conferindo na ECU'`, `CONCLUIDO→'✓ Gravado e conferido'`, `FALHOU→'✗ ' + (op.reason || {TRANSPORTE:'A comunicação com a ECU caiu', ECU:'A ECU não confirmou o valor', APP:'Erro interno do app'}[op.failureKind] || 'Falhou')`.
- `nextStepText`: `FALHOU/TRANSPORTE→'Reconecte o cabo. A foto de antes está guardada.'`, `FALHOU/ECU→'Nada foi dado como gravado. Desfazer volta à foto de antes.'`, `FALHOU/APP→'O detalhe ficou registrado na sessão.'`, `CONCLUIDO && undoable→'Desfazer volta à foto de antes até o fim da sessão.'`, resto `''`.
- Enquanto `snap.operation?.receiptId !== current()`, renderiza como `RECEBIDO` (resposta < 100 ms). Resultado em `[data-critical]`. `Desfazer` (`.norte-action`) visível só com `undoable===true` e `stage ∈ {CONCLUIDO, FALHOU}`; envia `{intent:'UNDO', payload:{receiptId: current()}}` e reabre no recibo novo. `Voltar` só fecha (a operação continua na fila). `details.tech` mostra `detail.sent`, `detail.readback`, `detail.durationMs`.

- [ ] **Step 1: Write the failing test** `tests/ui/operation-overlay.test.cjs`

```js
const O = () => loadUi(['core/store.js','core/snapshot-feed.js','components/operation-overlay.js']).window.OmegasUi.OperationOverlay;
test('etapas em português', () => {
  const o = O();
  assert.deepEqual(['RECEBIDO','PREPARANDO','EXECUTANDO','CONFERINDO','CONCLUIDO'].map(stage => o.stageText({ stage })),
    ['Recebido','Preparando','Gravando','Conferindo na ECU','✓ Gravado e conferido']);
  assert.equal(o.stageText({ stage: 'FALHOU', failureKind: 'TRANSPORTE' }), '✗ A comunicação com a ECU caiu');
  assert.equal(o.stageText({ stage: 'FALHOU', failureKind: 'ECU' }), '✗ A ECU não confirmou o valor');
  assert.equal(o.stageText({ stage: 'FALHOU', failureKind: 'APP' }), '✗ Erro interno do app');
  assert.equal(o.stageText({ stage: 'FALHOU', failureKind: 'ECU', reason: 'Readback do ponto 12 diferente' }), '✗ Readback do ponto 12 diferente');
  assert.equal(o.nextStepText({ stage: 'FALHOU', failureKind: 'TRANSPORTE' }), 'Reconecte o cabo. A foto de antes está guardada.');
});
test('Desfazer só quando a operação tem foto e acabou', () => {
  // harness: window.OmegasApp = { store }; store.patch({ snap:{ operation:{ receiptId:'r-1', ... } }, snapChanged:['operation'] }); overlay.open('r-1')
  // [stage, undoable] → hidden do botão [data-overlay-undo]:
  // ['CONCLUIDO', true]→false · ['FALHOU', true]→false · ['EXECUTANDO', true]→true · ['CONCLUIDO', false]→true
});
test('Desfazer envia UNDO e acompanha o novo recibo', () => {
  // omegas.request = p => { sent.push(p); return 'r-2'; }  → clicar [data-overlay-undo]
  // assert.deepEqual(sent, [{ intent: 'UNDO', payload: { receiptId: 'r-1' } }]); assert.equal(overlay.current(), 'r-2')
});
test('recibo ainda não no snapshot mostra Recebido; Voltar fecha sem intent', () => {
  // snap.operation = { receiptId:'r-8', stage:'CONCLUIDO' }; open('r-9') → [data-critical].textContent === 'Recebido'
  // clicar [data-overlay-back] → isOpen() === false; sent.length === 0
});
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh node tests/ui/operation-overlay.test.cjs` → `REMOTE_TEST=FAIL` (`ENOENT`).
- [ ] **Step 3: Implement** conforme Interfaces; inscrição no `store` no boot (`root.OmegasApp.store`), re-render só se `isOpen()` e `snapChanged` contém `operation`.
- [ ] **Step 4: Run** o mesmo → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(ui): overlay único de operação com etapas, Desfazer e Detalhes técnicos`

---

### Task 6.7: `EquivalenceCanvas` (cores por estado, AGORA com redesenho parcial)

**Files:**
- Create: `app/src/main/assets/ui/components/equivalence-canvas.js`
- Modify: `app/src/main/assets/ui/index.html` (script antes de `screens/dashboard.js`)
- Test: `tests/ui/equivalence-canvas.test.cjs`

**Interfaces:**
- Produces:
```js
OmegasUi.EquivalenceCanvas = {
  STATE_STYLE,                              // PointState → { fill: token|null, stroke: token }
  stateStyle(point) -> { fill, stroke },    // POBRE/RICO: |mixture| ≤ 0.08 (EquivalenceTolerances.LIGHT) → --warn; > 0.08 → --danger; mixture null → --warn
  layout(snap, width, height, { compact }) -> { x(ms), points:[{index,x,y,style}], kPath:[[x,y]], ghost:[[x,y]]|null, refPath:[[x,y]], ownPath:[[x,y]] },
  mount(host, { compact }) -> { draw(snap), drawNow(now), hitTest(xPx) -> index|null, destroy() } }
```
- Desenho (decisão desta fatia): eixo X único = `ms` de gasolina (eixo da Curva K), domínio `[min axisMs, max axisMs]` ±4%. Faixa de cima (só fora do `compact`): `ms × MAP`, Referência tracejada (`reference.points`) e Própria sólida (`reference.ownPetrol`), sombreado onde divergem. Faixa de baixo (única no `compact`): `ms × K`, linha `kCurrent`, os 30 pontos com `stateStyle`, e a proposta em fantasma (`nextAction.proposal.refinedRaw / 16384`, tracejada `--muted`). AGORA = linha vertical em `now.petrolMs` com ▲ e rótulo "AGORA" em `--accent`, e ponto `(petrolMs, mapBar)` na faixa de cima; só se `now.valid === true`.
- Duas camadas: `canvas[data-layer="base"]` (só `draw`) e `canvas[data-layer="now"]` (só `drawNow`, coalescido em `requestAnimationFrame`). `host.dataset.pointCount` e `host.dataset.nowMs` atualizados a cada desenho (prova no androidTest). `hitTest` = ponto mais próximo no eixo X (a largura toda é alvo).

- [ ] **Step 1: Write the failing test** `tests/ui/equivalence-canvas.test.cjs`

```js
test('mapa de cores por estado', () => {
  const C = loadUi(['components/equivalence-canvas.js']).window.OmegasUi.EquivalenceCanvas;
  assert.deepEqual(JSON.parse(JSON.stringify(C.STATE_STYLE)), {
    SEM_DADOS: { fill: null, stroke: '--dim' }, APRENDENDO: { fill: null, stroke: '--muted' }, MEDIDO: { fill: '--muted', stroke: '--muted' },
    EQUIVALENTE: { fill: '--ok', stroke: '--ok' }, CONFIRMADO: { fill: '--ok', stroke: '--ok' },
    POBRE: { fill: '--warn', stroke: '--warn' }, RICO: { fill: '--warn', stroke: '--warn' },
    EM_PROVA: { fill: null, stroke: '--warn' }, CONTESTADO: { fill: '--warn', stroke: '--warn' }, INCONCLUSIVO: { fill: '--dim', stroke: '--dim' } });
  assert.equal(C.stateStyle({ state: 'POBRE', mixture: 0.08 }).fill, '--warn');
  assert.equal(C.stateStyle({ state: 'POBRE', mixture: 0.081 }).fill, '--danger');
  assert.equal(C.stateStyle({ state: 'RICO', mixture: -0.12 }).fill, '--danger');
  assert.equal(C.stateStyle({ state: 'RICO', mixture: null }).fill, '--warn');
});
test('layout: 30 pontos, X crescente, fantasma só com proposta', () => {
  // snap.points = 30 pontos axisMs 2.0 + 0.35*i, kCurrent 1 → layout(snap, 1000, 400, {}).points.length === 30, x estritamente crescente
  // ghost === null sem nextAction.proposal; com proposal (refinedRaw[11] = 17203) → ghost.length === 30
});
test('AGORA redesenha só a própria camada', () => {
  const { window, document, flushFrames } = loadUi(['components/equivalence-canvas.js']);
  const host = document.createElement('div');
  const view = window.OmegasUi.EquivalenceCanvas.mount(host, { compact: false });
  view.draw(SNAP);
  const base = host.querySelector('[data-layer="base"]').getContext('2d');
  const now = host.querySelector('[data-layer="now"]').getContext('2d');
  base.calls.length = 0; now.calls.length = 0;
  for (let i = 0; i < 5; i += 1) view.drawNow({ valid: true, petrolMs: 4 + i * 0.1, mapBar: 0.5 });
  flushFrames();
  assert.equal(base.calls.length, 0);
  assert.equal(now.calls.filter(c => c === 'clearRect').length, 1);
  assert.equal(host.dataset.nowMs, '4.4'); assert.equal(host.dataset.pointCount, '30');
  view.drawNow({ valid: false }); flushFrames(); assert.equal(host.dataset.nowMs, '');
});
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh node tests/ui/equivalence-canvas.test.cjs` → `REMOTE_TEST=FAIL` (`ENOENT`).
- [ ] **Step 3: Implement** conforme Interfaces (cores lidas uma vez por `mount` de `getComputedStyle(document.documentElement)`; `devicePixelRatio` respeitado).
- [ ] **Step 4: Run** o mesmo → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(ui): canvas de equivalência com estados por cor e AGORA em camada própria`

---

### Task 6.8: Agora (índice, próxima ação com um botão, curva mini, combustível, conexão)

**Files:**
- Modify (reescrever): `app/src/main/assets/ui/screens/dashboard.js` (inteiro; sai `ensureStyles` :30-37 e o tile REFINO de `installLayout` :57)
- Delete: `app/src/main/assets/ui/styles-dashboard-now.css` (leva junto as regras mortas `body[data-omegas-route=…]` :3-23)
- Modify: `app/src/main/assets/ui/app.js` (sai `ensureScreen('dashboard')?.render(state)` em `refreshFast` :241 e `refreshStatus` :268; `activateRoute` :454-459 só faz `renderSnapshot` completo)
- Delete: `tests/ui/verde-dashboard-now.test.cjs`
- Modify (testes): `tests/ui/telemetry-truth-contract.test.cjs:44,49,52` (sai a linha da célula do dashboard; `telemetryValid` passa a vir de `now.valid === true`), `tests/test_clean_ui_contract.py` (`test_dashboard_prioritizes_petrol_injection_and_groups_context` → `test_agora_shows_index_next_action_and_levels_raw`)
- Test: `tests/ui/agora-screen.test.cjs`

**Interfaces:**
- Consumes: `snap.index`, `snap.nextAction`, `snap.points`, `snap.now`, `snap.connection`, `snap.live`; `EquivalenceCanvas.mount(host, {compact:true})`; `router.navigate`.
- Produces:
```js
OmegasUi.AgoraModel.ACTION_LABEL = { OPERATION:'Ver operação', FREEZE_REFERENCE:'Abrir Referência', CONTESTED:'Ver e desfazer',
  APPLY:'Ver ajuste', PROVING:'Ver pontos em prova', COLLECT:'Ver onde falta', NOTHING:null }
OmegasUi.AgoraModel.view(snap) -> { index, coverage, trend, provisional, action:{ text, label, route, subpage }|null, connection, fuel, levelRaw }
class DashboardScreen { constructor(store); renderSnapshot(snap, changed); onAction(action) }   // onAction → router.navigate(action.route, { subpage: action.subpage })
```
- Markup (ids pinados): `#agoraIndex[data-critical]`, `#agoraCoverage`, `#agoraTrend`, `#agoraProvisional.norte-chip` ("PROVISÓRIO · sem referência da ECU"), `#agoraActionText[data-critical]`, `#agoraActionButton.norte-action` (oculto quando `label` ou `route` é `null`), `#agoraMiniCurve`, `#agoraConnection`, `#dashFuel`, `#dashLevelsRaw` sob o rótulo `LEVELS RAW` (lê `live.level_raw`), cada fato em `[data-agora-fact]`.

- [ ] **Step 1: Write the failing test** `tests/ui/agora-screen.test.cjs`

```js
const M = () => loadUi(['components/equivalence-canvas.js','screens/dashboard.js']).window.OmegasUi.AgoraModel;
const APPLY = { kind: 'APPLY', text: '3 pontos pobres entre 6 e 8 ms (4–6%) · Aplicar ajuste', route: 'curve', subpage: 'equivalencia', pointIndexes: [11, 12, 13] };
test('vista com dados', () => {
  const v = M().view({ index: { value: 0.734, coverage: 18, provisional: false, trend: [0.69] }, nextAction: APPLY,
    connection: { state: 'ONLINE' }, live: { fuel: 'GNV', level_raw: 412 } });
  assert.equal(v.index, '73%'); assert.equal(v.coverage, '18 de 30 pontos medidos');
  assert.equal(v.trend, '+4 pts desde a última sessão');
  assert.deepEqual(JSON.parse(JSON.stringify(v.action)), { text: APPLY.text, label: 'Ver ajuste', route: 'curve', subpage: 'equivalencia' });
  assert.equal(v.connection, 'ECU conectada'); assert.equal(v.fuel, 'GNV'); assert.equal(v.levelRaw, '412');
});
test('sem nada não inventa número', () => {
  const v = M().view({});
  assert.deepEqual([v.index, v.coverage, v.trend, v.action, v.connection, v.fuel, v.levelRaw],
    ['—', '0 de 30 pontos medidos', '', null, 'Sem cabo', '—', '—']);
  assert.equal(M().view({ index: { value: 0, coverage: 3, trend: [] } }).index, '0%');
  assert.equal(M().view({ index: { value: 0.5, coverage: 3, trend: [] } }).trend, 'primeira sessão medida');
  assert.equal(M().view({ connection: { state: 'CONECTANDO' } }).connection, 'Conectando…');
  assert.equal(M().view({ nextAction: { kind: 'NOTHING', text: 'Equivalente. Nada a fazer.', route: null, subpage: null, pointIndexes: [] } }).action.label, null);
});
test('Agora nunca envia intent', () => {
  const src = read('app/src/main/assets/ui/screens/dashboard.js');
  assert.doesNotMatch(src, /\.request\(|omegas\(\)/);
  const { window } = loadUi(['components/equivalence-canvas.js','screens/dashboard.js']);
  const nav = []; let requests = 0;
  window.OmegasUi.Omegas = { request: () => { requests += 1; return 'x'; } };
  window.OmegasApp = { router: { navigate: (r, c) => nav.push([r, c]) } };
  window.OmegasUi.DashboardScreen.prototype.onAction.call({}, { route: 'curve', subpage: 'equivalencia' });
  assert.deepEqual(JSON.parse(JSON.stringify(nav)), [['curve', { subpage: 'equivalencia' }]]); assert.equal(requests, 0);
});
test('LEVELS RAW e folha de estilo antiga fora', () => {
  const src = read('app/src/main/assets/ui/screens/dashboard.js');
  for (const s of ['LEVELS RAW', 'id="dashLevelsRaw"', 'level_raw']) assert.ok(src.includes(s), s);
  assert.equal(fs.existsSync(path.join(ROOT, 'app/src/main/assets/ui/styles-dashboard-now.css')), false);
  assert.doesNotMatch(src, /dashRefino|data-dash-refino/);
});
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh node tests/ui/agora-screen.test.cjs` → `REMOTE_TEST=FAIL` (`AgoraModel` indefinido).
- [ ] **Step 3: Implement** conforme Interfaces. `trend` = `Math.round((value - trend.at(-1)) * 100)` pontos com sinal (`+4 pts`/`−2 pts`); `index` = `Math.round(value * 100) + '%'`; `fuel` reaproveita `DisplayRules.fuelLabel`; o AGORA mini só desenha com `const telemetryValid = !!(snap.now && snap.now.valid === true)` (mantém o padrão de `telemetry-truth-contract.test.cjs:49`). Redesenha `#agoraMiniCurve` só se `changed` tem `points|nextAction`; `drawNow` se tem `now`.
- [ ] **Step 4: Run** `tools/ci/remote-test.sh node tests/ui/agora-screen.test.cjs` → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh node tests/ui/telemetry-truth-contract.test.cjs` → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh python tests/test_autocal_levels_scope_contract.py` e `… tests/test_levels_autocal_operational_refresh_contract.py` → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(ui): Agora com índice, próxima ação que leva à aba certa e curva mini`

---

### Task 6.9: Curva K — estrutura em 3 subpáginas e `Equivalência` com Aplicar ajuste

**Files:**
- Modify: `app/src/main/assets/ui/index.html` (tela `curve` :97-156 reescrita: sai `page-intro`/`#curveViewSwitch`/`#curveReadButton`/barra de backup :98-114, `#curveSuggestionFocus` :115, visões :117-152, camadas de operação :154-155; entra `#curveTabs` + três `[data-curve-subpage]`; `details.evidence-disclosure` "CONTEXTO · Evidência técnica" :135 vira `details.tech`)
- Modify (reescrever): `app/src/main/assets/ui/screens/curve.js` (inteiro; `setView` :61-69 vira `SubpageTabs`)
- Delete: `tests/ui/curve-autocal-interoperability.test.cjs` (testava `setView('learning'|'editor')`)
- Test: `tests/ui/curve-norte.test.cjs`

**Interfaces:**
- Consumes: `SubpageTabs`, `EquivalenceCanvas`, `OperationOverlay`, `OmegasUi.omegas()`, seções `points, nextAction, index, reference, now, connection, operation, session`.
- Produces:
```js
OmegasUi.CurveModel = {
  SUBPAGES: [{ id:'equivalencia', label:'Equivalência', hint:'Gasolina da ECU (tracejada) × a que o app aprendeu (sólida): cada ponto diz se o GNV já anda igual.' },
             { id:'editar', label:'Editar', hint:'Mude ponto a ponto e grave; a foto de antes fica para Desfazer.' },
             { id:'backups', label:'Backups', hint:'Curvas guardadas: restaure ou volte a 1,0 num toque.' }],
  proposalPoints(proposal) -> [{ index, currentRaw, targetRaw }],   // só onde refinedRaw[i] !== currentRaw[i]
  driftText(reference) -> string|null,
  applyEnabled(snap) -> boolean,      // connection ONLINE && proposalPoints > 0 && (!operation || stage ∈ {CONCLUIDO, FALHOU})
  toRaw(k) -> int|null,               // Math.round(k*16384) se 0.6 ≤ k ≤ 4.0
  editedPoint(point, targetK) -> { index, currentRaw, targetRaw, deltaPct }|null }
class CurveScreen { constructor(store); onEnter(context); renderSnapshot(snap, changed); apply(); writePrepared(); restore(backupId); reset() }
```
- Markup `equivalencia` (ids pinados): `#curveEqChart` (canvas), `#curveProvisional.norte-chip` (visível se `reference === null` ou `index.provisional`), `#curveEcuDrift` (texto de `driftText`, oculto se `null`), `#curveEqIndex[data-critical]`, `#curveEqCoverage`, `#curveEqAction[data-critical]` (`nextAction.text`), `#curveApply.primary.norte-action` "Aplicar ajuste", `details.tech > #curveEqTech` (tabela 30 × `kCurrent`, `refinedRaw`, `samples`, `sources`). Markup `editar` e `backups`: contêineres `[data-curve-subpage="editar"]` (com `#curveChart`, inspector de `index.html:137-149` e `#curveReviewButton`) e `[data-curve-subpage="backups"]` (`#curveBackupList`, `#curveResetButton`), ligados na Task 6.10.
- `apply()`: `const id = OmegasUi.omegas().request({ intent: 'CURVE_WRITE', payload: { points: CurveModel.proposalPoints(snap.nextAction.proposal), reason: 'Aplicar ajuste (Equivalência)' } }); OperationOverlay.open(id)`. Fantasma sempre desenhado quando existe proposta; o botão é o único toque.

- [ ] **Step 1: Write the failing test** `tests/ui/curve-norte.test.cjs`

```js
const load = () => loadUi(['core/store.js','core/snapshot-feed.js','components/subpage-tabs.js','components/operation-overlay.js','components/equivalence-canvas.js','screens/curve.js']);
const RAW = Array(30).fill(16384);
const PROPOSAL = { currentRaw: RAW, refinedRaw: RAW.map((v, i) => (i === 11 ? 17203 : i === 12 ? 17121 : v)) };
test('proposta vira só os pontos que mudam', () => {
  const M = load().window.OmegasUi.CurveModel;
  assert.deepEqual(JSON.parse(JSON.stringify(M.proposalPoints(PROPOSAL))),
    [{ index: 11, currentRaw: 16384, targetRaw: 17203 }, { index: 12, currentRaw: 16384, targetRaw: 17121 }]);
  assert.deepEqual([...M.proposalPoints(null)], []);
});
test('textos e travas', () => {
  const M = load().window.OmegasUi.CurveModel;
  assert.equal(M.driftText({ ecuDrift: 0.032 }), 'a ECU reaprendeu gasolina desde o congelamento (diferença máx. 3,2%)');
  assert.equal(M.driftText({ ecuDrift: null }), null); assert.equal(M.driftText(null), null);
  const base = { connection: { state: 'ONLINE' }, nextAction: { kind: 'APPLY', proposal: PROPOSAL }, operation: null };
  assert.equal(M.applyEnabled(base), true);
  assert.equal(M.applyEnabled({ ...base, connection: { state: 'SEM_CABO' } }), false);
  assert.equal(M.applyEnabled({ ...base, operation: { stage: 'EXECUTANDO' } }), false);
  assert.equal(M.applyEnabled({ ...base, operation: { stage: 'FALHOU' } }), true);
  assert.deepEqual([M.toRaw(1), M.toRaw(1.05), M.toRaw(0.59), M.toRaw(4.01), M.toRaw(NaN)], [16384, 17203, null, null, null]);
  assert.deepEqual(JSON.parse(JSON.stringify(M.editedPoint({ index: 3, kCurrent: 1 }, 1.05))), { index: 3, currentRaw: 16384, targetRaw: 17203, deltaPct: 5 });
});
test('Aplicar ajuste: um toque = um CURVE_WRITE + overlay', () => {
  const { window } = load(); const sent = [];
  window.OmegasUi.Omegas = { request: p => { sent.push(p); return 'r-1'; } };
  window.OmegasApp = { store: new window.OmegasUi.Store({}) };
  const screen = new window.OmegasUi.CurveScreen(window.OmegasApp.store);   // tolera DOM ausente
  screen.renderSnapshot({ connection: { state: 'ONLINE' }, nextAction: { kind: 'APPLY', proposal: PROPOSAL }, points: [] }, ['nextAction']);
  screen.apply();
  assert.deepEqual(JSON.parse(JSON.stringify(sent)), [{ intent: 'CURVE_WRITE', payload: {
    points: [{ index: 11, currentRaw: 16384, targetRaw: 17203 }, { index: 12, currentRaw: 16384, targetRaw: 17121 }], reason: 'Aplicar ajuste (Equivalência)' } }]);
  assert.equal(window.OmegasUi.OperationOverlay.current(), 'r-1');
});
test('sem caminho antigo', () => {
  const src = read('app/src/main/assets/ui/screens/curve.js'); const html = read('app/src/main/assets/ui/index.html');
  assert.doesNotMatch(src, /confirm\(|startCurve|writeCurve\(|curveBackups\(|previewCurvePoint|suggestion/i);
  assert.doesNotMatch(html, /curveViewSwitch|data-curve-view|curveReadButton|curveSuggestionFocus|curveOperationResult|Evidência técnica/);
  assert.match(html, /id="curveTabs"/); assert.match(html, /id="curveChart"/);
});
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh node tests/ui/curve-norte.test.cjs` → `REMOTE_TEST=FAIL` (`CurveModel` indefinido).
- [ ] **Step 3: Implement** conforme Interfaces; `app.js` `ensureScreen('curve')` (:117) passa a `new ui.CurveScreen(store)` e `activateRoute` curve (:472-476) só chama `onEnter`. `renderSnapshot`: base do canvas só se `changed ∩ {points, reference, nextAction}`; `drawNow` se `now`; painel se `index|nextAction|connection|operation`.
- [ ] **Step 4: Run** `tools/ci/remote-test.sh node tests/ui/curve-norte.test.cjs` → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(ui): Curva K com subpáginas e Equivalência que aplica a proposta num toque`

---

### Task 6.10: Curva K — `Editar` e `Backups` pela fila

**Files:**
- Modify: `app/src/main/assets/ui/screens/curve.js` (métodos `selectPoint`, `nudgeActive`, `prepareActivePoint`, `writePrepared`, `renderBackups`, `restore`, `reset`)
- Modify (testes): `tests/ui/curve-map-editor-coherence.test.cjs:11` (tokens da curva → `['writePrepared()', "intent: 'CURVE_WRITE'"]`), `tests/test_clean_ui_contract.py` (`test_curve_k_is_global_and_uses_one_human_write_confirmation` → asserts de `CURVE_WRITE`, `CURVE_RESTORE`, `CURVE_RESET`, `curveReviewButton` → `writePrepared()`, sem `window.confirm`), `tests/test_platina_progbase_host_contract_v2.py:36` (`"writeCurve"` → `"CURVE_WRITE"`)
- Test: `tests/ui/curve-norte.test.cjs` (acrescentar)

**Interfaces:**
- Produces:
```js
CurveModel.backupRows(list, nowMs) -> [{ id, title, when, kind }]   // kind: 'FOTO'→'foto antes de gravar', 'MANUAL'→'salvo'; createdAt ≤ 0 → when 'data desconhecida'
CurveScreen.selectPoint(index); CurveScreen.setTarget(k); CurveScreen.nudgeActive(delta); CurveScreen.prepareActivePoint()
CurveScreen.writePrepared()  // CURVE_WRITE { points: [...preparados], reason: 'Ajuste manual (Editar)' } + overlay; limpa os preparados quando o recibo chega a CONCLUIDO
CurveScreen.restore(backupId) // CURVE_RESTORE { backupId } + overlay
CurveScreen.reset()           // CURVE_RESET {} + overlay — sem diálogo (foto + Desfazer protegem)
```
- `Editar`: toque no `#curveChart` (`EquivalenceCanvas.hitTest`) escolhe o ponto; inspector mostra `kCurrent`; `[data-curve-nudge]` (±0,01/±0,05) e `#curveTargetFactor`; `#curvePreparePoint` "Preparar este ponto" usa `editedPoint` (inválido → botão desabilitado e "Fora de 0,6–4,0"); `#curveReviewButton.norte-action` "Gravar N pontos".
- `Backups`: `#curveBackupList` de `snap.session.curveBackups` (linha ≥ 76 px, botão "Restaurar"); vazio → "Nenhum backup ainda. Toda gravação guarda a foto de antes aqui."; `#curveResetButton` "Voltar a Curva K para 1,0". **Não há botão "Salvar curva atual"** (sem intent na spec §2.2; ver lacunas).

- [ ] **Step 1: Write the failing test** (acrescentar em `curve-norte.test.cjs`)

```js
test('Editar, Restaurar e Resetar saem como intents', () => {
  const { window } = load(); const sent = []; let n = 0;
  window.OmegasUi.Omegas = { request: p => { sent.push(p); n += 1; return `r-${n}`; } };
  window.OmegasApp = { store: new window.OmegasUi.Store({}) };
  const s = new window.OmegasUi.CurveScreen(window.OmegasApp.store);
  s.renderSnapshot({ connection: { state: 'ONLINE' }, points: Array.from({ length: 30 }, (_, index) => ({ index, kCurrent: 1, axisMs: 2 + index * 0.35, state: 'MEDIDO' })) }, ['points']);
  s.selectPoint(3); s.setTarget(1.05); s.prepareActivePoint(); s.writePrepared();
  s.restore('kfactor-20261003-1432.json'); s.reset();
  assert.deepEqual(JSON.parse(JSON.stringify(sent)), [
    { intent: 'CURVE_WRITE', payload: { points: [{ index: 3, currentRaw: 16384, targetRaw: 17203 }], reason: 'Ajuste manual (Editar)' } },
    { intent: 'CURVE_RESTORE', payload: { backupId: 'kfactor-20261003-1432.json' } },
    { intent: 'CURVE_RESET', payload: {} }]);
});
test('linhas de backup', () => {
  const M = load().window.OmegasUi.CurveModel;
  const rows = M.backupRows([{ id: 'a', label: 'Antes de Aplicar ajuste', createdAt: 1759500000000, kind: 'FOTO' },
                             { id: 'b', label: 'Curva K', createdAt: 0, kind: 'MANUAL' }], 1759500060000);
  assert.deepEqual(rows.map(r => [r.id, r.kind]), [['a', 'foto antes de gravar'], ['b', 'salvo']]);
  assert.equal(rows[1].when, 'data desconhecida');
  const src = read('app/src/main/assets/ui/screens/curve.js');
  for (const s of ['data-curve-nudge', 'nudgeActive', 'writePrepared()']) assert.ok(src.includes(s), s);
});
```

- [ ] **Step 2: Run** `tools/ci/remote-test.sh node tests/ui/curve-norte.test.cjs` → `REMOTE_TEST=FAIL` (`s.selectPoint is not a function`).
- [ ] **Step 3: Implement** conforme Interfaces; atualizar os três testes existentes listados.
- [ ] **Step 4: Run** `tools/ci/remote-test.sh node tests/ui/curve-norte.test.cjs` → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh node tests/ui/curve-map-editor-coherence.test.cjs` → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh python tests/test_clean_ui_contract.py` → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(ui): Curva K Editar e Backups gravam pela fila com foto e Desfazer`

---

### Task 6.11: Refino — `Fases` e `Pontos`; coerência e orçamento de performance

**Files:**
- Modify (reescrever): `app/src/main/assets/ui/screens/refino.js` (inteiro; saem os ganchos `scheduler.addHook` :173-187, `POLL_MS`/`setTimeout(tick)` :337-343, o gráfico SVG próprio — ele vive agora na Curva K › Equivalência — e "Resultado da última gravação")
- Modify: `app/src/main/assets/ui/core/router.js` (cadeia :54-55 → `loadOptionalScript('core/autocal-api.js', () => loadOptionalScript('screens/autocal-cockpit.js'))`)
- Modify: `app/src/main/assets/ui/index.html` (`<script src="screens/refino.js" defer>` antes de `app.js`; `#refinoScreenHost` :162-164 mantém o host, sai a classe `autocal-route-screen`)
- Modify (reescrever teste): `tests/ui/refino-screen.test.cjs`; `tests/test_clean_ui_contract.py` (`test_refino_never_writes_without_review_and_readback` → `test_refino_only_navigates_or_undoes`)
- Test: `tests/ui/refino-screen.test.cjs`, `tests/ui/norte-coerencia.test.cjs`

**Interfaces:**
- Consumes: `snap.session.phase`, `snap.points`, `SubpageTabs`, `OperationOverlay`, `OmegasUi.omegas()`, `router.navigate`.
- Produces:
```js
OmegasUi.RefinoModel = {
  SUBPAGES: [{ id:'fases', label:'Fases', hint:'Onde o refino está e o único passo que falta.' },
             { id:'pontos', label:'Pontos', hint:'Os 30 pontos da Curva K, um por linha: toque para entender.' }],
  PHASES: [['ECU_TRABALHANDO','ECU no automático'],['COLETANDO_NOSSOS','Aprendendo os pontos'],['PROPOSTA_PRONTA','Ajuste pronto'],
           ['VERIFICANDO','Provando o ajuste'],['ESTAVEL','Equivalente']],
  phaseRows(phase, points) -> [{ id, label, status:'feito'|'agora'|'depois', summary, problem, button:{ label, kind:'navigate'|'undo', route?, subpage? }|null }],
  stateWord(point) -> string, pointRow(point) -> { ms, state, pct, samples, usage }, sentence(point) -> string, techRows(point) -> [[rótulo, valor]] }
class RefinoScreen { constructor(store); renderSnapshot(snap, changed); toggle(index) }   // linha aberta em store['refino.expanded']
```
- Fases: `SEM_ECU`/`LENDO_ECU` → tudo `depois`, sem botão; `RESTAURAR_TRECHO` → `VERIFICANDO` com `problem:true`. Botão só na linha `agora`: `ECU_TRABALHANDO` {`Ver AutoCal`, autocal/aquisicao} · `COLETANDO_NOSSOS` {`Ver pontos`, refino/pontos} · `PROPOSTA_PRONTA` {`Ver ajuste`, curve/equivalencia} · `VERIFICANDO` {`Ver pontos em prova`, refino/pontos} · problema {`Desfazer`, `undo` → `request({intent:'UNDO', payload:{}})` + overlay} · `ESTAVEL` sem botão. Resumo com contagem de estados de §1.2: `n de 30 pontos medidos` (estado ∉ {SEM_DADOS, APRENDENDO}), `n pontos fora da gasolina` (POBRE+RICO), `n pontos em prova` (EM_PROVA), `n pontos contestados: melhorou a mistura, piorou a suavidade` (CONTESTADO), `n pontos equivalentes · pode desconectar` (EQUIVALENTE+CONFIRMADO).
- Pontos: 30 `button.refino-point-row[data-point-index]` (≥ 76 px): `ms · estado · % · amostras · uso`; tocar expande no lugar com `sentence` (`[data-critical]`) e `details.tech` (`techRows`: K atual, K alvo, tolerância, tremor GNV/gasolina, quase-apagões GNV/gasolina, inclinação, fontes).
- `stateWord`: `SEM_DADOS 'Sem dados'`, `APRENDENDO 'Aprendendo'`, `MEDIDO 'Medido'`, `EQUIVALENTE 'Equivalente'`, `POBRE 'Pobre'`, `RICO 'Rico'`, `EM_PROVA 'Em prova'`, `CONFIRMADO 'Confirmado'`, `CONTESTADO 'Contestado'`, `INCONCLUSIVO 'Inconclusivo'`.

- [ ] **Step 1: Write the failing tests**

`tests/ui/refino-screen.test.cjs` (reescrito):
```js
const M = () => loadUi(['core/store.js','core/snapshot-feed.js','components/subpage-tabs.js','components/operation-overlay.js','screens/refino.js']).window.OmegasUi.RefinoModel;
const P = (state, extra = {}) => ({ index: 7, axisMs: 6.2, mixture: 0.051, samples: 42, usage: 0.12, tolerance: 0.04, state, ...extra });
test('linha do ponto', () => {
  assert.deepEqual(JSON.parse(JSON.stringify(M().pointRow(P('POBRE')))), { ms: '6,20 ms', state: 'Pobre', pct: '+5,1%', samples: '42 amostras', usage: '12% do tempo' });
  const empty = M().pointRow({ index: 0, axisMs: 2, mixture: null, samples: 1, usage: null, state: 'SEM_DADOS' });
  assert.deepEqual([empty.pct, empty.samples, empty.usage], ['—', '1 amostra', '—']);
});
test('frase humana', () => {
  assert.equal(M().sentence(P('POBRE')), 'GNV pobre 5,1% aqui, com 42 amostras: o ajuste sobe o K 5,1%.');
  assert.equal(M().sentence(P('RICO', { mixture: -0.034, samples: 30 })), 'GNV rico 3,4% aqui, com 30 amostras: o ajuste desce o K 3,4%.');
  assert.equal(M().sentence(P('CONTESTADO')), 'Melhorou a mistura, piorou a suavidade.');
  assert.equal(M().sentence(P('SEM_DADOS')), 'Ainda não passei por este ponto com o motor estável.');
  assert.equal(M().sentence(P('EQUIVALENTE')), 'Aqui o GNV já anda como a gasolina (dentro de ±4,0%).');
});
test('fases: um botão, só na fase atual', () => {
  const pts = Array.from({ length: 30 }, (_, i) => P(i < 3 ? 'POBRE' : i < 5 ? 'RICO' : 'EQUIVALENTE', { index: i }));
  const rows = M().phaseRows('PROPOSTA_PRONTA', pts);
  assert.deepEqual(rows.map(r => r.status), ['feito', 'feito', 'agora', 'depois', 'depois']);
  assert.equal(rows[2].summary, '5 pontos fora da gasolina');
  assert.deepEqual(JSON.parse(JSON.stringify(rows[2].button)), { label: 'Ver ajuste', kind: 'navigate', route: 'curve', subpage: 'equivalencia' });
  assert.equal(rows.filter(r => r.button).length, 1);
  const bad = M().phaseRows('RESTAURAR_TRECHO', pts.map((p, i) => (i === 9 ? { ...p, state: 'CONTESTADO' } : p)));
  assert.equal(bad[3].status, 'agora'); assert.equal(bad[3].problem, true); assert.equal(bad[3].button.kind, 'undo');
  assert.ok(M().phaseRows('SEM_ECU', pts).every(r => r.status === 'depois' && !r.button));
});
test('Desfazer do trecho contestado sai como UNDO e abre o overlay', () => { /* clicar botão undo → sent deepEqual [{ intent:'UNDO', payload:{} }] */ });
test('finite rejeita nulo', () => { assert.match(read('app/src/main/assets/ui/screens/refino.js'), /function finite\(value\)\s*\{\s*if \(value === null \|\| value === undefined/); });
```

`tests/ui/norte-coerencia.test.cjs`:
```js
const UI = 'app/src/main/assets/ui';
const ALLOW_INTERVAL = new Set(['core/scheduler.js']);   // pump de PresentSnapshot da faixa de status, Mapa K e AutoCal; a F7 migra e apaga
test('nenhum setInterval de UI fora do scheduler', () => {
  for (const rel of walkJs(UI)) if (/setInterval\s*\(/.test(read(`${UI}/${rel}`))) assert.ok(ALLOW_INTERVAL.has(rel), rel);
});
const F6 = ['core/snapshot-feed.js','components/subpage-tabs.js','components/operation-overlay.js','components/equivalence-canvas.js',
            'screens/dashboard.js','screens/curve.js','screens/refino.js'];
test('telas da F6 só redesenham por revisão', () => {
  for (const rel of F6) assert.doesNotMatch(read(`${UI}/${rel}`), /scheduler|addHook|setInterval|setTimeout\(/, rel);
});
test('"Detalhes técnicos" é o único nível técnico', () => {
  for (const rel of ['index.html', ...F6]) {
    const src = read(`${UI}/${rel}`);
    assert.doesNotMatch(src, /Evidência técnica|CONTEXTO<|Resultado da última gravação/, rel);
    for (const m of src.matchAll(/<details([^>]*)>\s*<summary[^>]*>([^<]*)</g)) {
      assert.match(m[1], /class="tech"/, rel); assert.equal(m[2].trim(), 'Detalhes técnicos', rel);
    }
  }
});
```
(`createElement('details')` dos componentes recebe `className = 'tech'` e `summary.textContent = 'Detalhes técnicos'`; o teste de cada componente já cobre.)

- [ ] **Step 2: Run** `tools/ci/remote-test.sh node tests/ui/refino-screen.test.cjs` → `REMOTE_TEST=FAIL` (`pointRow is not a function`); `tools/ci/remote-test.sh node tests/ui/norte-coerencia.test.cjs` → `REMOTE_TEST=FAIL` (`screens/refino.js` casa `scheduler`).
- [ ] **Step 3: Implement** conforme Interfaces; `app.js` `ensureScreen` cria `refino` (`new ui.RefinoScreen(store)`) e `activateRoute` refino (:486-489) só chama `renderSnapshot` completo.
- [ ] **Step 4: Run** os dois → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh node tests/ui/telemetry-truth-contract.test.cjs` → `REMOTE_TEST=PASS`; `tools/ci/remote-test.sh python tests/test_clean_ui_contract.py` → `REMOTE_TEST=PASS`.
- [ ] **Step 5: Commit** `feat(ui): Refino em Fases e Pontos; orçamento sem setInterval e Detalhes técnicos únicos`

---

### Task 6.12: Render no WebView real 1280×720 — 9 estados × 3 abas, pisos 76/24 px

**Files:**
- Create: `app/src/androidTest/java/com/omegas/prohub/UiStateFixtures.kt`
- Create: `app/src/androidTest/java/com/omegas/prohub/NorteUiRenderTest.kt`
- Modify: `.github/workflows/verde-android-render-evidence.yml` (matriz `include` :44-110: +9 entradas `class: NorteUiRenderTest`)
- Modify (se o Step 2 apontar): `app/src/main/assets/ui/styles-norte.css`

**Interfaces:**
- Consumes: o publicador de seções do `StateStore` da F5 (chamado num único lugar, `UiStateFixtures.publish`); `saveEvidence`/`evalJson`/`evalRaw` copiados de `RefinoRenderTest.kt:647-686`.
- Produces (F7 reutiliza):
```kotlin
enum class UiState { SEM_CABO, CONECTANDO, SEM_REFERENCIA, ECU_REAPRENDEU, APRENDENDO, NORMAL, EXECUTANDO, FALHA_TRANSPORTE, FALHA_ECU }
object UiStateFixtures {
  fun sections(state: UiState): JSONObject          // chaves = seções do snapshot (tabela do cabeçalho)
  fun publish(activity: MainActivity, state: UiState)
  const val HMI_AUDIT_JS: String                   // → {small:[…], tinyCritical:[…], criticalCount, nan, undef, hscroll}
}
```
- Fixture `NORMAL`: 30 pontos `axisMs = 2,0 + 0,35·i`, `kCurrent = 1 + 0,01·i`; estados `i<4 SEM_DADOS`, `4–7 APRENDENDO`, `8–10 POBRE (0,05/0,045/0,06)`, `11 RICO −0,03`, `12 RICO −0,11`, `13 CONTESTADO`, `14–15 EM_PROVA`, resto `EQUIVALENTE`; `index {value:0,734, coverage:18, provisional:false, trend:[0,69]}`; `nextAction` APPLY com `proposal` (`refinedRaw[8..10]` +5%); `connection ONLINE`; `now {valid:true, petrolMs:5,1, mapBar:0,52}`; `session.phase PROPOSTA_PRONTA`. Os outros estados derivam de `NORMAL` mudando só o que os define: `SEM_CABO` (`connection SEM_CABO`, `now.valid=false`), `CONECTANDO`, `SEM_REFERENCIA` (`reference=null`, `index.provisional=true`, `nextAction FREEZE_REFERENCE` → autocal/referencia), `ECU_REAPRENDEU` (`reference.ecuDrift=0,032`), `APRENDENDO` (todos `APRENDENDO`, `index.value=null`, `coverage 0`, `phase COLETANDO_NOSSOS`), `EXECUTANDO` (`operation {receiptId:'r-1', intent:'CURVE_WRITE', stage:'EXECUTANDO'}`), `FALHA_TRANSPORTE` (`stage FALHOU, failureKind TRANSPORTE, undoable true`), `FALHA_ECU` (`FALHOU, ECU, undoable true`).
- `HMI_AUDIT_JS`: percorre `button, [role=tab], [role=button], summary` visíveis em `.side-rail`, `[data-screen].active` e `#operationOverlay:not([hidden])`; `small` lista `"<id|classe> WxH"` com altura < 76 ou largura < 76; `tinyCritical` lista `[data-critical]` com `font-size` < 24; `nan`/`undef` por regex no `innerText`; `hscroll` = `scrollWidth > clientWidth + 1`.

- [ ] **Step 1: Write the failing test** — um `@Test` por estado (`semCabo`, `conectando`, `semReferencia`, `ecuReaprendeu`, `aprendendo`, `normal`, `executando`, `falhaTransporte`, `falhaEcu`), cada um:

```kotlin
@Test fun normal() = renderAll(UiState.NORMAL) { route, dom ->
    when (route) {
        "dashboard" -> { assertEquals("73%", dom.getString("agoraIndex")); assertEquals("Ver ajuste", dom.getString("agoraButton")) }
        "curve" -> { assertEquals("30", dom.getString("pointCount")); assertTrue(dom.getBoolean("applyEnabled")) }
        "refino" -> assertEquals("PROPOSTA_PRONTA:agora", dom.getString("currentPhase"))
    }
}
private fun renderAll(state: UiState, expect: (String, JSONObject) -> Unit) {
    val scenario = launch()
    try {
        scenario.onActivity { UiStateFixtures.publish(it, state) }
        for (route in listOf("dashboard", "curve", "refino")) {
            evalRaw(scenario, "document.querySelector('[data-route=\"$route\"]').click(); 'ok';"); SystemClock.sleep(500L)
            if (state in setOf(UiState.EXECUTANDO, UiState.FALHA_TRANSPORTE, UiState.FALHA_ECU))
                evalRaw(scenario, "OmegasUi.OperationOverlay.open('r-1'); 'ok';")
            for (sub in subpagesOf(route)) {            // curve: equivalencia, editar, backups · refino: fases, pontos · dashboard: ""
                if (sub.isNotEmpty()) { evalRaw(scenario, "document.querySelector('[data-subpage=\"$sub\"]').click(); 'ok';"); SystemClock.sleep(300L) }
                val audit = evalJson(scenario, UiStateFixtures.HMI_AUDIT_JS)
                saveEvidence("norte-${state.name.lowercase()}-$route${if (sub.isEmpty()) "" else "-$sub"}", audit, scenario)
                assertEquals("alvos < 76 px: ${audit.getJSONArray("small")}", 0, audit.getJSONArray("small").length())
                assertEquals("texto crítico < 24 px: ${audit.getJSONArray("tinyCritical")}", 0, audit.getJSONArray("tinyCritical").length())
                assertTrue("texto crítico presente", audit.getInt("criticalCount") >= 2)
                assertTrue(!audit.getBoolean("nan") && !audit.getBoolean("undef") && !audit.getBoolean("hscroll"))
            }
            expect(route, probe(scenario))               // probe lê os ids pinados nas Tasks 6.6–6.11
        }
    } finally { scenario.close() }
}
```

Esperados por estado (no `expect`): `SEM_CABO` → `#agoraConnection` "Sem cabo", `#curveApply` desabilitado · `CONECTANDO` → "Conectando…" · `SEM_REFERENCIA` → `#curveProvisional` visível, botão Agora "Abrir Referência" · `ECU_REAPRENDEU` → `#curveEcuDrift` = "a ECU reaprendeu gasolina desde o congelamento (diferença máx. 3,2%)" · `APRENDENDO` → `#agoraIndex` "—", `#agoraCoverage` "0 de 30 pontos medidos", fase atual `COLETANDO_NOSSOS` · `EXECUTANDO` → overlay `[data-critical]` "Gravando", `#curveApply` desabilitado · `FALHA_TRANSPORTE` → "✗ A comunicação com a ECU caiu" e `[data-overlay-undo]` visível · `FALHA_ECU` → "✗ A ECU não confirmou o valor".

Matriz (9 entradas, mesmo formato das de `RefinoRenderTest`): `norte-sem-cabo/semCabo`, `norte-conectando/conectando`, `norte-sem-referencia/semReferencia`, `norte-ecu-reaprendeu/ecuReaprendeu`, `norte-aprendendo/aprendendo`, `norte-normal/normal`, `norte-executando/executando`, `norte-falha-transporte/falhaTransporte`, `norte-falha-ecu/falhaEcu`, todas `class: NorteUiRenderTest`.

- [ ] **Step 2: Run to see it fail**

Run: `git push && gh pr checks --watch`
Expected: job `render` FAIL com `SCENARIO_RESULT=norte-normal:FAIL` e mensagem `alvos < 76 px: [".curve-nudge-row button 80x44", "#curveTargetFactor …", "#curveClearProposals …"]` (pisos de 44 px de `styles-calibration-obd.css:5` e `styles.css` ainda valem no `Editar`).

- [ ] **Step 3: Corrigir só no `styles-norte.css`** os seletores que o relatório listar (`.curve-screen .curve-nudge-row button`, `.curve-screen input`, `.curve-screen .quiet-button`, `.refino-point-row`, `.norte-overlay button` → `min-height:76px; min-width:76px`), sem mexer nos arquivos de outras fatias.

- [ ] **Step 4: Run to verify**

Run: `git push && gh pr checks --watch`
Expected: `SCENARIO_RESULT=norte-<estado>:PASS` para os 9; artefato `rendered-evidence/norte-*.png` (27+ prints).

- [ ] **Step 5: Commit** `test(android): render das abas Agora, Curva K e Refino em 9 estados com pisos 76/24 px`

---

### Task 6.13: Repontar as provas de render existentes para o DOM novo

**Files:**
- Modify: `app/src/androidTest/java/com/omegas/prohub/RefinoRenderTest.kt` (`refreshRefino` :200-203 vira só espera; `refinoDom` :205-252 lê `[data-phase]`; asserts de `refinoEcuNoAutomatico` :273, `refinoColetando` :293, `refinoCurvaPronta` :320, `refinoAppNovoEcuPronta` :343, `refinoVerificando` :367, `refinoEstavel` :393, `refinoRestaurarTrecho` :411, `refinoApagoes` :435; `refinoAgoraAcompanhaATelemetria` :486 → `curvaAgoraAcompanhaATelemetria`; `refinoLatenciaDaPonte` :528 mede `OmegasUi.omegas().snapshot(0)` se a F5 não o fez)
- Modify: `app/src/androidTest/java/com/omegas/prohub/DashboardLevelsRenderTest.kt` (`dashboardDom` :475-492; `curvePositiveDom` :639-670; `globalRouteDom` :672-700 sem `curveSource`/`curveReading`; `curveOfflineDoesNotFabricateEcuRead` :725; `curveOriginalLognovoRendersThirtyDecodedPoints` :747; `dashboardInvalidLevelsPlaceholder` :874)
- Modify: `.github/workflows/verde-android-render-evidence.yml` (`refino-agora-acompanha` → `curva-agora-acompanha` / `curvaAgoraAcompanhaATelemetria`)

**Interfaces:**
- Consumes: ids pinados nas Tasks 6.8–6.11; `#curveEqChart.dataset.pointCount/nowMs` (Task 6.7).

- [ ] **Step 1: Write the failing test** — trocar as sondas e asserts:

```kotlin
// refinoDom
phases: [...document.querySelectorAll('[data-phase]')].map(n => n.dataset.phase + ':' + n.dataset.status + (n.dataset.problem === 'true' ? '!' : '')),
currentButton: document.querySelector('[data-phase][data-status="agora"] .norte-action')?.textContent ?? null,
// refinoCurvaPronta
assertTrue(dom.getJSONArray("phases").toString().contains("PROPOSTA_PRONTA:agora")); assertEquals("Ver ajuste", dom.optString("currentButton"))
// refinoRestaurarTrecho
assertTrue(dom.getJSONArray("phases").toString().contains("VERIFICANDO:agora!")); assertEquals("Desfazer", dom.optString("currentButton"))
// refinoEstavel
assertTrue(dom.getJSONArray("phases").toString().contains("ESTAVEL:agora")); assertTrue(dom.isNull("currentButton"))
// refinoApagoes: abre Pontos e expande o primeiro ponto com nearStallRatio != null → techText contém "Quase-apagões"
// curvaAgoraAcompanhaATelemetria: dois injectLive → document.getElementById('curveEqChart').dataset.nowMs muda (assertNotEquals, delta 0.01)
// curvePositiveDom: pointCount = Number(document.getElementById('curveEqChart')?.dataset.pointCount || 0); backupsTabVisible = [data-subpage="backups"] no viewport
// curveOriginalLognovo: assertEquals(30, pointCount); assertTrue(backupsTabVisible)
// curveOffline: #curveApply desabilitado, #curveEqIndex "—", corpo sem "Gravado"
// dashboardDom: labels de '[data-agora-fact] small'; agoraIndex; dashboardInvalidLevelsPlaceholder: LEVELS RAW "—" e agoraIndex "—"
```

- [ ] **Step 2: Run** `git push && gh pr checks --watch` → `SCENARIO_RESULT=refino-curva-pronta:FAIL` (antes de ajustar as sondas restantes) ou PASS das já ajustadas; corrigir até todos.
- [ ] **Step 3: Ajustar** sondas/asserts restantes e o nome do cenário no workflow.
- [ ] **Step 4: Run** `git push && gh pr checks --watch` → todos os `SCENARIO_RESULT=…:PASS` da matriz (`refino-*`, `curva-agora-acompanha`, `curve-*`, `dashboard-*`, `norte-*`).
- [ ] **Step 5: Commit** `test(android): provas de render do Refino, Curva K e Agora lendo o DOM novo`

---

### Task 6.14: Gate completo e PR

- [ ] **Step 1: Gate**

Run: `tools/ci/remote-test.sh checks ""`
Expected: `REMOTE_TEST=PASS`.

- [ ] **Step 2: Descrição do PR** (`gh pr edit --body-file`): o que mudou (abas 01/03/05, Sugestões fora, `SnapshotFeed`, `SubpageTabs`, `OperationOverlay`, `EquivalenceCanvas`) · classe de prova: 1 contrato, 2 sintético (modelos e 9 estados), 4 APK no emulador (render 1280×720 + pisos 76/24 px), 3 replay real no Refino/Curva repontados · **não provado**: toque no carro, legibilidade a 70 cm real, leitura de cores ao sol, comportamento com ECU física; lacunas do snapshot listadas abaixo.

- [ ] **Step 3: Marcar pronto e mesclar**

Run: `gh pr ready && gh pr checks --watch && gh pr merge --merge`
Expected: `OMEGAS PLATINA CI` e a matriz `render` verdes no SHA do PR; PR mesclado em `OmegasPlatina`.

---

## Lacunas para resolver com a F5 / o índice antes de executar

1. **"Salvar curva atual" não tem intent** (spec §3.1 pede salvar; §2.2 não lista). A F6 não mostra o botão; a foto antes de cada `CURVE_*` já aparece em Backups. Se o dono quiser o botão, falta um intent verde (ex.: `CURVE_BACKUP_SAVE`) no contrato.
2. Campos do snapshot marcados "assumido/lacuna" na tabela do cabeçalho, em especial `nextAction.proposal` (a proposta suavizada `refinedRaw` não tem lugar nas 11 seções; `kTarget` é o alvo cru e não pode ser gravado sem passar pelo `AutoMatchRefinedEngine`), `session.curveBackups` e `session.phase`.
3. Nome do objeto JS exportado por `core/omegas.js` (assumido `OmegasUi.Omegas`; tudo passa por `OmegasUi.omegas()`).
4. Payloads de `CURVE_WRITE`/`CURVE_RESTORE`/`CURVE_RESET`/`UNDO` pinados acima; `UNDO {}` = "última operação desfazível" precisa ser aceito pela fila.
5. API do `StateStore` para publicar seções no androidTest (usada só em `UiStateFixtures.publish`).
6. `core/scheduler.js` continua com o único `setInterval` (pump da faixa de status, Mapa K e AutoCal); a F7 migra esses consumidores para o `SnapshotFeed` e apaga a exceção do `norte-coerencia.test.cjs`. O cabeçalho escondido no AutoCal (`autocal-focus`, `styles.css:67-72`) também fica para a F7.

## Cobertura da spec

| Requisito (spec) | Task |
|---|---|
| §3.1 trilho de 7, rótulos e ordem; Sugestões sai | 6.2, 6.3 |
| §3.1 bugs: `learning`, `routeMeta.predictor`, `drawers.js` mortos, `data-omegas-route` | 6.3, 6.4, 6.8 |
| §3.1 cabeçalho global + uma linha didática por aba | 6.2, 6.5 (`hint`) |
| §3.1 tablist na mesma posição, ≤ 3 itens, última lembrada | 6.5, 6.2 (`navigate` com `subpage`) |
| §3.1 overlay único: etapa → resultado → Desfazer/Voltar | 6.6 |
| §3.1 "Detalhes técnicos" único, último bloco | 6.5, 6.9, 6.11 (`norte-coerencia`) |
| §3.1 Agora: índice, próxima ação com um botão posicionado, curva mini ▲ AGORA, combustível, conexão; nunca executa | 6.8 |
| §3.1 Curva K › Equivalência (Ref tracejada, Própria sólida, 30 pontos por estado, ▲ AGORA, Aplicar com fantasma) | 6.7, 6.9 |
| §3.1 Curva K › Editar e › Backups (listar, restaurar, resetar) | 6.10 (salvar: lacuna 1) |
| §3.1 Refino › Fases (5 fases, estados §1.2, botão único) e › Pontos (30 linhas, expande) | 6.11 |
| §1.2 estados e frase "melhorou a mistura, piorou a suavidade" | 6.7, 6.11 |
| §1.6/§1.6b PROVISÓRIO e "ECU reaprendeu" na UI | 6.9, 6.12 |
| §2.3 etapas visíveis, transporte ≠ ECU, Desfazer | 6.6, 6.12 |
| §3.2 cor é estado; sem cor nova | 6.5, 6.7 |
| §3.2 alvo ≥ 76 px, texto crítico ≥ 24 px, verificado no render | 6.5, 6.12 |
| §3.2 estados obrigatórios renderizados no CI (sem cabo, conectando, sem Referência, ECU reaprendeu, aprendendo, normal, executando, falha transporte, falha ECU) | 6.12 |
| §3.2 contexto preservado ao trocar de aba (subpágina, linha expandida, operação) | 6.5, 6.11, 6.6 |
| §3.3 render só por revisão, diff por seção, sem `setInterval` de UI, redesenho parcial do AGORA | 6.1, 6.7, 6.11 |
| §4.3/§4.5 passo 5: tela Sugestões apagada junto com a UI nova | 6.2, 6.3 |
| §4.6 testes editados (`refino-screen`, `RefinoRenderTest`, `DashboardLevelsRenderTest`) | 6.11, 6.13 |
