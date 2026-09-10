'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const ROOT = path.resolve(__dirname, '../..');
const learningJs = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/learning.js'), 'utf8');

function element() {
  return {
    textContent: '', innerHTML: '', dataset: {}, classList: { toggle() {} },
    setAttribute() {}, addEventListener() {}, querySelectorAll() { return []; },
    querySelector() { return null; },
  };
}

function harness() {
  const nodes = new Map();
  ['learningGrid', 'learningCellDetail', 'learningCoverageSummary', 'learningSuggestionSummary'].forEach(id => nodes.set(id, element()));
  const screen = element();
  const updates = new Map();
  const context = {
    console,
    document: {
      querySelector(selector) { return selector === '[data-screen="learning"]' ? screen : null; },
      getElementById(id) { return nodes.get(id) || null; },
    },
    OmegasUi: {
      PhysicalGrid: class {
        constructor() {
          this.cells = new Map();
          for (let row = 0; row < 12; row += 1) for (let column = 0; column < 12; column += 1) {
            this.cells.set(`${row}:${column}`, { dataset: { row: String(row), column: String(column) } });
          }
        }
        setAxes() {}
        updateCell(row, column, value) { updates.set(`${row}:${column}`, value); }
      },
      LearningModel: { buildModel() { return { cells: [], counts: { petrol: 0, cng: 0, comparable: 0 }, epoch: 1 }; }, STATES: { COMPARABLE: 'COMPARABLE' } },
    },
  };
  context.window = context;
  context.globalThis = context;
  vm.runInNewContext(learningJs, context, { filename: 'learning.js' });
  const store = { get() { return {}; }, patch() {} };
  const router = { navigate() {} };
  const screenInstance = new context.OmegasUi.LearningScreen(store, router, {});
  return { screen: screenInstance, nodes, updates };
}

test('missing correction multiplier is never rendered as a valid zero target', () => {
  const { screen, nodes } = harness();
  screen.render({
    learning: { grid: { rpmBins: [], petrolBins: [] }, comparisons: [] },
    learningLayer: 'comparison',
    calibrationState: { proposal: { available: false, state: 'MEASURE_ACTUATOR_GAIN' } },
  });
  assert.match(nodes.get('learningSuggestionSummary').textContent, /aguardando ganho causal/i);
  assert.doesNotMatch(nodes.get('learningSuggestionSummary').textContent, /0[,.]0000/);
});

test('Blue comparison is rendered in its explicit current-GNV mapKCell', () => {
  const { screen, updates } = harness();
  screen.render({
    learning: {
      grid: { rpmBins: [], petrolBins: [] },
      comparisons: [{
        rpm: 1850, mapBar: 0.55, petrolReferenceMs: 4.5, petrolOnCngMs: 6.0,
        errorPercent: 33.3, quality: 0.9,
        row: 5, column: 2, cellKey: '5:2',
        mapKCell: { row: 5, column: 2, key: '5:2' },
      }],
    },
    learningLayer: 'comparison', calibrationState: { proposal: {} },
  });
  const rendered = updates.get('5:2');
  assert.ok(rendered, 'comparison must reach mapKCell 5:2');
  assert.match(rendered.text, /33[,.]3%/);
});

test('nested Blue proposal with explicit availability and numeric target is visible', () => {
  const { screen, nodes } = harness();
  screen.render({
    learning: { grid: { rpmBins: [], petrolBins: [] }, comparisons: [] },
    learningLayer: 'comparison',
    calibrationState: { proposal: { available: true, state: 'PROPOSAL_READY', correctionMultiplier: 1.075 } },
  });
  assert.match(nodes.get('learningSuggestionSummary').textContent, /1[,.]0750/);
});


test('missing K readback is shown as an actionable prerequisite', () => {
  const { screen, nodes } = harness();
  screen.render({
    learning: { grid: { rpmBins: [], petrolBins: [] }, comparisons: [] },
    learningLayer: 'comparison',
    calibrationState: {
      reason: 'CALIBRATION_READBACK_REQUIRED',
      error: 'Leia Mapa K e Curva K nesta sessão antes de calcular propostas.',
      proposal: { available: false, state: 'CALIBRATION_READBACK_REQUIRED' },
    },
  });
  const summary = nodes.get('learningSuggestionSummary').textContent;
  assert.match(summary, /leia mapa k e curva k/i);
  assert.doesNotMatch(summary, /aguardando ganho causal/i);
});
