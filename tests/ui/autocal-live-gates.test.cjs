'use strict';

// AutoCal leve: cursor AGORA e narrativa no máximo 10 vezes por segundo (dono, 2026-10-13: "o cursor não fica em tempo real";
// antes 2 por segundo, aprovado em 2026-10-11 quando cada amostra refazia o gráfico inteiro; agora só uma camada leve anda);
// números do topo seguem cada quadro (nada mais muda no que se vê); sem quadro novo, nenhuma escrita no DOM;
// releitura de 1 s com vigia: mesma leitura (só carimbos de tempo mudando) não redesenha; leitura nova,
// operação em curso, AGORA que aparece/some e o vigia de 2,5 s redesenham.
// OMEGAS_ROOT permite rodar contra outra cópia (ex.: o código anterior, que deve falhar).

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = process.env.OMEGAS_ROOT || path.resolve(__dirname, '../..');
const SOURCE = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');

/** Nó falso que conta toda escrita (atributo, dataset, texto, innerHTML), como um MutationObserver contaria. */
function fakeNode(name) {
  const attrs = new Map();
  const node = {
    name, writes: 0,
    getAttribute: key => (attrs.has(key) ? attrs.get(key) : null),
    hasAttribute: key => attrs.has(key),
    setAttribute(key, value) { node.writes += 1; attrs.set(key, String(value)); },
    removeAttribute(key) { if (attrs.has(key)) { node.writes += 1; attrs.delete(key); } },
    querySelector: () => null,
    querySelectorAll: () => [],
    classList: { toggle() {}, add() {}, remove() {}, contains: () => false },
    addEventListener() {},
  };
  node.dataset = new Proxy({}, { set(target, key, value) { node.writes += 1; target[key] = String(value); return true; } });
  let text = '';
  let html = '';
  let hidden = false;
  let disabled = false;
  Object.defineProperty(node, 'textContent', { get: () => text, set(value) { node.writes += 1; text = String(value); } });
  Object.defineProperty(node, 'innerHTML', { get: () => html, set(value) { node.writes += 1; html = String(value); } });
  Object.defineProperty(node, 'hidden', { get: () => hidden, set(value) { node.writes += 1; hidden = !!value; } });
  Object.defineProperty(node, 'disabled', { get: () => disabled, set(value) { node.writes += 1; disabled = !!value; } });
  return node;
}

function telemetry(petrolMs, mapBar, rpm) {
  return { valid: true, ageMs: 40, telemetryAgeMs: 40, live: { rpm, petrol_ms: petrolMs, load_bar: mapBar, fuel: 'GNV' } };
}

function boot(api, initialTelemetry) {
  const nodes = new Map();
  const byId = id => {
    if (id === 'autocalScreenHost') return null; // sem host: o painel real não é injetado; o teste entrega um painel falso
    if (!nodes.has(id)) nodes.set(id, fakeNode('#' + id));
    return nodes.get(id);
  };
  const document = {
    getElementById: byId, querySelector: () => null, querySelectorAll: () => [],
    createElement: () => fakeNode('created'), head: { appendChild() {} },
  };
  let clock = 50_000;
  class FakeDate extends Date { static now() { return clock; } }
  const context = { console, document, Date: FakeDate, setTimeout: () => 1, clearTimeout() {} };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  context.OmegasUi = { AutoCalApi: api };
  vm.runInContext(SOURCE, context, { filename: 'autocal-cockpit.js' });
  const hooks = {};
  const store = { state: { route: 'autocal', telemetry: initialTelemetry }, get: () => store.state, patch() {} };
  const cockpit = new context.OmegasUi.AutoCalCockpit({ store, scheduler: { addHook(cadence, fn) { hooks[cadence] = fn; return () => {}; } } });
  const layer = fakeNode('live-layer');
  const band = fakeNode('band');
  const label = fakeNode('label');
  const points = [fakeNode('halo'), fakeNode('point')];
  const cells = Array.from({ length: 8 }, (_, index) => {
    const cell = fakeNode('zone');
    if (index < 4) cell.dataset.autocalZonePetrol = String(index); else cell.dataset.autocalZoneGas = String(index - 4);
    cell.writes = 0;
    return cell;
  });
  const panel = fakeNode('panel');
  panel.querySelector = selector => ({ '.autocal-live-layer': layer, '[data-autocal-current-band]': band, '[data-autocal-live-label]': label }[selector] || null);
  panel.querySelectorAll = selector => (selector === '[data-autocal-live-point]' ? points
    : selector === '.autocal-zone-cell' || selector === '[data-autocal-zone-petrol]' || selector === '[data-autocal-zone-gas]' ? cells : []);
  cockpit.panel = panel;
  const extra = [layer, band, label, ...points, ...cells];
  return {
    cockpit, store, hooks, nodes, byId, points,
    tick(ms) { clock += ms; },
    now: () => clock,
    writes: () => [...nodes.values(), ...extra].reduce((sum, node) => sum + node.writes, 0),
    setScale() { cockpit.chartScale = { xMin: 0, xMax: 10, yMin: 0, yMax: 1.15, xFor: v => 64 + v * 90, yFor: v => 352 - v * 280 }; },
  };
}

