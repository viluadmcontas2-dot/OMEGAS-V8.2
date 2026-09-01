const test = require('node:test');
const assert = require('node:assert/strict');
const view = require('../../app/src/main/assets/ui/core/learning-model.js');

function cell(fuel, row, column, epoch, samples = 20) {
  return { fuel, row, column, epoch, samples, visit_count: 2, session_count: 2, confidence: 0.9, stage: 'CONFIRMED' };
}
function comparison(row, column) {
  return { petrol_target_ms: 5, petrol_on_cng_ms: 5.5, continuous_cell_weights: [{ row, column, weight: 1 }] };
}

test('classifica vazio, gasolina, GNV e comparável sem inventar prontidão', () => {
  const model = view.buildModel({ epoch: 3, grid: { rows: 12, columns: 12 }, cells: [
    cell('PETROL', 0, 1, 0),
    cell('CNG', 0, 2, 3),
    cell('PETROL', 0, 3, 0), cell('CNG', 0, 3, 3),
    cell('PETROL', 0, 4, 0), cell('CNG', 0, 4, 3),
  ], comparisons: [comparison(0, 4)] });
  const at = key => model.cells.find(item => item.key === key);
  assert.equal(at('0:0').state, view.STATES.EMPTY);
  assert.equal(at('0:1').state, view.STATES.PETROL);
  assert.equal(at('0:2').state, view.STATES.CNG);
  assert.equal(at('0:3').state, view.STATES.COMPARABLE);
  assert.equal(at('0:3').ready, false);
  assert.equal(at('0:4').ready, true);
});

test('GNV antigo fica histórico e não contamina a época atual', () => {
  const model = view.buildModel({ epoch: 7, grid: { rows: 12, columns: 12 }, cells: [
    cell('PETROL', 2, 2, 0), cell('CNG', 2, 2, 6),
  ], comparisons: [comparison(2, 2)] });
  const target = model.cells.find(item => item.key === '2:2');
  assert.equal(target.state, view.STATES.PETROL);
  assert.equal(target.ready, false);
  assert.equal(target.previousCng.length, 1);
  assert.match(target.readinessReason, /falta evidência GNV da época atual/i);
});

test('gasolina permanece referência ao mudar a época', () => {
  const model = view.buildModel({ epoch: 9, grid: { rows: 12, columns: 12 }, cells: [cell('PETROL', 4, 5, 0)] });
  const target = model.cells.find(item => item.key === '4:5');
  assert.ok(target.petrol);
  assert.equal(target.petrol.epoch, 0);
  assert.equal(target.state, view.STATES.PETROL);
});

test('ordem dos dados não altera classificação nem contagens', () => {
  const cells = [cell('PETROL', 1, 1, 0), cell('CNG', 1, 1, 4), cell('PETROL', 2, 2, 0)];
  const a = view.buildModel({ epoch: 4, grid: { rows: 12, columns: 12 }, cells, comparisons: [comparison(1, 1)] });
  const b = view.buildModel({ epoch: 4, grid: { rows: 12, columns: 12 }, cells: [...cells].reverse(), comparisons: [comparison(1, 1)] });
  assert.deepEqual(a.counts, b.counts);
  assert.deepEqual(a.cells.map(x => [x.key, x.state, x.ready]), b.cells.map(x => [x.key, x.state, x.ready]));
});

test('dados inválidos não criam células prontas', () => {
  const model = view.buildModel({ epoch: 2, grid: { rows: 12, columns: 12 }, cells: [
    cell('UNKNOWN', 1, 1, 2), cell('CNG', -1, 99, 2),
  ], comparisons: [{ continuous_cell_weights: [{ row: -1, column: 0, weight: 1 }] }] });
  assert.equal(model.counts.ready, 0);
  assert.equal(model.counts.empty, 144);
});

test('modelo aceita listas petrol e cng atuais sem depender do instalador antigo', () => {
  const model = view.buildModel({
    epoch: 5,
    grid: { rows: 12, columns: 12, rpmBins: [1000], petrolBins: [2.5] },
    petrol: [cell('PETROL', 0, 0, 0)],
    cng: [cell('CNG', 0, 0, 5)],
    comparisons: [comparison(0, 0)],
  });
  const target = model.cells.find(item => item.key === '0:0');
  assert.equal(target.petrolMs, 2.5);
  assert.equal(target.rpm, 1000);
  assert.equal(target.ready, true);
  assert.equal(target.previousCng.length, 0);
});

test('preserva RPM MAP e tempo medio reais de gasolina e GNV para a camada didatica', () => {
  const model = view.buildModel({
    epoch: 4,
    grid: { rows: 12, columns: 12, rpmBins: [2500], petrolBins: [4.5] },
    cells: [
      { ...cell('PETROL', 0, 0, 0, 8), rpm: 2488, map_bar: 0.604, petrol_ms: 4.42, petrol_spread_ms: 0.08, quality: 0.93 },
      { ...cell('CNG', 0, 0, 4, 7), rpm: 2496, map_bar: 0.611, petrol_ms: 4.84, petrol_spread_ms: 0.10, quality: 0.89 },
    ],
    comparisons: [comparison(0, 0)],
  });
  const target = model.cells.find(item => item.key === '0:0');
  assert.equal(target.petrol.petrolMs, 4.42);
  assert.equal(target.petrol.rpm, 2488);
  assert.equal(target.petrol.mapBar, 0.604);
  assert.equal(target.cng.petrolMs, 4.84);
  assert.equal(target.cng.rpm, 2496);
  assert.equal(target.cng.mapBar, 0.611);
  assert.equal(target.petrol.petrolSpreadMs, 0.08);
  assert.equal(target.cng.quality, 0.89);
});

test('projecao GLOBAL_ONLY nao ocupa uma celula de diferenca local', () => {
  assert.equal(view.localComparisonPrediction({ supportType: 'GLOBAL_ONLY', predictedErrorPercent: 0.9 }), null);
  assert.equal(view.localComparisonPrediction({ supportType: 'DIRECT', predictedErrorPercent: 0.9 }).predictedErrorPercent, 0.9);
  assert.equal(view.localComparisonPrediction({ supportType: 'NEAR', predictedErrorPercent: 0.7 }).predictedErrorPercent, 0.7);
});
