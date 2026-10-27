// Ajuste global (Curva K) no contrato de fluxo: principal fixo "Gravar N pontos" (nunca vira "Toque em Restaurar"),
// "Última gravação" com Desfazer, gravar a próxima mudança sem desfazer a anterior.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const H = require('./helpers/fluxo-tela.cjs');

const source = fs.readFileSync(path.join(H.ui, 'screens/curve.js'), 'utf8');
const points = raws => raws.map((factorRaw, index) => ({ index, petrolMs: 1.5 + index * 0.35, factorRaw, factor: factorRaw / 16384 }));

function harness() {
  const nodes = new Map();
  const node = id => ({
    id, textContent: '', innerHTML: '', value: '', disabled: false, hidden: false, dataset: {},
    classList: { add() {}, remove() {}, toggle() {} },
    querySelector() { return { textContent: '' }; }, querySelectorAll() { return []; }, addEventListener() {},
  });
  nodes.set('curveFluxo', H.hospedeiro());
  const document = {
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, node(id)); return nodes.get(id); },
    querySelector() { return node('root'); }, querySelectorAll() { return []; },
  };
  const context = { document, console };
  context.globalThis = context; context.window = context;
  H.instalarFluxo(context);
  vm.runInNewContext(source, context);
  const writes = []; let op = { busy: false, state: 'COMPLETED', ok: true, points: points(Array(30).fill(16384)) };
  const api = {
    startCurveRead: () => ({ ok: true, started: true }), curveOperation: () => op, curveBackups: () => [],
    writeCurve(list, reason) { writes.push({ list: JSON.parse(JSON.stringify(list)), reason }); return { ok: true, started: true }; },
  };
  const store = { state: { curve: {} }, get() { return this.state; }, patch(p) { Object.assign(this.state, p); } };
  const screen = new context.OmegasUi.CurveScreen(store, api);
  screen.data = op; screen.points = () => op.points;
  const host = nodes.get('curveFluxo');
  const prepare = (index, to) => { screen.proposals.set(index, { index, petrolMs: 2, currentRaw: 16384, targetRaw: to, currentFactor: 1, targetFactor: to / 16384, deltaPercent: 1 }); screen.renderProposalList(); };
  const confirm = () => { op = { busy: false, state: 'BATCH_CONFIRMED', readbackValid: true, ok: true, points: op.points }; screen.poll(); op = { busy: false, state: 'COMPLETED', ok: true, points: points(Array(30).fill(16384)) }; screen.reading = false; };
  return { screen, host, writes, prepare, confirm, nodes };
}

test('Ajuste global: principal fixo, Última gravação com Desfazer, gravar 2x e desfazer sem nada sumir', async () => {
  const h = harness(); const p = H.principalDe(h.host);
  assert.ok(p, 'botão principal montado em #curveFluxo');
  h.screen.renderProposalList();
  assert.match(p.textContent, /Gravar 0 pontos · Escolha um trecho e ajuste/);
  assert.equal(p.getAttribute('aria-disabled'), 'true');
  const antes = H.foto(h.host, 'curve');

  h.prepare(2, 16548); h.prepare(5, 16000);
  assert.equal(p.getAttribute('aria-disabled'), 'false'); assert.equal(p.textContent, 'Gravar 2 pontos');
  assert.deepEqual(H.compararEstados(antes, H.foto(h.host, 'curve')), [], 'preparado');
  p.click();
  assert.equal(h.writes.length, 1, 'um toque grava, sem janela de confirmação');
  assert.equal(p.textContent, 'Gravando…');
  assert.deepEqual(H.compararEstados(antes, H.foto(h.host, 'curve')), [], 'gravando');
  h.confirm(); await H.tick(); await H.tick();
  assert.match(h.screen.last.view().what, /2 pontos/);
  assert.equal(H.desfazerDe(h.host).getAttribute('aria-disabled'), 'false');
  assert.match(p.textContent, /Gravar 0 pontos · /);
  assert.deepEqual(H.compararEstados(antes, H.foto(h.host, 'curve')), [], 'gravado');

  h.prepare(9, 16600); p.click(); assert.equal(h.writes.length, 2, 'gravar a próxima sem desfazer a anterior');
  h.confirm(); await H.tick(); await H.tick();
  assert.match(h.screen.last.view().what, /1 ponto/);
  assert.deepEqual(H.compararEstados(antes, H.foto(h.host, 'curve')), [], 'gravar 2x');

  H.desfazerDe(h.host).click();
  assert.equal(h.writes.length, 3);
  assert.deepEqual(h.writes[2].list, [{ index: 9, currentRaw: 16600, targetRaw: 16384 }], 'devolve o valor de antes');
  h.confirm(); await H.tick(); await H.tick();
  assert.equal(h.screen.last.view().undone, true);
  assert.equal(H.desfazerDe(h.host).getAttribute('aria-disabled'), 'true');
  assert.deepEqual(H.compararEstados(antes, H.foto(h.host, 'curve')), [], 'desfeito');
});

test('Ajuste global: restauração preparada não troca o principal por "Toque em Restaurar"', () => {
  const h = harness(); const p = H.principalDe(h.host);
  h.screen.restoreContext = { fileName: 'b.json' };
  h.prepare(3, 16000);
  assert.equal(p.textContent, 'Gravar 1 pontos');
  assert.equal(p.getAttribute('aria-disabled'), 'false');
  assert.doesNotMatch(p.textContent, /Restaurar/);
});
