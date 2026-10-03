'use strict';
// M4 · Refino: toda fase do piloto × com/sem proposta × proposta vencida.
// Regras de PRODUTO (independentes do código): nada grava sozinho; enquanto a ECU faz o automático ou
// o app mede a última gravação não se oferece gravar; "Revisar e gravar" só existe com proposta;
// só há UMA ação principal; o Desfazer fica visível FORA do <details>.
const test = require('node:test');
const L = require('./lib.cjs');
const { assert } = L;
const { todo } = require('./registry.cjs');
const W = require('./world.cjs');

const D9 = todo('DEFECT-9');
const WRITE_CALLS = ['startCurveBatchWrite', 'startCurveRestoreWrite', 'startCurveReset', 'startMapBatchWrite'];

/** O que o dono PODE fazer em cada fase: 'enabled' (botão ativo), 'blocked' (sem botão ou desativado). */
function expected(phase, hasProposal, expiredFrom) {
  switch (phase) {
    case 'SEM_ECU': case 'LENDO_ECU': case 'ECU_TRABALHANDO': case 'VERIFICANDO': case 'ESTAVEL': return 'blocked';
    case 'COLETANDO_NOSSOS': case 'PROPOSTA_PRONTA': return hasProposal ? 'enabled' : 'blocked';
    case 'RESTAURAR_TRECHO': return 'enabled';
    case 'TENTATIVA_ENCERRADA': return (expiredFrom === 'PROPOSTA_PRONTA' || expiredFrom === 'ECU_TRABALHANDO') && hasProposal ? 'enabled' : 'blocked';
    default: throw new Error(phase);
  }
}

function prepared(phase, { proposal = false, expiredFrom, latest } = {}) {
  const w = new W.World();
  w.setFrame(W.realFrames('automatch_', f => f.fuel === 'GNV' && f.petrol_ms > 2)[10]);
  w.equivalence = W.equivalenceFor(phase, { expiredFrom, latest });
  w.refined = W.refinedAnalysis(proposal, w.curve);
  w.projection = W.realProjection('automatch_', 11);
  const app = L.boot({ world: w });
  app.go('refino');
  app.settle(4);
  return app;
}
const primary = app => app.$('[data-refino-primary]');
const isEnabled = el => !!el && !el.hasAttribute('hidden') && !el.hasAttribute('disabled');

const CASES = [];
for (const phase of W.PHASES) {
  for (const proposal of [false, true]) {
    if (phase === 'TENTATIVA_ENCERRADA') {
      for (const expiredFrom of ['PROPOSTA_PRONTA', 'ECU_TRABALHANDO', 'COLETANDO_NOSSOS']) CASES.push({ phase, proposal, expiredFrom, name: `${phase}(${expiredFrom}) ${proposal ? 'com' : 'sem'} proposta` });
    } else CASES.push({ phase, proposal, name: `${phase} ${proposal ? 'com' : 'sem'} proposta` });
  }
}

for (const c of CASES) {
  test(`M4 ${c.name}: renderiza sem exceção, ação principal conforme a fase, nada grava sozinho`, D9, () => {
    const app = prepared(c.phase, c);
    L.assertClean(app, `M4/${c.name}`);
    const want = expected(c.phase, c.proposal, c.expiredFrom);
    const btn = primary(app);
    assert.ok(btn, 'botão principal do Refino ausente do DOM');
    assert.equal(isEnabled(btn), want === 'enabled', `${c.name}: botão principal ${isEnabled(btn) ? 'ATIVO' : 'bloqueado'} (esperado ${want}); texto="${btn.textContent}"`);
    if (btn.hasAttribute('disabled')) assert.ok(btn.textContent.trim(), 'botão desativado sem motivo visível');
    assert.equal(app.world.calls.filter(x => WRITE_CALLS.includes(x.method)).length, 0, 'o app gravou/zerou sem toque do dono');
    // um único "próximo passo": no máximo um botão principal ativo na tela inteira
    const live = app.$$('[data-refino-primary]').filter(isEnabled);
    assert.ok(live.length <= 1);
    if (want === 'enabled') {
      btn.click(); app.flush();
      const review = app.byId('refinoReview');
      assert.ok(review && !review.hasAttribute('hidden'), 'tocar em Revisar não abriu a revisão antes da ECU');
      assert.ok(review.querySelector('[data-refino-confirm]'), 'a revisão precisa do botão de confirmar');
      assert.equal(app.world.calls.filter(x => WRITE_CALLS.includes(x.method)).length, 0, 'abrir a revisão já gravou');
    }
    app.destroy();
  });
}

