'use strict';

// Telemetria leve na tela: quadro novo com os mesmos valores não toca no DOM; valor novo escreve só ele;
// ciência sem mudança não vira store.patch; sem quadro novo, a idade continua andando depois de 10 s.
// OMEGAS_ROOT permite rodar contra outra cópia (ex.: o código anterior, que deve falhar).

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = process.env.OMEGAS_ROOT || path.resolve(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const read = rel => fs.readFileSync(path.join(UI, rel), 'utf8');

/** Nó falso que conta toda escrita (atributo, dataset, texto), como um MutationObserver contaria. */
function fakeNode(name) {
  const attrs = new Map();
  const node = {
    name, writes: 0,
    getAttribute: key => (attrs.has(key) ? attrs.get(key) : null),
    setAttribute(key, value) { node.writes += 1; attrs.set(key, String(value)); },
    querySelector: () => null,
  };
  node.dataset = new Proxy({}, { set(target, key, value) { node.writes += 1; target[key] = String(value); return true; } });
  let text = '';
  Object.defineProperty(node, 'textContent', { get: () => text, set(value) { node.writes += 1; text = String(value); } });
  return node;
}

function telemetry(petrolMs, mapBar, rpm, extra) {
  return { valid: true, ageMs: 40, telemetryAgeMs: 40, sequence: 1, live: { rpm, petrol_ms: petrolMs, load_bar: mapBar, fuel: 'GNV' }, ...(extra || {}) };
}

test('barra do topo: quadro novo com os mesmos valores não escreve; RPM novo escreve só o RPM', () => {
  const cells = Object.fromEntries(['voltage', 'map', 'petrol', 'rpm'].map(key => {
    const cell = fakeNode('cell:' + key);
    const value = fakeNode('value:' + key);
    cell.querySelector = () => value;
    return [key, { cell, value }];
  }));
  const bar = fakeNode('bar');
  bar.querySelector = selector => cells[/data-live="([^"]+)"/.exec(selector)?.[1]]?.cell || null;
  const document = { getElementById: id => (id === 'liveBar' ? bar : null) };
  const context = { console, document, setTimeout: () => 1 };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(read('components/live-bar.js'), context, { filename: 'live-bar.js' });
  const store = { state: {}, get: () => store.state, subscribe: () => () => {} };
  const component = new context.OmegasUi.LiveBar({ store });
  const writes = () => Object.values(cells).reduce((sum, item) => sum + item.cell.writes + item.value.writes, 0);
  component.render({ telemetry: telemetry(4.2, 0.5, 2100) });
  assert.ok(writes() > 0, 'primeira pintura escreve');
  const before = writes();
  component.render({ telemetry: { ...telemetry(4.2, 0.5, 2100), sequence: 2 } });
  assert.equal(writes() - before, 0, 'quadro novo com os mesmos valores não pode tocar no DOM');
  component.render({ telemetry: telemetry(4.2, 0.5, 2150, { sequence: 3 }) });
  assert.equal(writes() - before, 1, 'RPM novo: exatamente uma escrita');
  assert.equal(cells.rpm.value.textContent, '2.150');
  component.render({ telemetry: { valid: false } });
  assert.equal(cells.rpm.value.textContent, '—', 'telemetria inválida continua "—", nunca 0');
});

function bootApp(overrides) {
  const document = {
    getElementById: () => null, querySelector: () => null, querySelectorAll: () => [],
    createElement: () => ({ dataset: {} }), head: { appendChild() {} }, addEventListener() {},
  };
  const context = {
    console, document,
    setInterval: () => 1, clearInterval() {}, setTimeout: () => 1, clearTimeout() {},
    addEventListener() {}, localStorage: { getItem: () => null, setItem() {} },
  };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  for (const file of ['core/native-api.js', 'core/store.js', 'core/router.js', 'core/scheduler.js', 'app.js']) {
    vm.runInContext(read(file), context, { filename: file });
  }
  const app = context.OmegasApp;
  Object.assign(app.api, overrides || {});
  const patches = [];
  const patch = app.store.patch.bind(app.store);
  app.store.patch = partial => { patches.push(Object.keys(partial || {})); return patch(partial); };
  return { app, patches };
}

