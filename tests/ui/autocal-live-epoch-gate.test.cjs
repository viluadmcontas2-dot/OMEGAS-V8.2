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
    petrolGeneration: 0,
    gasGeneration: 1,
    nativeAutoMatchCount: 1,
  },
};
fake.snapshot = { fields: [
  { key: 'PETR_INJ_TBUF', status: 'VALID', physicalValues: Array.from({ length: 18 }, (_, i) => 1 + i * 0.5) },
  { key: 'MNFLD_PRESS_BUF', status: 'VALID', physicalValues: Array.from({ length: 18 }, (_, i) => 0.25 + i * 0.035) },
  { key: 'NUM_BUF_UPD_PETR', status: 'VALID', rawValues: Array.from({ length: 18 }, (_, i) => i < 8 ? 5 : 0) },
  { key: 'PETR_INJ_TBUF_GAS', status: 'STALE_EPOCH', physicalValues: Array.from({ length: 18 }, (_, i) => 1 + i * 0.5) },
  { key: 'MNFLD_PRESS_BUF_GAS', status: 'STALE_EPOCH', physicalValues: Array.from({ length: 18 }, (_, i) => 0.26 + i * 0.035) },
  { key: 'NUM_BUF_UPD_GAS', status: 'STALE_EPOCH', rawValues: Array.from({ length: 18 }, () => 9) },
] };
fake.analysis = {};
fake.referenceUsable = false;
fake.store = { get: () => ({ telemetry: { valid: false } }) };
fake.text = (id, value) => { labels[id] = value; };
fake.renderLiveNarrative = () => {};

proto.renderReferenceChart.call(fake, fake.snapshot);
assert.match(host.innerHTML, /AQUISIÇÃO EM TEMPO REAL/);
assert.match(host.innerHTML, /autocal-acquired-point petrol/);
assert.doesNotMatch(host.innerHTML, /autocal-acquired-point gas/);
assert.match(labels.autocalReferenceCount, /GNV 0\/18/);
assert.match(host.innerHTML, /nenhuma equivalência é calculada agora/);

for (const field of fake.snapshot.fields) {
  if (field.key.endsWith('_GAS')) field.status = 'VALID';
}
fake.projection.liveAcquisitionEpoch.gasPending = false;
proto.renderReferenceChart.call(fake, fake.snapshot);
assert.match(host.innerHTML, /autocal-acquired-point gas/);
assert.match(labels.autocalReferenceCount, /Gasolina 8\/18 · GNV 18\/18/);
assert.equal(fake.chartScale, null, 'aquisição parcial não deve reutilizar escala/equivalência RV30');

console.log('AUTOCAL_LIVE_EPOCH_GUARD=PASS');
