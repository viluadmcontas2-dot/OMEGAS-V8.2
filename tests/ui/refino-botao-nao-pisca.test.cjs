const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Botão que aparece e some (dono, 2026-10-15): leitura ruim da ponte ou tranco do cabo não pode esvaziar a barra de ações.
const SOURCE = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/screens/refino.js'), 'utf8');

function harness() {
  const nodes = {};
  const node = () => ({ hidden: true, textContent: '', dataset: {}, innerHTML: '', isConnected: true, addEventListener() {}, querySelector: () => null, getBoundingClientRect: () => ({ width: 800, height: 300 }), closest: () => null });
  const bySelector = {};
  for (const sel of ['[data-refino-preview]', '[data-refino-manual]', '[data-refino-undo]', '[data-refino-ok]']) bySelector[sel] = node();
  const host = { ...node(), querySelector: () => null };
  const document = {
    getElementById: id => (id === 'refinoScreenHost' ? host : (nodes[id] = nodes[id] || node())),
    querySelector: sel => bySelector[sel] || null,
  };
  const timers = [];
  let now = 1000;
  const window = { document, setTimeout: fn => { timers.push(fn); return timers.length; }, Date: { now: () => now } };
  window.window = window;
  let eq = null;
  const app = {
    store: { get: () => ({ route: 'refino', telemetry: {} }), subscribe() {}, patch() {} },
    scheduler: { addHook: () => () => {} },
    api: {},
  };
  window.OmegasApp = app;
  window.OmegasUi = { AutoCalApi: { available: () => true, equivalence: () => eq, projection: () => ({ ok: true, snapshot: {} }) }, AutoCalUxModel: {}, AutoCalOriginal: null };
  vm.runInNewContext(SOURCE.replace(/Date\.now\(\)/g, 'window.Date.now()'), { window, document, globalThis: window, console, Date: window.Date });
  const screen = new window.OmegasUi.RefinoScreen(app);
  return {
    screen, bySelector,
    setEq: value => { eq = value; },
    tick: ms => { now += ms; screen.refresh(true); },
    flush: () => { const run = timers.splice(0); run.forEach(fn => fn()); },
  };
}

const ready = { autopilot: { phase: 'PROPOSTA_PRONTA', ecuOnline: true, ecuDone: true, action: { kind: 'review', enabled: true, label: 'Revisar e gravar 2 pontos' }, secondary: [], proposal: { signature: 'x', points: [{ index: 4, currentRaw: 16384, targetRaw: 17000 }] } } };

test('leitura da ponte sem piloto não esconde o botão de ver alterações', () => {
  const h = harness();
  h.setEq(ready); h.tick(1000);
  assert.equal(h.bySelector['[data-refino-preview]'].hidden, false);
  h.setEq({ ok: false }); h.tick(1000);
  assert.equal(h.bySelector['[data-refino-preview]'].hidden, false, 'uma leitura ruim não pode esvaziar a barra');
  h.setEq(ready); h.tick(1000);
  assert.equal(h.bySelector['[data-refino-preview]'].hidden, false);
});

test('ação que some de verdade esconde o botão depois da carência, não antes', () => {
  const h = harness();
  h.setEq(ready); h.tick(1000);
  const gone = { autopilot: { ...ready.autopilot, phase: 'ESTAVEL', action: { kind: 'stable', enabled: false }, proposal: { points: [] } } };
  h.setEq(gone); h.tick(500);
  assert.equal(h.bySelector['[data-refino-preview]'].hidden, false, 'dentro da carência continua');
  h.tick(3000); h.flush();
  assert.equal(h.bySelector['[data-refino-preview]'].hidden, true, 'depois da carência some');
});

test('muitas leituras ruins seguidas (ECU/ponte fora de verdade) acabam limpando a tela', () => {
  const h = harness();
  h.setEq(ready); h.tick(1000);
  h.setEq({ ok: false });
  for (let i = 0; i < 9; i += 1) h.tick(1000);
  h.flush();
  assert.equal(h.bySelector['[data-refino-preview]'].hidden, true);
});
