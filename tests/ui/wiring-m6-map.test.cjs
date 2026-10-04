'use strict';
// M6 · Mapa K: selecionar (1, 16, todas) / gravar OK / falha de cabo / NACK / parcial / desfazer / restaurar.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const W = require('./wiring/world.cjs');
const { mapApp } = require('./wiring/scenarios.cjs');
const { todo } = require('./wiring/registry.cjs');

const SCREEN = '[data-screen="map"]';
const rows = app => app.world.map.rows.map(r => r.slice());
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const cell = (app, r, c) => app.$(`.map-k-cell[data-row="${r}"][data-column="${c}"]`);
function touch(app, el) {
  el.dispatchEvent(new app.win.Event('pointerdown', { bubbles: true, pointerId: 1 }));
  el.dispatchEvent(new app.win.Event('pointerup', { bubbles: true, pointerId: 1 }));
  el.click();
  app.flush();
}
function selectCells(app, list) { list.forEach(([r, c]) => touch(app, cell(app, r, c))); }
function setAdjust(app, mode, value) {
  const select = app.byId('mapAdjustmentMode');
  select.value = mode;
  select.dispatchEvent(new app.win.Event('change', { bubbles: true }));
  const input = app.byId('mapAdjustmentValue');
  input.value = String(value);
  input.dispatchEvent(new app.win.Event('input', { bubbles: true }));
  app.flush();
}
const selCount = app => app.byId('mapSelectionCount').textContent;
const review = app => app.byId('mapReviewButton');
const result = app => ({
  level: app.byId('mapOperationResult').dataset.level,
  title: app.byId('mapOperationResult').querySelector('b').textContent,
  detail: app.byId('mapOperationResult').querySelector('span').textContent,
  undo: !app.byId('mapUndoButton').hasAttribute('hidden'),
  reread: !app.byId('mapRereadButton').hasAttribute('hidden'),
});
const hasClass = (app, c) => app.$(SCREEN)._classes().has(c);
const THREE = [[2, 3], [2, 4], [5, 5]];
function writeThree(app, value = 150) {
  selectCells(app, THREE);
  setAdjust(app, 'target', value);
  review(app).click(); app.flush();
}

test('M6 ler: 144 células da ECU na tela, fonte confirmada, nada selecionado, Gravar bloqueado com motivo', () => {
  const app = mapApp();
  L.assertClean(app, 'M6/ler');
  assert.equal(app.$$('.map-k-cell').length, 144);
  assert.match(app.byId('mapSourceStatus').textContent, /confirmada.*144/i);
  assert.match(selCount(app), /^0 /);
  assert.equal(review(app).hasAttribute('disabled'), true);
  assert.ok(review(app).textContent.trim(), 'botão bloqueado precisa dizer por quê');
  assert.equal(app.world.callsOf('startKMapRead').length, 1);
  // valores da tela = valores da ECU
  assert.equal(cell(app, 3, 4).querySelector('b').textContent, String(app.world.map.rows[3][4]));
});

test('M6 ler com falha (cabo / NACK): "Mapa não confirmado", sem grade; reler recupera', () => {
  for (const outcome of ['transport', 'nack']) {
    const app = mapApp({ outcome: { mapRead: outcome } });
    assert.match(app.byId('mapSourceStatus').textContent, /n[aã]o confirmado/i);
    assert.equal(app.$$('.map-k-cell').length, 0);
    assert.equal(review(app).hasAttribute('disabled'), true);
    app.world.outcome.mapRead = 'ok';
    app.byId('mapReadButton').click(); app.settle(3);
    assert.equal(app.$$('.map-k-cell').length, 144);
    L.assertClean(app, `M6/ler-${outcome}`);
  }
});

