'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const file = 'app/src/main/assets/ui/core/gasoline-reference.js';
function load() {
  const ctx = {};
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(file, 'utf8'), ctx);
  return ctx.OmegasUi.GasolineReference;
}
const source = 'docs/evidence/2026-10-18-quadro-gasolina-gnv-k.md';
const data = { historical: true, readOnly: true, fonte: source, cells: [
  { mapMin: .3, mapMax: .4, rpmMin: 500, rpmMax: 1000, petrolMs: 2.5, n: 30 },
  { mapMin: .4, mapMax: .5, rpmMin: 500, rpmMax: 1000, petrolMs: null, n: 29 },
] };
test('lookup com fronteiras e desconhecidos, sem extrapolar', () => {
  const api = load().create(data);
  assert.equal(JSON.stringify(api.lookup(.35, 900)), JSON.stringify({ petrolMs: 2.5, n: 30, fonte: source }));
  for (const [m, r] of [[.4,900], [.35,1000], [.35,699], [null,900], [.35,NaN], [-1,900]]) assert.equal(api.lookup(m, r), null);
  assert.equal(load().create({ ...data, historical: false }).lookup(.35,900), null);
});
test('texto histórico só substitui o aviso de gasolina insuficiente e não muda dados', () => {
  const api = load().create(data);
  const before = JSON.stringify(data);
  const aviso = 'Rode alguns minutos na gasolina para criar a referência.';
  assert.match(api.text(aviso, .35, 900), /sem gasolina nesta sessão — referência histórica de 30 amostras/);
  assert.match(api.text('Sem evidência de gasolina', .35, 900), /origem: docs\/evidence\//);
  assert.equal(api.text('Continue rodando no GNV.', .35, 900), 'Continue rodando no GNV.');
  assert.equal(api.text(aviso, .4, 900), aviso);
  assert.equal(JSON.stringify(data), before);
});
test('cópia embutida deriva do JSON e carrega sem rede no WebView file', async () => {
  const api = load();
  const d = JSON.parse(fs.readFileSync('app/src/main/assets/ui/data/gasolina-referencia.json'));
  const ref = await api.load();
  for (const c of d.cells) {
    const actual = ref.lookup((c.mapMin+c.mapMax)/2, Math.max(700,c.rpmMin));
    assert.equal(actual?.petrolMs ?? null, c.petrolMs);
    if (actual) assert.equal(actual.n, c.n);
  }
});
test('JSON real tem célula consultável e origem', () => {
  const d = JSON.parse(fs.readFileSync('app/src/main/assets/ui/data/gasolina-referencia.json'));
  const c = d.cells.find(c => c.n >= 30);
  assert.equal(load().create(d).lookup((c.mapMin+c.mapMax)/2, Math.max(700,c.rpmMin)).petrolMs, c.petrolMs);
});
test('Refino atualiza consulta com dado novo e restaura aviso fora da cobertura', () => {
  const ctx = { console, setTimeout: () => 0 };
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(file, 'utf8'), ctx);
  vm.runInContext(fs.readFileSync('app/src/main/assets/ui/screens/refino.js','utf8'), ctx);
  const node = { textContent: '' };
  ctx.document = { getElementById: () => node };
  const state = { route: 'refino', telemetry: { load_bar: .35, rpm: 900 } };
  const aviso = 'Rode alguns minutos na gasolina para criar a referência.';
  const screen = Object.create(ctx.OmegasUi.RefinoScreen.prototype);
  Object.assign(screen, { store: { get: () => state }, gasolineReference: ctx.OmegasUi.GasolineReference.create(data), operation: { phase: 'idle' }, eq: { autopilot: { next: aviso } } });
  screen.renderHistoricalReference();
  assert.match(node.textContent, /30 amostras/);
  state.telemetry = { load_bar: .4, rpm: 900 };
  screen.renderHistoricalReference();
  assert.equal(node.textContent, aviso);
  screen.eq.autopilot.next = 'Continue rodando no GNV.';
  node.textContent = screen.eq.autopilot.next;
  screen.renderHistoricalReference();
  assert.equal(node.textContent, 'Continue rodando no GNV.');
});
