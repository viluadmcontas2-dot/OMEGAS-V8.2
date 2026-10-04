'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const host = { innerHTML: '' };
const context = {
  console,
  setTimeout: () => 0,
  clearTimeout: () => {},
  document: {
    getElementById(id) { return id === 'autocalReferenceChart' ? host : null; },
  },
};
context.globalThis = context;
vm.createContext(context);
require('./_support.cjs').preload(context);
vm.runInContext(source, context, { filename: 'autocal-cockpit.js' });
const proto = context.OmegasUi.AutoCalCockpit.prototype;

const fake = Object.create(proto);
const labels = {};
fake.projection = {
  liveAcquisitionEpoch: {
    comparisonAllowed: false,
    gasPending: true,
    petrolPending: false,
    referencePending: true,
    petrolReferencePending: false,
    gasReferencePending: true,
    petrolGeneration: 0,
    gasGeneration: 1,
    nativeAutoMatchCount: 1,
  },
};
fake.snapshot = { source: 'ECU_READ', fields: [
  { key: 'PETR_INJ_TBP', status: 'VALID', capturedAtMs: 1000,
    physicalValues: Array.from({ length: 30 }, (_, i) => 0.5 + i * 0.5) },
  { key: 'PETR_MNFLD_PRESS_RV', status: 'VALID', capturedAtMs: 1200,
    physicalValues: Array.from({ length: 30 }, (_, i) => 0.2 + i * 0.025) },
  { key: 'MUL_ACT', status: 'VALID', physicalValues: Array.from({ length: 30 }, () => 1) },
  { key: 'PETR_INJ_TBUF_GAS_PREV', status: 'VALID',
    physicalValues: Array.from({ length: 18 }, (_, i) => 1 + i * 0.48) },
  { key: 'MNFLD_PRESS_BUF_GAS_PREV', status: 'VALID',
    physicalValues: Array.from({ length: 18 }, (_, i) => 0.24 + i * 0.026) },
  { key: 'PETR_INJ_TBUF', status: 'VALID', physicalValues: Array.from({ length: 18 }, (_, i) => 1 + i * 0.5) },
  { key: 'MNFLD_PRESS_BUF', status: 'VALID', physicalValues: Array.from({ length: 18 }, (_, i) => 0.25 + i * 0.035) },
  { key: 'NUM_BUF_UPD_PETR', status: 'VALID', rawValues: Array.from({ length: 18 }, (_, i) => i < 8 ? 5 : 0) },
  { key: 'PETR_INJ_TBUF_GAS', status: 'STALE_EPOCH', physicalValues: Array.from({ length: 18 }, (_, i) => 1 + i * 0.5) },
  { key: 'MNFLD_PRESS_BUF_GAS', status: 'STALE_EPOCH', physicalValues: Array.from({ length: 18 }, (_, i) => 0.26 + i * 0.035) },
  { key: 'NUM_BUF_UPD_GAS', status: 'STALE_EPOCH', rawValues: Array.from({ length: 18 }, () => 9) },
] };
fake.projection.referenceTimingLimitMs = 1000;
fake.analysis = {};
fake.referenceUsable = false;
fake.store = { get: () => ({ telemetry: { valid: false } }) };
fake.text = (id, value) => { labels[id] = value; };
fake.renderLiveNarrative = () => {};

proto.renderReferenceChart.call(fake, fake.snapshot);
assert.match(host.innerHTML, /AQUISIÇÃO EM TEMPO REAL/);
assert.match(host.innerHTML, /autocal-acquired-point petrol/);
assert.doesNotMatch(host.innerHTML, /autocal-acquired-point gas/);
assert.match(labels.autocalReferenceCount, /GNV 0\/18 faixas com amostra/);
assert.match(host.innerHTML, /autocal-reference-line petrol epoch-anchor/,
  'a referência gasolina deve sobreviver ao RESET_GAS da ECU');
assert.match(host.innerHTML, /autocal-previous-gas-point/,
  'GNV_PREV permanece só como contexto, sem contador de aquisição atual');
assert.doesNotMatch(host.innerHTML, /autocal-epoch-acquisition-line gas/);
assert.match(labels.autocalChartInspector, /Comparação gasolina\/GNV suspensa/);
assert.match(labels.autocalChartInspector, /Curva K: 30 fatores nativos/);

for (const field of fake.snapshot.fields) {
  if (field.key.endsWith('_GAS')) field.status = 'VALID';
}
fake.projection.liveAcquisitionEpoch.gasPending = false;
proto.renderReferenceChart.call(fake, fake.snapshot);
assert.match(host.innerHTML, /autocal-acquired-point gas/);
assert.match(host.innerHTML, /autocal-reference-line petrol epoch-anchor/,
  'gasolina continua até que só ela seja reiniciada');
assert.match(labels.autocalReferenceCount, /Gasolina 8\/18 · GNV 18\/18/);
assert.equal(fake.chartScale, null, 'aquisição parcial não deve reutilizar escala/equivalência RV30');

fake.state = { maxAutomatch: 3 };
fake.projection.liveAcquisitionEpoch.nativeAutoMatchCount = 3;
proto.renderReferenceChart.call(fake, fake.snapshot);
assert.match(labels.autocalChartInspector, /Cota de AutoMatch atingida; a leitura NÃO terminou/,
  '3/3 não encerra a coleta viva de GNV');

fake.projection.liveAcquisitionEpoch.petrolPending = true;
fake.projection.liveAcquisitionEpoch.petrolReferencePending = true;
for (const field of fake.snapshot.fields) {
  if (['PETR_INJ_TBUF', 'MNFLD_PRESS_BUF', 'NUM_BUF_UPD_PETR', 'PETR_MNFLD_PRESS_RV']
      .includes(field.key)) field.status = 'STALE_EPOCH';
}
proto.renderReferenceChart.call(fake, fake.snapshot);
assert.doesNotMatch(host.innerHTML, /autocal-reference-line petrol epoch-anchor/,
  'RESET_PETROL deve retirar a referência gasolina anterior');
assert.match(labels.autocalChartInspector, /Gasolina reiniciada/);

console.log('AUTOCAL_LIVE_EPOCH_GUARD=PASS');
