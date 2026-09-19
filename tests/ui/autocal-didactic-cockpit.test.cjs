const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-autocal-cockpit.css'), 'utf8');

const context = { console, setTimeout: () => 0, clearTimeout: () => {} };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(source, context, { filename: 'autocal-cockpit.js' });

const model = context.OmegasUi?.AutoCalUxModel;
assert.ok(model, 'AutoCalUxModel deve ser exportado para teste do estado humano sem DOM');

const snapshot = {
  available: true,
  autoCalEnabled: 1,
  maxAutomatch: 3,
  nativeStatus: { autoMatchCount: 3 },
  nativeMaturityEvents: [
    { bandIndex: 4, correlationState: 'CORRELATED', counter: 10, threshold: 10, rpm: 1450, correlationConfidence: 0.92 },
  ],
  fields: [
    { key: 'ACQUIRED_ZONES_PETROL', status: 'VALID', rawValues: [1, 1, 1, 1], physicalValues: [1, 1, 1, 1] },
    { key: 'ACQUIRED_ZONES_GAS', status: 'VALID', rawValues: [1, 1, 1, 0], physicalValues: [1, 1, 1, 0] },
    { key: 'NUM_BUF_UPD_GAS', status: 'VALID', rawValues: Array.from({ length: 18 }, (_, i) => i < 16 ? 10 : 0), physicalValues: Array.from({ length: 18 }, (_, i) => i < 16 ? 10 : 0) },
    { key: 'PETR_INJ_TBP', status: 'VALID', rawValues: Array.from({ length: 30 }, (_, i) => 1000 + i), physicalValues: Array.from({ length: 30 }, (_, i) => 2 + i * 0.2) },
    { key: 'PETR_MNFLD_PRESS_RV', status: 'VALID', rawValues: Array.from({ length: 30 }, (_, i) => 300 + i), physicalValues: Array.from({ length: 30 }, (_, i) => 0.30 + i * 0.01) },
    { key: 'GAS_MNFLD_PRESS_RV', status: 'VALID', rawValues: Array.from({ length: 30 }, (_, i) => 310 + i), physicalValues: Array.from({ length: 30 }, (_, i) => 0.31 + i * 0.01) },
  ],
};

const human = model.humanState(snapshot, { state: 'READY' });
assert.equal(human.title, 'AutoCal ativo');
assert.equal(human.petrolZones, 4);
assert.equal(human.gasZones, 3);
assert.match(human.progress, /Gasolina 4\/4 zonas/);
assert.match(human.progress, /GNV 3\/4 zonas/);
assert.match(human.autoMatch, /3 AutoMatch executados/);
assert.match(human.nextAction, /Continue dirigindo normalmente/);

const paused = model.humanState({ ...snapshot, autoCalEnabled: 0 }, { state: 'PAUSED' });
assert.equal(paused.title, 'AutoCal pausado');
assert.match(paused.nextAction, /Retome a coleta/);

const refs = model.referencePoints(snapshot);
assert.equal(refs.length, 30);
assert.deepEqual(
  { petrolMs: refs[0].petrolMs, petrolMapBar: refs[0].petrolMapBar, gasMapBar: refs[0].gasMapBar },
  { petrolMs: 2, petrolMapBar: 0.3, gasMapBar: 0.31 },
);

const bands = model.bandStrip(snapshot);
assert.equal(bands.length, 18);
assert.equal(bands[4].state, 'anchored');
assert.equal(bands[16].state, 'empty');
assert.equal(bands[13].zoneAcquired, true);
assert.equal(bands[16].zoneAcquired, false);

assert.equal(model.toggleAction(1), 'DISABLE_AUTO_CAL');
assert.equal(model.toggleAction(0), 'ENABLE_AUTO_CAL');
assert.equal(model.toggleAction(null), null, 'estado nativo ausente não pode virar comando de retomar coleta');
assert.equal(model.toggleAction(undefined), null, 'estado nativo indefinido não pode virar comando de retomar coleta');
const disconnected = model.humanState({ available: false }, { state: 'DISCONNECTED' });
assert.equal(disconnected.enabled, null, 'DISCONNECTED deve preservar estado AutoCal desconhecido');

let view = model.updateChartView(null, 'zoom-in');
assert.ok(view.zoom > 1);
view = model.updateChartView(view, 'pan', { dx: 40, dy: -20 });
assert.notEqual(view.panX, 0);
assert.notEqual(view.panY, 0);
view = model.updateChartView(view, 'fit');
assert.equal(view.zoom, 1);
assert.equal(view.panX, 0);
assert.equal(view.panY, 0);

assert.equal(source.includes('data-autocal-chart-action="zoom-in"'), true);
assert.equal(source.includes('data-autocal-chart-action="zoom-out"'), true);
assert.equal(source.includes('data-autocal-chart-action="fit"'), true);
assert.equal(source.includes('data-autocal-toggle'), true);
assert.equal(source.includes('data-autocal-band-index'), true);
assert.equal(source.includes('autocalHumanTitle'), true);
assert.equal(source.includes('autocalTechnicalDetails'), true);
assert.equal(source.includes('AUTOMATCH ECU'), false);
assert.equal(source.includes('EVENTOS MADUROS'), false);
assert.equal(source.includes('RESET_ALL'), false);
assert.equal(source.includes('setInterval'), false);

assert.equal(css.includes('overflow-x: hidden'), true);
assert.match(css, /\.autocal-cockpit-view\s*\{[^}]*overflow-y:\s*auto/s, 'cockpit deve rolar verticalmente dentro da viewport em vez de cortar bandas e controles');
assert.equal(css.includes('container-type: inline-size'), true);
assert.equal(css.includes('min-height: 56px'), true);
assert.equal(css.includes('grid-template-columns: minmax(0, 1.45fr)'), false);
assert.equal(css.includes('min-height: 40px'), false);

console.log('AUTOCAL_DIDACTIC_COCKPIT=PASS');
