'use strict';

// Revisão 2026-10-18 (achado 13): nada espera para sempre. A sonda armada desarma em outro toque, na troca de rota ou
// quando o USB cai; as esperas de operação (Curva→Mapa, MapFix, Refino, sonda) terminam na resposta final OU no evento
// usbConnected=false. Evento, nunca prazo.

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const ROOT = path.join(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(UI, file), 'utf8');

function loadDrawers() {
  const label = { textContent: '' };
  const button = { querySelector: () => label, contains: target => target === button };
  const listeners = new Set();
  const context = {
    console, Date, Math, JSON, Intl, Set, Map, Number, String, Array, Object, setTimeout, clearTimeout,
    document: {
      getElementById: id => (id === 'toolBufferProbe' ? button : null), querySelectorAll: () => [],
      addEventListener: (type, fn) => { if (type === 'click') listeners.add(fn); },
      removeEventListener: (type, fn) => { if (type === 'click') listeners.delete(fn); },
    },
  };
  context.window = context;
  context.globalThis = context;
  context.OmegasUi = {};
  vm.createContext(context);
  vm.runInContext(read('components/drawers.js'), context, { filename: 'drawers.js' });
  const drawers = Object.create(context.OmegasUi.Drawers.prototype);
  const state = { route: 'tools', status: { usbConnected: true } };
  drawers.store = { patch() {}, get: () => state, subscribe: () => () => {} };
  return { context, drawers, label, button, listeners, state };
}

test('sonda armada desarma em qualquer outro toque', () => {
  const { context, drawers, label, button, listeners } = loadDrawers();
  let calls = 0;
  context.OmegasAutoCal = { startBufferWriteProbe: () => { calls += 1; return '{"ok":true}'; }, getBufferWriteProbeStatus: () => '{}' };
  context.OmegasApp = { scheduler: { addHook: () => () => {} } };
  drawers.runBufferProbe();
  assert.match(label.textContent, /Toque de novo/);
  assert.equal(listeners.size, 1);
  [...listeners].forEach(fn => fn({ target: button })); // o próprio botão não desarma
  assert.equal(drawers.bufferProbeArmed, true);
  [...listeners].forEach(fn => fn({ target: {} }));
  assert.equal(drawers.bufferProbeArmed, false);
  assert.equal(listeners.size, 0, 'o ouvinte sai junto');
  drawers.runBufferProbe();
  assert.equal(calls, 0, 'depois de desarmar, um toque só arma de novo');
});

test('acompanhamento da sonda termina quando o USB cai', () => {
  const { context, drawers, label, state } = loadDrawers();
  let hook = null;
  context.OmegasAutoCal = { startBufferWriteProbe: () => '{"ok":true,"started":true}', getBufferWriteProbeStatus: () => '{"state":"RUNNING"}' };
  context.OmegasApp = { scheduler: { addHook: (_, fn) => { hook = fn; return () => { hook = null; }; } } };
  drawers.runBufferProbe();
  drawers.runBufferProbe();
  assert.equal(drawers.bufferProbeRunning, true);
  hook();
  assert.equal(drawers.bufferProbeRunning, true, 'com USB e sem resposta final, continua esperando a ECU');
  state.status = { usbConnected: false };
  hook();
  assert.equal(drawers.bufferProbeRunning, false);
  assert.match(label.textContent, /USB desconectado/);
});

test('etapa da curva (Curva K → Mapa K) termina no evento USB desconectado', async () => {
  const runner = require(path.join(UI, 'core/balance-runner.js'));
  let events = 0;
  let online = true;
  const api = { startCurveRead: () => ({ ok: true, started: true }), curveOperation: () => ({ busy: true, state: 'READING' }) };
  const nextEvent = async () => { events += 1; if (events === 3) online = false; if (events > 50) throw new Error('espera sem fim'); };
  const outcome = await runner.writeCurveStage(api, [{ index: 0, currentRaw: 1, targetRaw: 2 }], { nextEvent, offline: () => !online });
  assert.equal(outcome.ok, false);
  assert.match(outcome.error, /USB desconectado/);
  assert.ok(events < 10);
});

test('MapFix (Refino → Mapa K) termina no evento USB desconectado', () => {
  const context = { console, JSON, Math, Number, String, Array, Object, Map, Set };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(read('core/map-fix.js'), context, { filename: 'map-fix.js' });
  let tick = null;
  let online = true;
  const fix = new context.OmegasMapFix.MapFix({
    native: { startMapRead: () => ({ ok: true, started: true }), mapReadResult: () => ({ busy: true, state: 'READING' }) },
    editor: {},
    env: { subscribe: fn => { tick = fn; return () => { tick = null; }; }, offline: () => !online },
  });
  fix.start(() => ({ ok: false }));
  assert.equal(fix.state.phase, 'reading');
  tick();
  assert.equal(fix.state.phase, 'reading');
  online = false;
  tick();
  assert.equal(fix.state.phase, 'failed');
  assert.match(fix.state.message, /USB desconectado/);
  assert.equal(tick, null, 'a espera sai do agendador');
});

test('Refino e Curva K → Mapa K também encerram a espera sem USB', () => {
  const refino = read('screens/refino.js');
  const poll = refino.slice(refino.indexOf('    poll(onFinish) {'), refino.indexOf('    fail(message) {'));
  assert.match(poll, /usbOffline\(\)/);
  const map = read('screens/map.js');
  const curve = map.slice(map.indexOf('    pollCurveToMap() {'), map.indexOf('    applyCurveToMap(points) {'));
  assert.match(curve, /usbConnected === false/);
});
