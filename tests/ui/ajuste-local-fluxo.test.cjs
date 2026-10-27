// Ajuste local no contrato de fluxo: botão principal fixo "Gravar N células", bloco "Última gravação" com Desfazer,
// gravar a próxima mudança sem desfazer a anterior. Compara a posição/visibilidade dos botões em cada estado.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const H = require('./helpers/fluxo-tela.cjs');

const editorSource = fs.readFileSync(path.join(H.ui, 'map-editor.js'), 'utf8');
const screenSource = fs.readFileSync(path.join(H.ui, 'screens/map.js'), 'utf8');

function harness() {
  const nodes = new Map();
  const node = id => ({
    id, textContent: '', innerHTML: '', value: '', disabled: false, hidden: false, dataset: {}, style: {},
    classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
    querySelector() { return null; }, querySelectorAll() { return []; }, addEventListener() {}, prepend() {}, insertBefore() {}, focus() {},
  });
  nodes.set('mapFluxo', H.hospedeiro());
  nodes.set('mapCurveFluxo', H.hospedeiro());
  const document = {
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, node(id)); return nodes.get(id); },
    querySelector() { return null; }, querySelectorAll() { return []; }, createElement() { return node('el'); },
  };
  const context = { document, console, setTimeout: () => 0 };
  context.globalThis = context; context.window = context;
  vm.runInNewContext(editorSource, context);
  H.instalarFluxo(context);
  vm.runInNewContext(screenSource, context);
  const writes = []; let op = { state: 'IDLE' };
  const api = {
    previewMapAdjustment: (cells, mode, value) => ({ ok: true, items: cells.map(c => ({ ...c, target: c.current + Math.round(value) })) }),
    writeMap(items, reason) { writes.push({ items: JSON.parse(JSON.stringify(items)), reason }); return { ok: true, started: true, startedAt: writes.length }; },
    mapWriteOperation: () => op,
    startMapRead: () => ({ ok: true, started: true }),
  };
  const store = { state: { map: {} }, get() { return this.state; }, patch(p) { Object.assign(this.state, p); } };
  const screen = new context.OmegasUi.MapScreen(store, api, null);
  const load = () => screen.editor.load({
    rows: Array.from({ length: 12 }, (_, r) => Array.from({ length: 12 }, (_, c) => 110 + r + c)),
    axes: { petrolBins: [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18], rpmBins: [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500] },
  });
  load();
  document.getElementById('mapAdjustmentMode').value = 'delta';
  const host = nodes.get('mapFluxo');
  const pick = (cells, value) => {
    screen.editor.clearSelection(); cells.forEach(([r, c]) => screen.editor.toggle(r, c));
    document.getElementById('mapAdjustmentValue').value = String(value);
    screen.applyAdjustment();
  };
  // Confirmação da central; a tela relê o Mapa K em seguida (aqui: recarrega o editor, como a leitura de volta faria).
  const confirm = () => { op = { state: 'BATCH_CONFIRMED', readbackValid: true, startedAt: writes.length, busy: false }; screen.pollWrite(); screen.reading = false; load(); screen.renderEditor(); };
  return { screen, host, writes, pick, confirm, api };
}

test('Ajuste local: principal fixo, Última gravação com Desfazer, gravar 2x e desfazer sem nada sumir', async () => {
  const h = harness();
  const p = H.principalDe(h.host), last = h.screen.last;
  assert.ok(p, 'botão principal montado no hospedeiro #mapFluxo');
  assert.match(p.textContent, /Gravar 0 células · Escolha um trecho e ajuste/);
  assert.equal(p.getAttribute('aria-disabled'), 'true');
  assert.match(h.host.textContent, /Nenhuma gravação ainda/);
  const antes = H.foto(h.host, 'map');

  h.pick([[3, 0], [3, 1]], 2);
  assert.equal(p.getAttribute('aria-disabled'), 'false');
  assert.match(p.textContent, /^Gravar 2 células$/);
  assert.deepEqual(H.compararEstados(antes, H.foto(h.host, 'map')), [], 'preparado');

  p.click();
  assert.equal(h.writes.length, 1, 'um toque grava, sem janela de confirmação');
  assert.equal(p.textContent, 'Gravando…');
  assert.deepEqual(H.compararEstados(antes, H.foto(h.host, 'map')), [], 'gravando');
  h.confirm(); await H.tick(); await H.tick();
  assert.match(last.view().what, /2 células/);
  assert.match(H.ultimaDe(h.host).textContent, /Última gravação/);
  assert.equal(H.desfazerDe(h.host).getAttribute('aria-disabled'), 'false');
  assert.equal(p.getAttribute('aria-disabled'), 'true');
  assert.match(p.textContent, /Gravar 0 células · /);
  assert.deepEqual(H.compararEstados(antes, H.foto(h.host, 'map')), [], 'gravado');

  // Gravar a próxima mudança NÃO exige desfazer a anterior.
  h.pick([[5, 5]], 3); p.click();
  assert.equal(h.writes.length, 2);
  h.confirm(); await H.tick(); await H.tick();
  assert.match(last.view().what, /1 célula/);
  assert.equal(h.screen.last.view().undone, undefined);
  assert.deepEqual(H.compararEstados(antes, H.foto(h.host, 'map')), [], 'gravar 2x');

  // Desfazer devolve os valores de antes, pela mesma ponte.
  H.desfazerDe(h.host).click();
  assert.equal(h.writes.length, 3);
  assert.deepEqual(h.writes[2].items.map(i => [i.row, i.column, i.target]), [[5, 5, 120]]);
  h.confirm(); await H.tick(); await H.tick();
  assert.equal(last.view().undone, true);
  assert.equal(H.desfazerDe(h.host).getAttribute('aria-disabled'), 'true');
  assert.deepEqual(H.compararEstados(antes, H.foto(h.host, 'map')), [], 'desfeito');
  h.pick([[7, 7]], 1); p.click(); assert.equal(h.writes.length, 4, 'desfazer e gravar de novo funciona');
});

test('Ajuste local: falha de gravação deixa o principal no lugar com o caminho de volta', async () => {
  const h = harness(); const p = H.principalDe(h.host);
  h.api.writeMap = () => ({ ok: false, error: 'Central ocupada' });
  h.pick([[3, 0]], 2); p.click(); await H.tick(); await H.tick();
  assert.equal(h.host.querySelector('[data-fluxo="principal"]'), p);
  assert.equal(p.hidden, false);
  assert.equal(h.screen.last.view(), null, 'nada foi gravado, nada foi registrado');
});
