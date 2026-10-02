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

function harness(curveFactors, equivalence) {
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
    equivalence: () => equivalence || { ok: false },
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
  assert.match(model.headline(polish, model.deriveFlow(polish)).title, /linearidade/);
});

test('painel mostra uma ação primária e detalhes técnicos sob demanda', () => {
  const { panel, host } = harness(Array(30).fill(15000));
  panel.refresh();
  assert.match(host.innerHTML, /Revisar e aplicar 15 pontos/);
  assert.match(host.innerHTML, /<details class="refine-details">/);
  assert.match(host.innerHTML, /GASOLINA B10/);
  assert.match(host.innerHTML, /Puxada no GNV/);
  assert.doesNotMatch(host.innerHTML, /Serrilhado/);
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

function pilotEquivalence(phase, extra = {}) {
  const before = Array(30).fill(16384);
  const after = before.map((v, i) => (i >= 20 && i <= 23 ? 18000 : v));
  return {
    ok: true, ratio: 1.06, samples: 80,
    autopilot: { phase, headline: `fase ${phase}`, next: 'faça algo', autoMatchCount: 3, maxAutomatch: 3, petrolValid: 18, gasValid: 16, ourPoints: 80, canDisconnect: phase === 'ESTAVEL' },
    refinement: { latest: { status: 'PIOROU_EM_PARTE', beforeRaw: before, afterRaw: after, bands: [
      { fromMs: 3, toMs: 4.5, verdict: 'PASSOU', ratioBefore: 1.05, ratioAfter: 0.96 },
      { fromMs: 7.5, toMs: 9, verdict: 'PIOROU', ratioBefore: 1.02, ratioAfter: 1.09 },
      { fromMs: 9, toMs: 12, verdict: 'NAO_ALTERADA' },
    ] } },
    restorePoints: [{ index: 20, currentRaw: 18000, targetRaw: 16384 }, { index: 21, currentRaw: 18000, targetRaw: 16384 }],
    ...extra,
  };
}

test('piloto mostra a fase, os pontos da ECU e os nossos, e avisa quando pode desconectar', () => {
  const { panel, host } = harness(Array(30).fill(15000), pilotEquivalence('ESTAVEL'));
  panel.refresh();
  assert.match(host.innerHTML, /data-phase="ESTAVEL"/);
  assert.match(host.innerHTML, /automático ECU 3\/3/);
  assert.match(host.innerHTML, /80 pontos nossos/);
  assert.match(host.innerHTML, /<b>pode desconectar<\/b>/);
});

test('ECU ainda no automático: avisa que ela pode sobrescrever a curva', () => {
  const { panel, host } = harness(Array(30).fill(15000), pilotEquivalence('ECU_TRABALHANDO'));
  panel.refresh();
  assert.match(host.innerHTML, /ainda está no automático e pode sobrescrever/);
});

test('diário: veredito por faixa e restaurar só o trecho que piorou (com conferência e readback)', () => {
  const factors = Array(30).fill(15000);
  factors[20] = 18000;
  factors[21] = 18000;
  const { panel, host, calls, flush } = harness(factors, pilotEquivalence('RESTAURAR_TRECHO'));
  panel.refresh();
  assert.match(host.innerHTML, /Um trecho piorou/);
  assert.match(host.innerHTML, /passou do ponto/);
  assert.doesNotMatch(host.innerHTML, /NAO_ALTERADA/);
  assert.match(host.innerHTML, /Restaurar trecho que piorou \(2 pontos\)/);
  panel.restoreBand();
  flush();
  const write = calls.find(c => Array.isArray(c) && c[0] === 'write');
  assert.ok(write);
  assert.equal(write[1], 2);
  assert.match(write[2], /trecho que piorou/);
});

test('desfazer a última gravação usa antes/depois do diário', () => {
  const { ui } = load();
  const points = ui.AutoCalRefineModel.undoPoints(pilotEquivalence('VERIFICANDO').refinement.latest);
  assert.equal(points.length, 4);
  assert.equal(JSON.stringify(points[0]), JSON.stringify({ index: 20, currentRaw: 18000, targetRaw: 16384 }));
  assert.equal(ui.AutoCalRefineModel.undoPoints({}).length, 0);
});

test('Agora: cartão da calibração resume fase e índice GNV ÷ gasolina', () => {
  const src = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/dashboard.js'), 'utf8');
  const window = {};
  window.window = window;
  vm.runInNewContext(src, { window, globalThis: window, console });
  const card = window.OmegasUi.DashboardModel.pilotCard(pilotEquivalence('PROPOSTA_PRONTA'));
  assert.equal(card.tone, 'accent');
  assert.match(card.detail, /\+6,0%/);
  assert.equal(window.OmegasUi.DashboardModel.pilotCard({ autopilot: { phase: 'SEM_ECU' } }), null);
});
