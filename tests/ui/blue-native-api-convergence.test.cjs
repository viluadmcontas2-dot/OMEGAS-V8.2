'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/core/native-api.js'), 'utf8');

test('browser bridge exposes evidence-only demo and meaningful session defaults', () => {
  const sandbox = { console, Date, Math, JSON, Number, String, Array, Object, setTimeout, clearTimeout };
  sandbox.globalThis = sandbox;
  sandbox.OmegasUi = {};
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox, { filename: 'native-api.js' });

  const api = new sandbox.OmegasUi.NativeApi();
  const learning = api.learning();
  assert.equal(Object.prototype.hasOwnProperty.call(learning, 'assistedCalibration'), false,
    'demo learning must not expose retired assistedCalibration authority');
  assert.equal(source.includes('kFactorSuggestions'), false,
    'native-api demo must not fabricate K suggestions');

  const status = api.sessionStatus();
  assert.equal(status.settings.keepSessions, 30,
    'demo/session UI contract must default to 30 meaningful sessions');

  const coerced = api.setSessionSettings({
    telemetryEveryMs: 500,
    maxSessionMb: 64,
    keepSessions: 3,
    autoStartOnUsb: true,
    captureRawUsb: false,
  });
  assert.equal(coerced.settings.keepSessions, 20,
    'browser bridge must mirror native minimum useful retention of 20');
});

console.log('BLUE_NATIVE_API_CONVERGENCE=PASS');