test('cursor AGORA e narrativa: no máximo 10 vezes por segundo; números do topo seguem cada quadro', () => {
  const rig = boot({ available: () => false }, telemetry(4.0, 0.5, 2000));
  rig.setScale();
  const cursor = [];
  const narrative = [];
  let rpmChanges = 0;
  let last = { cx: null, text: null, rpm: null };
  for (let index = 0; index < 40; index += 1) { // 2 s de quadros a 20 Hz, todos diferentes
    rig.store.state = { ...rig.store.state, telemetry: telemetry(4.0 + index * 0.05, 0.5 + index * 0.005, 2000 + index * 10) };
    rig.hooks.fast();
    const cx = rig.points[1].getAttribute('cx');
    const text = rig.byId('autocalLiveNarrative').textContent;
    const rpm = rig.byId('autocalLiveRpm').textContent;
    if (cx !== last.cx) cursor.push(rig.now());
    if (text !== last.text) narrative.push(rig.now());
    if (rpm !== last.rpm) rpmChanges += 1;
    last = { cx, text, rpm };
    rig.tick(50);
  }
  assert.equal(cursor.length, 20, 'cursor vivo, a cada 0,1 s: ' + cursor.length + ' posições em 2 s');
  assert.equal(narrative.length, 20, 'narrativa a cada 0,1 s: ' + narrative.length);
  for (let index = 1; index < cursor.length; index += 1) assert.ok(cursor[index] - cursor[index - 1] >= 100);
  assert.equal(rpmChanges, 40, 'RPM do topo continua mudando a cada quadro (nada mais muda no que se vê)');
});

test('sem quadro novo, o tick de 50 ms não escreve nada no DOM', () => {
  const rig = boot({ available: () => false }, telemetry(4.0, 0.5, 2000));
  rig.setScale();
  rig.hooks.fast();
  rig.tick(600);
  rig.hooks.fast();
  const before = rig.writes();
  for (let index = 0; index < 30; index += 1) { rig.tick(50); rig.hooks.fast(); }
  assert.equal(rig.writes() - before, 0, 'mesma telemetria por 1,5 s: zero escritas');
});

function projection(counter, updatedAt) {
  const range = (n, a, b) => Array.from({ length: n }, (_, i) => a + (b - a) * i / (n - 1));
  const field = (key, values) => ({ key, status: 'VALID', rawValues: values, physicalValues: values, rawPayloadHex: values.join(','), capturedAtMs: updatedAt });
  const fields = [
    field('AUTO_CAL_ENABLE', [1]), field('MAX_AUTOMATCH', [3]), field('NUM_AUTOMATCH_EXECUTED', [1]),
    field('ACQUIRED_ZONES_PETROL', [1, 1, 1, 0]), field('ACQUIRED_ZONES_GAS', [1, 1, 0, 0]), field('MNFLD_PRESS_THD', range(18, 0.2, 1.05)),
    field('NUM_BUF_UPD_GAS', Array.from({ length: 18 }, (_, i) => (i === 3 ? counter : i < 3 ? 20 : 0))),
  ];
  const snapshot = { available: true, snapshotHash: 'h' + counter, fields };
  return {
    ok: true, sessionId: 1, sourceUpdatedAt: updatedAt, referenceUsable: false, snapshot, analysis: {},
    nativeStatus: { state: 'MONITORING', autoCalEnabled: 1, updatedAt, acquisitionRefresh: updatedAt % 2 === 0, latestSnapshot: snapshot },
    nativeSnapshot: snapshot, acquisitionZones: { petrol: [true, true, true, false], gas: [true, true, false, false] },
    correlation: [], correlationState: { correlatedBands: [2] },
  };
}

test('releitura com vigia: mesma leitura não redesenha; leitura nova, operação, AGORA e vigia de 2,5 s redesenham', () => {
  let counter = 0;
  let updatedAt = 1;
  let action = { state: 'IDLE', message: 'Nenhuma ação preparada.' };
  const calls = { projection: 0 };
  const api = {
    available: () => true,
    projection: () => { calls.projection += 1; return JSON.parse(JSON.stringify(projection(counter, updatedAt))); },
    actionStatus: () => ({ ...action }),
    sessionStatus: () => ({ recording: true, durationMs: 61_000 + updatedAt * 1000 }),
  };
  const rig = boot(api, telemetry(4.0, 0.5, 2000));
  let renders = 0;
  const render = rig.cockpit.render.bind(rig.cockpit);
  rig.cockpit.render = () => { renders += 1; render(); };
  rig.hooks.status();
  assert.equal(renders, 1, 'primeira leitura desenha');
  const writes = rig.writes();
  for (let second = 0; second < 2; second += 1) { rig.tick(1000); updatedAt += 1; rig.hooks.status(); }
  assert.equal(calls.projection, 3, 'a ponte continua lida a cada segundo (dado novo não atrasa)');
  assert.equal(renders, 1, 'só carimbos de tempo mudaram: nada a redesenhar');
  assert.equal(rig.writes() - writes, 0, 'e nenhuma escrita no DOM');
  rig.tick(600); rig.hooks.status();
  assert.equal(renders, 2, 'vigia: 2,5 s sem redesenhar força uma releitura completa');
  rig.tick(1000); counter = 9; rig.hooks.status();
  assert.equal(renders, 3, 'contador nativo andou: redesenha no mesmo segundo');
  action = { state: 'SENDING_ACTION', busy: true, message: 'Enviando' };
  rig.tick(1000); rig.hooks.status();
  rig.tick(1000); rig.hooks.status();
  assert.equal(renders, 5, 'operação em curso: redesenha a cada segundo');
  action = { state: 'IDLE', message: 'Nenhuma ação preparada.' };
  rig.tick(1000); rig.hooks.status();
  const settled = renders;
  rig.store.state = { ...rig.store.state, telemetry: { valid: false } };
  rig.tick(1000); rig.hooks.status();
  assert.equal(renders, settled + 1, 'AGORA sumiu: redesenha para tirar a camada do gráfico');
});
