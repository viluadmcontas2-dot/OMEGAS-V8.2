'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve('.');
const read = relative => fs.readFileSync(path.join(ROOT, relative), 'utf8');

const app = read('app/src/main/assets/ui/app.js');
const dashboard = read('app/src/main/assets/ui/screens/dashboard.js');
const floating = read('app/src/main/assets/ui/components/floating-telemetry.js');
const vehicle = read('app/src/main/assets/ui/components/vehicle-status-strip.js');
const obd = read('app/src/main/assets/ui/screens/obd.js');

function between(source, start, end) {
  const a = source.indexOf(start);
  const b = source.indexOf(end, a + start.length);
  assert.notEqual(a, -1, `missing start marker: ${start}`);
  assert.notEqual(b, -1, `missing end marker: ${end}`);
  return source.slice(a, b);
}

test('fast snapshot identity follows native sequence and freshness instead of freezing semantic progress', () => {
  const signature = between(app, 'function telemetryVisualSignature', 'function renderLightLiveContext');
  assert.match(signature, /source\.sequence/, 'native sequence must participate in fast snapshot identity');
  assert.match(signature, /source\.(?:telemetryAgeMs|ageMs)/, 'freshness must participate in fast snapshot identity');
});

test('global fuel gives priority to live telemetry over the slower status snapshot', () => {
  const match = app.match(/const fuel = fuelLabel\(([^;]+)\);/);
  assert.ok(match, 'global fuel expression missing');
  const expression = match[1];
  const live = expression.indexOf('liveFrom(state).fuel');
  const status = expression.indexOf('status.fuelState');
  assert.ok(live >= 0, 'live fuel missing from global shell');
  assert.ok(status > live, `status fuel must be fallback after live fuel: ${expression}`);
});

test('invalid interpolation cannot materialize a fake physical cell', () => {
  const light = between(app, 'function renderLightLiveContext', '/** Único pump de PresentSnapshot');
  assert.match(light, /interpolation\.valid\s*===\s*true/, 'learning/map live context must honor interpolation.valid');
  assert.match(dashboard, /interpolation\.valid\s*===\s*true/, 'dashboard cell must honor interpolation.valid');
  assert.match(floating, /interpolation\.valid\s*===\s*true/, 'floating cell must honor interpolation.valid');
});

test('unavailable telemetry never turns HubStatus numeric defaults into fake measurements', () => {
  assert.match(dashboard, /telemetryValid\s*=\s*[^;]*valid\s*===\s*true/, 'dashboard must require valid telemetry before showing measurements');
  assert.match(floating, /telemetryValid\s*=\s*[^;]*valid\s*===\s*true/, 'floating telemetry must require valid telemetry before showing measurements');
  assert.match(vehicle, /telemetryValid\s*=\s*[^;]*valid\s*===\s*true/, 'vehicle strip must require valid telemetry before showing measurements');
  assert.doesNotMatch(dashboard, /id=\\?"dashRpm\\?"[^>]*>0<\/b>/, 'dashboard initial state cannot claim 0 RPM before valid telemetry');
});

test('missing OBD PID stays unavailable instead of becoming numeric zero', () => {
  const finiteFn = obd.match(/function finite\(value\)\s*\{[\s\S]*?\}/)?.[0] || '';
  assert.match(finiteFn, /value\s*===\s*null|value\s*==\s*null/, 'OBD finite must explicitly reject null');
  assert.match(finiteFn, /undefined|value\s*==\s*null/, 'OBD finite must explicitly reject undefined');
});

console.log('TELEMETRY_TRUTH_CONTRACT=PASS');