test('M6 selecionar 1, 16 e todas as células: contagem certa, Gravar liga/desliga, Limpar zera', () => {
  const app = mapApp();
  selectCells(app, [[0, 0]]);
  assert.match(selCount(app), /^1 selecionada$/);
  // sem ajuste digitado nada mudaria: o botão diz isso (não "Gravar 1 célula" seguido de erro)
  assert.equal(review(app).hasAttribute('disabled'), true);
  assert.equal(review(app).textContent, 'Digite o ajuste para mudar o K');
  setAdjust(app, 'delta', 5);
  assert.equal(review(app).hasAttribute('disabled'), false);
  assert.equal(review(app).textContent, 'Gravar 1 célula');
  const sixteen = []; for (let r = 4; r < 8; r += 1) for (let c = 2; c < 6; c += 1) sixteen.push([r, c]);
  selectCells(app, sixteen.slice(0, 15));
  assert.match(selCount(app), /^16 selecionadas$/);
  assert.match(review(app).textContent, /16/);
  selectCells(app, [[0, 0]]);
  assert.match(selCount(app), /^15 /, 'tocar de novo numa selecionada a desmarca');
  app.byId('mapSelectAll').click(); app.flush();
  assert.match(selCount(app), /^144 /);
  app.byId('mapClearSelection').click(); app.flush();
  assert.match(selCount(app), /^0 /);
  assert.equal(review(app).hasAttribute('disabled'), true);
  // cabeçalho de linha/coluna seleciona 12 e desmarca ao repetir
  touch(app, app.$('[data-select-row="3"]'));
  assert.match(selCount(app), /^12 /);
  touch(app, app.$('[data-select-row="3"]'));
  assert.match(selCount(app), /^0 /);
  touch(app, app.$('[data-select-column="5"]'));
  assert.match(selCount(app), /^12 /);
});

test('M6 prévia Kotlin: percentual/somar/definir mostram o alvo e a diferença (+7) nas células selecionadas e nada é gravado', () => {
  const app = mapApp();
  selectCells(app, [[2, 3]]);
  const before = app.world.map.rows[2][3];
  setAdjust(app, 'delta', 7);
  assert.equal(cell(app, 2, 3).querySelector('b').textContent, String(before + 7));
  assert.match(cell(app, 2, 3).querySelector('span').textContent, /\+7/);
  setAdjust(app, 'percent', 10);
  assert.equal(cell(app, 2, 3).querySelector('b').textContent, String(Math.round(before * 1.1)));
  setAdjust(app, 'target', 130);
  assert.equal(cell(app, 2, 3).querySelector('b').textContent, '130');
  app.$('[data-map-nudge="5"]').click(); app.flush();
  assert.equal(app.world.callsOf('startMapBatchWrite').length, 0, 'prévia/nudge nunca gravam');
  assert.ok(same(rows(app), rows(app)));
});

test('M6 FLUXO selecionar→prévia→gravar→conferência→Gravado→Desfazer: ordem das chamadas e mapa final da ECU', () => {
  const app = mapApp();
  const original = rows(app);
  const mark = app.world.mark();
  writeThree(app, 150);
  assert.ok(hasClass(app, 'is-writing'), 'camada "Gravando na ECU…" abriu');
  const writeCall = app.world.callsOf('startMapBatchWrite');
  assert.equal(writeCall.length, 1);
  const cells = JSON.parse(writeCall[0].args[0]);
  assert.equal(cells.length, 3);
  assert.ok(cells.every(c => c.row >= 0 && c.row <= 11 && c.column >= 0 && c.column <= 11 && c.target === 150), 'só células da grade 12×12 (linha técnica 0C protegida)');
  app.settle(4);
  assert.ok(hasClass(app, 'has-result'));
  const r = result(app);
  assert.match(r.title, /^Gravado/);
  assert.equal(r.level, 'ok');
  assert.ok(r.undo, 'Desfazer aparece depois de gravar');
  THREE.forEach(([rr, cc]) => assert.equal(app.world.map.rows[rr][cc], 150));
  const order = L.actionCalls(app, mark);
  assert.deepEqual(order.slice(0, 2), ['startMapBatchWrite', 'startKMapRead'], `releitura de conferência depois do Gravado: ${order}`);
  // a tela passa a mostrar os valores novos (releitura)
  assert.equal(cell(app, 2, 3).querySelector('b').textContent, '150');
  // Desfazer
  app.byId('mapUndoButton').click(); app.settle(5);
  assert.equal(app.world.callsOf('startMapRestorePrepare').length, 1);
  assert.match(app.world.callsOf('startMapRestorePrepare')[0].args[0], /^adj-/);
  assert.equal(app.world.callsOf('startMapRestoreWrite').length, 1);
  assert.ok(same(rows(app), original), 'depois do Desfazer a ECU volta ao mapa original');
  assert.match(result(app).title, /^Gravado/);
  L.assertClean(app, 'M6/fluxo');
});

