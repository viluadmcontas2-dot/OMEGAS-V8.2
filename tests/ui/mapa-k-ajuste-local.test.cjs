// Dono, 2026-10-11 ("a curva deve aprender tudo e saber onde ajustar o quê"): o Refino abre o Mapa K com o ajuste local
// em % por célula. O alvo sai da prévia Kotlin (o mesmo caminho do ajuste percentual manual), sobre o valor lido da central:
// o JS não faz a conta do Mapa K. Abrir não grava; a gravação continua no botão "Gravar" com readback.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const editorSource = fs.readFileSync(path.join(ui, 'map-editor.js'), 'utf8');
const screenSource = fs.readFileSync(path.join(ui, 'screens/map.js'), 'utf8');

function harness(previewReply) {
  const nodes = new Map();
  const node = id => ({
    id, textContent: '', innerHTML: '', value: '', disabled: false, dataset: {}, style: {},
    classList: { add() {}, remove() {}, toggle() {} },
    querySelector() { return null; }, querySelectorAll() { return []; }, addEventListener() {}, prepend() {}, insertBefore() {}, focus() {},
  });
  const document = {
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, node(id)); return nodes.get(id); },
    querySelector() { return null; }, querySelectorAll() { return []; }, createElement() { return node('el'); },
  };
  const context = { document, console };
  context.globalThis = context;
  context.window = context;
  vm.runInNewContext(editorSource, context);
  vm.runInNewContext(screenSource, context);
  const previews = [];
  const api = {
    previewMapAdjustment(cells, mode, value) {
      previews.push({ cells: cells.map(c => [c.row, c.column, c.current]), mode, value });
      return previewReply(cells, mode, value);
    },
    writeMap() { throw new Error('abrir o Mapa K não pode gravar'); },
  };
  const alerts = [];
  const store = { state: { map: {} }, get() { return this.state; }, patch(p) { Object.assign(this.state, p); if (p.alert) alerts.push(p.alert.message); } };
  const screen = new context.OmegasUi.MapScreen(store, api, null);
  screen.editor.load({
    rows: Array.from({ length: 12 }, (_, r) => Array.from({ length: 12 }, (_, c) => 110 + r + c)),
    axes: { petrolBins: [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18], rpmBins: [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500] },
  });
  return { screen, previews, alerts, context };
}

const plain = value => JSON.parse(JSON.stringify(value));
const kotlinPreview = (cells, mode, value) => ({ ok: true, items: cells.map(c => ({ row: c.row, column: c.column, target: c.current + Math.round(value) })) });

test('ajuste local em % usa a prévia Kotlin, um pedido por percentual, sobre o valor lido da central', () => {
  const { screen, previews } = harness(kotlinPreview);
  screen.applyContext({ origin: 'refino', suggestion: { mapChanges: [
    { row: 3, column: 0, deltaPercent: 12 }, { row: 4, column: 0, deltaPercent: 12 }, { row: 5, column: 0, deltaPercent: -6 },
  ] } });
  assert.equal(previews.length, 2, 'um pedido de prévia por percentual');
  assert.deepEqual(plain(previews.map(p => [p.mode, p.value])), [['percent', 12], ['percent', -6]]);
  assert.deepEqual(plain(previews[0].cells), [[3, 0, 113], [4, 0, 114]], 'a prévia recebe o valor lido da central');
  const review = screen.editor.buildReview();
  assert.deepEqual(plain(review.items.map(i => [i.row, i.column, i.current, i.target])), [[3, 0, 113, 125], [4, 0, 114, 126], [5, 0, 115, 109]],
    'o alvo é exatamente o que o Kotlin devolveu');
});

test('prévia Kotlin indisponível: nenhum alvo inventado no JS, aviso ao dono', () => {
  const { screen, alerts } = harness(() => ({ ok: false, error: 'Prévia Kotlin do Mapa K indisponível' }));
  screen.applyContext({ origin: 'refino', suggestion: { mapChanges: [{ row: 3, column: 0, deltaPercent: 12 }] } });
  assert.equal(screen.editor.changeCount(), 0);
  assert.match(alerts.join(' '), /Prévia Kotlin do Mapa K indisponível/);
});

test('sugestão pendente não prepara nem grava; toque prepara percentuais pela prévia nativa', () => {
  const { screen, previews } = harness(kotlinPreview);
  const suggestion = { verdict: 'SUGERIR', reason: 'RESIDUAL_LOCAL', cells: [
    { row: 3, column: 0, deltaPercent: 12, current: 999, target: 255 },
  ], equivalence: { cells: [{ band: 0, column: 0, rpm: 850, fromMs: 3, toMs: 4.5, errorPercent: 12 }] } };
  screen.renderMapSuggestion(suggestion);
  assert.equal(previews.length, 0);
  assert.equal(screen.editor.selectionCount(), 0);
  screen.prepareMapSuggestion();
  assert.equal(previews.length, 1);
  assert.equal(screen.editor.buildReview().items[0].target, 125, 'ignora alvo antigo; usa mapa presente e Kotlin');
});

test('tabela distingue zero, ausência e diferença medida; não usa residual como equivalência', () => {
  const { screen } = harness(kotlinPreview);
  const html = screen.equivalenceTable({ cells: [
    { band: 0, column: 0, rpm: 850, fromMs: 3, toMs: 4.5, errorPercent: null },
    { band: 0, column: 1, rpm: 1350, fromMs: 3, toMs: 4.5, errorPercent: 0 },
    { band: 0, column: 2, rpm: 1850, fromMs: 3, toMs: 4.5, errorPercent: -8.2, residualPercent: 99 },
  ] });
  assert.match(html, /—/);
  assert.match(html, /0,0%/);
  assert.match(html, /-8,2%/);
  assert.doesNotMatch(html, /99/);
  assert.doesNotMatch(html, /button|input/);
});

