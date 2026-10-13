'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve('.');
const read = relative => fs.readFileSync(path.join(ROOT, relative), 'utf8');

const app = read('app/src/main/assets/ui/app.js');
const liveBar = read('app/src/main/assets/ui/components/live-bar.js');
const refino = read('app/src/main/assets/ui/screens/refino.js');

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
});

test('unavailable telemetry never turns HubStatus numeric defaults into fake measurements', () => {
  assert.match(liveBar, /source\.valid\s*===\s*true/, 'a barra do topo exige telemetria válida antes de mostrar medidas');
  assert.doesNotMatch(read('app/src/main/assets/ui/index.html'), /data-live="rpm"[^>]*><small>RPM<\/small><b>0</, 'a barra não afirma 0 RPM antes de haver telemetria');
});

test('missing refino value stays unavailable instead of becoming numeric zero', () => {
  const finiteFn = refino.match(/function finite\(value\)\s*\{[\s\S]*?\}/)?.[0] || '';
  assert.match(finiteFn, /value\s*===\s*null|value\s*==\s*null/, 'Refino finite must explicitly reject null');
  assert.match(finiteFn, /undefined|value\s*==\s*null/, 'Refino finite must explicitly reject undefined');
});

console.log('TELEMETRY_TRUTH_CONTRACT=PASS');
