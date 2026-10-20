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

test('zona: lote recusado pela central → apaga um por um e segue mesmo se um ponto falhar', () => {
  const cockpit = read('app/src/main/assets/ui/screens/autocal-cockpit.js');
  const a = cockpit.indexOf('advanceZoneQueue(failed) {');
  const b = cockpit.indexOf('    reacquireTargets(rawTargets)');
  assert.ok(a > 0 && b > a);
  const advance = new Function('return {' + cockpit.slice(a, b) + '}')().advanceZoneQueue;
  const sent = [];
  const alerts = [];
  const self = {
    zoneQueue: { mode: 'batch', targets: [{ fuel: 'GAS', index: 0 }, { fuel: 'GAS', index: 1 }, { fuel: 'GAS', index: 2 }], rest: [], failed: 0, total: 3 },
    api: {
      preparePointDelete: (fuel, index) => ({ ok: true, prepared: true, preparationId: fuel + index }),
      execute: id => { sent.push(id); return { ok: true }; },
      cancelPreparation() {},
    },
    expectActionResult() {},
    store: { patch: p => alerts.push(p.alert) },
  };
  advance.call(self, true);            // lote falhou → começa o um por um
  assert.deepEqual(sent, ['GAS0']);
  advance.call(self, false);           // ponto 0 ok
  advance.call(self, true);            // ponto 1 falhou → segue para o 2
  assert.deepEqual(sent, ['GAS0', 'GAS1', 'GAS2']);
  advance.call(self, false);           // ponto 2 ok → fim
  assert.equal(self.zoneQueue, null);
  assert.equal(alerts.at(-1).level, 'warning');
  assert.match(alerts.at(-1).message, /1 de 3/);
});

test('zona: lote aceito pela central encerra a fila sem apagar um por um', () => {
  const cockpit = read('app/src/main/assets/ui/screens/autocal-cockpit.js');
  const a = cockpit.indexOf('advanceZoneQueue(failed) {');
  const b = cockpit.indexOf('    reacquireTargets(rawTargets)');
  const advance = new Function('return {' + cockpit.slice(a, b) + '}')().advanceZoneQueue;
  const self = { zoneQueue: { mode: 'batch', targets: [{ fuel: 'GAS', index: 0 }], rest: [], failed: 0, total: 1 } };
  advance.call(self, false);
  assert.equal(self.zoneQueue, null);
});
