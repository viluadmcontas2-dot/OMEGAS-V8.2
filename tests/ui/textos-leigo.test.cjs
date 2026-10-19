// Textos para leigo (dono, 2026-10-11): aviso legível acima da navegação, "ECU desconectada" dizendo o que fazer,
// vírgula decimal, desconhecido = "—", sem inglês/código cru. Mede o que a tela escreve, não só o código-fonte.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(ui, file), 'utf8');

function fakeNode(id = '') {
  const children = new Map();
  return {
    id, textContent: '', innerHTML: '', value: '', disabled: false, hidden: false, dataset: {}, style: {},
    classList: { add() {}, remove() {}, toggle() {} },
    querySelector(selector) { if (!children.has(selector)) children.set(selector, fakeNode(selector)); return children.get(selector); },
    querySelectorAll() { return []; }, addEventListener() {}, appendChild() {},
  };
}
function fakeDocument() {
  const nodes = new Map();
  return {
    nodes,
    head: fakeNode('head'),
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, fakeNode(id)); return nodes.get(id); },
    querySelector() { return fakeNode('q'); },
    querySelectorAll() { return []; },
    createElement() { return fakeNode('el'); },
  };
}
function load(file, document) {
  const context = { document, console, setTimeout: () => 0, clearTimeout() {} };
  context.globalThis = context;
  context.window = context;
  vm.runInNewContext(read(file), context);
  return context.OmegasUi;
}

test('aviso (toast): texto ≥ 20 px, à direita acima da navegação (não cobre os botões da esquerda); status da barra com 18 px', () => {
  const css = read('styles.css');
  const toast = css.match(/\.alert-toast\{([^}]*)\}/)[1];
  assert.match(toast, /right:16px;left:auto/, 'os botões ficam embaixo à esquerda: o aviso fica à direita');
  assert.match(toast, /max-width:min\(600px,calc\(48vw - 16px\)\)/, 'no máximo metade da largura: nunca alcança os botões da esquerda');
  assert.match(toast, /bottom:calc\(var\(--nav-height\) \+ \d+px\)/, 'fica acima da barra de 8 abas, sem cobrir nenhuma');
  assert.match(toast, /text-align:left/);
  const size = Number(css.match(/\.alert-toast b\{[^}]*font-size:(\d+)px/)[1]);
  assert.ok(size >= 20, `texto do aviso com ${size}px`);
  assert.match(css, /\.rail-status span\{[^}]*font-size:18px/);
  assert.match(css, /\.rail-status b\{[^}]*font-size:18px/);
});

test('AutoCal: ponto tocado com vírgula, "12 medições (mínimo 8)" e "Reaprender este ponto"', () => {
  const document = fakeDocument();
  const ns = load('screens/autocal-cockpit.js', document);
  const cockpit = Object.create(ns.AutoCalCockpit.prototype);
  cockpit.selectedAcquiredPoints = new Set();
  cockpit.currentAcquiredPoints = [{ fuel: 'GAS', fuelLabel: 'GNV', index: 0, point: 1, zone: 1, counter: 12, threshold: 8, progress: 1, acquisitionState: 'ACQUIRED', petrolMs: 2.04, mapBar: 0.229 }];
  cockpit.inspectAcquiredPoint('GAS', 0);
  const html = document.nodes.get('autocalChartInspector').innerHTML.replace(/\u00a0/g, ' ');
  assert.match(html, /GNV · ponto 1 · zona 1 · pronto/);
  assert.match(html, /2,04 ms · 0,229 bar · 12 medições \(mínimo 8\)/);
  assert.match(html, />Marcar mais pontos<\/button>/);
  assert.match(html, />Reaprender este ponto<\/button>/);
  assert.doesNotMatch(html, /amostras|adquirido|\d\.\d/, 'sem "12 / 8 amostras" e sem ponto decimal');
  // Com pontos marcados, a ação vale para eles.
  cockpit.selectedAcquiredPoints.add('GAS:0');
  cockpit.inspectAcquiredPoint('GAS', 0);
  const batch = document.nodes.get('autocalChartInspector').innerHTML;
  assert.match(batch, />Desmarcar<\/button>/);
  assert.match(batch, />Reaprender 1 marcado<\/button>/);
});

