'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const api = read('app/src/main/assets/ui/core/autocal-api.js');
const cockpit = read('app/src/main/assets/ui/screens/autocal-cockpit.js');
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
  ['data-autocal-action', "querySelectorAll('[data-autocal-action]')", 'this.prepare(button.dataset.autocalAction)'],
  ['data-autocal-band-index', "closest('[data-autocal-band-index]')", 'inspectBand'],
  ['data-autocal-ref-index', "closest('[data-autocal-ref-index]')", 'inspectReferencePoint'],
];
for (const [selector, hook, handler] of controls) {
  assert.ok(cockpit.includes(selector), selector + ' não existe no cockpit');
  assert.ok(cockpit.includes(hook), selector + ' existe sem binding de clique');
  assert.ok(cockpit.includes(handler), selector + ' não alcança handler esperado');
}

assert.equal(cockpit.includes('data-autocal-chart-action'), false, 'zoom/pan genérico não pode voltar com eixos físicos fixos');
assert.equal(cockpit.includes('updateChartView'), false, 'transformação visual desacoplada dos eixos não pode voltar');
assert.ok(cockpit.includes('Petrol Inj. (ms)'), 'eixo X físico precisa permanecer explícito');
assert.ok(cockpit.includes('MAP (bar)'), 'eixo Y físico precisa permanecer explícito');
assert.equal(cockpit.includes('data-autocal-history'), false, 'não existe controle de leitura anterior (dono, 2026-10-11: sem leitura anterior)');
assert.equal(cockpit.includes('renderReview'), false, 'Readquirir não pede segunda confirmação (dono, 2026-10-11: um toque)');

assert.ok(index.includes('data-route="autocal"'), 'rota AutoCal ausente');
assert.ok(index.indexOf('data-route="autocal"') < index.indexOf('data-route="refino"'), 'AutoCal deve preceder o Refino');
assert.equal(cockpit.includes('data-curve-view="autocal"'), false, 'subview legada não pode voltar');
assert.equal(cockpit.includes('data-curve-panel="autocal"'), false, 'painel legado não pode voltar');

assert.match(cockpit, /runOperational\(action\)[\s\S]*this\.api\.setAcquisitionEnabled/, 'Start\/Pause precisa usar ação operacional de um toque');
assert.match(cockpit, /prepare\(action\)[\s\S]*this\.api\.prepare\(action\)/, 'reset nativo precisa passar por prepare');
assert.match(cockpit, /prepare\(action\)[\s\S]*this\.api\.prepare\(action\)[\s\S]*this\.confirmPrepared\(\)/, 'um toque: preparar já executa (dono, 2026-10-11)');
assert.match(cockpit, /confirmPrepared\(\)[\s\S]*this\.api\.execute\(prepared\.preparationId\)/, 'executa exatamente a preparação feita no mesmo toque');
assert.match(actions, /ENABLE_AUTO_CAL[\s\S]*setEnabled\(true\)/, 'iniciar aquisição deve usar ação nativa existente');
assert.match(actions, /DISABLE_AUTO_CAL[\s\S]*setEnabled\(false\)/, 'pausar aquisição deve usar ação nativa existente');
assert.equal(actions.includes('RESET_ALL('), true, 'RESET_ALL do ProgBase deve permanecer exposto atrás da confirmação crítica');

console.log('AUTOCAL_CONSUMER_GRAPH=PASS');
