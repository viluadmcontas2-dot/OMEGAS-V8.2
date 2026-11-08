'use strict';

// Dono (2026-10-26/27): a matriz Zona × Combustível apaga a zona com 1 toque (sem janela) e o cartão tem "Desfazer"
// sempre presente (cinza com o motivo quando não há o que desfazer ou a central ainda não deixa restaurar).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const read = file => fs.readFileSync(path.join(root, 'app/src/main/assets/ui', file), 'utf8');
const cockpitSource = read('screens/autocal-cockpit.js');
const plain = x => JSON.parse(JSON.stringify(x));

function load(file, extra = {}) {
  const sandbox = { console, setTimeout: () => 0, clearTimeout: () => {}, ...extra };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(read(file), sandbox);
  return sandbox.OmegasUi;
}

test('matriz: as 8 células (4 zonas × GNV/gasolina) são botões de 1 toque, com a frase "toque para apagar"', () => {
  const card = cockpitSource.slice(cockpitSource.indexOf('id="autocalZoneMeter"'), cockpitSource.indexOf('id="autocalZoneSummary"'));
  for (const [fuel, key] of [['GAS', 'gas'], ['PETROL', 'petrol']]) {
    for (let zone = 0; zone < 4; zone += 1) {
      assert.match(card, new RegExp('<button type="button" class="autocal-zone-cell" data-autocal-zone-' + key + '="' + zone +
        '" data-autocal-zone-reacquire="' + zone + '" data-autocal-zone-fuel="' + fuel + '"'), fuel + ' zona ' + zone);
    }
  }
  assert.match(cockpitSource, /toque para apagar/i);
  assert.doesNotMatch(card, /role="listitem"/);
});

test('cartão: "Desfazer" existe sempre, no mesmo lugar, desabilitado por padrão', () => {
  assert.match(cockpitSource, /<button type="button"[^>]*data-autocal-zone-undo[^>]*disabled[^>]*>Desfazer<\/button>/);
  assert.match(cockpitSource, /data-zone-undo-note/);
});

function harness(api) {
  const ui = load('core/ponto-limpeza.js');
  const alerts = [];
  const counters = { id: 'a', GAS: new Array(18).fill(3), PETROL: new Array(18).fill(0) };
  const clearer = ui.PointClear.create({ api, notify: (l, m) => alerts.push([l, m]), counters: () => counters });
  const run = () => {
    clearer.request([{ fuel: 'GAS', index: 5 }, { fuel: 'GAS', index: 6 }], { kind: 'zone', label: 'GNV · zona 2' });
    clearer.onActionState({ preparationId: 'p1', state: 'CONFIRMED' });
    clearer.onSnapshot({ id: 'b', GAS: new Array(18).fill(0), PETROL: new Array(18).fill(0) });
  };
  return { clearer, alerts, run };
}
const baseApi = extra => ({
  preparePointDeleteBatch: () => ({ ok: true, prepared: true, preparationId: 'p1' }),
  execute: () => ({ ok: true }), cancelPreparation() {}, undoPointDelete: () => ({ ok: true }), ...extra,
});

test('limpeza: sem nada apagado, não há o que desfazer', () => {
  const { clearer } = harness(baseApi({ canUndoPointDelete: () => true }));
  assert.deepEqual(plain(clearer.view().undo), { has: false, supported: true, label: '' });
});

test('limpeza: depois de apagar uma zona, guarda a última e "Desfazer" restaura em 1 toque', () => {
  const calls = [];
  const { clearer, alerts, run } = harness(baseApi({ canUndoPointDelete: () => true, undoPointDelete: p => { calls.push(JSON.parse(p)); return { ok: true }; } }));
  run();
  assert.deepEqual(plain(clearer.view().undo), { has: true, supported: true, label: 'GNV · zona 2' });
  assert.equal(clearer.undo().ok, true);
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0].targets.map(t => t.fuel + t.index), ['GAS5', 'GAS6']);
  assert.deepEqual(calls[0].before, { 'GAS:5': 3, 'GAS:6': 3 });
  assert.equal(alerts.at(-1)[1], 'Restaurei GNV · zona 2.');
  assert.equal(clearer.view().undo.has, false, 'depois de desfazer não há mais o que desfazer');
});

test('limpeza: central que ainda não restaura → avisa com o motivo, sem fingir', () => {
  const { clearer, alerts, run } = harness(baseApi({ canUndoPointDelete: () => false }));
  run();
  assert.equal(clearer.view().undo.supported, false);
  assert.equal(clearer.undo().ok, false);
  assert.match(alerts.at(-1)[1], /não deixa restaurar/);
});

test('limpeza: restauração recusada pela central mantém a última para tentar de novo', () => {
  const { clearer, alerts, run } = harness(baseApi({ canUndoPointDelete: () => true, undoPointDelete: () => ({ ok: false, error: 'ocupada' }) }));
  run();
  assert.equal(clearer.undo().ok, false);
  assert.equal(alerts.at(-1)[0], 'warning');
  assert.equal(clearer.view().undo.has, true);
});

test('cockpit: zoneUndoView diz o motivo quando está cinza', () => {
  const ctx = { console, setTimeout: () => 0, clearTimeout: () => {} };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(cockpitSource, ctx);
  const C = ctx.OmegasUi.AutoCalCockpit;
  const make = undo => Object.assign(Object.create(C.prototype), { clearer: { view: () => ({ pending: 0, undo }) } });
  let v = plain(make({ has: false, supported: true, label: '' }).zoneUndoView());
  assert.deepEqual(v, { disabled: true, note: 'Nada para desfazer' });
  v = plain(make({ has: true, supported: true, label: 'GNV · zona 2' }).zoneUndoView());
  assert.deepEqual(v, { disabled: false, note: 'Última: GNV · zona 2 apagada' });
  v = plain(make({ has: true, supported: false, label: 'GNV · zona 2' }).zoneUndoView());
  assert.equal(v.disabled, true);
  assert.match(v.note, /Última: GNV · zona 2 apagada · a central ainda não deixa restaurar/);
});
