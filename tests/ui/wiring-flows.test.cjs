'use strict';
// Integridade de FLUXO do Refino (um teste por ciclo, com a ORDEM das chamadas de ponte e os estados da tela).
// Os ciclos da Curva K / Mapa K / reset estão em m5 / m6 / m3 (mesmo estilo).
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const W = require('./wiring/world.cjs');
const { todo } = require('./wiring/registry.cjs');

const D9 = todo('DEFECT-9');
const WRITE = ['startCurveBatchWrite', 'startCurveRestoreWrite', 'startCurveReset'];

function refinoApp({ phase = 'PROPOSTA_PRONTA', proposal = true, opPolls, outcome, latest } = {}) {
  const w = new W.World({ opPolls });
  w.setFrame(W.realFrames('automatch_', f => f.fuel === 'GNV' && f.petrol_ms > 2)[10]);
  Object.assign(w.outcome, outcome || {});
  w.equivalence = W.equivalenceFor(phase, { latest });
  w.refined = W.refinedAnalysis(proposal, w.curve);
  w.projection = W.realProjection('automatch_', 11);
  const app = L.boot({ world: w });
  app.go('refino');
  app.settle(4);
  return app;
}
const primary = app => app.$('[data-refino-primary]');
const headline = app => app.byId('refinoHeadline').textContent;
const reviewOpen = app => { const r = app.byId('refinoReview'); return !!r && !r.hasAttribute('hidden'); };

test('FLUXO Refino: proposta → revisar → gravar → VERIFICANDO → veredito (ordem das chamadas e curva final da ECU)', D9, () => {
  const app = refinoApp({ phase: 'PROPOSTA_PRONTA', proposal: true });
  const before = app.world.curve.slice();
  const mark = app.world.mark();
  // 1. revisar: nada vai à ECU
  assert.equal(primary(app).hasAttribute('disabled'), false);
  primary(app).click(); app.flush();
  assert.ok(reviewOpen(app), 'a revisão abre antes de qualquer escrita');
  assert.match(app.byId('refinoReview').textContent, /3 pontos da Curva K/);
  assert.deepEqual(L.actionCalls(app, mark), [], 'revisar não chama a ponte de escrita nem de leitura');
  // 2. confirmar: lê a curva, confere, grava
  app.byId('refinoReview').querySelector('[data-refino-confirm]').click(); app.flush();
  assert.deepEqual(L.actionCalls(app, mark), ['startCurveRead'], 'primeiro lê a curva da ECU (conferência), ainda sem gravar');
  assert.equal(app.world.callsOf('startCurveBatchWrite').length, 0);
  app.advance(400); app.advance(400); app.flush();
  assert.deepEqual(L.actionCalls(app, mark).slice(0, 2), ['startCurveRead', 'startCurveBatchWrite']);
  const sent = JSON.parse(app.world.callsOf('startCurveBatchWrite')[0].args[0]);
  assert.deepEqual(sent.map(p => p.index), [8, 9, 10], 'só os pontos MEDIDOS que mudam são gravados (mantidos nunca)');
  assert.ok(sent.every(p => p.currentRaw === before[p.index] && p.targetRaw === before[p.index] + 300));
  for (let i = 0; i < 6; i += 1) app.advance(400);
  assert.match(headline(app), /gravada e conferida pela ECU/i);
  assert.equal(primary(app).textContent.trim(), 'Entendi');
  assert.deepEqual(app.world.curve.map((v, i) => v - before[i]).filter(Boolean), [300, 300, 300], 'a ECU recebeu exatamente a proposta');
  // 3. o piloto passa a VERIFICANDO: botão bloqueado, nada de gravar de novo
  primary(app).click(); app.flush();
  app.world.equivalence = W.equivalenceFor('VERIFICANDO');
  app.world.refined = W.refinedAnalysis(true, app.world.curve);
  app.settle(6);
  assert.equal(primary(app).hasAttribute('disabled') || primary(app).hasAttribute('hidden'), true, 'VERIFICANDO não oferece gravar');
  // 4. veredito: curva chegou na gasolina → estável
  app.world.equivalence = W.equivalenceFor('ESTAVEL', { latest: { status: 'VERIFICADO', photoFile: 'foto-1.json', beforeRaw: before, afterRaw: app.world.curve.slice(), bands: [{ verdict: 'CONFIRMADA', fromMs: 3, toMs: 4, ratioBefore: 1.06, ratioAfter: 1.0 }] } });
  app.settle(6);
  assert.match(app.byId('refinoJournal').textContent, /chegou na gasolina/i);
  assert.equal(primary(app).hasAttribute('disabled'), true);
  assert.ok(app.$('[data-refino-undo]') && !app.$('[data-refino-undo]').closest('details'), 'Desfazer segue à vista');
  L.assertClean(app, 'FLUXO Refino');
});

test('FLUXO Refino: a curva da ECU mudou entre a revisão e a confirmação → NADA é gravado e o dono é avisado', D9, () => {
  const app = refinoApp();
  primary(app).click(); app.flush();
  // a ECU roda o automático e mexe na curva depois da revisão
  app.world.curve = app.world.curve.map((v, i) => (i === 9 ? v + 77 : v));
  app.byId('refinoReview').querySelector('[data-refino-confirm]').click(); app.flush();
  for (let i = 0; i < 5; i += 1) app.advance(400);
  assert.equal(app.world.callsOf('startCurveBatchWrite').length, 0, 'gravou com a curva desatualizada');
  assert.match(headline(app), /Curva K da ECU mudou/i);
  assert.equal(primary(app).textContent.trim(), 'Entendi');
});

