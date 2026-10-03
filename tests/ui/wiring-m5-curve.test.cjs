'use strict';
// M5 · Curva K: ler / editar / salvar foto / gravar OK / falha de cabo / NACK da ECU / falha parcial / reset / desfazer / restaurar.
// O mundo fake mantém a curva da ECU: o teste confere o ESTADO FINAL da ECU, não só o texto da tela.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const W = require('./wiring/world.cjs');

const SCREEN = '[data-screen="curve"]';
const WRITES = ['startCurveBatchWrite', 'startCurveRestoreWrite', 'startCurveReset'];

const { curveApp, editPoint, tapReview } = require('./wiring/scenarios.cjs');
const { todo } = require('./wiring/registry.cjs');
const result = app => ({
  level: app.byId('curveOperationResult').dataset.level,
  title: app.byId('curveOperationResult').querySelector('b').textContent,
  detail: app.byId('curveOperationResult').querySelector('span').textContent,
  undoVisible: !app.byId('curveUndoButton').hasAttribute('hidden'),
});
const screen = app => app.$(SCREEN);
const hasClass = (app, c) => screen(app)._classes().has(c);
const raws = app => app.world.curve.slice();
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

test('M5 ler: a curva da ECU chega à tela (30 pontos, fonte confirmada, gráfico sem NaN)', () => {
  const app = curveApp();
  L.assertClean(app, 'M5/ler');
  assert.match(app.byId('curveSourceStatus').textContent, /confirmada.*30/i);
  assert.equal(app.$$('circle[data-curve-index]').length, 30, '30 pontos tocáveis no gráfico');
  assert.equal(app.world.callsOf('startCurveRead').length, 1, 'uma leitura ao abrir a aba');
  assert.equal(app.byId('curveReviewButton').hasAttribute('disabled'), true, 'sem pontos preparados não se grava');
});

test('M5 ler com falha de cabo: a tela diz que não confirmou e se recupera ao reler', () => {
  const app = curveApp({ outcome: { curveRead: 'transport' } });
  assert.match(app.byId('curveSourceStatus').textContent, /n[aã]o confirmada/i);
  assert.equal(app.$$('circle[data-curve-index]').length, 0, 'sem dado da ECU não há pontos para editar');
  app.world.outcome.curveRead = 'ok';
  app.byId('curveReadButton').click(); app.settle(3);
  assert.equal(app.$$('circle[data-curve-index]').length, 30);
  L.assertClean(app, 'M5/ler-falha');
});

test('M5 editar: tocar ponto + nudge prepara o ponto, mostra antes→depois e libera Gravar; Limpar volta ao vazio', () => {
  const app = curveApp();
  editPoint(app, 9);
  assert.match(app.byId('curveReviewButton').textContent, /Gravar 1 ponto/);
  assert.equal(app.byId('curveReviewButton').hasAttribute('disabled'), false);
  assert.match(app.byId('curveProposalList').textContent, /→/);
  assert.equal(app.world.callsOf('startCurveBatchWrite').length, 0, 'preparar não grava');
  app.byId('curveClearProposals').click(); app.flush();
  assert.equal(app.byId('curveReviewButton').hasAttribute('disabled'), true);
});