test('M4 fases do passo-a-passo: no máximo uma etapa ativa e nenhuma NaN/undefined', D9, () => {
  for (const phase of W.PHASES) {
    const app = prepared(phase, { proposal: true, expiredFrom: 'PROPOSTA_PRONTA' });
    const active = app.$$('#refinoSteps [data-state="active"]');
    assert.ok(active.length <= 1, `${phase}: ${active.length} etapas ativas`);
    L.assertClean(app, `M4/passos ${phase}`);
    app.destroy();
  }
});

test('M4 Desfazer sempre à vista (fora de <details>) quando há foto de antes; some quando não há', D9, () => {
  const withPhoto = prepared('ESTAVEL', { latest: { status: 'VERIFICADO', photoFile: 'foto-9.json', beforeRaw: W.bentRaws(), afterRaw: W.bentRaws().map(v => v + 10), bands: [] } });
  const undo = withPhoto.$('[data-refino-undo]');
  assert.ok(undo, 'sem botão Desfazer apesar da foto de antes');
  assert.equal(undo.closest('details'), null, 'Desfazer dentro de <details> recolhido');
  assert.ok(isEnabled(undo));
  const without = prepared('ESTAVEL', { latest: null });
  const none = without.$('[data-refino-undo]');
  assert.ok(!none || none.closest('[hidden]'), 'Desfazer visível sem nada para desfazer');
});

test('M4 fase falha parcial no journal: texto "ECU pode ter sido alterada em parte" + Desfazer visível', D9, () => {
  const latest = { status: 'FALHA_PARCIAL', photoFile: 'foto-3.json', beforeRaw: W.bentRaws(), afterRaw: W.bentRaws().map((v, i) => (i < 4 ? v + 50 : v)), bands: [] };
  const app = prepared('COLETANDO_NOSSOS', { proposal: true, latest });
  const details = app.byId('refinoJournal');
  assert.match(details.textContent, /alterad[ao]/i);
  const undo = app.$('[data-refino-undo]');
  assert.ok(undo && !undo.closest('details') && !undo.closest('[hidden]'));
});

test('M4 idempotência: o mesmo dado duas vezes não redesenha o gráfico nem muda o DOM', D9, () => {
  const app = prepared('PROPOSTA_PRONTA', { proposal: true });
  const sched = app.win.OmegasApp.scheduler;
  sched.run();
  const dom = L.serialize(app.doc.body);
  const writes = app.chartWrites('refinoChart');
  sched.run(); sched.run(); sched.run();
  assert.equal(L.serialize(app.doc.body), dom);
  assert.equal(app.chartWrites('refinoChart'), writes, 'gráfico do Refino redesenhado sem dado novo');
});

test('M4 sem ECU/sem pontos: gráfico mostra estado vazio, nunca coordenadas NaN', D9, () => {
  const w = new W.World();
  w.equivalence = W.equivalenceFor('SEM_ECU');
  w.equivalence.denseBands = { petrol: [], gas: [] };
  w.projection = { ok: true, source: 'NONE', snapshot: { available: false, fields: [] }, nativeStatus: {}, nativeSnapshot: { available: false }, manualStatus: {}, manualSnapshot: { available: false } };
  const app = L.boot({ world: w });
  app.go('refino'); app.settle(4);
  L.assertClean(app, 'M4/vazio');
});

for (const [phase, proposal] of [['PROPOSTA_PRONTA', true], ['RESTAURAR_TRECHO', false], ['ESTAVEL', false], ['ECU_TRABALHANDO', true]]) {
  test(`M4 ${phase}${proposal ? ' com proposta' : ''}: nenhum elemento tocável do Refino está congelado`, D9, () => {
    const r = L.sweep({ prepare: () => prepared(phase, { proposal }), within: '[data-screen="refino"]' });
    assert.deepEqual(r.failures, [], `${r.exercised}/${r.total}`);
  });
}

test('M4 fuzz de equivalência/análise refinada/projeção: nunca exceção/NaN, recupera', D9, () => {
  const { cases, effective, failures } = L.fuzz({
    prepare: () => prepared('PROPOSTA_PRONTA', { proposal: true }),
    methods: ['getEquivalence', 'getRefinedAnalysis', 'getUiProjection'],
    maxPathsPerMethod: 60,
  });
  assert.ok(effective > cases * 0.2, `fuzz inócuo ${effective}/${cases}`);
  assert.ok(cases > 300, `casos ${cases}`);
  assert.deepEqual(failures.slice(0, 6), [], `${failures.length}/${cases}`);
});
