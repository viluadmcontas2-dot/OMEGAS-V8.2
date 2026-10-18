'use strict';

// D1 (dono, 2026-10-18): o app NÃO grava sozinho; AutoMatch manual continua manual. Interruptor: DESLIGADO · SOMBRA (padrão, só registra).

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const ROOT = path.join(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(UI, file), 'utf8');

function load(initialMode = 'SHADOW') {
  const label = { textContent: '' };
  const button = { querySelector: () => label };
  const calls = [];
  const context = {
    console, Date, Math, JSON, Intl, Set, Map, Number, String, Array, Object, setTimeout, clearTimeout,
    document: { getElementById: id => (id === 'toolAutoRefine' ? button : null), querySelectorAll: () => [] },
    OmegasAutoCal: {
      mode: initialMode,
      getAutoRefineMode() { return this.mode; },
      setAutoRefineMode(mode) { calls.push(mode); this.mode = mode; return JSON.stringify({ ok: true, mode }); },
    },
  };
  context.window = context;
  context.globalThis = context;
  context.OmegasUi = {};
  vm.createContext(context);
  vm.runInContext(read('components/drawers.js'), context, { filename: 'drawers.js' });
  const drawers = Object.create(context.OmegasUi.Drawers.prototype);
  drawers.store = { patch() {} };
  return { context, drawers, label, calls };
}

test('botão do Refino automático existe em Ferramentas', () => {
  assert.match(read('index.html'), /id="toolAutoRefine"[\s\S]*?Refino automático/);
});

test('texto de cada modo diz o que acontece, em português simples', () => {
  const { context } = load();
  const D = context.OmegasUi.Drawers;
  assert.match(D.describeAutoRefineMode('OFF'), /desligado/i);
  assert.match(D.describeAutoRefineMode('SHADOW'), /sombra[\s\S]*manual/i);
  assert.doesNotMatch(D.describeAutoRefineMode('ON'), /grava sozinho/i, 'não existe modo que grave sozinho');
});

test('alterna só entre SOMBRA e DESLIGADO e nunca pede ON', () => {
  const { drawers, calls } = load('SHADOW');
  drawers.syncAutoRefine();
  drawers.toggleAutoRefine();
  drawers.toggleAutoRefine();
  drawers.toggleAutoRefine();
  assert.deepEqual(calls, ['OFF', 'SHADOW', 'OFF']);
});

test('a ponte nativa expõe o modo e o serviço decide só com a política', () => {
  const bridge = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt'), 'utf8');
  assert.match(bridge, /fun getAutoRefineMode\(\)/);
  assert.match(bridge, /fun setAutoRefineMode\(/);
  const service = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt'), 'utf8');
  assert.match(service, /AutoRefinePolicy\.decide\(/);
  assert.match(service, /"d1_decision"/);
});

test('não existe modo ON: a política só tem OFF e SHADOW e o serviço nunca chama a gravação em lote do D1', () => {
  const policy = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/autocal/AutoRefinePolicy.kt'), 'utf8');
  assert.match(policy, /enum class Mode \{ OFF, SHADOW \}/);
  assert.match(policy, /enum class Verdict \{ SHADOW_WOULD_WRITE, SKIP \}/);
  assert.doesNotMatch(policy, /"GRAVAR"/);
  const service = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt'), 'utf8');
  assert.doesNotMatch(service, /Mode\.ON\b/);
  assert.match(service, /"Modo inválido"/);
  assert.doesNotMatch(service, /D1 · Refino automático após AutoMatch[\s\S]{0,0}startBatchWrite/);
  assert.doesNotMatch(service, /startBatchWrite\(batch, "D1/);
});
