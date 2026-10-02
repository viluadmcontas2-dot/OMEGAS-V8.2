const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.resolve(__dirname, '../..');
const SOURCE = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/autocal-refine.js'), 'utf8');

function load() {
  const timers = [];
  const window = { setTimeout: fn => { timers.push(fn); return timers.length; } };
  window.window = window;
  vm.runInNewContext(SOURCE, { window, globalThis: window, console });
  return { ui: window.OmegasUi, window, flush: () => { while (timers.length) timers.shift()(); } };
}

function analysis(overrides = {}) {
  const points = Array.from({ length: 30 }, (_, index) => {
    const changed = index >= 4 && index <= 18;
    const currentRaw = 15000;
    const calculatedRaw = changed ? 15300 : 15000;
    return {
      index, referenceTimeMs: index < 20 ? 0.5 * (index + 1) : 11 + (index - 20),
      currentRaw, calculatedRaw, currentFactor: currentRaw / 16384, calculatedFactor: calculatedRaw / 16384,
      origin: changed ? 'MEASURED' : 'HELD',
    };
  });
  return {
    ok: true, available: true, refinementMode: 'EQUIVALENCE', matureCommonPoints: 8, minimumMatureCommonPoints: 4,
    changedCount: 15, elasticityLimit: 0.35, needsAnotherPass: false, buffersCoherent: true,
    guards: { maximumStepPercent: 15, maximumElasticity: 0.35 },
    metricsBefore: { maxElasticity: 1.92, roughness: 0.09, maxNeighborStep: 0.12 },
    metricsAfter: { maxElasticity: 0.35, roughness: 0.004, maxNeighborStep: 0.04 },
    targets: [], rejectedBands: [{ fuel: 'GASOLINA', band: 9, mapBar: 0.697, timeMs: 6.551 }],
    points, ...overrides,
  };
}

function harness(curveFactors) {
  const { ui, flush } = load();
  const calls = [];
  const host = { innerHTML: '', addEventListener() {} };
  const a = analysis();
  const review = { ok: true, points: a.points.filter(p => p.origin === 'MEASURED').map(p => ({ index: p.index, currentRaw: p.currentRaw, targetRaw: p.calculatedRaw, currentFactor: p.currentFactor, targetFactor: p.calculatedFactor })) };
  const api = {
    refinedAnalysis: () => a,
    createRefinedDraft: () => { calls.push('draft'); return { ok: true }; },
    draftReview: () => review,
    clearDraft: () => calls.push('clear'),
  };
  let op = { busy: false, state: 'IDLE' };
  const native = {
    startCurveRead: () => { calls.push('read'); op = { busy: false, ok: true, state: 'COMPLETED', factorsRaw: curveFactors }; return { ok: true, started: true }; },
    writeCurve: (points, reason) => { calls.push(['write', points.length, reason]); op = { busy: false, ok: true, state: 'BATCH_CONFIRMED', readbackValid: true }; return { ok: true, started: true }; },
    curveOperation: () => op,
  };
  const app = { store: { patch: value => calls.push(['alert', value.alert?.message]) }, api: native };
  const panel = new ui.AutoCalRefinePanel(host, app, api);
  return { panel, host, calls, flush };
}

test('fluxo humano: estados e mensagem principal derivados da análise Kotlin', () => {
  const { ui } = load();
  const model = ui.AutoCalRefineModel;
  assert.equal(JSON.stringify(model.deriveFlow({ available: false })), JSON.stringify({ step: 1, state: 'waiting' }));
  assert.equal(model.deriveFlow({ available: true, refinementMode: 'POLISH', changedCount: 0 }).state, 'collecting');
  assert.equal(model.deriveFlow({ available: true, refinementMode: 'POLISH', changedCount: 3 }).state, 'polish');
  assert.equal(model.deriveFlow(analysis()).state, 'ready');
  assert.equal(model.headline(analysis(), model.deriveFlow(analysis())).title, 'Curva refinada pronta para revisão');
  const polish = { available: true, refinementMode: 'POLISH', changedCount: 4, matureCommonPoints: 1, minimumMatureCommonPoints: 4 };
  assert.match(model.headline(polish, model.deriveFlow(polish)).title, /trancos/);
});

test('painel mostra uma ação primária e detalhes técnicos sob demanda', () => {
  const { panel, host } = harness(Array(30).fill(15000));
  panel.refresh();
  assert.match(host.innerHTML, /Revisar e aplicar 15 pontos/);
  assert.match(host.innerHTML, /<details class="refine-details">/);
  assert.match(host.innerHTML, /GASOLINA B10/);
  assert.equal((host.innerHTML.match(/class="primary"/g) || []).length, 1);
});

test('aplicar relê a curva, confere, grava com readback e oferece restaurar', () => {
  const { panel, host, calls, flush } = harness(Array(30).fill(15000));
  panel.refresh();
  panel.openReview();
  assert.match(host.innerHTML, /Ainda nada foi enviado/);
  assert.ok(!calls.some(c => Array.isArray(c) && c[0] === 'write'));
  panel.apply();
  flush();
  const order = calls.filter(c => c === 'read' || (Array.isArray(c) && c[0] === 'write')).map(c => (Array.isArray(c) ? c[0] : c));
  assert.deepEqual(order, ['read', 'write']);
  assert.match(host.innerHTML, /Gravada e conferida/);
  assert.match(host.innerHTML, /Restaurar curva anterior/);
});

test('curva da ECU diferente do snapshot aborta sem gravar (falha fechada)', () => {
  const factors = Array(30).fill(15000);
  factors[10] = 14000;
  const { panel, host, calls, flush } = harness(factors);
  panel.refresh();
  panel.openReview();
  panel.apply();
  flush();
  assert.ok(!calls.some(c => Array.isArray(c) && c[0] === 'write'));
  assert.match(host.innerHTML, /curva da ECU mudou/);
});
