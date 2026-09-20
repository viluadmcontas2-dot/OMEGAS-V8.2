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

const controls = [
  ['data-autocal-read', "querySelector('[data-autocal-read]')", 'requestRead()'],
  ['data-autocal-cancel-read', "querySelector('[data-autocal-cancel-read]')", 'cancelRead()'],
  ['data-autocal-toggle', "querySelector('[data-autocal-toggle]')", 'this.runOperational(action)'],
  ['data-autocal-action', "querySelectorAll('[data-autocal-action]')", 'this.prepare(button.dataset.autocalAction)'],
  ['data-autocal-chart-action', "querySelectorAll('[data-autocal-chart-action]')", 'updateChartView'],
  ['data-autocal-history', "querySelector('[data-autocal-history]')", 'chartHistoryVisible'],
  ['data-autocal-band-index', "closest('[data-autocal-band-index]')", 'inspectBand'],
  ['data-autocal-ref-index', "closest('[data-autocal-ref-index]')", 'inspectReferencePoint'],
  ['data-autocal-confirm', "closest('[data-autocal-confirm]')", 'confirmPrepared'],
  ['data-autocal-cancel', "closest('[data-autocal-cancel]')", 'cancelPrepared'],
];
for (const [selector, hook, handler] of controls) {
  assert.ok(cockpit.includes(selector), selector + ' não existe no cockpit');
  assert.ok(cockpit.includes(hook), selector + ' existe sem binding de clique');
  assert.ok(cockpit.includes(handler), selector + ' não alcança handler esperado');
}

assert.ok(index.includes('data-route="autocal"'), 'rota AutoCal ausente');
assert.ok(index.indexOf('data-route="autocal"') < index.indexOf('data-route="obd"'), 'AutoCal deve preceder OBD');
assert.equal(cockpit.includes('data-curve-view="autocal"'), false, 'subview legada não pode voltar');
assert.equal(cockpit.includes('data-curve-panel="autocal"'), false, 'painel legado não pode voltar');

assert.match(cockpit, /runOperational\(action\)[\s\S]*this\.api\.setAcquisitionEnabled/, 'Start\/Pause precisa usar ação operacional de um toque');
assert.match(cockpit, /prepare\(action\)[\s\S]*this\.api\.prepare\(action\)/, 'reset nativo precisa passar por prepare');
assert.match(cockpit, /confirmPrepared\(\)[\s\S]*this\.api\.execute\(prepared\.preparationId\)/, 'confirmação deve executar exatamente a preparação revisada');
assert.match(actions, /ENABLE_AUTO_CAL[\s\S]*setEnabled\(true\)/, 'iniciar aquisição deve usar ação nativa existente');
assert.match(actions, /DISABLE_AUTO_CAL[\s\S]*setEnabled\(false\)/, 'pausar aquisição deve usar ação nativa existente');
assert.equal(actions.includes('RESET_ALL('), false, 'RESET_ALL não pode ser exposto');

console.log('AUTOCAL_CONSUMER_GRAPH=PASS');
