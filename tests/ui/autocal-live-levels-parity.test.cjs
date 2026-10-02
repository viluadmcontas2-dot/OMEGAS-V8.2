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

test('LEVELS do frame global não entra no modelo AutoCal', () => {
  const model = loadModel();
  const live = model.livePoint({
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
  });

  assert.ok(live);
  assert.equal(live.rpm, 875);
  assert.equal(live.petrolMs, 4.8);
  assert.equal(live.mapBar, 0.452);
  assert.equal(Object.prototype.hasOwnProperty.call(live, 'levelRaw'), false);
});

test('projection legada de LEVELS não altera contexto AutoCal', () => {
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
  assert.equal(Object.prototype.hasOwnProperty.call(live, 'levelRaw'), false);
});
