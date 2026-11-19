'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/components/drawers.js'), 'utf8');
function runSelfTest(receipt) {
  const context = { globalThis: { innerHeight: 720, OmegasUi: {} }, document: {} };
  vm.runInNewContext(source, context, { filename: 'drawers.js' });
  const alerts = [];
  const instance = Object.create(context.globalThis.OmegasUi.Drawers.prototype);
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


test('overlay: ligar e desligar encaminham a intenção exata e nunca ocultam falha Android', () => {
  const context = { globalThis: { innerHeight: 720, OmegasUi: {} }, document: {} };
  vm.runInNewContext(source, context, { filename: 'drawers.js' });
  const inst = Object.create(context.globalThis.OmegasUi.Drawers.prototype);
  const calls = [], alerts = [];
  inst.store = { get: () => ({}), patch: s => alerts.push(s.alert) };
  inst.renderTools = () => {};
  inst.api = { setTelemetryOverlayEnabled: enabled => {
    calls.push(enabled);
    return { ok: false, error: 'Serviço do balão indisponível' };
  } };
  for (const sel of ['[data-tool-overlay-enable]', '[data-tool-overlay-disable]']) {
    const target = { id: '', matches: item => item === sel, closest: item => item === 'button' ? target : null };
    inst.handleToolClick({ target });
  }
  assert.deepEqual(calls, [true, false], 'não inverter ou duplicar intenção do interruptor');
  assert.equal(alerts.length, 2);
  for (const alert of alerts) {
    assert.equal(alert.level, 'warning');
    assert.match(alert.message, /indisponível/);
  }
});

test('registro: filtro ausente reinicia categoria/página sem esconder avisos novos', () => {
  const host = { innerHTML: '', childElementCount: 0, querySelector: () => null, dataset: {} };
  const context = {
    globalThis: { innerHeight: 720, OmegasUi: {} },
    document: { getElementById: id => id === 'toolDiagnosticsWorkspace' ? host : null }
  };
  vm.runInNewContext(source, context, { filename: 'drawers.js' });
  const inst = Object.create(context.globalThis.OmegasUi.Drawers.prototype);
  inst.logLevel = 'ALL'; inst.logCategory = 'ANTIGA'; inst.logPage = 2;
  inst.view = 'log'; inst.page = 2; inst.autoRefineMode = 'SHADOW';
  inst.api = { batteryOptimizationStatus: () => ({}), overlayStatus: () => ({ ok: false }) };
  const live = { logs: [{ level: 'WARN', category: 'APP', time: '12:00', message: 'Aviso novo' }],
    status: { serviceRunning: true, engineStuck: false } };
  inst.renderTools(live);
  assert.equal(inst.logCategory, 'ALL', 'categoria inexistente não fica filtrando escondida');
  assert.equal(inst.logPage, 0, 'página antiga não esconde registro recente');
  assert.match(host.innerHTML, /Aviso novo/);
  assert.match(host.innerHTML, /Todos os assuntos/);
  inst.logCategory = 'APP';
  inst.renderTools(live);
  assert.equal(inst.logCategory, 'APP', 'categoria que existe é preservada');
  inst.logPage = 3;
  let redraws = 0;
  inst.store = { get: () => live };
  inst.renderTools = () => { redraws++; };
  inst.handleToolChange({ target: { value: 'ERROR', matches: sel => sel === '[data-log-level]' } });
  assert.equal(inst.logLevel, 'ERROR');
  assert.equal(inst.logPage, 0);
  inst.handleToolChange({ target: { value: 'APP', matches: sel => sel === '[data-log-category]' } });
  assert.equal(inst.logCategory, 'APP');
  assert.equal(inst.logPage, 0);
  assert.equal(redraws, 2, 'um redesenho por filtro, sem polling extra');
});

test('autoteste: exceção da ponte não derruba Ferramentas nem inventa sucesso', () => {
  const context = { globalThis: { innerHeight: 720, OmegasUi: {} }, document: {} };
  vm.runInNewContext(source, context, { filename: 'drawers.js' });
  const alerts = [];
  const inst = Object.create(context.globalThis.OmegasUi.Drawers.prototype);
  inst.api = { selfTest: () => { throw new Error('Ponte indisponível'); } };
  inst.store = { patch: value => alerts.push(value.alert) };
  const target = {
    id: '',
    matches: selector => selector === '[data-tool-selftest]',
    closest: selector => selector === 'button' ? target : null
  };
  assert.doesNotThrow(() => inst.handleToolClick({ target }));
  assert.equal(alerts.length, 1);
  assert.equal(alerts[0].level, 'warning');
  assert.match(alerts[0].message, /Ponte indisponível/);
  assert.doesNotMatch(alerts[0].message, /concluído/i);
});

test('balão: exceção da ponte não derruba Ferramentas nem confirma mudança', () => {
  const context = { globalThis: { innerHeight: 720, OmegasUi: {} }, document: {} };
  vm.runInNewContext(source, context, { filename: 'drawers.js' });
  const inst = Object.create(context.globalThis.OmegasUi.Drawers.prototype);
  const alerts = [], calls = [];
  inst.store = { get: () => ({}), patch: state => alerts.push(state.alert) };
  inst.renderTools = () => {};
  inst.api = { setTelemetryOverlayEnabled: enabled => {
    calls.push(enabled);
    throw new Error('Ponte do balão indisponível');
  } };
  for (const selector of ['[data-tool-overlay-enable]', '[data-tool-overlay-disable]']) {
    const target = { id: '', matches: value => value === selector,
      closest: value => value === 'button' ? target : null };
    assert.doesNotThrow(() => inst.handleToolClick({ target }));
  }
  assert.deepEqual(calls, [true, false]);
  assert.equal(alerts.length, 2);
  for (const alert of alerts) {
    assert.equal(alert.level, 'warning');
    assert.match(alert.message, /Ponte do balão indisponível/);
  }
});
