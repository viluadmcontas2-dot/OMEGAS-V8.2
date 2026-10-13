'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(
  path.join(__dirname, '../../app/src/main/assets/ui/core/native-api.js'),
  'utf8',
);

function buildApi(nativeOverrides = {}) {
  const calls = [];
  const native = {
    getReleaseIdentity: () => JSON.stringify({ product: 'OMEGAS', generation: 'V7' }),
    getFullEngineSnapshot: () => JSON.stringify({}),
    getLearningToleranceSettings: () => JSON.stringify({ ok: true }),
    setLearningToleranceSettings: payload => {
      calls.push(['setLearningToleranceSettings', payload]);
      return JSON.stringify({ ok: true, received: JSON.parse(payload) });
    },
    resetLearningToleranceSettings: () => JSON.stringify({ ok: true, reset: true }),
    listObdDevices: () => JSON.stringify({ permissionRequired: false, enabled: true, devices: [] }),
    connectObd: address => {
      calls.push(['connectObd', address]);
      return JSON.stringify({ ok: true, state: 'CONECTANDO', address });
    },
    disconnectObd: () => JSON.stringify({ ok: true }),
    setObdMode: mode => {
      calls.push(['setObdMode', mode]);
      return JSON.stringify({ ok: true, mode });
    },
    setObdManualFuel: fuel => {
      calls.push(['setObdManualFuel', fuel]);
      return JSON.stringify({ ok: true, manualFuel: fuel || null, manualFuelSource: fuel ? 'MANUAL_OPERATOR' : 'UNKNOWN' });
    },
    ...nativeOverrides,
  };

  const context = {
    console,
    Date,
    Math,
    JSON,
    Intl,
    OmegasNative: native,
    OmegasV7: {},
  };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(source, context, { filename: 'native-api.js' });
  return { api: new context.OmegasUi.NativeApi(), calls };
}

