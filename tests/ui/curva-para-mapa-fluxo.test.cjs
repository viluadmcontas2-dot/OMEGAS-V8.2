// Curva K -> Mapa K no contrato de fluxo: principal fixo "Passar para o Mapa K", plano em 2 etapas (mapa, depois curva),
// "Última gravação" com Desfazer, e a regra 6: interrupção não perde trabalho (o plano fica guardado e o app oferece "Concluir").
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const H = require('./helpers/fluxo-tela.cjs');

const read = file => fs.readFileSync(path.join(H.ui, file), 'utf8');
const points = raws => raws.map((factorRaw, index) => ({ index, petrolMs: 1.5 + index * 0.35, factorRaw, factor: factorRaw / 16384 }));
const NODE = { index: 1, currentRaw: 16384, targetRaw: 16000 };

function harness(storage) {
  const nodes = new Map([['mapFluxo', H.hospedeiro()], ['mapCurveFluxo', H.hospedeiro()]]);
  const node = id => ({
    id, textContent: '', innerHTML: '', value: '', disabled: false, hidden: false, dataset: {}, style: {},
    classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
    querySelector() { return null; }, querySelectorAll() { return []; }, addEventListener() {}, prepend() {}, insertBefore() {}, focus() {},
  });
  const document = {
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, node(id)); return nodes.get(id); },
    querySelector() { return null; }, querySelectorAll() { return []; }, createElement() { return node('el'); },
  };
  const context = { document, console, setTimeout: () => 0, OmegasApp: { scheduler: { nextEvent: async () => {} } } };
  context.globalThis = context; context.window = context;
  vm.runInNewContext(read('map-editor.js'), context);
  const F = H.instalarFluxo(context);
  if (storage) F.pending.use(storage);
  vm.runInNewContext(read('core/balance-runner.js'), context);
  vm.runInNewContext(read('screens/map.js'), context);
  const mapWrites = [], curveWrites = [];
  let mapOp = { state: 'IDLE' }, curveOp = {}, curveRaw = 16384, curveFails = false;
  const api = {
    writeMap(items, reason) { mapWrites.push({ items: JSON.parse(JSON.stringify(items)), reason }); return { ok: true, started: true, startedAt: mapWrites.length }; },
    mapWriteOperation: () => mapOp,
    startMapRead: () => ({ ok: true, started: true }),
    startCurveRead() { curveOp = curveFails ? { state: 'FAILED', ok: false, error: 'Tempo limite aguardando a central.' }
      : { state: 'COMPLETED', ok: true, points: points(Array.from({ length: 30 }, (_, i) => (i === 1 ? curveRaw : 16384))) }; return { ok: true, started: true }; },
    curveOperation: () => curveOp,
    writeCurve(list) { curveWrites.push(JSON.parse(JSON.stringify(list))); curveRaw = list[0].targetRaw; curveOp = { state: 'BATCH_CONFIRMED', readbackValid: true, ok: true }; return { ok: true, started: true }; },
  };
  const alerts = [];
  const store = { state: { map: {} }, get() { return this.state; }, patch(p) { Object.assign(this.state, p); if (p.alert) alerts.push(p.alert.message); } };
  const screen = new context.OmegasUi.MapScreen(store, api, null);
  const load = () => screen.editor.load({
    rows: Array.from({ length: 12 }, (_, r) => Array.from({ length: 12 }, (_, c) => 110 + r + c)),
    axes: { petrolBins: [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18], rpmBins: [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500] },
  });
  load(); screen.renderEditor();
  const propose = () => {
    screen.curvePlan = { changes: [{ row: 3, column: 0, target: 125 }], nodes: [{ ...NODE }], armed: false };
    screen.editor.setTargetOverrides([{ row: 3, column: 0, target: 125 }]);
    screen.renderEditor();
  };
  const confirmMap = () => { mapOp = { state: 'BATCH_CONFIRMED', readbackValid: true, startedAt: mapWrites.length, busy: false }; screen.pollWrite(); screen.reading = false; load(); screen.renderEditor(); };
  return { screen, nodes, F, api, mapWrites, curveWrites, alerts, propose, confirmMap, setCurveFails: v => { curveFails = v; }, reload: () => { load(); screen.renderEditor(); }, host: nodes.get('mapCurveFluxo'), local: nodes.get('mapFluxo'), curveRaw: () => curveRaw };
}
const memoria = () => { const m = new Map(); return { setItem: (k, v) => m.set(k, v), getItem: k => m.get(k) ?? null, removeItem: k => m.delete(k), m }; };
// Deixa as promessas correrem e termina a releitura do Mapa K que a tela pede ao fim (como o poll faria).
const settle = async (h) => { for (let i = 0; i < 8; i += 1) await H.tick(); if (h) { h.screen.reading = false; h.reload(); } };

