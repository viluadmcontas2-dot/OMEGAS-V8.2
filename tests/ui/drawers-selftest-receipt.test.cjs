'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/components/drawers.js'), 'utf8');
function runSelfTest(receipt) {
  const context = { OmegasUi: {}, globalThis: { innerHeight: 720 }, document: {} };
  vm.runInNewContext(source, context, { filename: 'drawers.js' });
  const alerts = [];
  const instance = Object.create(context.OmegasUi.Drawers.prototype);
  instance.api = { selfTest: () => receipt };
  instance.store = { patch: value => alerts.push(value.alert) };
  const target = {
    id: '',
    matches: selector => selector === '[data-tool-selftest]',
    closest: selector => selector === 'button' ? target : null
  };
  instance.handleToolClick({ target });
  return alerts.at(-1);
}

test('autoteste do app só confirma sucesso quando ok é booleano true', () => {
  assert.equal(runSelfTest({ ok: true }).level, 'ok');
  for (const receipt of [undefined, null, {}, { ok: 'true' }, { ok: 1 }, { ok: false }]) {
    const alert = runSelfTest(receipt);
    assert.equal(alert.level, 'warning', 'resposta parcial ou truthy não é confirmação');
    assert.doesNotMatch(alert.message, /concluído/i);
  }
});
