'use strict';

// Dono (2026-10-27): arrastar o dedo no gráfico escolhe os pontos dentro da caixa; tocar num ponto continua marcando/desmarcando;
// tocar fora dos pontos (com escolha feita) desmarca. A escolha alimenta "Apagar N pontos".
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-autocal-cockpit.css'), 'utf8');
const ctx = { console, setTimeout: () => 0, clearTimeout: () => {} };
ctx.globalThis = ctx;
vm.createContext(ctx);
vm.runInContext(source, ctx);
const Cockpit = ctx.OmegasUi.AutoCalCockpit;

const dot = (fuel, index, x, y) => ({
  dataset: { autocalAcquiredFuel: fuel, autocalAcquiredIndex: String(index) },
  getBoundingClientRect: () => ({ left: x - 5, top: y - 5, width: 10, height: 10 }),
  closest(sel) { return sel === '[data-autocal-acquired-index]' ? this : null; },
});

function make(nodes) {
  const calls = { render: 0, paint: 0, box: [] };
  const host = {
    querySelectorAll: () => nodes,
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 800, height: 400 }),
    setPointerCapture() {}, releasePointerCapture() {},
  };
  ctx.document = { getElementById: id => (id === 'autocalReferenceChart' ? host : id === 'autocalChartDragBox' ? { style: {}, hidden: true } : null) };
  const c = Object.create(Cockpit.prototype);
  Object.assign(c, {
    selectedAcquiredPoints: new Set(), selectedAcquiredPoint: null, snapshot: {},
    renderReferenceChart() { calls.render += 1; }, paintChartActions() { calls.paint += 1; },
    inspectAcquiredPoint() {}, clearAcquiredPointSelection() { c.selectedAcquiredPoints.clear(); calls.cleared = true; },
  });
  return { c, calls, host };
}
const ev = (x, y, target) => ({ clientX: x, clientY: y, pointerId: 1, button: 0, target: target || { closest: () => null } });

test('arrastar uma caixa escolhe só os pontos dentro dela', () => {
  const nodes = [dot('GAS', 0, 100, 100), dot('GAS', 1, 200, 120), dot('PETROL', 4, 500, 300)];
  const { c, calls } = make(nodes);
  c.onChartPointerDown(ev(50, 50));
  c.onChartPointerMove(ev(260, 160));
  c.onChartPointerUp(ev(260, 160));
  assert.deepEqual(Array.from(c.selectedAcquiredPoints).sort(), ['GAS:0', 'GAS:1']);
  assert.ok(calls.render >= 1 && calls.paint >= 1, 'repinta o gráfico e a barra de ações');
  assert.equal(c.justDragged, true, 'o clique que vem depois do arrasto é ignorado');
});

test('caixa arrastada de baixo para cima e da direita para a esquerda também vale', () => {
  const { c } = make([dot('GAS', 0, 100, 100), dot('PETROL', 2, 400, 300)]);
  c.onChartPointerDown(ev(450, 350));
  c.onChartPointerMove(ev(60, 60));
  c.onChartPointerUp(ev(60, 60));
  assert.equal(c.selectedAcquiredPoints.size, 2);
});

test('arrasto que começa em cima de um ponto não vira caixa (toque no ponto segue o caminho antigo)', () => {
  const nodes = [dot('GAS', 0, 100, 100)];
  const { c } = make(nodes);
  c.onChartPointerDown(ev(100, 100, nodes[0]));
  c.onChartPointerMove(ev(300, 300));
  c.onChartPointerUp(ev(300, 300));
  assert.equal(c.selectedAcquiredPoints.size, 0);
});

test('toque curto fora dos pontos, com escolha feita, desmarca tudo; sem escolha não faz nada', () => {
  const { c, calls } = make([dot('GAS', 0, 100, 100)]);
  c.selectedAcquiredPoints.add('GAS:0');
  c.onChartPointerDown(ev(600, 350));
  c.onChartPointerUp(ev(602, 351));
  assert.equal(calls.cleared, true);
  const other = make([dot('GAS', 0, 100, 100)]);
  other.c.onChartPointerDown(ev(600, 350));
  other.c.onChartPointerUp(ev(601, 350));
  assert.equal(other.calls.cleared, undefined);
});

test('cancelar o gesto não escolhe nada', () => {
  const { c } = make([dot('GAS', 0, 100, 100)]);
  c.onChartPointerDown(ev(50, 50));
  c.onChartPointerMove(ev(260, 160));
  c.onChartPointerCancel(ev(260, 160));
  c.onChartPointerUp(ev(260, 160));
  assert.equal(c.selectedAcquiredPoints.size, 0);
});

test('o gráfico liga os eventos de ponteiro e a caixa de arrasto existe; o gráfico não rola com o dedo', () => {
  for (const name of ['pointerdown', 'pointermove', 'pointerup', 'pointercancel']) assert.ok(source.includes("'" + name + "'"), name);
  assert.match(source, /id="autocalChartDragBox"/);
  assert.match(css, /\.autocal-chart-host \.ao-main\s*\{[^}]*touch-action:\s*none/);
  assert.match(css, /\.autocal-drag-box/);
});
