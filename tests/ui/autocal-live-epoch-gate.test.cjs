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
fake.readout = text => { labels.autocalChartInspector = text; };
fake.renderLiveNarrative = () => {};
fake.cursor = new context.OmegasUi.LiveStore.EaseCursor(() => null);

proto.renderReferenceChart.call(fake, fake.snapshot);
assert.match(host.innerHTML, /CURVAS DA ECU/);
assert.match(host.innerHTML, /autocal-acquired-point petrol/);
assert.doesNotMatch(host.innerHTML, /autocal-acquired-point gas/);
assert.match(labels.autocalReferenceCount, /GNV 0\/18 faixas com amostra/);
assert.match(host.innerHTML, /autocal-reference-line petrol epoch-anchor/,
  'a referência gasolina deve sobreviver ao RESET_GAS da ECU');
assert.doesNotMatch(host.innerHTML, /autocal-previous-gas-point/,
  'leitura anterior (GNV_PREV) nunca é desenhada (dono, 2026-10-08)');
assert.doesNotMatch(host.innerHTML, /autocal-epoch-acquisition-line gas/);
assert.match(labels.autocalChartInspector, /GNV|Aguardando|Coleta/, 'frase humana curta sobre a leitura recomeçada');
assert.doesNotMatch(labels.autocalChartInspector, /Curva K|RV30|ACK/);

for (const field of fake.snapshot.fields) {
  if (field.key.endsWith('_GAS')) field.status = 'VALID';
}
fake.projection.liveAcquisitionEpoch.gasPending = false;
proto.renderReferenceChart.call(fake, fake.snapshot);
assert.match(host.innerHTML, /autocal-acquired-point gas/);
assert.match(host.innerHTML, /autocal-reference-line petrol epoch-anchor/,
  'gasolina continua até que só ela seja reiniciada');
assert.match(labels.autocalReferenceCount, /Gasolina 8\/18 · GNV 18\/18/);
assert.ok(fake.chartScale, 'aquisição parcial tem escala física para o cursor vivo');
assert.equal(fake.chartSignature, null, 'aquisição parcial não reutiliza comparação RV30');
assert.match(host.innerHTML, /autocal-live-layer/, 'mesma camada rápida de telemetria');

fake.state = { maxAutomatch: 3 };
fake.projection.liveAcquisitionEpoch.nativeAutoMatchCount = 3;
proto.renderReferenceChart.call(fake, fake.snapshot);
assert.match(labels.autocalChartInspector, /a leitura continua/,
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
assert.match(labels.autocalChartInspector, /curvas atuais de gasolina e GNV/);

console.log('AUTOCAL_LIVE_EPOCH_GUARD=PASS');
