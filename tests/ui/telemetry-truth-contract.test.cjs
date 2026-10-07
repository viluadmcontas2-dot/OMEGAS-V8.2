'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve('.');
const read = relative => fs.readFileSync(path.join(ROOT, relative), 'utf8');

const app = read('app/src/main/assets/ui/app.js');
const dashboard = read('app/src/main/assets/ui/screens/dashboard.js');
const vehicle = read('app/src/main/assets/ui/components/vehicle-status-strip.js');
const refino = read('app/src/main/assets/ui/screens/refino.js');

function between(source, start, end) {
  const a = source.indexOf(start);
  const b = source.indexOf(end, a + start.length);
  assert.notEqual(a, -1, `missing start marker: ${start}`);
  assert.notEqual(b, -1, `missing end marker: ${end}`);
  return source.slice(a, b);
}

// Revisto (P1-4): a sequência nativa muda a cada quadro e forçava redesenho de tudo com os mesmos números.
// O quadro novo segue rastreado por lastPresentSequence (o pedido incremental à ponte); a assinatura visual
// só leva o que aparece na tela e o frescor, que continua andando (cinza/sumir não congela).
test('fast snapshot identity follows visible values and freshness, not the per-frame sequence', () => {
  const signature = between(app, 'function telemetryVisualSignature', 'function renderLightLiveContext');
  assert.doesNotMatch(signature, /sourceSequence,|source\.sequence\)/, 'per-frame sequence must not force a repaint');
  assert.match(app, /lastPresentSequence = Number\.isFinite\(Number\(telemetry\.sequence\)\)/, 'frame identity still tracked for the bridge');
  assert.match(signature, /source\.(?:telemetryAgeMs|ageMs)/, 'freshness must participate in fast snapshot identity');
});

test('global fuel gives priority to live telemetry over the slower status snapshot', () => {
  const match = app.match(/const fuel = reading\.level === 'fresh' \|\| reading\.level === 'late' \? fuelLabel\(([^;]+)\) : '—';/);
  assert.ok(match, 'global fuel expression missing (só com leitura fresca ou atrasada; velha vira —)');
  const expression = match[1];
  const live = expression.indexOf('reading.fuel');
  const status = expression.indexOf('status.fuelState');
  assert.ok(live >= 0, 'live fuel missing from global shell');
  assert.ok(status > live, `status fuel must be fallback after live fuel: ${expression}`);
});

test('invalid interpolation cannot materialize a fake physical cell', () => {
  const light = between(app, 'function renderLightLiveContext', '/** Único pump de PresentSnapshot');
  assert.match(light, /interpolation\.valid\s*===\s*true/, 'learning/map live context must honor interpolation.valid');
  assert.doesNotMatch(dashboard, /dashCell/, 'o Agora não mostra célula (só ms, RPM, MAP e combustível)');
});

test('unavailable telemetry never turns HubStatus numeric defaults into fake measurements', () => {
  const store = read('app/src/main/assets/ui/core/live-store.js');
  assert.match(store, /valid\s*=\s*telemetry\.valid\s*===\s*true/, 'a leitura única exige telemetria válida antes de mostrar medição');
  assert.match(dashboard, /LiveStore\.read\(state\)/, 'dashboard lê pela leitura única');
  assert.match(vehicle, /LiveStore\.read\(state, \{ fallback: true \}\)/, 'faixa lê pela leitura única');
  assert.doesNotMatch(dashboard, /id=\\?"dashRpm\\?"[^>]*>0<\/b>/, 'dashboard initial state cannot claim 0 RPM before valid telemetry');
});

test('missing refino value stays unavailable instead of becoming numeric zero', () => {
  // Uma única definição de finite (core/display-rules.js) rejeita null, undefined, vazio e booleano; o Refino a usa.
  const rules = read('app/src/main/assets/ui/core/display-rules.js');
  const finiteFn = rules.match(/function finite\(value\)\s*\{[\s\S]*?\n  \}/)?.[0] || '';
  assert.match(finiteFn, /value\s*===\s*null/, 'finite must explicitly reject null');
  assert.match(finiteFn, /undefined/, 'finite must explicitly reject undefined');
  assert.match(refino, /const finite = D\.finite/, 'Refino reuses the single finite');
  assert.doesNotMatch(refino, /function finite\(/, 'Refino does not define its own finite');
});

console.log('TELEMETRY_TRUTH_CONTRACT=PASS');