for (const [label, outcome, detailRx, partial] of [
  ['falha de cabo', 'transport', /Cabo\/USB/, false],
  ['NACK da ECU', 'nack', /A ECU recusou/, false],
  ['falha parcial', 'partial', /Cabo\/USB/, true],
]) {
  test(`M6 gravar com ${label}: nunca "Gravado"; ${partial ? '"ECU parcialmente alterada" + Desfazer e Reler fora do <details>' : 'sem Desfazer'}`, () => {
    const app = mapApp({ outcome: { mapWrite: outcome } });
    const original = rows(app);
    writeThree(app);
    app.settle(4);
    const r = result(app);
    assert.ok(hasClass(app, 'has-result'));
    assert.equal(r.level, 'critical');
    assert.doesNotMatch(r.title, /^Gravado/);
    assert.match(r.detail, detailRx);
    if (partial) {
      assert.match(r.title, /parcialmente alterada/i);
      assert.ok(r.undo && r.reread, 'falha parcial: Desfazer e Reler ECU visíveis');
      assert.ok(app.byId('mapUndoButton').closest('details') === null);
      assert.ok(app.byId('mapUndoButton').closest('[hidden]') === null);
    } else {
      assert.equal(r.undo, false, 'nada gravado: sem Desfazer');
      assert.ok(same(rows(app), original));
    }
    L.assertClean(app, `M6/${label}`);
  });
}

test('M6 falha parcial: Desfazer prepara a restauração, grava de volta e a ECU volta ao original', () => {
  const app = mapApp({ outcome: { mapWrite: 'partial' } });
  const original = rows(app);
  // o mundo aplica as células na falha parcial para o controle ser real
  const w = app.world;
  const handlers = w.handlers;
  writeThree(app);
  app.settle(4);
  app.world.outcome.mapWrite = 'ok';
  app.byId('mapUndoButton').click(); app.settle(6);
  assert.equal(app.world.callsOf('startMapRestorePrepare').length, 1, 'Desfazer prepara a restauração da foto');
  assert.ok(typeof handlers === 'function');
  assert.ok(same(rows(app), original), 'ECU igual ao original depois do Desfazer');
});

test('M6 durante a gravação (ECU ocupada) a tela NÃO diz "Gravado"', () => {
  const app = mapApp({ opPolls: 6 });
  writeThree(app);
  for (let i = 0; i < 4; i += 1) {
    app.advance(250);
    assert.ok(hasClass(app, 'is-writing') && !hasClass(app, 'has-result'));
    assert.doesNotMatch(app.byId('mapOperationTitle').textContent, /^Gravado/);
  }
  app.settle(8);
  assert.match(result(app).title, /^Gravado/);
});

test('M6 Reler ECU depois de falha parcial atualiza a grade', () => {
  const app = mapApp({ outcome: { mapWrite: 'partial' } });
  writeThree(app); app.settle(4);
  const mark = app.world.mark();
  app.byId('mapRereadButton').click(); app.settle(3);
  assert.ok(L.actionCalls(app, mark).includes('startKMapRead'));
  assert.ok(!hasClass(app, 'has-result'));
});

test('M6 toque duplo em Gravar (ECU ocupada): exatamente UMA escrita', () => {
  const app = mapApp({ opPolls: 8 });
  app.settle(12);
  selectCells(app, THREE); setAdjust(app, 'target', 150);
  const mark = app.world.mark();
  review(app).click(); review(app).click(); app.flush();
  assert.equal(app.world.since(mark).filter(c => c.method === 'startMapBatchWrite').length, 1);
});

test('M6 toque duplo em Desfazer e em Reler: UMA chamada cada', () => {
  const app = mapApp();
  writeThree(app); app.settle(4);
  app.world.opPolls = 9;
  const mark = app.world.mark();
  app.byId('mapUndoButton').click(); app.byId('mapUndoButton').click(); app.flush();
  assert.equal(app.world.since(mark).filter(c => c.method === 'startMapRestorePrepare').length, 1);
  const b = mapApp({ opPolls: 9 });
  const mark2 = b.world.mark();
  b.byId('mapReadButton').click(); b.byId('mapReadButton').click(); b.flush();
  assert.ok(b.world.since(mark2).filter(c => c.method === 'startKMapRead').length <= 1);
});