test('M5 FLUXO leitura→edição→gravação→conferência→Gravado→Desfazer: ordem das chamadas e curva final da ECU', () => {
  const app = curveApp();
  const original = raws(app);
  editPoint(app, 9);
  const mark = app.world.mark();
  tapReview(app);
  assert.ok(hasClass(app, 'is-writing'), 'ao tocar em Gravar a camada "Gravando na ECU…" abre');
  assert.equal(L.actionCalls(app, mark).join('>'), 'startCurveBatchWrite');
  const sent = JSON.parse(app.world.callsOf('startCurveBatchWrite')[0].args[0]);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].index, 9);
  assert.equal(sent[0].currentRaw, original[9]);
  assert.ok(sent[0].targetRaw > original[9]);
  app.settle(4);
  assert.ok(hasClass(app, 'has-result') && !hasClass(app, 'is-writing'));
  const r = result(app);
  assert.match(r.title, /^Gravado/);
  assert.equal(r.level, 'ok');
  assert.ok(r.undoVisible, 'Desfazer precisa aparecer depois de gravar');
  assert.equal(raws(app)[9], sent[0].targetRaw, 'a ECU recebeu o novo valor');
  // conferência: o app relê a curva depois do Gravado
  const after = L.actionCalls(app, mark);
  assert.deepEqual(after.slice(0, 2), ['startCurveBatchWrite', 'startCurveRead'], `ordem: ${after.join('>')}`);
  // Desfazer: restaura a foto desta operação, grava de volta e a ECU volta ao original
  app.byId('curveUndoButton').click(); app.settle(3);
  const photo = app.world.callsOf('startCurveRestorePrepare').pop();
  assert.ok(photo, 'Desfazer precisa preparar a restauração da foto');
  assert.match(photo.args[0], /^foto-/, 'restaura a foto DESTA gravação');
  const restore = app.byId('curveBackupRestore');
  assert.equal(restore.hasAttribute('disabled'), false, 'prévia de restauração pronta');
  restore.click(); app.settle(4);
  assert.equal(app.world.callsOf('startCurveRestoreWrite').length, 1);
  assert.ok(same(raws(app), original), 'depois do Desfazer a ECU volta exatamente à curva original');
  assert.match(result(app).title, /^Gravado/);
  L.assertClean(app, 'M5/fluxo');
});

const FAILS = [
  ['falha de cabo', 'transport', /Cabo\/USB/, false],
  ['NACK da ECU', 'nack', /A ECU recusou/, false],
  ['readback não confirmou', 'readback', /readback/i, false],
  ['falha parcial', 'partial', /Cabo\/USB/, true],
];
for (const [label, outcome, detailRx, partial] of FAILS) {
  test(`M5 gravar com ${label}: nunca diz "Gravado"; ${partial ? 'diz "ECU parcialmente alterada" e mostra Desfazer fora do <details>' : 'não oferece Desfazer'}`, () => {
    const app = curveApp({ outcome: { curveWrite: outcome } });
    const original = raws(app);
    editPoint(app, 9);
    tapReview(app);
    app.settle(4);
    const r = result(app);
    assert.ok(hasClass(app, 'has-result'), 'o resultado aparece');
    assert.equal(r.level, 'critical');
    assert.doesNotMatch(r.title, /^Gravado/, `"${r.title}" não pode soar como sucesso`);
    assert.match(r.detail, detailRx, `detalhe: ${r.detail}`);
    if (partial) {
      assert.match(r.title, /parcialmente alterada/i);
      assert.ok(r.undoVisible, 'falha parcial exige Desfazer visível');
      assert.equal(app.byId('curveUndoButton').closest('details'), null, 'Desfazer dentro de <details>');
      assert.equal(app.byId('curveUndoButton').closest('[hidden]'), null);
      assert.ok(!same(raws(app), original), 'controle: a ECU realmente foi alterada em parte');
      // Desfazer volta à foto de antes
      app.byId('curveUndoButton').click(); app.settle(3);
      app.byId('curveBackupRestore').click(); app.settle(4);
      assert.ok(same(raws(app), original), 'Desfazer após falha parcial restaura a curva original');
    } else {
      assert.equal(r.undoVisible, false, 'nada foi alterado: Desfazer não deve aparecer');
      assert.ok(same(raws(app), original), 'a ECU ficou intacta');
    }
    L.assertClean(app, `M5/${label}`);
  });
}

test('M5 durante a gravação (ECU ainda ocupada) a tela NÃO mostra "Gravado"', () => {
  const app = curveApp({ opPolls: 6 });
  editPoint(app, 4);
  tapReview(app);
  for (let i = 0; i < 4; i += 1) {
    app.advance(250);
    assert.ok(hasClass(app, 'is-writing') && !hasClass(app, 'has-result'), `tick ${i}: ainda gravando`);
    assert.doesNotMatch(app.byId('curveOperationTitle').textContent, /^Gravado/);
  }
  app.settle(6);
  assert.match(result(app).title, /^Gravado/);
});

