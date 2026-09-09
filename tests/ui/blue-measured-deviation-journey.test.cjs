'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const ROOT = path.resolve(__dirname, '../..');
const learningJs = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/learning.js'), 'utf8');

function loadLearningScreen() {
  const context = {
    console,
    document: { querySelector() { return null; }, getElementById() { return null; } },
    OmegasUi: { LearningModel: { STATES: { COMPARABLE: 'COMPARABLE' } } },
  };
  context.window = context;
  context.globalThis = context;
  vm.runInNewContext(learningJs, context, { filename: 'learning.js' });
  return context.OmegasUi.LearningScreen;
}

const LearningScreen = loadLearningScreen();

function comparison(overrides = {}) {
  return {
    rpm: 1500,
    mapBar: 0.50,
    petrolReferenceMs: 4.00,
    petrolOnCngMs: 4.40,
    errorPercent: 10.0,
    quality: 0.95,
    row: 4,
    column: 3,
    cellKey: '4:3',
    mapKCell: { row: 4, column: 3, key: '4:3' },
    ...overrides,
  };
}

function screenForDetail(model = { cells: [], counts: { petrol: 0, cng: 0, comparable: 0 }, epoch: 1 }) {
  const instance = Object.create(LearningScreen.prototype);
  instance.cellPane = { innerHTML: '' };
  instance.buildEvidenceModel = () => model;
  return instance;
}

function stateWith(item, proposal = { available: false, state: 'MEASURE_ACTUATOR_GAIN' }) {
  return {
    learning: {
      grid: { rpmBins: [900, 1200, 1500, 1800], petrolBins: [3, 3.5, 4, 4.5, 5] },
      comparisons: item ? [item] : [],
    },
    calibrationState: {
      latestComparison: item || null,
      proposal,
    },
  };
}

function renderGrid(item) {
  const updates = new Map();
  const instance = Object.create(LearningScreen.prototype);
  instance.root = { querySelectorAll() { return []; } };
  instance.grid = {
    cells: new Map([['4:3', { dataset: { row: '4', column: '3' } }]]),
    setAxes() {},
    updateCell(row, column, value) { updates.set(`${row}:${column}`, value); },
  };
  instance.selectedCell = null;
  instance.observeDecision = () => {};
  instance.buildEvidenceModel = () => ({ cells: [], counts: { petrol: 0, cng: 0, comparable: 0 }, epoch: 1 });
  instance.renderCollection = () => {};
  instance.renderDetail = () => {};
  instance.render({ ...stateWith(item), learningLayer: 'comparison' });
  return updates.get('4:3');
}

test('journey output +10 percent reaches comparison cell as a measured value', () => {
  const rendered = renderGrid(comparison());
  assert.ok(rendered, 'comparison cell must be updated');
  assert.match(rendered.text, /^\+10[,.]0%$/);
  assert.equal(rendered.subtext, 'par medido');
  assert.equal(rendered.hasData, true);
});

test('journey output zero percent remains numeric instead of becoming blank', () => {
  const rendered = renderGrid(comparison({ petrolOnCngMs: 4.00, errorPercent: 0.0 }));
  assert.match(rendered.text, /^0[,.]0%$/);
  assert.equal(rendered.subtext, 'par medido');
  assert.notEqual(rendered.text, '·');
  assert.notEqual(rendered.text, '•');
});

test('journey output negative ten percent reaches comparison cell with sign intact', () => {
  const rendered = renderGrid(comparison({ petrolOnCngMs: 3.60, errorPercent: -10.0 }));
  assert.match(rendered.text, /^-10[,.]0%$/);
  assert.equal(rendered.subtext, 'par medido');
});

test('detail panel shows the exact measured petrol to GNV journey', () => {
  const item = comparison();
  const instance = screenForDetail();
  instance.renderDetail(stateWith(item), 4, 3);
  assert.match(instance.cellPane.innerHTML, /Desvio medido/i);
  assert.match(instance.cellPane.innerHTML, /4[,.]00[^0-9]+4[,.]40 ms[^0-9]+\+10[,.]0%/);
});

test('missing causal gain never hides an already measured deviation', () => {
  const item = comparison();
  const instance = screenForDetail();
  instance.renderDetail(stateWith(item, { available: false, state: 'MEASURE_ACTUATOR_GAIN' }), 4, 3);
  assert.match(instance.cellPane.innerHTML, /4[,.]00[^0-9]+4[,.]40 ms[^0-9]+\+10[,.]0%/);
  assert.match(instance.cellPane.innerHTML, /MEASURE_ACTUATOR_GAIN/);
  assert.match(instance.cellPane.innerHTML, /Nenhum alvo K/i);
});

test('deadband measurement remains visible while proposal stays separate', () => {
  const item = comparison({ petrolOnCngMs: 4.04, errorPercent: 1.0 });
  const instance = screenForDetail();
  instance.renderDetail(stateWith(item, { available: false, state: 'MEASURED_WITHIN_ACTION_DEADBAND' }), 4, 3);
  assert.match(instance.cellPane.innerHTML, /4[,.]00[^0-9]+4[,.]04 ms[^0-9]+\+1[,.]0%/);
  assert.match(instance.cellPane.innerHTML, /MEASURED_WITHIN_ACTION_DEADBAND/);
});

test('no equivalent pair is explicit and never rendered as zero deviation', () => {
  const instance = screenForDetail();
  instance.renderDetail(stateWith(null), 4, 3);
  assert.match(instance.cellPane.innerHTML, /ainda não existe par equivalente válido/i);
  assert.doesNotMatch(instance.cellPane.innerHTML, />0[,.]0%/);
});

test('measured comparison is addressed by current GNV cell and not petrol reference value', () => {
  const item = comparison({ petrolReferenceMs: 3.50, petrolOnCngMs: 4.40, errorPercent: 25.7142857 });
  const rendered = renderGrid(item);
  assert.ok(rendered, 'explicit GNV cell 4:3 must be populated');
  assert.match(rendered.text, /^\+25[,.]7%$/);
});
