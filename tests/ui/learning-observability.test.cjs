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

  assert.match(screen.collectionPane.innerHTML, /Learning restaurando/);
  assert.match(screen.collectionPane.innerHTML, /EM SEGUNDO PLANO/);
  assert.match(screen.collectionPane.innerHTML, /LEARNING_RESTORE_PENDING/);
  assert.match(screen.collectionPane.innerHTML, /2\.500/);
  assert.match(screen.collectionPane.innerHTML, /4,20 ms/);
});

test('payload de sugestão mostra tempos em ms e o novo valor K sem confundir as unidades', () => {
  const { context } = loadScript('app/src/main/assets/ui/screens/learning.js');
  const screen = Object.create(context.OmegasUi.LearningScreen.prototype);
  screen.cellPane = { innerHTML: '', querySelector: () => ({ addEventListener: () => {} }) };
  screen.router = { navigate: () => {} };
  screen.renderDetail({
    learning: {
      grid: { rpmBins: [2000], petrolBins: [4.5] },
      regions: [
        { fuel: 'GASOLINA', epoch: 0, cell_row: 0, cell_column: 0, rpm: 2000, map_bar: 0.5, petrol_ms: 4.7, samples: 6, visit_count: 6, confidence: 0.8 },
        { fuel: 'GNV', epoch: 1, cell_row: 0, cell_column: 0, rpm: 2000, map_bar: 0.5, petrol_ms: 5.08, samples: 6, visit_count: 6, confidence: 0.8 },
      ],
      comparisons: [{ cell_row: 0, cell_column: 0, petrol_target_ms: 4.7, petrol_on_cng_ms: 5.08, error_percent: 8.1 }],
    },
    calibrationState: {
      suggestionItems: [{ target: 'MAP_K', lifecycle: 'PENDING', actionable: true, mapChanges: [{ row: 0, column: 0, before: 100, after: 108 }] }],
      learningStability: { map: [{ row: 0, column: 0, confidence: 0.8, consolidatedErrorPercent: 8.1, state: 'CONSOLIDATED' }] },
    },
    telemetry: { live: { fuel: 'GNV' } },
  }, 0, 0);

  assert.match(screen.cellPane.innerHTML, /Gasolina esperada.*4,70 ms/);
  assert.match(screen.cellPane.innerHTML, /No GNV agora.*5,08 ms/);
  assert.match(screen.cellPane.innerHTML, /Diferença.*\+8,1%/);
  assert.match(screen.cellPane.innerHTML, /Novo valor K sugerido.*108/);
  assert.doesNotMatch(screen.cellPane.innerHTML, /GNV Alvo/);
  assert.match(screen.cellPane.innerHTML, /Abrir o editor não escreve na ECU/);
});

test('Diferença separa último par, consolidado e tendência sem apagar estabilidade', () => {
  const { context } = loadScript('app/src/main/assets/ui/screens/learning.js');
  const screen = Object.create(context.OmegasUi.LearningScreen.prototype);
  screen.cellPane = { innerHTML: '', querySelector: () => ({ addEventListener: () => {} }) };
  screen.router = { navigate: () => {} };
  screen.renderDetail({
    learning: {
      grid: { rpmBins: [2000], petrolBins: [4.5] },
      tolerancePolicy: { equivalenceDeadbandPercent: 2.5 },
      regions: [
        { fuel: 'GASOLINA', epoch: 0, cell_row: 0, cell_column: 0, rpm: 2000, map_bar: 0.5, petrol_ms: 4.7, samples: 8, visit_count: 8, confidence: 0.9 },
        { fuel: 'GNV', epoch: 1, cell_row: 0, cell_column: 0, rpm: 2000, map_bar: 0.5, petrol_ms: 5.26, samples: 8, visit_count: 8, confidence: 0.9 },
      ],
      comparisons: [{ cell_row: 0, cell_column: 0, petrol_target_ms: 4.7, petrol_on_cng_ms: 5.26, error_percent: 12.0 }],
    },
    calibrationState: {
      suggestionItems: [{ target: 'MAP_K', lifecycle: 'OBSERVING', actionable: false, mapChanges: [{ row: 0, column: 0, before: 100, after: 108 }] }],
      learningStability: { map: [{ row: 0, column: 0, confidence: 0.9, consolidatedErrorPercent: 8.1, recentErrorPercent: 3.0, state: 'REVALIDATING' }] },
    },
    telemetry: { live: { fuel: 'GNV' } },
  }, 0, 0);

  assert.match(screen.cellPane.innerHTML, /Diferença agora.*\+12,0%/s);
  assert.match(screen.cellPane.innerHTML, /Diferença estável.*\+8,1%/s);
  assert.match(screen.cellPane.innerHTML, /Tendência recente.*\+3,0%.*revalidando/s);
  assert.match(screen.cellPane.innerHTML, /Sem sugestão segura/);
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

test('review click navigates without accessing any ECU writer', () => {
  const { context } = loadScript('app/src/main/assets/ui/screens/learning.js');
  const screen = Object.create(context.OmegasUi.LearningScreen.prototype);
  let click; const calls = [];
  screen.cellPane = { innerHTML: '', querySelector: () => ({ addEventListener: (_, fn) => { click = fn; } }) };
  screen.api = new Proxy({}, { get() { throw Error('Unexpected ECU API access'); } });
  screen.router = { navigate: (...args) => calls.push(args) };
  screen.renderDetail({
    learning: {},
    calibrationState: { suggestionItems: [{
      target: 'MAP_K', lifecycle: 'PENDING', actionable: true,
      mapChanges: [{ row: 0, column: 0, before: 110, after: 115 }]
    }] }
  }, 0, 0);
  assert.match(screen.cellPane.innerHTML, /Confiança/);
  assert.match(screen.cellPane.innerHTML, /Revisar no Mapa K/);
  click();
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], 'map');
  assert.equal(calls[0][1].suggestion.mapChanges[0].after, 115);
});

test('null injection reference is not rendered as zero milliseconds', () => {
  const { context } = loadScript('app/src/main/assets/ui/screens/learning.js');
  const screen = Object.create(context.OmegasUi.LearningScreen.prototype);
  screen.cellPane = { innerHTML: '', querySelector: () => null };
  screen.renderDetail({ learning: { comparisons: [{
    row: 0, column: 0, petrol_target_ms: null, petrol_on_cng_ms: null
  }] } }, 0, 0);
  assert.match(screen.cellPane.innerHTML, /Gasolina esperada<\/dt><dd>sem evidência/);
  assert.doesNotMatch(screen.cellPane.innerHTML, /0,00 ms/);
});

test('backend equivalent comparison stays neutral in the detail view', () => {
  const { context } = loadScript('app/src/main/assets/ui/screens/learning.js');
  const screen = Object.create(context.OmegasUi.LearningScreen.prototype);
  screen.cellPane = { innerHTML: '', querySelector: () => null };
  screen.renderDetail({ learning: { comparisons: [{
    row: 0, column: 0, petrol_target_ms: 5, petrol_on_cng_ms: 5.1,
    error_pct: 2, direction: 'EQUIVALENT'
  }] } }, 0, 0);
  assert.match(screen.cellPane.innerHTML, /2,0% \(equivalente\)/);
  assert.doesNotMatch(screen.cellPane.innerHTML, /precisa mais GNV/);
});
