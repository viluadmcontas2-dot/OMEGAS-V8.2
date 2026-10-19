// Auditoria de fluxo (2026-10-11): a ponte guarda uma "última operação" só para Curva K, Mapa K, backup e reset.
// A aba Mapa K mostrava "A ECU não confirmou toda a operação" depois de uma leitura/gravação da Curva K.
// Agora ela só segue a gravação que ela mesma iniciou (mesmo startedAt).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const UI = path.resolve(__dirname, '../../app/src/main/assets/ui');

function el(id) {
  const classes = new Set();
  const kids = new Map();
  return {
    id, dataset: {}, style: { setProperty() {} }, textContent: '', innerHTML: '', value: '', disabled: false, hidden: false, isConnected: true,
    classList: {
      add: (...c) => c.forEach(x => classes.add(x)), remove: (...c) => c.forEach(x => classes.delete(x)),
      toggle: (c, on) => { const v = on === undefined ? !classes.has(c) : !!on; if (v) classes.add(c); else classes.delete(c); return v; },
      contains: c => classes.has(c),
    },
    addEventListener() {}, setAttribute() {}, getAttribute() { return null; }, removeAttribute() {}, appendChild(c) { return c; }, prepend() {}, insertBefore() {},
    querySelector(sel) { if (!kids.has(sel)) kids.set(sel, el(null)); return kids.get(sel); },
    querySelectorAll() { return []; }, closest() { return null; }, focus() {},
  };
}

function run(operation, ownStartedAt = null) {
  const byId = new Map();
  const bySel = new Map();
  const g = { console, Number, String, Array, Map, Set, JSON, Math, Object, Error, Date, Intl };
  g.globalThis = g;
  g.window = g;
  g.document = {
    getElementById(id) { if (!byId.has(id)) byId.set(id, el(id)); return byId.get(id); },
    querySelector(sel) { if (!bySel.has(sel)) bySel.set(sel, el(null)); return bySel.get(sel); },
    querySelectorAll() { return []; }, createElement() { return el(null); }, head: { appendChild() {} },
  };
  vm.createContext(g);
  vm.runInContext(fs.readFileSync(path.join(UI, 'map-editor.js'), 'utf8'), g);
  vm.runInContext(fs.readFileSync(path.join(UI, 'screens/map.js'), 'utf8'), g);
  let state = { map: { state: 'idle' }, routeContext: null };
  const store = { get: () => state, patch: p => { state = { ...state, ...p }; } };
  const api = {
    startMapRead: () => ({ ok: true, started: true, state: 'READING' }),
    mapReadResult: () => ({ ok: true, busy: true, state: 'READING' }),
    mapWriteOperation: () => operation,
  };
  const screen = new g.OmegasUi.MapScreen(store, api, null);
  screen.onEnter(null);
  if (ownStartedAt !== null) screen.writeStartedAt = ownStartedAt;
  screen.poll();
  screen.pollWrite();
  const root = bySel.get('[data-screen="map"]');
  const result = byId.get('mapOperationResult');
  return { overlay: root.classList.contains('has-result'), title: result?.querySelector('b').textContent || '' };
}

test('falha de leitura da Curva K não aparece no Mapa K', () => {
  const r = run({ ok: false, state: 'FAILED', busy: false, error: 'Timeout lendo Curva K', startedAt: 1, finishedAt: 2 });
  assert.equal(r.overlay, false);
  assert.doesNotMatch(r.title, /não confirmou/);
});

test('gravação confirmada da Curva K não vira "alterações confirmadas" no Mapa K', () => {
  const r = run({ ok: true, state: 'BATCH_CONFIRMED', busy: false, progress: 100, readbackValid: true, totalPoints: 3, startedAt: 7 });
  assert.equal(r.overlay, false);
  assert.doesNotMatch(r.title, /undefined/);
});

test('a gravação do próprio Mapa K continua mostrando o resultado', () => {
  const r = run({ ok: true, state: 'BATCH_CONFIRMED', busy: false, progress: 100, readbackValid: true, confirmedCells: 2, totalCells: 2, startedAt: 42 }, 42);
  assert.equal(r.overlay, true);
  assert.match(r.title, /2 alterações gravadas/);
});
