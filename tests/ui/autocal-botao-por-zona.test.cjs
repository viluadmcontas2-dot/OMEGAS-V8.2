'use strict';

// Dono, 2026-10-20: um botão pequeno por zona e por combustível (verde GNV, vermelho gasolina) que faz o
// "Reaprender" de todos os pontos daquela zona, pelo mesmo caminho do "Reaprender marcados".
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const context = { console };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(read('app/src/main/assets/ui/screens/autocal-original.js'), context);
const O = context.OmegasUi.AutoCalOriginal;
const snapshot = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/autocal-captura-ac-t1000.json'), 'utf8')).snapshot;

test('bandas de cada zona: 0–4, 5–8, 9–12, 13–17', () => {
  assert.deepEqual(JSON.parse(JSON.stringify([0, 1, 2, 3].map(z => O.zoneBands(z)))),
    [[0, 1, 2, 3, 4], [5, 6, 7, 8], [9, 10, 11, 12], [13, 14, 15, 16, 17]]);
});

test('gráfico principal traz 4 botões de GNV (verde) e 4 de gasolina (vermelho), um por zona', () => {
  const model = O.build(snapshot, { fuel: 'gas' });
  const svg = O.renderMain(model, 900, 420);
  for (const fuel of ['GAS', 'PETROL']) {
    for (let zone = 0; zone < 4; zone += 1) {
      assert.ok(svg.includes('data-autocal-zone-reacquire="' + zone + '" data-autocal-zone-fuel="' + fuel + '"'), fuel + ' zona ' + zone);
    }
  }
  assert.match(svg, /class="ao-zone-btn gas"/);
  assert.match(svg, /class="ao-zone-btn petrol"/);
});

test('cockpit liga o botão por zona ao mesmo caminho de reaprender em lote, sem confirmação', () => {
  const cockpit = read('app/src/main/assets/ui/screens/autocal-cockpit.js');
  assert.match(cockpit, /data-autocal-zone-reacquire/);
  assert.match(cockpit, /requestZoneReacquisition/);
  assert.match(cockpit, /preparePointDeleteBatch/);
  const css = read('app/src/main/assets/ui/styles-autocal-cockpit.css');
  assert.match(css, /\.ao-zone-btn\.gas/);
  assert.match(css, /\.ao-zone-btn\.petrol/);
});

test('zona: um toque vira UM pedido ao caminho único de limpeza (sem plano B ponto a ponto em cascata)', () => {
  const cockpit = read('app/src/main/assets/ui/screens/autocal-cockpit.js');
  assert.ok(!/advanceZoneQueue|zoneQueue/.test(cockpit), 'a fila antiga (lote → um por um) saiu: mesma causa falhava 5 vezes');
  const a = cockpit.indexOf('requestZoneReacquisition(fuel, zone) {');
  const b = cockpit.indexOf('    reacquireTargets(rawTargets)');
  const fn = new Function('ns', 'return {' + cockpit.slice(a, b) + '}')({ AutoCalOriginal: O }).requestZoneReacquisition;
  const asked = [];
  const self = { clearer: { request: (targets, meta) => asked.push({ targets: JSON.parse(JSON.stringify(targets)), meta }) }, paintClearState() {} };
  fn.call(self, 'GAS', 1);
  fn.call(self, 'PETROL', 3);
  assert.equal(asked.length, 2);
  assert.deepEqual(asked[0].targets.map(t => t.index), [5, 6, 7, 8]);
  assert.equal(asked[0].meta.label, 'GNV · zona 2');
  assert.deepEqual(asked[1].targets.map(t => t.fuel + t.index), ['PETROL13', 'PETROL14', 'PETROL15', 'PETROL16', 'PETROL17']);
  assert.equal(asked[1].meta.label, 'gasolina · zona 4');
  fn.call(self, 'GAS', 4); // zona inválida não pede nada
  assert.equal(asked.length, 2);
});

test('zona: o destaque das bandas em limpeza existe no CSS e respeita movimento reduzido', () => {
  const css = read('app/src/main/assets/ui/styles-autocal-cockpit.css');
  assert.match(css, /\.ao-zone-btn\[data-clearing="true"\]/);
  assert.match(css, /prefers-reduced-motion[\s\S]{0,120}data-clearing/);
});
