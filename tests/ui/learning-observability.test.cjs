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

test('restore do Learning é explícito sem esconder a telemetria ao vivo', () => {
  const { context } = loadScript('app/src/main/assets/ui/screens/learning.js');
  const screen = Object.create(context.OmegasUi.LearningScreen.prototype);
  screen.collectionPane = {
    innerHTML: '',
    querySelector: () => null,
  };
  screen.decisionHistory = [];

  screen.renderCollection({
    learningStatus: {
      state: 'LEARNING_RESTORING',
      restoring: true,
      learning: false,
      reason: 'Restaurando conhecimento persistido.',
    },
    learningDecision: {
      state: 'OBSERVING_ENGINE',
      reason_code: 'OBSERVING_ENGINE',
      reason: 'Aguardando decisão do núcleo.',
      learning_eligible: false,
    },
    learningTolerance: {},
    telemetry: {
      live: { rpm: 2500, petrol_ms: 4.2, load_bar: 0.41, fuel: 'CNG' },
      interpolation: { rpm: 2500, petrolMs: 4.2, mapBar: 0.41 },
    },
  });

  assert.match(screen.collectionPane.innerHTML, /Restaurando/);
  assert.match(screen.collectionPane.innerHTML, /EM SEGUNDO PLANO/);
  assert.match(screen.collectionPane.innerHTML, /LEARNING_RESTORE_PENDING/);
  assert.match(screen.collectionPane.innerHTML, /2\.500/);
  assert.match(screen.collectionPane.innerHTML, /4,20 ms/);
});

test('grade física não conserva rastro temporal nem cria timer', () => {
  const { source } = loadScript('app/src/main/assets/ui/components/physical-grid.js');
  assert.equal(source.includes('setInterval'), false);
  assert.equal(source.includes('setTimeout'), false);
  assert.equal(source.includes('setTrace('), false);
  assert.equal(source.includes('traceTrail'), false);
});