test('AutoCal: sem ECU o gráfico diz "ECU desconectada" e o que fazer', () => {
  const document = fakeDocument();
  const ns = load('screens/autocal-cockpit.js', document);
  const cockpit = Object.create(ns.AutoCalCockpit.prototype);
  Object.assign(cockpit, {
    state: { state: 'DISCONNECTED', message: 'USB desconectado' }, projection: {}, analysis: {}, referenceUsable: false,
    selectedAcquiredPoints: new Set(), store: { get: () => ({ status: { usbConnected: false }, telemetry: {} }) },
    liveTelemetry: () => ({}), renderLiveNarrative() {},
  });
  cockpit.renderReferenceChart({ available: false, fields: [] });
  assert.match(document.nodes.get('autocalReferenceChart').innerHTML, /<b>ECU desconectada<\/b><span>Ligue a ignição e confira o cabo USB\. O gráfico volta sozinho\.<\/span>/);
  assert.equal(document.nodes.get('autocalChartInspector').textContent, 'ECU desconectada · Ligue a ignição e confira o cabo USB. O gráfico volta sozinho.');
});

test('AutoCal: rótulos para leigo e uma grafia só ("AutoCal")', () => {
  const source = read('screens/autocal-cockpit.js');
  for (const label of ['Reaprender GNV <span class="autocal-action-note">(apaga o atual)</span>', 'Reaprender gasolina <span class="autocal-action-note">(apaga o atual)</span>',
    '>Recomeçar tudo<', '>Curva K toda em 1,0<', "'Pausar aprendizado'", "'Retomar aprendizado'",
    '<small>Ajustes automáticos</small>', '0 pontos da central', '>Pontos já medidos<', "'Enviado. Conferindo na central…'"]) {
    assert.ok(source.includes(label), label);
  }
  assert.doesNotMatch(source, /Auto Calibration|AUTO_CAL_ENABLE=0|Confirmando ACK|Aguarde ACK|Readquirir|Selecionar junto|ΔMAP/);
  assert.doesNotMatch(source, /value\.toFixed\(\d\) \+ '<\/text>'/, 'eixos com vírgula');
});

test('Curva K: desconhecido = "—", "Gasolina" por extenso, sem jargão na tela', () => {
  const document = fakeDocument();
  const ns = load('screens/curve.js', document);
  const api = { curveBackups: () => [], previewCurvePoint: () => ({ ok: true }) };
  const store = { state: { curve: {} }, get() { return this.state; }, patch(p) { Object.assign(this.state, p); } };
  const screen = new ns.CurveScreen(store, api);
  screen.renderProposalList();
  assert.equal(document.nodes.get('curveReviewButton').textContent, 'Gravar na central');
  screen.restoreContext = { fileName: 'b.json' };
  screen.renderProposalList();
  assert.equal(document.nodes.get('curveReviewButton').textContent, 'Toque em Restaurar, no rodapé');
  screen.renderChart();
  assert.match(document.nodes.get('curveChart').innerHTML, /Curva não lida\. Ligue a central: ela aparece sozinha\./);
  const index = read('index.html');
  assert.match(index, /<div class="curve-axis">Injeção \(ms\) →<\/div>/);
  assert.match(index, /id="curveTargetFactor" type="text" inputmode="decimal"/, 'campo aceita vírgula');
});

test('Curva K: o campo "Fator desejado" mostra e aceita vírgula', () => {
  const document = fakeDocument();
  const ns = load('screens/curve.js', document);
  const requested = [];
  const api = { curveBackups: () => [], previewCurvePoint: (index, value) => { requested.push(value); return { ok: true, index, changed: true, targetFactor: value, currentFactor: 1 }; } };
  const store = { state: { curve: {} }, get() { return this.state; }, patch(p) { Object.assign(this.state, p); } };
  const screen = new ns.CurveScreen(store, api);
  screen.data = { points: [{ index: 0, petrolMs: 1.5, factorRaw: 16384, factor: 1 }] };
  screen.selectPoint(0);
  const input = document.nodes.get('curveTargetFactor');
  assert.equal(input.value, '1');
  screen.nudgeActive(0.01);
  assert.equal(input.value, '1,01');
  assert.deepEqual(requested, [1.01]);
  input.value = '1,05';
  screen.prepareActivePoint();
  assert.deepEqual(requested, [1.01, 1.05], '"1,05" vira 1.05 para a prévia da central');
});

test('Refino: "Gasolina" por extenso e "GNV vs gasolina"', () => {
  const source = read('screens/refino.js');
  assert.doesNotMatch(source, /refino-steps|<small>GNV vs gasolina<\/small>/, 'Refino reformulado (dono, 2026-10-13): sem fases nem números de bastidor');
  assert.doesNotMatch(source, /GNV ÷ Gasolina|`Gas \$\{/);
});
