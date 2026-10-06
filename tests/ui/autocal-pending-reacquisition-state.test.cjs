'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const context = { console, setTimeout, clearTimeout };
context.window = context;
context.globalThis = context;
context.OmegasUi = {};
context.addEventListener = () => {};
vm.createContext(context);
require('./_support.cjs').preload(context);
vm.runInContext(source, context, { filename: 'autocal-cockpit.js' });
const model = context.OmegasUi.AutoCalUxModel;

const base = {
  usbSessionId: 9,
  nativeAutoMatchCount: 1,
  petrolGeneration: 2,
  gasGeneration: 3,
  petrolPending: false,
  gasPending: false,
  petrolReferencePending: false,
  gasReferencePending: false,
  comparisonAllowed: true,
};

const gas = model.effectiveEpoch(base, { busy: true, action: 'RESET_GAS', state: 'READING_AFTER' });
assert.equal(gas.gasPending, true);
assert.equal(gas.gasReferencePending, true);
assert.equal(gas.petrolPending, false);
assert.equal(gas.petrolReferencePending, false);
assert.equal(gas.comparisonAllowed, false);
assert.equal(gas.intentPending, true);

const petrol = model.effectiveEpoch(base, { busy: true, action: 'RESET_PETROL', state: 'SENDING_ACTION' });
assert.equal(petrol.petrolPending, true);
assert.equal(petrol.petrolReferencePending, true);
assert.equal(petrol.gasPending, false);
assert.equal(petrol.gasReferencePending, false);
assert.equal(petrol.comparisonAllowed, false);

const confirmed = model.effectiveEpoch(base, { busy: false, action: 'RESET_GAS', state: 'CONFIRMED' });
assert.equal(confirmed.gasPending, false);
assert.equal(confirmed.comparisonAllowed, true);
assert.equal(confirmed.intentPending, undefined);

console.log('AUTOCAL_PENDING_REACQUISITION_STATE=PASS');
