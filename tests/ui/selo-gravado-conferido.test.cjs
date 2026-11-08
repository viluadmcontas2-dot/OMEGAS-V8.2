'use strict';
// I1 (dono, 2026-10-27): barra do topo por prioridade (No gás · Nível · Inj. gasolina · MAP · RPM · Tensão) e selo discreto
// "Gravado e conferido" no canto direito, que fica até o próximo evento (nunca por tempo).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const UI = path.resolve(__dirname, '../../app/src/main/assets/ui');
const read = relative => fs.readFileSync(path.join(UI, relative), 'utf8');
const WV = require(path.join(UI, 'core/write-verify.js'));

function mount() {
  const cells = {}, order = [];
  const make = name => ({ name, dataset: {}, textContent: '', isConnected: true, setAttribute() {}, querySelector: () => null });
  const bar = make('bar');
  bar.appendChild = n => { const i = order.indexOf(n.name); if (i >= 0) order.splice(i, 1); order.push(n.name); };
  bar.querySelector = selector => {
    const key = /data-live="([^"]+)"/.exec(selector)?.[1];
    if (!key) return null;
    if (!cells[key]) {
      const cell = make('cell:' + key), value = make('value:' + key), small = make('small:' + key);
      cell.querySelector = s => (s === 'small' ? small : value); cell.small = small; cell.value = value; cells[key] = cell; order.push(cell.name);
    }
    return cells[key];
  };
  const verify = make('verify'); verify.hidden = true; const label = make('label');
  verify.querySelector = s => (s === 'span' ? label : null);
  const context = { console, document: { getElementById: id => (id === 'liveBar' ? bar : id === 'liveVerify' ? verify : null) }, setTimeout: () => { throw new Error('relógio proibido'); }, setInterval: () => { throw new Error('relógio proibido'); } };
  context.window = context; vm.createContext(context);
  vm.runInContext(read('core/write-verify.js'), context);
  vm.runInContext(read('components/live-bar.js'), context);
  const store = { state: {}, get: () => store.state, subscribe: () => () => {}, patch() {} };
  const app = { store, api: { writeVerify: () => ({ phase: 'idle' }) } };
  context.OmegasApp = app;
  const component = new context.OmegasUi.LiveBar(app);
  return { component, cells, order, verify, label, app };
}

test('a barra segue a ordem do dono: No gás · Nível · Inj. gasolina · MAP · RPM · Tensão, com rótulos curtos MAP e RPM', () => {
  const { cells, order } = mount();
  assert.deepEqual(order.filter(n => n.startsWith('cell:')), ['source', 'level', 'petrol', 'map', 'rpm', 'voltage'].map(k => 'cell:' + k));
  assert.equal(cells.map.small.textContent, 'MAP');
  assert.equal(cells.rpm.small.textContent, 'RPM');
  assert.equal(cells.petrol.small.textContent, 'Inj. gasolina');
});

test('write-verify: view() entende a fase ok como "Gravado e conferido"', () => {
  assert.deepEqual(WV.view({ phase: 'ok' }), { phase: 'ok', label: 'Gravado e conferido', detail: '' });
  assert.equal(WV.view({ phase: 'idle' }).phase, 'idle');
});

test('selo: done() mostra "Gravado e conferido" e fica (sem relógio) até o próximo evento', () => {
  const { app, verify, label } = mount();
  app.operationStatus.update('refino', 'Gravando…');
  assert.equal(label.textContent, 'Gravando…');
  app.operationStatus.clear('refino');
  app.operationStatus.done('refino', 'Gravado e conferido');
  assert.equal(verify.hidden, false);
  assert.equal(verify.dataset.phase, 'ok');
  assert.equal(label.textContent, 'Gravado e conferido');
  app.store.patch({}); // quadros da ECU não apagam o selo por si só; só o próximo evento de gravação
  app.operationStatus.update('map', 'Gravando…');
  assert.equal(verify.dataset.phase, 'checking');
  assert.equal(label.textContent, 'Gravando…');
});

test('selo: um clear de OUTRO dono não apaga o selo; o do mesmo dono limpa', () => {
  const { app, verify } = mount();
  app.operationStatus.done('refino', 'Gravado e conferido');
  app.operationStatus.clear('map');
  assert.equal(verify.dataset.phase, 'ok');
  app.operationStatus.clear('refino');
  assert.equal(verify.hidden, true);
});

test('selo: nova gravação (writeVerify.begin) troca o selo por "conferindo"', () => {
  const { app, verify, label } = mount();
  app.operationStatus.done('refino', 'Gravado e conferido');
  app.writeVerify.begin();
  assert.equal(verify.dataset.phase, 'checking');
  assert.equal(label.textContent, 'conferindo');
});

test('o Refino mostra "conferindo" ao gravar (begin) em vez de só chamar resolve', () => {
  const src = read('screens/refino.js');
  assert.match(src, /writeVerify\?\.begin\?\.\(\)/);
});