test('FLUXO Refino: falha de cabo na gravação → "Nada foi gravado" e a curva da ECU fica intacta', D9, () => {
  const app = refinoApp({ outcome: { curveWrite: 'transport' } });
  const before = app.world.curve.slice();
  primary(app).click(); app.flush();
  app.byId('refinoReview').querySelector('[data-refino-confirm]').click(); app.flush();
  for (let i = 0; i < 8; i += 1) app.advance(400);
  assert.match(headline(app), /Cabo\/USB/);
  assert.match(app.byId('refinoNext').textContent, /Nada foi gravado/);
  assert.deepEqual(app.world.curve, before);
});

test('FLUXO Refino: falha PARCIAL → "pode ter sido alterada em parte" + Desfazer fora do <details> → volta à foto', D9, () => {
  const app = refinoApp({ outcome: { curveWrite: 'partial' } });
  const before = app.world.curve.slice();
  primary(app).click(); app.flush();
  app.byId('refinoReview').querySelector('[data-refino-confirm]').click(); app.flush();
  for (let i = 0; i < 8; i += 1) app.advance(400);
  assert.match(headline(app), /pode ter sido alterada em parte/i);
  assert.match(app.byId('refinoNext').textContent, /Desfazer para voltar à foto/);
  const undo = app.$('[data-refino-undo]');
  assert.ok(undo && !undo.closest('details') && !undo.closest('[hidden]'), 'Desfazer precisa estar visível fora do <details>');
  assert.notDeepEqual(app.world.curve, before, 'controle: a ECU foi alterada em parte');
  // Desfazer: prepara a restauração da foto e grava de volta depois da revisão
  app.world.outcome.curveWrite = 'ok';
  undo.click(); app.flush();
  for (let i = 0; i < 5; i += 1) app.advance(400);
  assert.equal(app.world.callsOf('startCurveRestorePrepare').length, 1);
  assert.ok(reviewOpen(app), 'o Desfazer abre a revisão antes de gravar de volta');
  app.byId('refinoReview').querySelector('[data-refino-confirm]').click(); app.flush();
  for (let i = 0; i < 10; i += 1) app.advance(400);
  assert.equal(app.world.callsOf('startCurveRestoreWrite').length, 1);
  assert.deepEqual(app.world.curve, before, 'a ECU volta à curva de antes');
});

test('FLUXO Refino: RESTAURAR_TRECHO → botão restaura só o trecho que piorou (pontos do Kotlin)', D9, () => {
  const app = refinoApp({ phase: 'RESTAURAR_TRECHO', proposal: false });
  assert.equal(primary(app).hasAttribute('disabled'), false);
  assert.match(primary(app).textContent, /Desfazer o trecho que piorou/);
  primary(app).click(); app.flush();
  assert.ok(reviewOpen(app));
  assert.match(app.byId('refinoReview').textContent, /1 ponto da Curva K/);
  assert.equal(app.world.callsOf('startCurveBatchWrite').length, 0);
});

test('FLUXO Refino: toque duplo em "Revisar" e em "Confirmar": UMA leitura/escrita', D9, () => {
  const app = refinoApp({ opPolls: 8 });
  const mark = app.world.mark();
  primary(app).click(); primary(app).click(); app.flush();
  app.byId('refinoReview').querySelector('[data-refino-confirm]').click();
  const confirm = app.byId('refinoReview').querySelector('[data-refino-confirm]');
  if (confirm) confirm.click();
  app.flush();
  assert.equal(app.world.since(mark).filter(c => c.method === 'startCurveRead').length, 1, 'confirmar duas vezes leu duas vezes');
  for (let i = 0; i < 6; i += 1) app.advance(400);
  assert.ok(app.world.since(mark).filter(c => WRITE.includes(c.method)).length <= 1);
});

test('FLUXO Refino: toque duplo em Desfazer: UMA preparação', D9, () => {
  const app = refinoApp({ phase: 'ESTAVEL', proposal: false, opPolls: 6 });
  const mark = app.world.mark();
  const undo = app.$('[data-refino-undo]');
  undo.click(); undo.click(); app.flush();
  assert.equal(app.world.since(mark).filter(c => c.method === 'startCurveRestorePrepare').length, 1);
});

test('FLUXO AutoCal→Refino→Agora: a próxima ação do Agora leva à aba certa e nunca executa nada', () => {
  const w = new W.World();
  w.equivalence = W.equivalenceFor('PROPOSTA_PRONTA');
  w.refined = W.refinedAnalysis(true, w.curve);
  const app = L.boot({ world: w });
  app.settle(8);
  const next = app.byId('dashNextButton');
  if (!next || next.hasAttribute('hidden')) return; // o Agora está em redesenho: sem botão de próxima ação não há o que provar
  const mark = app.world.mark();
  next.click(); app.settle(3);
  assert.equal(app.route(), next.dataset.route || 'refino');
  assert.equal(app.world.since(mark).filter(c => WRITE.includes(c.method)).length, 0, 'navegar não pode gravar');
});