test('M5 salvar foto mostra o caminho/hash do arquivo', todo('DEFECT-15'), () => {
  const app = curveApp();
  app.byId('curveBackupSave').click(); app.settle(3);
  assert.match(app.byId('curveBackupStatus').textContent, /Omegas|abcdef12/, 'o dono não vê onde a foto foi salva');
});

test('M5 salvar foto: UMA chamada, lista de backups atualizada; falha vira alerta e nada some', () => {
  const app = curveApp();
  const mark = app.world.mark();
  app.byId('curveBackupSave').click(); app.settle(3);
  assert.equal(L.actionCalls(app, mark).join('>'), 'startCurveBackup');
  assert.equal(app.byId('curveBackupSelect').options.length, 2, 'backup novo aparece na lista (+ placeholder)');
  // falha
  const bad = curveApp({ outcome: { curveBackup: 'transport' } });
  bad.byId('curveBackupSave').click(); bad.settle(3);
  assert.match(bad.state().alert.message, /Cabo\/USB/);
  assert.equal(bad.byId('curveBackupSelect').options.length, 1);
  assert.equal(bad.$$('circle[data-curve-index]').length, 30, 'a curva continua na tela');
});

test('M5 RESET: foto antes, só depois zera; a ECU fica em 1.0; Desfazer volta à curva de antes', () => {
  const app = curveApp();
  const original = raws(app);
  const mark = app.world.mark();
  app.byId('curveResetButton').click(); app.flush();
  assert.deepEqual(L.actionCalls(app, mark), ['startCurveBackup'], 'no toque só a foto; nada zerado ainda');
  assert.ok(same(raws(app), original));
  app.settle(5);
  const order = L.actionCalls(app, mark);
  assert.equal(order.indexOf('startCurveBackup'), 0);
  assert.ok(order.indexOf('startCurveReset') > 0, `reset só depois da foto: ${order}`);
  assert.equal(order.filter(m => m === 'startCurveReset').length, 1);
  assert.ok(raws(app).every(v => v === W.Q), 'ECU em 1.0');
  assert.match(result(app).title, /^Gravado/);
  assert.ok(result(app).undoVisible);
  app.byId('curveUndoButton').click(); app.settle(3);
  assert.match(app.world.callsOf('startCurveRestorePrepare').pop().args[0], /^curva-/, 'o Desfazer do reset restaura a FOTO tirada antes dele');
  app.byId('curveBackupRestore').click(); app.settle(4);
  assert.ok(same(raws(app), original), 'depois do Desfazer a curva é a de antes do reset');
});

test('M5 RESET com falha na foto: NADA é zerado e o dono é avisado', () => {
  const app = curveApp({ outcome: { curveBackup: 'transport' } });
  const original = raws(app);
  app.byId('curveResetButton').click(); app.settle(5);
  assert.equal(app.world.callsOf('startCurveReset').length, 0, 'zerou sem foto');
  assert.ok(same(raws(app), original));
  assert.ok(app.state().alert && /foto|nada foi zerado|Cabo/i.test(app.state().alert.message), 'sem aviso ao dono');
});

test('M5 RESET com a curva já em 1.0: não faz foto nem zera (diz que não há o que zerar)', () => {
  const app = curveApp({ raws: W.neutralRaws() });
  const mark = app.world.mark();
  app.byId('curveResetButton').click(); app.settle(3);
  assert.deepEqual(L.actionCalls(app, mark), []);
  assert.match(app.byId('curveBackupStatus').textContent, /1\.0|nada a zerar/i);
});

test('M5 RESET com falha de gravação parcial: "ECU parcialmente alterada" e Desfazer volta à foto', () => {
  const app = curveApp({ outcome: { curveReset: 'partial' } });
  const original = raws(app);
  app.byId('curveResetButton').click(); app.settle(6);
  const r = result(app);
  assert.match(r.title, /parcialmente alterada/i);
  assert.ok(r.undoVisible && !app.byId('curveUndoButton').closest('details'));
  app.byId('curveUndoButton').click(); app.settle(3);
  app.byId('curveBackupRestore').click(); app.settle(4);
  assert.ok(same(raws(app), original));
});

