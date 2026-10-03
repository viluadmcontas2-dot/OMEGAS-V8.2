const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const zlib = require('node:zlib');

const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const cockpit = read('app/src/main/assets/ui/screens/autocal-cockpit.js');
const schedulerSource = read('app/src/main/assets/ui/core/scheduler.js');

function loadModel() {
  const context = { console, setTimeout: () => 0, clearTimeout: () => {} };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(cockpit, context, { filename: 'autocal-cockpit.js' });
  return context.OmegasUi.AutoCalUxModel;
}
const model = loadModel();

const live = { petrol_ms: 4.5, load_bar: 0.45, rpm: 2000, fuel: 'GNV' };

test('B1: cursor cinza com 1,5 s e some com 3 s (mesma régua do Agora)', () => {
  const at = ageMs => model.livePoint({ valid: true, ageMs, live });
  assert.equal(at(1500).grey, false);
  assert.equal(at(1501).grey, true);
  assert.equal(at(3000).grey, true);
  assert.equal(at(3001), null);
  assert.match(cockpit, /AUTO_CAL_LIVE_GREY_MS = 1500/);
  assert.match(cockpit, /AUTO_CAL_LIVE_STALE_MS = 3000/);
});

test('B2: cursor suave por rAF do scheduler, sem timer novo e sem extrapolar', () => {
  assert.match(cockpit, /addFrameHook/);
  assert.match(cockpit, /animateCursor\(timestamp\)/);
  assert.match(cockpit, /AUTO_CAL_CURSOR_EASE_MS = 50/);
  assert.match(cockpit, /AUTO_CAL_NARRATIVE_MS = 500/);
  assert.doesNotMatch(cockpit, /setInterval|setTimeout/);
  const app = read('app/src/main/assets/ui/app.js');
  assert.match(app, /const AUTOCAL_CADENCE_MS = 50;/);
  assert.match(app, /getPresentSnapshotIfChanged|presentSnapshot\(lastPresentSequence\)/);
});

test('B2: o scheduler roda quadros só enquanto houver quem peça e para ao cancelar', () => {
  const frames = [];
  const sandbox = {
    console,
    requestAnimationFrame: callback => { frames.push(callback); return frames.length; },
    cancelAnimationFrame: () => {},
    setInterval: () => 1,
    clearInterval: () => {},
  };
  sandbox.window = sandbox;
  vm.runInNewContext(schedulerSource, sandbox);
  const scheduler = new sandbox.OmegasUi.Scheduler({ intervalMs: 50 });
  const seen = [];
  const cancel = scheduler.addFrameHook(ts => seen.push(ts));
  assert.equal(frames.length, 1, 'um pedido de quadro por vez');
  frames.shift()(16);
  assert.deepEqual(seen, [16]);
  assert.equal(frames.length, 1, 're-arma enquanto houver gancho');
  cancel();
  frames.shift()(32);
  assert.deepEqual(seen, [16], 'gancho cancelado não roda');
  assert.equal(frames.length, 0, 'sem ganchos o laço para');
});

function fixtureSnapshots(name) {
  const data = JSON.parse(zlib.gunzipSync(fs.readFileSync(path.join(root, 'fixtures/autocal/real', `${name}.json.gz`))).toString('utf8'));
  return data.snapshots;
}

test('B6: faixa i = (THD[i], THD[i+1]] em todas as leituras reais; lenta e faixa 18 não são "indisponível"', () => {
  let checked = 0;
  for (const name of ['ref_2026-10-01_1719', 'automatch_2026-10-01_1301', 'gnv_only_2026-09-30_0931']) {
    for (const snap of fixtureSnapshots(name)) {
      const fields = Object.fromEntries(snap.fields.filter(f => f.status === 'VALID').map(f => [f.key, f]));
      if (!fields.MNFLD_PRESS_THD) continue;
      const thd = fields.MNFLD_PRESS_THD.rawValues.map(v => v / 1024);
      const snapshot = { fields: [{ key: 'MNFLD_PRESS_THD', status: 'VALID', physicalValues: thd }] };
      for (const [mapKey, countKey] of [['MNFLD_PRESS_BUF', 'NUM_BUF_UPD_PETR'], ['MNFLD_PRESS_BUF_GAS', 'NUM_BUF_UPD_GAS']]) {
        if (!fields[mapKey] || !fields[countKey]) continue;
        fields[mapKey].rawValues.forEach((raw, index) => {
          const count = fields[countKey].rawValues[index];
          if (!count || raw <= 0 || (raw & 0x8000)) return;
          const region = model.liveRegion(snapshot, { mapBar: raw / 1024 });
          assert.equal(region.kind, 'band', `${name}#${snap.sequence} faixa ${index}`);
          assert.equal(region.index, index, `${name}#${snap.sequence} MAP ${raw / 1024} caiu na faixa ${region.index}, a ECU diz ${index}`);
          checked += 1;
        });
      }
    }
  }
  assert.ok(checked > 500, `leituras reais conferidas: ${checked}`);
  const thd = Array.from({ length: 18 }, (_, i) => 0.15 + 0.05 * i);
  const snapshot = { fields: [{ key: 'MNFLD_PRESS_THD', status: 'VALID', physicalValues: thd }] };
  const idle = model.liveRegion(snapshot, { mapBar: 0.10 });
  assert.equal(idle.kind, 'idle');
  assert.equal(idle.zone, null);
  const above = model.liveRegion(snapshot, { mapBar: 1.30 });
  assert.equal(above.kind, 'above');
  assert.equal(above.index, thd.length - 1);
  assert.equal(above.zone, 4);
  assert.equal(model.liveRegion({ fields: [] }, { mapBar: 0.5 }).kind, 'unknown');
  assert.doesNotMatch(cockpit, /zona indisponível/);
});

test('B3: leitura atrasada e campos sem leitura aparecem com palavras, não como "—" mudo', () => {
  const now = 1_790_000_100_000;
  assert.equal(model.readingNote({ capturedAtMs: now - 2_000, fields: [] }, now), '');
  assert.equal(model.readingNote({ capturedAtMs: now - 12_000, fields: [] }, now), 'leitura atrasada há 12 s');
  assert.match(model.readingNote({ capturedAtMs: now - 300_000, fields: [] }, now), /há 5 min/);
  const missing = model.readingNote({ capturedAtMs: now, fields: [{ status: 'VALID' }, { status: 'TIMEOUT' }, { status: 'INVALID' }] }, now);
  assert.match(missing, /2 campos sem leitura/);
  assert.equal(model.readingNote({ capturedAtMs: now, partial: true, fields: [{ status: 'VALID' }] }, now), 'leitura parcial');
});
