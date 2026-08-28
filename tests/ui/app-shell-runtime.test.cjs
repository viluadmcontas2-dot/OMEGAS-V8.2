'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function bootCore() {
  const root = path.resolve('.');
  const storage = new Map();
  let intervalCount = 0;
  let clearCount = 0;
  let intervalHandler = null;
  const context = {
    console,
    setInterval(handler) { intervalCount += 1; intervalHandler = handler; return intervalCount; },
    clearInterval() { clearCount += 1; intervalHandler = null; },
    setTimeout() { return 1; },
    clearTimeout() {},
    localStorage: {
      getItem(key) { return storage.has(key) ? storage.get(key) : null; },
      setItem(key, value) { storage.set(key, String(value)); },
    },
  };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  for (const file of ['core/native-api.js', 'core/store.js', 'core/router.js', 'core/scheduler.js']) {
    const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui', file), 'utf8');
    vm.runInContext(source, context, { filename: file });
  }
  return {
    context,
    storage,
    intervalCount: () => intervalCount,
    clearCount: () => clearCount,
    fireInterval: () => intervalHandler?.(),
  };
}

test('Store é a autoridade única e notifica mudanças sem duplicar estado', () => {
  const { context } = bootCore();
  const store = new context.OmegasUi.Store({ route: 'dashboard', value: 1 });
  const snapshots = [];
  store.subscribe(state => snapshots.push({ ...state }), true);
  store.patch({ value: 2 });
  store.update('route', 'learning');
  assert.deepEqual(JSON.parse(JSON.stringify(store.get())), { route: 'learning', value: 2 });
  assert.equal(snapshots.length, 3);
});

test('Router aceita sete destinos humanos e limpa contexto antigo ao navegar', () => {
  const { context, storage } = bootCore();
  const store = new context.OmegasUi.Store(context.OmegasUi.createInitialState());
  const router = new context.OmegasUi.Router(store);
  assert.equal(router.navigate('map', { suggestion: { id: 1 } }), true);
  assert.equal(store.get().route, 'map');
  assert.equal(store.get().routeContext.suggestion.id, 1);
  assert.equal(router.navigate('curve'), true);
  assert.equal(store.get().route, 'curve');
  assert.equal(store.get().routeContext, null);
  assert.equal(router.navigate('suggestions'), true);
  assert.equal(router.navigate('tools'), true);
  assert.equal(router.navigate('adjust'), false);
  assert.equal(storage.get('omegas-v8-route'), 'tools');
});

test('Scheduler cria um único intervalo e separa fast status e contexto', () => {
  const shell = bootCore();
  const calls = { fast: 0, status: 0, context: 0 };
  const scheduler = new shell.context.OmegasUi.Scheduler({
    intervalMs: 200,
    onFast: () => { calls.fast += 1; },
    onStatus: () => { calls.status += 1; },
    onContext: () => { calls.context += 1; },
  });
  scheduler.start();
  scheduler.start();
  assert.equal(shell.intervalCount(), 1);
  assert.deepEqual(calls, { fast: 1, status: 1, context: 1 });
  for (let index = 0; index < 10; index += 1) shell.fireInterval();
  assert.equal(calls.fast, 11);
  assert.equal(calls.status, 3);
  assert.equal(calls.context, 2);
  scheduler.stop();
  assert.equal(shell.clearCount(), 1);
});

test('modo navegador é simulador visual e nunca escreve ECU', () => {
  const { context } = bootCore();
  const api = new context.OmegasUi.NativeApi();
  assert.equal(api.isDemo(), true);
  assert.equal(api.mapReadResult().writableCells, 144);
  assert.equal(api.readCurve, undefined);
  assert.equal(api.curveOperation().points.length, 30);
  assert.equal(api.telemetry().interpolation.cell.continuousWeights.length, 4);
  assert.equal(api.writeMap([{ row: 0, column: 0, current: 120, target: 121 }]).simulationOnly, true);
  assert.equal(api.writeCurve([{ index: 0, currentRaw: 100, targetRaw: 101 }]).simulationOnly, true);
});

test('APK usa OmegasV7 para uma única intenção de mapa e curva', () => {
  const { context } = bootCore();
  const calls = [];
  context.OmegasNative = {
    getStatus: () => '{}',
    getLiveTelemetry: () => '{}',
    getLearningMaps: () => '{}',
    getLearningSyncStatus: () => '{}',
    getObdStatus: () => '{}',
  };
  context.OmegasV7 = {
    startMapBatchWrite(payload, maxStep, pauseMs, reason) {
      calls.push({ type: 'map', payload: JSON.parse(payload), maxStep, pauseMs, reason });
      return JSON.stringify({ ok: true, started: true });
    },
    startCurveBatchWrite(payload, reason) {
      calls.push({ type: 'curve', payload: JSON.parse(payload), reason });
      return JSON.stringify({ ok: true, started: true });
    },
  };
  const api = new context.OmegasUi.NativeApi();
  assert.equal(api.isDemo(), false);
  api.writeMap([{ row: 0, column: 0, current: 120, target: 125 }], 3, 150, 'teste');
  api.writeCurve([{ index: 0, currentRaw: 12000, targetRaw: 12100 }], 'teste curva');
  assert.equal(calls.length, 2);
  assert.equal(calls[0].type, 'map');
  assert.equal(calls[0].payload.length, 1);
  assert.equal(calls[1].type, 'curve');
  assert.equal(calls[1].payload.length, 1);
});
