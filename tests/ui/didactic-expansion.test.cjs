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

test('learningDecision normaliza somente o snapshot produzido pelo núcleo', () => {
  const { api } = buildApi({
    getFullEngineSnapshot: () => JSON.stringify({
      ok: true,
      live: {
        fuel: 'PETROL',
        sample_state: 'FORMING_SAMPLE',
        sample_reason: 'Formando amostra 7/10 leituras',
        sample_frame_count: 7,
        sample_minimum_frames: 6,
        sample_desired_frames: 10,
        sample: {
          state: 'FORMING_SAMPLE',
          reason: 'Formando amostra 7/10 leituras',
          reason_code: 'FORMING_SAMPLE',
          frame_count: 7,
          minimum_frames: 6,
          desired_frames: 10,
          learning_eligible: false,
          fuel_confirmed: 'PETROL',
          window_age_ms: 1800,
          window_budget_ms: 3000,
          cell_row: 2,
          cell_column: 5,
          quality: 0.82,
        },
      },
    }),
  });

  const decision = api.learningDecision();
  assert.equal(decision.state, 'FORMING_SAMPLE');
  assert.equal(decision.reason, 'Formando amostra 7/10 leituras');
  assert.equal(decision.reason_code, 'FORMING_SAMPLE');
  assert.equal(decision.frame_count, 7);
  assert.equal(decision.minimum_frames, 6);
  assert.equal(decision.desired_frames, 10);
  assert.equal(decision.learning_eligible, false);
  assert.equal(decision.fuel_confirmed, 'PETROL');
  assert.equal(decision.cell_row, 2);
  assert.equal(decision.cell_column, 5);
  assert.equal(decision.quality, 0.82);
});

test('tolerâncias semânticas são entregues ao Kotlin sem fórmula paralela', () => {
  const { api, calls } = buildApi();
  const result = api.setLearningToleranceControls({
    rpm: 3,
    map: 2,
    petrol: 1,
    pressure: 2,
    collection: 3,
    minimumWaterC: 65,
  });

  assert.equal(result.ok, true);
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], 'setLearningToleranceSettings');
  const payload = JSON.parse(calls[0][1]);
  assert.deepEqual(JSON.parse(JSON.stringify(payload)), {
    semanticControls: {
      rpm: 3,
      map: 2,
      petrol: 1,
      pressure: 2,
      collection: 3,
      minimumWaterC: 65,
    },
  });
});

test('OBD lista dispositivos e conecta exatamente ao endereço escolhido', () => {
  const { api, calls } = buildApi({
    listObdDevices: () => JSON.stringify({
      permissionRequired: false,
      enabled: true,
      devices: [
        { name: 'ELM327', address: 'AA:BB:CC:DD:EE:FF', bonded: true },
      ],
    }),
  });

  const devices = api.obdDevices();
  assert.equal(devices.devices.length, 1);
  assert.equal(devices.devices[0].address, 'AA:BB:CC:DD:EE:FF');

  const result = api.connectObd('AA:BB:CC:DD:EE:FF');
  assert.equal(result.ok, true);
  assert.equal(result.address, 'AA:BB:CC:DD:EE:FF');
  assert.deepEqual(calls, [['connectObd', 'AA:BB:CC:DD:EE:FF']]);
});

test('OBD entrega local/remote/off e combustível manual ao núcleo sem calibrar', () => {
  const { api, calls } = buildApi();

  assert.equal(api.setObdMode('remote').mode, 'remote');
  assert.equal(api.setObdMode('off').mode, 'off');
  const fuel = api.setObdManualFuel('GASOLINA');
  assert.equal(fuel.ok, true);
  assert.equal(fuel.manualFuel, 'GASOLINA');
  assert.deepEqual(calls, [
    ['setObdMode', 'remote'],
    ['setObdMode', 'off'],
    ['setObdManualFuel', 'GASOLINA'],
  ]);
});

test('browser demo nunca transforma conexão ou tolerância em escrita de ECU', () => {
  const context = { console, Date, Math, JSON, Intl };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(source, context, { filename: 'native-api.js' });
  const api = new context.OmegasUi.NativeApi();

  assert.equal(api.isDemo(), true);
  assert.equal(api.setLearningToleranceControls({ rpm: 4 }).ok, true);
  assert.equal(api.connectObd('demo').ok, true);
  assert.equal(api.setObdMode('remote').ok, true);
  assert.equal(api.setObdManualFuel('GNV').ok, true);
  assert.equal(api.writeMap([{ row: 0, column: 0, target: 120 }]).ok, false);
  assert.equal(api.writeCurve([{ index: 0, targetRaw: 1 }]).ok, false);
});
