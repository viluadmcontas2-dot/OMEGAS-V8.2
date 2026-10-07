'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const api = read('app/src/main/assets/ui/core/autocal-api.js');
const cockpit = read('app/src/main/assets/ui/screens/autocal-cockpit.js');
const chart = read('app/src/main/assets/ui/components/curve-chart.js');
const index = read('app/src/main/assets/ui/index.html');
const bridge = read('app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt');
const actions = read('app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt');

const invoked = [...api.matchAll(/invoke\('([^']+)'/g)].map(match => match[1]);
for (const method of invoked) {
  assert.match(bridge, new RegExp('fun\\s+' + method + '\\s*\\('), 'bridge Kotlin ausente para AutoCalApi.' + method);
}

assert.equal(cockpit.includes('data-autocal-read'), false, 'consulta manual da ECU não pertence ao cockpit operacional');
assert.equal(cockpit.includes('requestRead()'), false, 'cockpit deve depender do monitor nativo automático');

const controls = [
  ['data-autocal-toggle', "querySelector('[data-autocal-toggle]')", 'this.runOperational(action)'],
  // Revisto (W2): sem botão "Leitura anterior"; Reler GNV/gasolina em um toque (sem confirmação).
  ['data-autocal-action', "querySelectorAll('[data-autocal-action]')", 'this.prepare(button.dataset.autocalAction)'],
  ['data-autocal-ref-index', "closest('[data-autocal-ref-index]')", 'inspectReferencePoint'],
  ['data-autocal-acquired-index', "closest('[data-autocal-acquired-index]')", 'tapAcquiredPoint'],
  ['data-autocal-reacquire-selected', "closest('[data-autocal-reacquire-selected]')", 'requestSelectedPointReacquisition'],
];
// Revisto (P3 código morto): faixa de regiões e cartão de revisão não existiam mais na tela (um toque, decisão do dono).
for (const dead of ['data-autocal-band-index', 'data-autocal-confirm', 'data-autocal-cancel', 'renderReview']) {
  assert.equal(cockpit.includes(dead), false, dead + ' é código morto');
}
for (const [selector, hook, handler] of controls) {
  assert.ok(cockpit.includes(selector), selector + ' não existe no cockpit');
  assert.ok(cockpit.includes(hook), selector + ' existe sem binding de clique');
  assert.ok(cockpit.includes(handler), selector + ' não alcança handler esperado');
}

assert.equal(cockpit.includes('data-autocal-chart-action'), false, 'zoom/pan genérico não pode voltar com eixos físicos fixos');
assert.equal(cockpit.includes('updateChartView'), false, 'transformação visual desacoplada dos eixos não pode voltar');
assert.ok(cockpit.includes('Injeção (ms)'), 'eixo X físico precisa permanecer explícito');
assert.ok(chart.includes('MAP (bar)'), 'eixo Y físico precisa permanecer explícito no construtor compartilhado');
assert.ok(cockpit.includes('ns.CurveChart.buildSvg(model'), 'reaquisição deve usar os mesmos eixos');
assert.ok(cockpit.includes('data-autocal-history'), 'histórico de leitura continua sendo o controle gráfico permitido');

assert.ok(index.includes('data-route="autocal"'), 'rota AutoCal ausente');
assert.ok(index.indexOf('data-route="autocal"') < index.indexOf('data-route="refino"'), 'AutoCal deve preceder o Refino');
assert.equal(cockpit.includes('data-curve-view="autocal"'), false, 'subview legada não pode voltar');
assert.equal(cockpit.includes('data-curve-panel="autocal"'), false, 'painel legado não pode voltar');

assert.match(cockpit, /runOperational\(action\)[\s\S]*this\.api\.setAcquisitionEnabled/, 'Start\/Pause precisa usar ação operacional de um toque');
assert.match(cockpit, /prepare\(action\)[\s\S]*this\.api\.prepare\(action\)/, 'reset nativo precisa passar por prepare');
assert.match(cockpit, /confirmPrepared\(\)[\s\S]*this\.api\.execute\(prepared\.preparationId\)/, 'confirmação deve executar exatamente a preparação revisada');
assert.match(actions, /ENABLE_AUTO_CAL[\s\S]*setEnabled\(true\)/, 'iniciar aquisição deve usar ação nativa existente');
assert.match(actions, /DISABLE_AUTO_CAL[\s\S]*setEnabled\(false\)/, 'pausar aquisição deve usar ação nativa existente');
assert.equal(actions.includes('RESET_ALL('), true, 'RESET_ALL fica no manager (modo 0x04 provado), sem botão');

console.log('AUTOCAL_CONSUMER_GRAPH=PASS');