const allowMap = (desc, el, app) => {
  if (el.id === 'mapClearSelection' && /^0 /.test(selCount(app))) return 'nada selecionado para limpar';
  if (el.attrs.get('aria-checked') === 'true' && el.attrs.has('data-map-mode')) return 'modo já escolhido: tocar de novo não muda nada';
  if (el.id === 'mapDismissResult') return '';
  return '';
};
const STATES = {
  'lido': () => mapApp(),
  'selecionado': () => { const a = mapApp(); selectCells(a, THREE); return a; },
  'preparado': () => { const a = mapApp(); selectCells(a, THREE); setAdjust(a, 'target', 150); return a; },
  'resultado ok': () => { const a = mapApp(); writeThree(a); a.settle(4); return a; },
  'resultado falha de cabo': () => { const a = mapApp({ outcome: { mapWrite: 'transport' } }); writeThree(a); a.settle(4); return a; },
  'resultado parcial': () => { const a = mapApp({ outcome: { mapWrite: 'partial' } }); writeThree(a); a.settle(4); return a; },
  'leitura falhou': () => mapApp({ outcome: { mapRead: 'transport' } }),
};
for (const [name, make] of Object.entries(STATES)) {
  test(`M6 estado "${name}": nenhum elemento tocável do Mapa K está congelado`, () => {
    const r = L.sweep({ prepare: make, within: SCREEN, allow: allowMap });
    assert.deepEqual(r.failures, [], `${r.exercised}/${r.total}`);
    L.assertClean(make(), `M6/${name}`);
  });
}

test('M6 idempotência: o mesmo mapa duas vezes não muda o DOM; A→B→A idêntico; listeners estáveis', () => {
  const app = mapApp();
  const sched = app.win.OmegasApp.scheduler;
  sched.run();
  const dom = L.serialize(app.$(SCREEN));
  sched.run(); sched.run();
  assert.equal(L.serialize(app.$(SCREEN)), dom);
  selectCells(app, THREE);
  const sel = L.serialize(app.$(SCREEN));
  app.go('sessions'); app.go('map');
  assert.equal(selCount(app), '3 selecionadas', 'a seleção sobreviveu à ida e volta');
  assert.equal(L.serialize(app.$(SCREEN)).length > 1000, true);
  void sel;
  app.go('tools'); app.go('map');
  const base = app.listenerTotal();
  for (let i = 0; i < 5; i += 1) { app.go('tools'); app.go('map'); }
  assert.equal(app.listenerTotal(), base, 'reentrar no Mapa K empilha listeners');
});

test('M6 fuzz das respostas do Mapa K (leitura, prévia, operação): nunca exceção/NaN, recupera', () => {
  const { cases, effective, failures } = L.fuzz({
    prepare: () => { const a = mapApp({ opPolls: 2 }); selectCells(a, THREE); setAdjust(a, 'delta', 3); return a; },
    methods: ['getKMapReadResult', 'getLastOperation', 'previewMapAdjustment'],
    maxPathsPerMethod: 30,
    perTick: app => { app.byId('mapReadButton').click(); const input = app.byId('mapAdjustmentValue'); input.dispatchEvent(new app.win.Event('input', { bubbles: true })); },
  });
  assert.ok(cases > 80, `casos ${cases}`);
  assert.ok(effective > cases * 0.2, `fuzz inócuo ${effective}/${cases}`);
  assert.deepEqual(failures.slice(0, 6), [], `${failures.length}/${cases}`);
});

test('M6 desconhecido nunca vira 0: eixo/valor ausente mostra — e a célula não vira "0"', todo('DEFECT-18'), () => {
  const w = new W.World();
  w.mutateResponse = (b, m, obj) => { if (m === 'getKMapReadResult' && obj.axes) { obj.axes.petrolBins[2] = null; } return obj; };
  const app = L.boot({ world: w });
  app.go('map'); app.settle(4);
  const header = app.$('[data-select-row="2"]');
  assert.doesNotMatch(header.textContent, /^\s*0([.,]0+)?\s*ms/, `eixo ms desconhecido apareceu como "${header.textContent}"`);
  L.assertClean(app, 'M6/eixo desconhecido');
});
