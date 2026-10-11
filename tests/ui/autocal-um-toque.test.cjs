'use strict';
// Dono, 2026-10-11: "Readquirir GNV" relê direto, sem pedir para executar de novo (sem dupla confirmação).
// Um toque prepara e executa; a proteção é a foto da preparação e o readback da ECU.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const ctx = { console, setTimeout: () => 0, clearTimeout: () => {} };
ctx.globalThis = ctx;
vm.createContext(ctx);
vm.runInContext(source, ctx);
const { AutoCalCockpit: Cockpit } = ctx.OmegasUi;

function cockpit(executeResult) {
  const calls = { prepare: [], execute: [], cancel: 0 };
  const alerts = [];
  const c = Object.create(Cockpit.prototype);
  c.api = {
    available: () => true,
    prepare: action => { calls.prepare.push(action); return { ok: true, prepared: true, preparationId: 'p-' + action, action }; },
    execute: id => { calls.execute.push(id); return executeResult; },
    cancelPreparation: () => { calls.cancel += 1; },
  };
  c.store = { patch: alert => alerts.push(alert) };
  c.refresh = () => {};
  ctx.document = { getElementById: () => null };
  return { c, calls, alerts };
}

test('Readquirir GNV executa no mesmo toque, sem segunda confirmação', () => {
  const { c, calls, alerts } = cockpit({ ok: true, started: true });
  c.prepare('RESET_GAS');
  assert.deepEqual(calls.prepare, ['RESET_GAS']);
  assert.deepEqual(calls.execute, ['p-RESET_GAS']);
  assert.equal(calls.cancel, 0);
  assert.equal(c.prepared, null);
  assert.equal(alerts.at(-1).alert.level, 'working');
});

test('se a ECU recusar, avisa e libera o próximo toque', () => {
  const { c, calls, alerts } = cockpit({ ok: false, error: 'ECU recusou' });
  c.prepare('RESET_PETROL');
  assert.deepEqual(calls.execute, ['p-RESET_PETROL']);
  assert.equal(calls.cancel, 1);
  assert.equal(c.prepared, null);
  assert.equal(alerts.at(-1).alert.level, 'warning');
  assert.equal(alerts.at(-1).alert.message, 'ECU recusou');
});
