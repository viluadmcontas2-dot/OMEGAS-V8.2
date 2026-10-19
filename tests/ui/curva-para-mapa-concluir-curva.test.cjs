// Curva K → Mapa K, etapa 2 falhou (auditoria, 2026-10-16): o mapa novo já está na central e a curva planejada não. Antes o aviso
// mandava "refazer o plano" — e um plano novo partiria do produto já dobrado (mapa novo × curva velha), fixando a dobra.
// Agora o plano fica armado com `failed`, o rodapé do Mapa K mostra "Concluir curva" (só a etapa 2 de novo) e o botão some
// quando a curva confirma. Uma gravação manual no meio não dispara a etapa 2 sozinha nem mata a pendência.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(ui, file), 'utf8');

function harness() {
  const nodes = new Map();
  const node = id => {
    const kids = new Map();
    return {
      id, textContent: '', innerHTML: '', value: '', disabled: false, hidden: false, dataset: {}, style: {},
      classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
      querySelector(sel) { if (!kids.has(sel)) kids.set(sel, node(null)); return kids.get(sel); },
      querySelectorAll() { return []; }, addEventListener() {}, prepend() {}, insertBefore() {}, focus() {},
    };
  };
  const document = {
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, node(id)); return nodes.get(id); },
    querySelector() { return null; }, querySelectorAll() { return []; }, createElement() { return node('el'); },
  };
  const context = { document, console, setTimeout: () => 0 };
  context.globalThis = context;
  context.window = context;
  vm.runInNewContext(read('map-editor.js'), context);
  vm.runInNewContext(read('screens/map.js'), context);
  const alerts = [];
  const writes = [];
  const store = { state: { map: {} }, get() { return this.state; }, patch(p) { Object.assign(this.state, p); if (p.alert) alerts.push(p.alert.message); } };
  let operation = { state: 'IDLE' };
  const api = {
    writeMap(...args) { writes.push(args); operation = { state: 'QUEUED', busy: true, startedAt: writes.length }; return { ok: true, started: true, startedAt: writes.length }; },
    mapWriteOperation: () => operation,
    startMapRead: () => ({ ok: true, started: true }),
    mapReadResult: () => ({ ok: true, busy: true, state: 'READING' }),
    previewMapAdjustment: () => ({ ok: false, error: 'sem prévia neste teste' }),
  };
  const screen = new context.OmegasUi.MapScreen(store, api, null);
  screen.editor.load({
    rows: Array.from({ length: 12 }, (_, r) => Array.from({ length: 12 }, (_, c) => 110 + r + c)),
    axes: { petrolBins: [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18], rpmBins: [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500] },
  });
  return { context, screen, alerts, writes, node: id => document.getElementById(id), confirmLast() { operation = { state: 'BATCH_CONFIRMED', busy: false, ok: true, readbackValid: true, confirmedCells: 1, totalCells: 1, startedAt: writes.length }; } };
}

const PLAN = () => ({ changes: [{ row: 3, column: 0, target: 125 }], nodes: [{ index: 1, currentRaw: 16384, targetRaw: 16000 }], armed: true });

test('etapa 2 falhou: plano fica armado, "Concluir curva" aparece e o aviso diz o que fazer sem mandar refazer o plano', async () => {
  const { context, screen, alerts, node } = harness();
  screen.renderEditor();
  assert.equal(node('mapFinishCurve').hidden, true, 'sem etapa pendente o botão não ocupa lugar');
  let outcome = { ok: false, error: 'Tempo limite aguardando a central.' };
  let runs = 0;
  context.OmegasBalanceRunner = { writeCurveStage: async () => { runs += 1; return outcome; } };
  screen.curvePlan = PLAN();
  await screen.runCurveStage();
  assert.equal(runs, 1);
  assert.equal(screen.curvePlan?.armed, true, 'o plano continua armado');
  assert.equal(screen.curvePlan.failed, 'Tempo limite aguardando a central.');
  assert.equal(node('mapFinishCurve').hidden, false, 'o rodapé oferece Concluir curva');
  assert.match(alerts.at(-1), /mapa foi gravado, mas a curva NÃO/);
  assert.match(alerts.at(-1), /Concluir curva/);
  assert.doesNotMatch(alerts.at(-1), /refazer o plano|refaça o plano/i);
  // Curva mudou (runner recusa): a etapa continua pendente, sem mandar refazer o plano.
  outcome = { ok: false, stale: true, error: 'A Curva K da central mudou desde a leitura do plano. Nada foi gravado na curva.' };
  await screen.runCurveStage();
  assert.equal(node('mapFinishCurve').hidden, false);
  assert.doesNotMatch(alerts.at(-1), /refazer o plano|refaça o plano/i);
  // De novo, e a releitura mostra a curva já no alvo (o runner trata como concluído): plano some, botão some.
  outcome = { ok: true, written: 0, message: 'A curva já está como o plano pede.' };
  await screen.runCurveStage();
  assert.equal(runs, 3);
  assert.equal(screen.curvePlan, null);
  assert.equal(node('mapFinishCurve').hidden, true);
  assert.match(alerts.at(-1), /gravados e conferidos pela central/);
});

test('gravação manual com a etapa 2 pendente não dispara a curva sozinha nem apaga a pendência', async () => {
  const { context, screen, writes, node, confirmLast } = harness();
  let runs = 0;
  context.OmegasBalanceRunner = { writeCurveStage: async () => { runs += 1; return { ok: false, error: 'NAK' }; } };
  screen.curvePlan = { ...PLAN(), failed: 'Tempo limite aguardando a central.' };
  screen.renderEditor();
  assert.equal(node('mapFinishCurve').hidden, false);
  screen.editor.setTargetOverrides([{ row: 5, column: 2, target: 140 }]); // edição manual de outra célula
  screen.writePrepared();
  assert.equal(writes.length, 1, 'a edição manual grava normalmente');
  confirmLast();
  screen.pollWrite();
  await Promise.resolve();
  assert.equal(runs, 0, 'a etapa 2 só roda com o toque em Concluir curva');
  assert.equal(screen.curvePlan?.failed, 'Tempo limite aguardando a central.', 'a pendência continua');
});

test('rodapé do Mapa K: o botão nasce escondido ao lado de Curva K → Mapa K, com a mesma classe, e chama só a etapa 2', () => {
  const html = read('index.html');
  const bar = html.match(/<div class="surface-toolbar"><div>([\s\S]*?)<\/div><\/div>/)?.[1] || '';
  assert.match(bar, /id="mapFromCurve"[^>]*class="quiet-button"/);
  assert.match(bar, /id="mapFinishCurve"[^>]*class="quiet-button"[^>]*hidden[^>]*>Concluir curva</);
  assert.ok(bar.indexOf('mapFromCurve') < bar.indexOf('mapFinishCurve'));
  const map = read('screens/map.js');
  assert.match(map, /getElementById\('mapFinishCurve'\)\?\.addEventListener\('click', \(\) => this\.runCurveStage\(\)\)/);
  assert.doesNotMatch(map, /refazer o plano/);
  assert.doesNotMatch(read('core/balance-runner.js'), /refaça o plano/);
});
