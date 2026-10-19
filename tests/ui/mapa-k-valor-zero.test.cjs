// Mapa K (dono, 2026-10-11): tocar numa célula com o valor 0 não habilita "Gravar" nem mostra o aviso
// "A alteração escolhida não muda nenhuma célula." — o botão fica desabilitado em "Escolha quanto mudar".
// A trava de gravação continua: buildReview() recusa gravar quando nada muda.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const editorSource = fs.readFileSync(path.join(ui, 'map-editor.js'), 'utf8');
const screenSource = fs.readFileSync(path.join(ui, 'screens/map.js'), 'utf8');
const index = fs.readFileSync(path.join(ui, 'index.html'), 'utf8');

function harness() {
  const nodes = new Map();
  const node = id => ({
    id, textContent: '', innerHTML: '', value: '', disabled: false, dataset: {}, style: {},
    classList: { add() {}, remove() {}, toggle() {} },
    querySelector() { return null; }, querySelectorAll() { return []; }, addEventListener() {}, prepend() {}, insertBefore() {},
  });
  const document = {
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, node(id)); return nodes.get(id); },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    createElement() { return node('el'); },
  };
  const context = { document, console };
  context.globalThis = context;
  context.window = context;
  vm.runInNewContext(editorSource, context);
  vm.runInNewContext(screenSource, context);
  const previews = [];
  let previewTarget = current => current + 1;
  const api = {
    previewMapAdjustment(cells, mode, value) {
      previews.push([mode, value]);
      return { ok: true, items: cells.map(cell => ({ ...cell, target: previewTarget(cell.current) })) };
    },
  };
  const alerts = [];
  const store = { state: { map: {} }, get() { return this.state; }, patch(p) { Object.assign(this.state, p); if (p.alert) alerts.push(p.alert.message); } };
  const screen = new context.OmegasUi.MapScreen(store, api, null);
  screen.editor.load({
    rows: Array.from({ length: 12 }, () => Array(12).fill(120)),
    axes: { petrolBins: [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18], rpmBins: [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500] },
  });
  const input = nodes.get('mapAdjustmentValue') || document.getElementById('mapAdjustmentValue');
  document.getElementById('mapAdjustmentMode').value = 'percent';
  // Mesmo caminho do toque na célula (pointerup): seleciona, desenha o editor e pede a prévia.
  const tap = (row, column) => {
    screen.editor.toggle(row, column);
    screen.review = null;
    screen.renderEditor(row, column);
    screen.refreshSelectionPreview();
  };
  return { screen, nodes, input, tap, alerts, previews, button: () => nodes.get('mapReviewButton'), setTarget(fn) { previewTarget = fn; } };
}

test('valor 0: tocar numa célula não habilita Gravar nem mostra aviso', () => {
  const h = harness();
  h.input.value = '0';
  h.tap(3, 4);
  assert.deepEqual(h.alerts, [], 'nenhum aviso sem o dono ter errado');
  assert.equal(h.previews.length, 0, 'somar 0 % não pede prévia');
  assert.equal(h.button().disabled, true);
  assert.equal(h.button().textContent, 'Gravar na central');
  assert.equal(h.nodes.get('mapActiveCell').textContent, '3,5 ms · 3.000 RPM', 'milhar com ponto, como no cabeçalho');
});

test('com +1 o botão diz quantas células mudam; prévia sem mudança também não avisa', () => {
  const h = harness();
  h.input.value = '0';
  h.tap(3, 4);
  h.input.value = '1';
  h.screen.applyAdjustment();
  assert.equal(h.button().disabled, false);
  assert.equal(h.button().textContent, 'Gravar 1 alteração na central');
  h.setTarget(current => current); // ex.: 0,2 % que arredonda para o mesmo K
  h.input.value = '0.2';
  h.screen.applyAdjustment();
  assert.deepEqual(h.alerts, []);
  assert.equal(h.button().disabled, true);
  assert.equal(h.button().textContent, 'Gravar na central');
  assert.throws(() => h.screen.editor.buildReview(), /não muda nenhuma célula/, 'a gravação continua recusada quando nada muda');
});

test('textos do Mapa K para leigo', () => {
  assert.match(index, />Selecionar tudo</);
  assert.doesNotMatch(index, /Selecionar 144|Linha técnica 0C protegida|Prévia somente/);
  assert.match(index, /<b>Nada foi gravado\.<\/b><span>Confira os números e toque em Gravar; o app confere na central sozinho\.<\/span>/);
  assert.equal((screenSource.match(/Última linha: só leitura/g) || []).length, 1, '"Última linha: só leitura" uma vez só');
  assert.match(screenSource, /text\('mapSourceStatus', 'Mapa lido da central'\)/);
});
