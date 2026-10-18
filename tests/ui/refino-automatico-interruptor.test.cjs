'use strict';

// D1 (dono, 2026-10-16): depois de cada AutoMatch da ECU o Refino pode gravar sozinho, dentro das travas. Um interruptor único.
// Estados: DESLIGADO · SOMBRA (só registra na sessão o que gravaria; padrão) · LIGADO (grava). Ligar exige toque duplo.

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
  assert.match(D.describeAutoRefineMode('SHADOW'), /sombra[\s\S]*só registra/i);
  assert.match(D.describeAutoRefineMode('ON'), /ligado[\s\S]*grava sozinho/i);
  assert.match(D.describeAutoRefineMode('???'), /sombra/i, 'valor desconhecido cai no modo seguro');
});

test('ao abrir, mostra o modo que o app guardou', () => {
  const { drawers, label } = load('ON');
  drawers.syncAutoRefine();
  assert.match(label.textContent, /grava sozinho/i);
});

test('SOMBRA → LIGADO só no segundo toque; LIGADO → DESLIGADO → SOMBRA', () => {
  const { drawers, label, calls } = load('SHADOW');
  drawers.syncAutoRefine();
  drawers.toggleAutoRefine();
  assert.deepEqual(calls, [], 'primeiro toque só arma');
  assert.match(label.textContent, /toque de novo/i);
  drawers.toggleAutoRefine();
  assert.deepEqual(calls, ['ON']);
  assert.match(label.textContent, /grava sozinho/i);
  drawers.toggleAutoRefine();
  assert.deepEqual(calls, ['ON', 'OFF'], 'desligar é um toque');
  drawers.toggleAutoRefine();
  assert.deepEqual(calls, ['ON', 'OFF', 'SHADOW'], 'voltar à sombra é um toque');
  clearTimeout(drawers.autoRefineTimer);
});

test('a ponte nativa expõe o modo e o serviço decide só com a política', () => {
  const bridge = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt'), 'utf8');
  assert.match(bridge, /fun getAutoRefineMode\(\)/);
  assert.match(bridge, /fun setAutoRefineMode\(/);
  const service = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt'), 'utf8');
  assert.match(service, /AutoRefinePolicy\.decide\(/);
  assert.match(service, /"d1_decision"/);
});
