const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function loadModel() {
  const root = path.resolve(__dirname, '../..');
  const context = {
    console,
    setTimeout() { return 1; },
    clearTimeout() {},
    OmegasUi: { AutoCalApi: {} },
  };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  const source = fs.readFileSync(
    path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'),
    'utf8',
  );
  vm.runInContext(source, context, { filename: 'autocal-cockpit.js' });
  return context.OmegasUi.AutoCalUxModel;
}

test('LEVELS RAW do frame vivo vence projection AutoCal mais antiga', () => {
  const model = loadModel();
  const live = model.livePoint(
    {
      valid: true,
      ageMs: 40,
      sequence: 77,
      live: {
        rpm: 875,
        petrol_ms: 4.8,
        load_bar: 0.452,
        level_raw: 126,
        fuel: 'GNV',
      },
    },
    { levelsRaw: 91 },
  );

  assert.ok(live);
  assert.equal(live.levelRaw, 126);
});

test('LEVELS RAW ausente no frame vivo falha fechado em vez de reutilizar projection velha', () => {
  const model = loadModel();
  const live = model.livePoint(
    {
      valid: true,
      ageMs: 40,
      sequence: 78,
      live: {
        rpm: 875,
        petrol_ms: 4.8,
        load_bar: 0.452,
        fuel: 'GNV',
      },
    },
    { levelsRaw: 91 },
  );

  assert.ok(live);
  assert.equal(live.levelRaw, null);
});