test('Curva -> Mapa K: principal fixo, mapa e depois curva, Última gravação, Desfazer, passar 2x', async () => {
  const h = harness(memoria()); const p = H.principalDe(h.host);
  assert.ok(p, 'principal montado na barra (#mapCurveFluxo)');
  assert.match(p.textContent, /^Passar para o Mapa K$/, 'com o mapa lido o principal já está pronto');
  assert.match(h.local.textContent, /Nenhuma gravação ainda/, 'a Última gravação é a do painel (um bloco só)');
  const antes = [...H.foto(h.host, 'curva'), ...H.foto(h.local, 'map')];
  const agora = () => [...H.foto(h.host, 'curva'), ...H.foto(h.local, 'map')];
  h.propose();
  assert.deepEqual(H.compararEstados(antes, agora()), [], 'proposta na grade');
  p.click();
  assert.equal(h.mapWrites.length, 1, 'um toque grava o mapa, sem janela de confirmação');
  assert.equal(p.textContent, 'Gravando…');
  assert.equal(H.principalDe(h.local).textContent, 'Gravando…', 'o principal do Ajuste local também fica ocupado');
  assert.deepEqual(H.compararEstados(antes, agora()), [], 'gravando');
  h.confirmMap(); await settle(h);
  assert.deepEqual(h.curveWrites, [[NODE]], 'só depois do mapa confirmado a curva é gravada');
  assert.equal(h.F.pending.get('curva-mapa'), null, 'plano concluído não deixa pendência');
  assert.match(h.screen.last.view().what, /Mapa K: 1 células · Curva K: 1 ponto/);
  assert.equal(H.desfazerDe(h.local).getAttribute('aria-disabled'), 'false');
  assert.deepEqual(H.compararEstados(antes, agora()), [], 'gravado');

  // Desfazer: mapa de volta, depois a curva de volta.
  H.desfazerDe(h.local).click();
  assert.equal(h.mapWrites.length, 2);
  assert.deepEqual(h.mapWrites[1].items.map(i => [i.row, i.column, i.target]), [[3, 0, 113]]);
  h.confirmMap(); await settle(h);
  assert.deepEqual(h.curveWrites[1], [{ index: 1, currentRaw: 16000, targetRaw: 16384 }]);
  assert.equal(h.screen.last.view().undone, true);
  assert.deepEqual(H.compararEstados(antes, agora()), [], 'desfeito');

  // Passar de novo sem nenhum Desfazer no meio funciona e a nova passagem vira a "última".
  h.propose(); p.click(); assert.equal(h.mapWrites.length, 3);
  h.confirmMap(); await settle(h);
  assert.equal(h.screen.last.view().undone, undefined);
  assert.equal(h.F.history().filter(e => e.flowId === 'map').length, 2);
  assert.deepEqual(H.compararEstados(antes, agora()), [], 'passar 2x');
});

test('Regra 6: curva falhou depois do mapa gravado -> plano guardado; ao reabrir o app oferece Concluir e conclui', async () => {
  const storage = memoria();
  const h = harness(storage); const p = H.principalDe(h.host);
  h.setCurveFails(true); h.propose(); p.click(); h.confirmMap(); await settle(h);
  assert.equal(h.screen.curvePlan.failed, 'Tempo limite aguardando a central.');
  assert.equal(h.F.history().filter(e => e.flowId === 'map').length, 0, 'sem curva não há "Última gravação"');
  assert.equal(h.nodes.get('mapCurvePending').textContent, 'Mapa K gravado. Curva K ainda não → Concluir');
  assert.equal(h.nodes.get('mapFinishCurve').disabled, false);
  assert.equal(p.getAttribute('aria-disabled'), 'false', 'o principal segue ativo: tocar conclui');
  assert.ok(storage.getItem('omegas.fluxo.curva-mapa'), 'plano guardado em armazenamento durável');

  // App reaberto (tela nova, mesmo armazenamento): lembra o que faltou, sem refazer o plano.
  const again = harness(storage);
  assert.equal(again.screen.curvePlan.armed, true);
  assert.equal(again.nodes.get('mapCurvePending').textContent, 'Mapa K gravado. Curva K ainda não → Concluir');
  assert.equal(again.nodes.get('mapFinishCurve').disabled, false);
  H.principalDe(again.host).click(); await settle(again);
  assert.deepEqual(again.curveWrites, [[NODE]]);
  assert.equal(again.mapWrites.length, 0, 'concluir grava só a curva');
  assert.equal(again.F.pending.get('curva-mapa'), null);
  assert.match(again.screen.last.view().what, /Curva K: 1 ponto/);
  assert.equal(again.nodes.get('mapFinishCurve').disabled, true);
});