test('M5 restaurar backup da lista: prévia, botão só ativa depois, uma gravação, ECU = backup', () => {
  const app = curveApp();
  const original = raws(app);
  app.byId('curveBackupSave').click(); app.settle(3);
  // ECU muda depois da foto
  editPoint(app, 15); tapReview(app); app.settle(4);
  app.byId('curveDismissResult').click();
  assert.ok(!same(raws(app), original));
  const select = app.byId('curveBackupSelect');
  const manual = select.options.find(o => /curva-/.test(o.attrs.get('value') || ''));
  assert.ok(manual, 'backup manual listado');
  assert.equal(app.byId('curveBackupRestore').hasAttribute('disabled'), true, 'restaurar bloqueado antes de escolher');
  select.value = manual.attrs.get('value');
  select.dispatchEvent(new app.win.Event('change', { bubbles: true }));
  app.settle(3);
  assert.equal(app.byId('curveBackupRestore').hasAttribute('disabled'), false, 'prévia pronta libera Restaurar');
  assert.equal(app.world.callsOf('startCurveRestoreWrite').length, 0, 'prévia não grava');
  app.byId('curveBackupRestore').click(); app.settle(4);
  assert.equal(app.world.callsOf('startCurveRestoreWrite').length, 1);
  assert.ok(same(raws(app), original));
});

// ---------------------------------------------------------------- toque duplo
for (const [name, act, call] of [
  ['Gravar', app => { editPoint(app, 9); app.byId('curveReviewButton').click(); app.byId('curveReviewButton').click(); }, 'startCurveBatchWrite'],
  ['Resetar', app => { app.byId('curveResetButton').click(); app.byId('curveResetButton').click(); app.advance(300); app.advance(300); }, 'startCurveBackup'],
  ['Salvar foto', app => { app.byId('curveBackupSave').click(); app.byId('curveBackupSave').click(); }, 'startCurveBackup'],
  ['Reler ECU', app => { app.byId('curveReadButton').click(); app.byId('curveReadButton').click(); }, 'startCurveRead'],
]) {
  test(`M5 toque duplo em ${name} com a ECU ocupada: exatamente UMA chamada de ponte (${call})`, name === 'Gravar' ? todo('DEFECT-14') : {}, () => {
    const app = curveApp({ opPolls: 8 });
    // a leitura inicial ainda pode estar ocupada; assenta para que só o toque duplo conte
    app.world.opPolls = 8;
    app.settle(12);
    const mark = app.world.mark();
    act(app);
    app.flush();
    const n = app.world.since(mark).filter(c => c.method === call).length;
    assert.equal(n, 1, `${name}: ${n} chamadas de ${call} em 2 toques`);
  });
}

test('M5 toque duplo em Desfazer (prévia ainda ocupada): UMA preparação', () => {
  const app = curveApp();
  editPoint(app, 9); tapReview(app); app.settle(4);
  app.world.opPolls = 8;
  const mark = app.world.mark();
  app.byId('curveUndoButton').click();
  app.byId('curveUndoButton').click();
  assert.equal(app.world.since(mark).filter(c => c.method === 'startCurveRestorePrepare').length, 1);
});

// ---------------------------------------------------------------- varredura e fuzz
const allowCurve = (desc, el, app) => {
  if (/\bactive\b/.test(el.attrs.get('class') || '') && el.attrs.has('data-curve-view')) return 'aba já ativa';
  if (el.localName === 'select' && el.options.length <= 1) return 'sem backups: só há o placeholder';
  if (el.id === 'curveClearProposals' && /Nenhum/.test(app.byId('curveProposalList').textContent)) return 'nada preparado para limpar';
  if (el.hasAttribute('data-curve-index') && el.dataset.curveIndex === '0') return 'ponto 0 já está selecionado após a leitura';
  if (el.localName === 'summary') return '';
  return '';
};
const SWEEP_STATES = {
  'lida': () => curveApp(),
  'editando': () => { const a = curveApp(); editPoint(a, 9); return a; },
  'resultado ok': () => { const a = curveApp(); editPoint(a, 9); tapReview(a); a.settle(4); return a; },
  'resultado parcial': () => { const a = curveApp({ outcome: { curveWrite: 'partial' } }); editPoint(a, 9); tapReview(a); a.settle(4); return a; },
  'resultado falha de cabo': () => { const a = curveApp({ outcome: { curveWrite: 'transport' } }); editPoint(a, 9); tapReview(a); a.settle(4); return a; },
  'leitura falhou': () => curveApp({ outcome: { curveRead: 'nack' } }),
  'com backup': () => { const a = curveApp(); a.byId('curveBackupSave').click(); a.settle(3); return a; },
};
for (const [name, make] of Object.entries(SWEEP_STATES)) {
  test(`M5 estado "${name}": nenhum elemento tocável da Curva K está congelado`, () => {
    const r = L.sweep({ prepare: make, within: SCREEN, allow: allowCurve });
    assert.deepEqual(r.failures, [], `${r.exercised}/${r.total} (defeitos conhecidos mascarados: ${r.knownHits.length})`);
    L.assertClean(make(), `M5/${name}`);
  });
}

