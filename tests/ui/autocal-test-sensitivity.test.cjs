'use strict';

const assert = require('node:assert/strict');
const cp = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const required = [
  'tests/ui/autocal-final-product.test.cjs',
  'app/src/main/assets/ui/core/router.js',
  'app/src/main/assets/ui/index.html',
  'app/src/main/assets/ui/app.js',
  'app/src/main/assets/ui/core/autocal-api.js',
  'app/src/main/assets/ui/screens/autocal-cockpit.js',
  'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt',
  'app/src/main/java/com/omegas/prohub/autocal/AutoCalUiProjection.kt',
  'app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt',
  'app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt',
];

function stage(name) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'omegas-autocal-mutant-' + name + '-'));
  for (const rel of required) {
    const dest = path.join(dir, rel);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.copyFileSync(path.join(root, rel), dest);
  }
  return dir;
}
function replaceOnce(dir, rel, from, to) {
  const file = path.join(dir, rel);
  const source = fs.readFileSync(file, 'utf8');
  assert.ok(source.includes(from), 'mutant needle ausente: ' + rel + ' :: ' + from);
  fs.writeFileSync(file, source.replace(from, to));
}
function productContract(dir) {
  return cp.spawnSync(process.execPath, ['tests/ui/autocal-final-product.test.cjs'], { cwd: dir, encoding: 'utf8' });
}
const baseline = stage('baseline');
const baselineRun = productContract(baseline);
assert.equal(baselineRun.status, 0, 'baseline do meta-teste precisa estar verde');

const mutants = [
  ['route-removed', 'app/src/main/assets/ui/core/router.js', "'curve', 'autocal', 'obd'", "'curve', 'obd'"],
  ['nav-reordered', 'app/src/main/assets/ui/index.html',
    'data-route="autocal"><i>05</i><span>AutoCal</span></button>\n        <button type="button" data-route="obd"',
    'data-route="obd"><i>05</i><span>OBD</span></button>\n        <button type="button" data-route="autocal"'],
  ['reader-status-miswired', 'app/src/main/assets/ui/core/autocal-api.js',
    "readerStatus: () => invoke('getStatus'", "readerStatus: () => invoke('getNativeMonitorStatus'"],
  ['reader-snapshot-miswired', 'app/src/main/assets/ui/core/autocal-api.js',
    "readerSnapshot: () => invoke('getSnapshot'", "readerSnapshot: () => invoke('getNativeMonitorSnapshot'"],
  ['cancel-api-removed', 'app/src/main/assets/ui/core/autocal-api.js',
    "cancelRead: () => invoke('cancelRead'", "cancelReadBROKEN: () => invoke('cancelRead'"],
  ['live-layer-disabled', 'app/src/main/assets/ui/screens/autocal-cockpit.js',
    'const liveMarkup = live', 'const liveMarkup = false && live'],
  ['stale-live-not-hidden', 'app/src/main/assets/ui/screens/autocal-cockpit.js',
    "if (layer) layer.setAttribute('display', 'none');", "if (layer) layer.removeAttribute('display');"],
  ['x-axis-unit-removed', 'app/src/main/assets/ui/screens/autocal-cockpit.js',
    'Petrol Inj. (ms)', 'Petrol Inj.'],
  ['levels-projection-detached', 'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt',
    'telemetryStatus = JSONObject(service.telemetryStore.liveJson()),',
    'telemetryStatus = JSONObject(),'],
  ['native-bootstrap-killed', 'app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt',
    'snapshotRequested = newSessionId > 0L', 'snapshotRequested = false'],
  ['automatch-u8-killed', 'app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt',
    'val NUM_AUTOMATCH_EXECUTED = Field("NUM_AUTOMATCH_EXECUTED", 0x0174, Encoding.U8_OR_U16_LE, Shape.SCALAR, 1)',
    'val NUM_AUTOMATCH_EXECUTED = Field("NUM_AUTOMATCH_EXECUTED", 0x0174, Encoding.U16_LE, Shape.SCALAR, 1)'],
  ['manual-snapshot-authority', 'app/src/main/assets/ui/screens/autocal-cockpit.js',
    "const enabled = finite(state.autoCalEnabled ?? nativeSnapshot.autoCalEnabled ?? scalarValue(nativeSnapshot, 'AUTO_CAL_ENABLE'));",
    "const enabled = finite(snapshot.autoCalEnabled ?? state.autoCalEnabled ?? nativeSnapshot.autoCalEnabled);"],
];

const survived = [];
for (const [name, rel, from, to] of mutants) {
  const dir = stage(name);
  replaceOnce(dir, rel, from, to);
  const result = productContract(dir);
  if (result.status === 0) survived.push(name);
}
assert.deepEqual(survived, [], 'mutantes sobreviveram: ' + survived.join(', '));
console.log('AUTOCAL_TEST_SENSITIVITY=PASS mutants=' + mutants.length + '/' + mutants.length);