test('ciência sem mudança (changed:false) não grava no store nem redesenha', () => {
  const { app, patches } = bootApp({ scienceSnapshotSince: () => ({ ok: true, changed: false, revision: 7, refreshing: false }) });
  app.router.navigate('map');
  patches.length = 0;
  app.scheduler.onContext();
  app.scheduler.onContext();
  assert.deepEqual(patches, [], 'nada mudou na ciência: nenhum store.patch');
});

test('ciência nova (changed:true) e recálculo em curso continuam chegando ao store', () => {
  // Marcador neutro: a fila V7 congelada (sugestões/estabilidade) é zerada na entrada do app (withoutFrozenQueue).
  let answer = { ok: true, changed: true, revision: 8, refreshing: false, data: { calibrationState: { curveRevision: 2, suggestionPending: 5, suggestionItems: [{ id: 'velha' }] } } };
  const { app, patches } = bootApp({ scienceSnapshotSince: () => answer });
  app.router.navigate('tools'); // só Ferramentas lê a ciência (poda, 2026-10-13)
  patches.length = 0;
  app.scheduler.onContext();
  assert.equal(patches.length, 1);
  assert.equal(app.store.get().calibrationState.curveRevision, 2);
  assert.equal(app.store.get().calibrationState.suggestionPending, 0, 'fila congelada não chega à tela');
  assert.equal(app.store.get().calibrationState.suggestionItems.length, 0, 'fila congelada não chega à tela');
  answer = { ok: true, changed: false, revision: 8, refreshing: true };
  app.scheduler.onContext();
  assert.equal(app.store.get().scienceRefreshing, true, 'mudança do recálculo ainda é gravada');
});

test('sem quadro novo, a idade continua andando depois de 10 s (uma atualização por segundo)', () => {
  let age = 9_000;
  const { app } = bootApp({ presentSnapshot: () => ({ ok: true, revision: 1, data: { ...telemetry(4.0, 0.5, 2000), sequence: 5, ageMs: age, telemetryAgeMs: age } }) });
  const ages = [];
  for (; age <= 14_000; age += 250) {
    app.scheduler.onFast();
    ages.push(app.store.get().telemetry.telemetryAgeMs);
  }
  const seen = [...new Set(ages)];
  assert.ok(seen.some(value => value >= 13_000), 'a idade guardada passou de 13 s: ' + seen.join(','));
  const afterTen = seen.filter(value => value >= 10_000);
  assert.ok(afterTen.length >= 4 && afterTen.length <= 6, 'depois de 10 s: uma atualização por segundo: ' + afterTen.join(','));
  const beforeTen = seen.filter(value => value < 10_000);
  assert.deepEqual(beforeTen, [9000, 9500], 'antes de 10 s continua de meio em meio segundo');
});

test('Ferramentas não relê o disco das gravações a cada ciclo (só ao entrar e ao começar/parar)', () => {
  let sessionsCalls = 0;
  let recording = false;
  const { app } = bootApp({
    scienceSnapshotSince: () => ({ ok: true, changed: false, revision: 1, refreshing: false }),
    sessionStatus: () => ({ recording, sessionId: recording ? 's2' : 's1' }),
    sessions: () => { sessionsCalls += 1; return [{ id: 's1' }]; },
    logs: () => [],
  });
  app.router.navigate('tools');
  app.scheduler.onContext();
  const afterEnter = sessionsCalls;
  assert.ok(afterEnter >= 1, 'entrar na aba lista as gravações');
  for (let i = 0; i < 5; i += 1) app.scheduler.onContext();
  assert.equal(sessionsCalls, afterEnter, 'parado na aba: nenhuma leitura de disco nova');
  recording = true;
  app.scheduler.onContext();
  assert.equal(sessionsCalls, afterEnter + 1, 'começou a gravar: relista uma vez');
});