test('M5 idempotência: a mesma leitura duas vezes não redesenha o gráfico; A→B→A restaura o mesmo estado; sem listeners empilhados', () => {
  const app = curveApp();
  const sched = app.win.OmegasApp.scheduler;
  sched.run();
  const dom = L.serialize(screen(app));
  const writes = app.chartWrites('curveChart');
  sched.run(); sched.run();
  assert.equal(L.serialize(screen(app)), dom);
  assert.equal(app.chartWrites('curveChart'), writes, 'gráfico da Curva K redesenhado sem dado novo');
  // A→B→A
  const before = L.serialize(screen(app));
  app.go('sessions'); app.go('curve');
  assert.equal(L.serialize(screen(app)), before, 'voltar à Curva K muda o que estava na tela');
  // listeners estáveis em reentradas repetidas
  app.go('sessions'); app.go('curve');
  const baseline = app.listenerTotal();
  for (let i = 0; i < 5; i += 1) { app.go('tools'); app.go('curve'); }
  assert.equal(app.listenerTotal(), baseline, 'reentrar na aba empilha listeners');
});

test('M5 fuzz das respostas da Curva K (leitura, prévia, operação, backups): nunca exceção/NaN, recupera', () => {
  const { cases, effective, failures } = L.fuzz({
    prepare: () => { const a = curveApp({ opPolls: 2 }); editPoint(a, 9); a.byId('curveBackupSave').click(); a.settle(3); return a; },
    methods: ['getLastOperation', 'previewKFactorPoint', 'listCurveBackups'],
    maxPathsPerMethod: 40,
    // cada tick: nova leitura + novo nudge, para o app consultar as três respostas durante a mutação
    perTick: app => { app.byId('curveReadButton').click(); const n = app.$('[data-curve-nudge="0.01"]'); if (n) n.click(); app.byId('curveBackupSave').click(); },
  });
  assert.ok(cases > 100, `casos ${cases}`);
  assert.ok(effective > cases * 0.2, `fuzz inócuo ${effective}/${cases}`);
  assert.deepEqual(failures.slice(0, 6), [], `${failures.length}/${cases}`);
});

test('M5 desconhecido nunca vira 0: fator ausente de um ponto mostra — (nem "0,0000")', todo('DEFECT-16'), () => {
  const w = new W.World();
  const base = W.curvePoints(W.bentRaws());
  const app = L.boot({ world: w });
  w.lastOperation = { ok: true, state: 'COMPLETED', busy: false, points: base.map((p, i) => (i === 3 ? { ...p, factor: null, factorRaw: null } : p)), pointCount: 30 };
  w.curve = W.bentRaws();
  w.outcome.curveRead = 'ok';
  // injeta a resposta com fator nulo no ponto 3
  w.mutateResponse = (b, m, obj) => { if (m === 'getLastOperation' && Array.isArray(obj.points)) obj.points[3].factor = null; return obj; };
  app.go('curve'); app.settle(4);
  app.$('circle[data-curve-index="3"]').click(); app.flush();
  assert.doesNotMatch(app.byId('curveCurrentFactor').textContent, /^0([,.]0+)?$/, `fator desconhecido apareceu como "${app.byId('curveCurrentFactor').textContent}"`);
});
