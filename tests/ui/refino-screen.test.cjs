const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(ROOT, relative), 'utf8');
const SOURCE = read('app/src/main/assets/ui/screens/refino.js');

function model() {
  const window = { setTimeout: () => 0 };
  window.window = window;
  vm.runInNewContext(SOURCE, { window, globalThis: window, console });
  return window.OmegasUi.RefinoModel;
}

function analysis(changed) {
  return {
    available: true,
    points: Array.from({ length: 30 }, (_, index) => ({
      index,
      currentRaw: 16384,
      calculatedRaw: changed.includes(index) ? 17000 : 16384,
      origin: changed.includes(index) ? 'MEASURED' : 'HELD',
      referenceTimeMs: index + 1,
    })),
  };
}

test('revisão propõe só pontos medidos que mudam; mantidos nunca são gravados', () => {
  const m = model();
  const points = m.proposedPoints(analysis([4, 5, 6]));
  assert.equal(points.length, 3);
  assert.equal(JSON.stringify(points[0]), JSON.stringify({ index: 4, currentRaw: 16384, targetRaw: 17000 }));
  const held = analysis([]);
  held.points[2] = { ...held.points[2], calculatedRaw: 17000, origin: 'HELD' };
  assert.equal(m.proposedPoints(held).length, 0);
});

test('desfazer usa antes/depois do diário', () => {
  const m = model();
  const before = Array(30).fill(16384);
  const after = before.map((v, i) => (i === 10 ? 18000 : v));
  const points = m.undoPoints({ beforeRaw: before, afterRaw: after });
  assert.equal(points.length, 1);
  assert.equal(JSON.stringify(points[0]), JSON.stringify({ index: 10, currentRaw: 18000, targetRaw: 16384 }));
  assert.equal(m.undoPoints({}).length, 0);
});

test('uma ação principal por fase do piloto', () => {
  const m = model();
  const ready = analysis([4]);
  assert.equal(m.primaryAction({ autopilot: { phase: 'PROPOSTA_PRONTA' } }, ready).kind, 'review');
  assert.equal(m.primaryAction({ autopilot: { phase: 'ESTAVEL' } }, ready).kind, 'stable');
  assert.equal(m.primaryAction({ autopilot: { phase: 'RESTAURAR_TRECHO' }, restorePoints: [{ index: 1 }] }, ready).kind, 'restore');
  const early = m.primaryAction({ autopilot: { phase: 'ECU_TRABALHANDO' } }, ready);
  assert.equal(early.kind, 'review');
  assert.equal(early.early, true);
  assert.equal(m.primaryAction({ autopilot: { phase: 'COLETANDO_NOSSOS' } }, analysis([])).kind, 'none');
});

test('Refino é um destino próprio logo abaixo do AutoCal e grava só com conferência e readback', () => {
  const index = read('app/src/main/assets/ui/index.html');
  const router = read('app/src/main/assets/ui/core/router.js');
  assert.ok(index.indexOf('data-route="autocal"') < index.indexOf('data-route="refino"'));
  assert.match(index, /data-screen="refino"/);
  assert.match(router, /screens\/refino\.js/);
  assert.match(SOURCE, /startCurveRead/);
  assert.match(SOURCE, /A Curva K da ECU mudou/);
  assert.match(SOURCE, /done\.state === 'BATCH_CONFIRMED' && done\.readbackValid === true/);
  assert.doesNotMatch(SOURCE, /setInterval/);
  // Mesma linguagem visual do AutoCal da Platina: mesmo gráfico e mesmas classes.
  assert.match(SOURCE, /autocal-reference-svg/);
  assert.match(SOURCE, /AutoCalUxModel/);
});