test('invalidação ou falta de dados impede preparar sugestão', () => {
  const { screen, previews } = harness(kotlinPreview);
  screen.renderMapSuggestion({ verdict: 'SEM_EVIDENCIA', cells: [{ row: 0, column: 0, deltaPercent: 12 }] });
  screen.prepareMapSuggestion();
  screen.renderMapSuggestion(null);
  screen.prepareMapSuggestion();
  assert.equal(previews.length, 0);
});


test('ponte invalida a sugestão entre render e toque: não prepara alvo antigo', () => {
  const { screen, previews, context } = harness(kotlinPreview);
  screen.renderMapSuggestion({ verdict: 'SUGERIR', cells: [{ row: 3, column: 0, deltaPercent: 12 }] });
  context.OmegasUi.AutoCalApi = { mapSuggestion: () => null };
  screen.prepareMapSuggestion();
  assert.equal(previews.length, 0);
  assert.equal(screen.editor.selectionCount(), 0);
});

// Revisão 2026-10-18 (achado 7): a sugestão era redesenhada só dentro do poll() da leitura/gravação, e ANTES de a leitura
// terminar (botão ficava desabilitado). Agora: redesenho pelo gancho de status da rota Mapa K e logo após a leitura.
test('sugestão redesenha fora da leitura e o botão libera quando a leitura termina', () => {
  const { screen, context } = harness(kotlinPreview);
  const suggestion = { verdict: 'SUGERIR', reason: 'RESIDUAL_LOCAL', cells: [{ row: 3, column: 0, deltaPercent: 12 }] };
  context.OmegasUi.AutoCalApi = { mapSuggestion: () => suggestion };
  const button = context.document.getElementById('mapPrepareSuggestion');
  button.disabled = true;
  screen.refreshSuggestion();
  assert.equal(button.disabled, false, 'tela parada também mostra a sugestão nova');

  button.disabled = true;
  Object.assign(screen, { buildGrid() {}, renderGrid() {}, renderEditor() {}, applyContext() {}, continueCurveToMap() {} });
  screen.api.mapReadResult = () => ({ ok: true, state: 'COMPLETED', busy: false,
    rows: Array.from({ length: 12 }, () => Array(12).fill(150)),
    axes: { petrolBins: [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18], rpmBins: [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500] } });
  screen.api.mapWriteOperation = () => ({});
  screen.reading = true;
  screen.poll();
  assert.equal(screen.reading, false);
  assert.equal(button.disabled, false, 'no fim da leitura a sugestão é redesenhada já com a leitura concluída');
});

test('app.js redesenha a sugestão no gancho de status da rota Mapa K', () => {
  const app = fs.readFileSync(path.join(ui, 'app.js'), 'utf8');
  const status = app.slice(app.indexOf('function refreshStatus'), app.indexOf('function toolsEditing'));
  assert.match(status, /route === 'map'[^\n]*refreshSuggestion/);
});

// Re-revisão 2026-10-18 (C): o redesenho a cada amostra de status chamava getEquivalence (índice + bandas típicas +
// sugestão). A sugestão tem ponte própria e barata (getMapSuggestion, memorizada por revisão + sessão + mapa).
test('redesenho da sugestão por amostra não chama a Equivalência inteira', () => {
  const { screen, context } = harness(kotlinPreview);
  let equivalenceCalls = 0;
  let suggestionCalls = 0;
  const suggestion = { verdict: 'SUGERIR', reason: 'RESIDUAL_LOCAL', cells: [{ row: 3, column: 0, deltaPercent: 12 }] };
  context.OmegasUi.AutoCalApi = {
    equivalence: () => { equivalenceCalls += 1; return { mapSuggestion: suggestion }; },
    mapSuggestion: () => { suggestionCalls += 1; return suggestion; },
  };
  for (let i = 0; i < 5; i += 1) screen.refreshSuggestion();
  screen.prepareMapSuggestion();
  assert.equal(equivalenceCalls, 0, 'refresh e toque usam só a ponte da sugestão');
  assert.equal(suggestionCalls, 6);
  assert.equal(screen.mapSuggestion, suggestion);
});

test('AutoCalApi expõe mapSuggestion pela ponte getMapSuggestion', () => {
  const api = fs.readFileSync(path.join(ui, 'core/autocal-api.js'), 'utf8');
  assert.match(api, /mapSuggestion: \(\) => invoke\('getMapSuggestion'/);
  const bridge = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt'), 'utf8');
  const fn = bridge.slice(bridge.indexOf('fun getMapSuggestion'), bridge.indexOf('fun getMapSuggestion') + 600);
  assert.ok(bridge.indexOf('fun getMapSuggestion') > 0, 'ponte barata existe');
  assert.match(bridge.slice(bridge.indexOf('fun getMapSuggestion') - 40, bridge.indexOf('fun getMapSuggestion')), /@JavascriptInterface/);
  assert.match(fn, /mapSuggestionJson\(\)/);
  assert.doesNotMatch(fn.slice(0, fn.indexOf('catch')), /index\(\)|typicalBandsJson|denseBandsJson/);
});
