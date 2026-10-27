'use strict';

// Dono (2026-10-26/27) + contrato de fluxo: a faixa "Curva K" sai do AutoCal e, no lugar dela, sob o gráfico, ficam SEMPRE
// "Apagar N pontos", "Reaprender GNV" e "Reaprender gasolina". Nenhum aparece nem some por estado: o que não se aplica
// fica cinza e diz o motivo. "Limpar" da seleção vira "Desmarcar todos" (limpar = apagar pontos em todo o resto).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const read = file => fs.readFileSync(path.join(root, 'app/src/main/assets/ui', file), 'utf8');
const source = read('screens/autocal-cockpit.js');
const ctx = { console, setTimeout: () => 0, clearTimeout: () => {} };
ctx.globalThis = ctx;
vm.createContext(ctx);
vm.runInContext(source, ctx);
const Cockpit = ctx.OmegasUi.AutoCalCockpit;
const plain = x => JSON.parse(JSON.stringify(x));

function make(extra = {}) {
  const c = Object.create(Cockpit.prototype);
  Object.assign(c, {
    api: { available: () => true },
    selectedAcquiredPoints: new Set(),
    clearer: { view: () => ({ pending: 0, active: false }), request() {} },
    lastCounters: null,
    operationalPending: false,
  }, extra);
  return c;
}

test('o modelo traz a barra de ações sob o gráfico, antes do rodapé, e o rodapé não repete Reaprender', () => {
  const bar = source.indexOf('id="autocalChartActions"');
  const footer = source.indexOf('class="autocal-focus-actions"');
  assert.ok(bar > 0 && footer > bar, 'barra sob o gráfico, antes do rodapé');
  const barHtml = source.slice(bar, footer);
  assert.match(barHtml, /data-autocal-chart-clear/);
  assert.match(barHtml, /data-autocal-action="RESET_GAS"/);
  assert.match(barHtml, /data-autocal-action="RESET_PETROL"/);
  const footerHtml = source.slice(footer, source.indexOf('<details class="autocal-secondary-details"'));
  assert.doesNotMatch(footerHtml, /RESET_GAS|RESET_PETROL/);
});

test('a faixa da Curva K sai do gráfico do AutoCal (renderK fica para o Refino)', () => {
  assert.doesNotMatch(source, /O\.renderK\(/);
  assert.doesNotMatch(source, /ao-cell ao-k|ao-cell ao-legend/);
});

test('sem pontos escolhidos: "Apagar pontos" fica cinza e diz o motivo; Reaprender segue ativo', () => {
  const view = plain(make().chartActionView());
  assert.equal(view.clear.label, 'Apagar pontos');
  assert.equal(view.clear.disabled, true);
  assert.match(view.clear.note, /escolha pontos no gráfico/i);
  assert.equal(view.gas.disabled, false);
  assert.equal(view.petrol.disabled, false);
});

test('com pontos escolhidos: "Apagar N pontos" ativo (singular e plural)', () => {
  const c = make({ selectedAcquiredPoints: new Set(['GAS:0']) });
  assert.equal(c.chartActionView().clear.label, 'Apagar 1 ponto');
  c.selectedAcquiredPoints.add('PETROL:3');
  const v = plain(c.chartActionView());
  assert.equal(v.clear.label, 'Apagar 2 pontos');
  assert.equal(v.clear.disabled, false);
});

test('sem ligação com a central: os três ficam cinza com o motivo (nenhum some)', () => {
  const v = plain(make({ api: { available: () => false }, selectedAcquiredPoints: new Set(['GAS:0']) }).chartActionView());
  for (const key of ['clear', 'gas', 'petrol']) {
    assert.equal(v[key].disabled, true, key);
    assert.match(v[key].note, /sem conexão/i, key);
  }
});

test('combustível sem nenhum ponto (contadores todos 0): Reaprender daquele fica cinza com o motivo', () => {
  const zeros = new Array(18).fill(0);
  const ones = new Array(18).fill(2);
  const v = plain(make({ lastCounters: { GAS: zeros, PETROL: ones } }).chartActionView());
  assert.equal(v.gas.disabled, true);
  assert.match(v.gas.note, /sem pontos de GNV/i);
  assert.equal(v.petrol.disabled, false);
  // desconhecido continua desconhecido: não vira "sem pontos"
  const unknown = plain(make({ lastCounters: { GAS: new Array(18).fill(null), PETROL: ones } }).chartActionView());
  assert.equal(unknown.gas.disabled, false);
});

test('"Apagar N pontos" manda os pontos escolhidos pelo caminho único de limpeza; a escolha só some quando a limpeza termina', () => {
  const asked = [];
  let pending = 0;
  ctx.document = { getElementById: () => ({ querySelector: () => null }) };
  const c = make({
    selectedAcquiredPoints: new Set(['GAS:0', 'PETROL:4']),
    clearer: { view: () => ({ pending }), request: (t, m) => { pending = 1; asked.push({ t: plain(t), m }); } },
    paintClearState() {}, renderReferenceChart() {}, snapshot: {},
  });
  c.requestSelectedPointClear();
  assert.equal(asked.length, 1);
  assert.deepEqual(asked[0].t, [{ fuel: 'GAS', index: 0 }, { fuel: 'PETROL', index: 4 }]);
  assert.equal(asked[0].m.kind, 'selection');
  assert.equal(c.selectedAcquiredPoints.size, 2, 'limpeza em curso: a escolha continua');
  assert.equal(c.chartActionView().clear.disabled, true, 'sem tocar duas vezes enquanto apaga');
  pending = 0; // a limpeza terminou
  c.paintChartActions();
  assert.equal(c.selectedAcquiredPoints.size, 0);
});

test('pedido recusado de cara: a escolha fica para tocar de novo', () => {
  ctx.document = { getElementById: () => ({ querySelector: () => null }) };
  const c = make({
    selectedAcquiredPoints: new Set(['GAS:0']),
    clearer: { view: () => ({ pending: 0 }), request() {} },
    paintClearState() {}, renderReferenceChart() {}, snapshot: {},
  });
  c.requestSelectedPointClear();
  assert.equal(c.selectedAcquiredPoints.size, 1);
});

test('inspetor: o lote diz "Desmarcar todos" (e não "Limpar")', () => {
  assert.match(source, /data-autocal-clear-point-selection>Desmarcar todos<\/button>/);
  assert.doesNotMatch(source, /data-autocal-clear-point-selection>Limpar</);
});

test('ponto-limpeza: seleção apagada diz "Apaguei N pontos."', () => {
  const sandbox = { console };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(read('core/ponto-limpeza.js'), sandbox);
  const alerts = [];
  let counters = { id: 'a', GAS: new Array(18).fill(3), PETROL: new Array(18).fill(0) };
  const api = {
    preparePointDeleteBatch: () => ({ ok: true, prepared: true, preparationId: 'p1' }),
    execute: () => ({ ok: true }), cancelPreparation() {},
  };
  const clearer = sandbox.OmegasUi.PointClear.create({ api, notify: (l, m) => alerts.push(m), counters: () => counters });
  clearer.request([{ fuel: 'GAS', index: 0 }, { fuel: 'GAS', index: 1 }], { kind: 'selection', label: '2 pontos' });
  clearer.onActionState({ preparationId: 'p1', state: 'CONFIRMED' });
  clearer.onSnapshot({ id: 'b', GAS: new Array(18).fill(0), PETROL: new Array(18).fill(0) });
  assert.equal(alerts.at(-1), 'Apaguei 2 pontos.');
});
