// Prévia do dono (2026-10-12): Sugestões mostrava "Pendentes 0 · Observando 0 · Aplicadas 0" (fila V7 desligada) e
// repetia "Curva refinada pronta" no título e no texto. Agora: só o que o Refino tem pronto (curva e ajuste local do
// Mapa K), sem contadores vazios; o ajuste local abre o Mapa K com as células (abrir não grava).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const UI = path.resolve(__dirname, '../../app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(UI, file), 'utf8');

function boot(autopilot) {
  const listeners = new Map();
  const host = {
    innerHTML: '', childElementCount: 0,
    querySelector(selector) {
      const key = selector.replace(/[[\]]/g, '');
      if (!this.innerHTML.includes(key)) return null;
      return { addEventListener: (type, fn) => listeners.set(key, fn) };
    },
    querySelectorAll() { return []; },
  };
  const document = {
    getElementById: id => (id === 'suggestionList' ? host : null), querySelector: () => null, querySelectorAll: () => [],
    createElement: () => ({ dataset: {} }), head: { appendChild() {} }, addEventListener() {}, activeElement: null,
  };
  const context = { console, document, setInterval: () => 1, clearInterval() {}, setTimeout: () => 1, clearTimeout() {}, addEventListener() {}, localStorage: { getItem: () => null, setItem() {} } };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  for (const file of ['core/native-api.js', 'core/store.js', 'core/router.js', 'core/scheduler.js', 'app.js']) vm.runInContext(read(file), context, { filename: file });
  const app = context.OmegasApp;
  context.OmegasUi.AutoCalApi = { refinementPhase: () => ({ ok: true, autopilot }) };
  const navigations = [];
  const navigate = app.router.navigate.bind(app.router);
  app.router.navigate = (route, ctx) => { navigations.push({ route, ctx: ctx ? JSON.parse(JSON.stringify(ctx)) : null }); return navigate(route, ctx); };
  app.router.navigate('suggestions');
  return { host, listeners, navigations };
}

test('curva pronta + ajuste local: dois cartões, sem contadores zerados e sem título repetido', () => {
  const { host, listeners, navigations } = boot({
    phase: 'PROPOSTA_PRONTA', proposal: { count: 12 },
    secondary: [{ kind: 'local', enabled: true, label: 'Revisar ajuste local no Mapa K (2 células)', cells: [{ row: 3, column: 0, deltaPercent: 12 }, { row: 4, column: 0, deltaPercent: 12 }] }],
  });
  assert.match(host.innerHTML, /Curva refinada pronta<\/h3><p>12 pontos da Curva K para revisar e gravar\./);
  assert.doesNotMatch(host.innerHTML, /Curva refinada pronta: /, 'título não se repete no texto');
  assert.match(host.innerHTML, /AJUSTE LOCAL · MAPA K[\s\S]*2 células para revisar/);
  assert.doesNotMatch(host.innerHTML, /suggestion-queue-summary|PENDENTES/, 'sem contadores vazios da fila desligada');
  listeners.get('data-open-local')();
  const last = navigations.at(-1);
  assert.equal(last.route, 'map');
  assert.deepEqual(last.ctx, { origin: 'suggestions', suggestion: { mapChanges: [{ row: 3, column: 0, deltaPercent: 12 }, { row: 4, column: 0, deltaPercent: 12 }] } });
});

test('nada pronto: estado vazio em português, sem cartões', () => {
  const { host } = boot({ phase: 'ESTAVEL', secondary: [] });
  assert.match(host.innerHTML, /Nada para revisar agora/);
  assert.doesNotMatch(host.innerHTML, /data-open-refino|data-open-local|PENDENTES/);
});
