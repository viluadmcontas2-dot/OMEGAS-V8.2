'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

function loadScript(relativePath, extra = {}) {
  const source = fs.readFileSync(path.join(__dirname, '../..', relativePath), 'utf8');
  const context = { console, Date, Math, JSON, Intl, Set, Map, ...extra };
  context.window = context;
  context.globalThis = context;
  context.OmegasUi = context.OmegasUi || {};
  vm.createContext(context);
  vm.runInContext(source, context, { filename: path.basename(relativePath) });
  return { context, source };
}

class FakeClassList {
  constructor() { this.values = new Set(); }
  add(...names) { names.forEach(name => this.values.add(name)); }
  remove(...names) { names.forEach(name => this.values.delete(name)); }
  contains(name) { return this.values.has(name); }
  toggle(name, force) {
    if (force === true) { this.values.add(name); return true; }
    if (force === false) { this.values.delete(name); return false; }
    if (this.values.has(name)) { this.values.delete(name); return false; }
    this.values.add(name); return true;
  }
}

function fakeCell() {
  const styles = new Map();
  const trace = { textContent: '' };
  return {
    classList: new FakeClassList(),
    style: {
      setProperty: (key, value) => styles.set(key, String(value)),
      removeProperty: key => styles.delete(key),
      getPropertyValue: key => styles.get(key) || '',
    },
    querySelector: selector => selector === '.cell-trace' ? trace : { textContent: '' },
    trace,
  };
}

test('histórico visual registra apenas decisões significativas, deduplica e limita a seis', () => {
  const { context } = loadScript('app/src/main/assets/ui/screens/learning.js');
  const screen = Object.create(context.OmegasUi.LearningScreen.prototype);
  screen.decisionHistory = [];
  screen.lastDecisionHistorySignature = '';

  screen.observeDecision({
    state: 'FORMING_SAMPLE', reason_code: 'FORMING_SAMPLE', reason: 'Formando 4/10',
    learning_eligible: false, fuel_confirmed: 'PETROL', cell_row: 1, cell_column: 1,
  });
  assert.equal(screen.decisionHistory.length, 0, 'formação normal não deve poluir o histórico');

  const accepted = {
    state: 'SAMPLE_ACCEPTED', reason_code: 'SAMPLE_ACCEPTED', reason: 'Amostra forte',
    learning_eligible: true, fuel_confirmed: 'PETROL', cell_row: 1, cell_column: 1,
  };
  screen.observeDecision(accepted);
  screen.observeDecision(accepted);
  assert.equal(screen.decisionHistory.length, 1, 'mesma decisão não pode duplicar');
  assert.equal(screen.decisionHistory[0].level, 'accepted');

  for (let index = 0; index < 9; index += 1) {
    screen.observeDecision({
      state: 'SAMPLE_REJECTED', reason_code: `REJECT_${index}`, reason: `Motivo ${index}`,
      learning_eligible: false, fuel_confirmed: index % 2 ? 'CNG' : 'PETROL',
      cell_row: index % 12, cell_column: (index + 1) % 12,
    });
  }
  assert.equal(screen.decisionHistory.length, 6, 'histórico visual precisa permanecer limitado');
  assert.equal(screen.decisionHistory[0].code, 'REJECT_8');
  assert.equal(screen.decisionHistory[5].code, 'REJECT_3');
});

test('Live Tracing temporal é limitado, reutiliza setTrace e não cria timer', () => {
  const { context, source } = loadScript('app/src/main/assets/ui/components/physical-grid.js');
  assert.equal(source.includes('setInterval'), false);
  assert.equal(source.includes('setTimeout'), false);

  const grid = Object.create(context.OmegasUi.PhysicalGrid.prototype);
  grid.rows = 12;
  grid.columns = 12;
  grid.cells = new Map();
  grid.traceKeys = new Set();
  grid.activeTraceKey = null;
  grid.traceTrail = new Map();
  grid.traceTrailMs = 1400;
  grid.traceTrailMax = 16;
  grid.key = (row, column) => `${row}:${column}`;

  for (let row = 0; row < 12; row += 1) {
    for (let column = 0; column < 12; column += 1) {
      grid.cells.set(`${row}:${column}`, fakeCell());
    }
  }

  grid.setTrace([{ row: 0, column: 0, weight: 1 }], { row: 0, column: 0 });
  grid.setTrace([{ row: 0, column: 1, weight: 1 }], { row: 0, column: 1 });
  assert.equal(grid.cells.get('0:0').classList.contains('live-trail'), true, 'célula anterior deve virar rastro');
  assert.equal(grid.cells.get('0:1').classList.contains('live-contributor'), true, 'célula atual continua contribuição ativa');

  for (let index = 0; index < 30; index += 1) {
    const row = Math.floor(index / 12);
    const column = index % 12;
    grid.setTrace([{ row, column, weight: 0.75 }], { row, column });
  }
  assert.equal(grid.traceTrail.size <= 16, true, 'rastro precisa ser estritamente limitado');
});
