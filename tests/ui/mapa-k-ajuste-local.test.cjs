// Dono, 2026-10-11 ("a curva deve aprender tudo e saber onde ajustar o quê"): o Refino abre o Mapa K com o ajuste local
// em % por célula. O alvo sai da prévia Kotlin (o mesmo caminho do ajuste percentual manual), sobre o valor lido da ECU:
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
  return { screen, previews, alerts };
}

const plain = value => JSON.parse(JSON.stringify(value));
const kotlinPreview = (cells, mode, value) => ({ ok: true, items: cells.map(c => ({ row: c.row, column: c.column, target: c.current + Math.round(value) })) });

test('ajuste local em % usa a prévia Kotlin, um pedido por percentual, sobre o valor lido da ECU', () => {
  const { screen, previews } = harness(kotlinPreview);
  screen.applyContext({ origin: 'refino', suggestion: { mapChanges: [
    { row: 3, column: 0, deltaPercent: 12 }, { row: 4, column: 0, deltaPercent: 12 }, { row: 5, column: 0, deltaPercent: -6 },
  ] } });
  assert.equal(previews.length, 2, 'um pedido de prévia por percentual');
  assert.deepEqual(plain(previews.map(p => [p.mode, p.value])), [['percent', 12], ['percent', -6]]);
  assert.deepEqual(plain(previews[0].cells), [[3, 0, 113], [4, 0, 114]], 'a prévia recebe o valor lido da ECU');
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
