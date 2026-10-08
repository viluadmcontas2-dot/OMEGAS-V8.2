'use strict';
// Fix UI · Curva K (achados 4, 8, 9, 10, 14). Testes de USO: botão congelado, valor sem produtor, valor velho.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const { curveApp, editPoint, tapReview } = require('./wiring/scenarios.cjs');

const review = app => app.byId('curveReviewButton');
const nudges = app => app.$$('[data-curve-nudge]');
const writes = app => app.world.calls.filter(c => ['startCurveBatchWrite', 'startCurveRestoreWrite', 'startCurveReset'].includes(c.method));
const DISABLED = 'Escolha um ponto';

test('14. UM botão primário: sem "Preparar ponto" nem "Prepare pontos"; desabilitado diz o que fazer; a lista mostra o ponto preparado', () => {
  const app = curveApp();
  assert.equal(app.byId('curvePreparePoint'), null);
  assert.doesNotMatch(app.text(), /Prepare pontos|Preparar ponto/);
  app.$('circle[data-curve-index="9"]').click(); app.flush();
  // ponto escolhido mas nada ajustado: o botão ainda diz o que fazer
  assert.equal(review(app).textContent, DISABLED);
  assert.equal(review(app).hasAttribute('disabled'), true);
  app.$('[data-curve-nudge="0.05"]').click(); app.flush();
  assert.match(review(app).textContent, /^Gravar 1 ponto na ECU$/);
  assert.equal(review(app).hasAttribute('disabled'), false);
  assert.match(app.byId('curveProposalList').textContent, /→/);
  assert.equal(app.$$('.proposal-row').length, 1);
  L.assertClean(app, 'curva 14');
});

test('14. "K desejado" em pt-BR (vírgula, 3 casas); digitar "1,05" e tocar no botão principal grava o ponto (um toque, sem passo extra)', () => {
  const app = curveApp();
  app.$('circle[data-curve-index="9"]').click(); app.flush();
  const input = app.byId('curveTargetFactor');
  assert.match(input.value, /^\d,\d{3}$/, `K com vírgula: ${input.value}`);
  input.value = '1,05';
  input.dispatchEvent(new app.win.Event('input', { bubbles: true })); // o dono digitou: o botão já se oferece
  assert.equal(review(app).hasAttribute('disabled'), false);
  const mark = app.world.mark();
  tapReview(app);
  const calls = app.world.since(mark).filter(c => c.method === 'startCurveBatchWrite');
  assert.equal(calls.length, 1, 'um toque prepara e grava');
  const sent = JSON.parse(calls[0].args[0]);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].index, 9);
  assert.equal(sent[0].targetRaw, Math.round(1.05 * 16384));
  app.destroy();
  // com ponto decimal também funciona
  const b = curveApp();
  b.$('circle[data-curve-index="9"]').click(); b.flush();
  b.byId('curveTargetFactor').value = '1.05';
  b.byId('curveTargetFactor').dispatchEvent(new b.win.Event('input', { bubbles: true }));
  tapReview(b);
  assert.equal(b.world.callsOf('startCurveBatchWrite').length, 1);
  b.destroy();
});

test('4/DEFECT-11: depois de gravar e conferir, o botão e a lista voltam ao vazio (nada de botão ativo morto)', () => {
  const app = curveApp();
  editPoint(app, 9); tapReview(app); app.settle(4);
  assert.equal(review(app).hasAttribute('disabled'), true);
  assert.equal(review(app).textContent, DISABLED);
  assert.match(app.byId('curveProposalList').textContent, /Nenhum ponto preparado/);
  assert.equal(app.$$('.proposal-row').length, 0);
  L.assertClean(app, 'curva 11');
  app.destroy();
});

test('4/DEFECT-12: sem curva lida (leitura falhou) os ajustes ficam desativados e dizem por quê', () => {
  const app = curveApp({ outcome: { curveRead: 'transport' } });
  assert.match(app.byId('curveSourceStatus').textContent, /ECU não confirmada/);
  assert.ok(nudges(app).length === 4);
  for (const n of nudges(app)) { assert.equal(n.hasAttribute('disabled'), true); assert.equal(n.attrs.get('title'), DISABLED); }
  assert.equal(app.byId('curveTargetFactor').hasAttribute('disabled'), true);
  assert.equal(review(app).hasAttribute('disabled'), true);
  assert.equal(review(app).textContent, DISABLED);
  // releu: tudo volta
  app.world.outcome.curveRead = 'ok';
  app.byId('curveReadButton').click(); app.settle(3);
  for (const n of nudges(app)) assert.equal(n.hasAttribute('disabled'), false);
  app.destroy();
});

test('4/DEFECT-13: falha parcial de gravação — a curva antiga some, "ECU não confirmada" e ajustes desativados; reler volta tudo', () => {
  const app = curveApp({ outcome: { curveWrite: 'partial' } });
  editPoint(app, 9); tapReview(app); app.settle(4);
  assert.equal(app.$$('circle[data-curve-index]').length, 0, 'sem pontos mortos desenhados');
  assert.match(app.byId('curveSourceStatus').textContent, /ECU não confirmada/);
  for (const n of nudges(app)) assert.equal(n.hasAttribute('disabled'), true);
  assert.equal(review(app).textContent, DISABLED);
  // o Desfazer da falha parcial continua disponível
  assert.equal(app.byId('curveUndoButton').hasAttribute('hidden'), false);
  app.world.outcome.curveWrite = 'ok';
  app.byId('curveReadButton').click(); app.settle(3);
  assert.equal(app.$$('circle[data-curve-index]').length, 30);
  app.destroy();
});

test('4. a aba "Evidência" morta saiu da Curva K', () => {
  const app = curveApp();
  assert.equal(app.$('.evidence-disclosure'), null);
  assert.equal(app.byId('curveEvidenceList'), null);
  assert.doesNotMatch(app.$('[data-screen="curve"]').textContent, /EVIDÊNCIA/);
  app.destroy();
});

test('4. prévia inválida da Curva K: mensagem humana com próxima ação', () => {
  const app = curveApp();
  app.$('circle[data-curve-index]').click();
  app.world.raw = { previewKFactorPoint: JSON.stringify({ ok: false }) };
  app.$('[data-curve-nudge="0.05"]').click(); app.flush();
  assert.match(app.byId('alertToast').textContent, /Não deu para calcular este ponto\. Toque no ponto e tente de novo\./);
  assert.doesNotMatch(app.byId('alertToast').textContent, /inválida/);
  app.destroy();
});

test('9. pedido de reset do AutoCal nunca vira reset sem toque novo (falha de leitura, entrada sem resetNow, leitura ocupada)', () => {
  // (a) a leitura falha: o pedido morre
  let app = curveApp({ outcome: { curveRead: 'transport' } });
  const curve = app.win.OmegasApp.screens.curve;
  curve.onEnter({ resetNow: true });
  app.settle(3);
  assert.equal(curve.pendingReset, false, 'leitura falhou: o pedido some');
  app.world.outcome.curveRead = 'ok';
  app.go('tools'); app.go('curve'); app.settle(4);
  assert.equal(app.world.callsOf('startCurveBackup').length, 0, 'voltar à aba depois não zera nem tira a foto de reset');
  assert.equal(writes(app).length, 0);
  app.destroy();
  // (b) entra com resetNow e logo entra de novo sem ele: o pedido é apagado
  app = curveApp();
  const c2 = app.win.OmegasApp.screens.curve;
  c2.data = null; app.world.op = { idle: true };
  c2.onEnter({ resetNow: true });
  assert.equal(c2.pendingReset, true, 'a leitura da ECU está em andamento, o pedido espera');
  c2.onEnter(null);
  assert.equal(c2.pendingReset, false, 'entrada comum apaga o pedido');
  app.settle(4);
  assert.equal(app.world.callsOf('startCurveBackup').length, 0);
  assert.equal(writes(app).length, 0);
  app.destroy();
  // (c) regressão: com toque do AutoCal (resetNow) e curva lida, o reset segue (foto antes, depois zera)
  app = curveApp();
  app.win.OmegasApp.screens.curve.onEnter({ resetNow: true });
  app.settle(6);
  assert.equal(app.world.callsOf('startCurveBackup').length, 0, 'nenhum arquivo da curva sem o botão Salvar');
  assert.equal(app.world.callsOf('startCurveReset').length, 1, 'zera');
  app.destroy();
});

test('10. reset não depende de arquivo visível: nenhum startCurveBackup, e falha de salvar não bloqueia nem aparece', () => {
  const app = curveApp({ outcome: { curveBackup: 'transport' } });
  app.byId('curveResetButton').click(); app.settle(4);
  assert.equal(app.world.callsOf('startCurveBackup').length, 0, 'sem toque em Salvar não há arquivo');
  assert.equal(app.world.callsOf('startCurveReset').length, 1);
  assert.doesNotMatch(app.byId('curveBackupStatus').textContent, /foto salva|backup/i);
  app.destroy();
});

test('10/8. Desfazer da Curva K só aparece com o que desfazer; sobrevive a voltar do segundo plano (foto e prévia mantidas)', () => {
  const app = curveApp();
  const restore = app.byId('curveBackupRestore');
  assert.equal(restore.hasAttribute('hidden'), true, 'sem foto escolhida não há Desfazer');
  // uma foto salva e a ECU mudou depois (algo para desfazer)
  app.byId('curveBackupSave').click(); app.settle(3);
  app.world.curve = app.world.curve.map((v, i) => (i === 4 ? v + 500 : v));
  app.byId('curveReadButton').click(); app.settle(3);
  const select = app.byId('curveBackupSelect');
  const option = select.options.find(o => o.attrs.get('value'));
  assert.match(option.textContent, /(agora|há \d+ s|há \d+ min)/, 'a foto mostra a idade');
  select.value = option.attrs.get('value');
  select.dispatchEvent(new app.win.Event('change', { bubbles: true }));
  app.settle(3);
  assert.equal(restore.hasAttribute('hidden'), false);
  assert.equal(restore.hasAttribute('disabled'), false);
  assert.match(restore.textContent, /Desfazer · 1 ponto/);
  // vai para o segundo plano e volta
  app.doc.hidden = true; app.doc.dispatchEvent(new app.win.Event('visibilitychange'));
  app.doc.hidden = false; app.doc.dispatchEvent(new app.win.Event('visibilitychange'));
  app.settle(3);
  assert.equal(app.byId('curveBackupSelect').value, option.attrs.get('value'), 'a foto escolhida continua escolhida');
  assert.equal(restore.hasAttribute('disabled'), false, 'o Desfazer não morre');
  assert.match(restore.textContent, /Desfazer · 1 ponto/);
  const mark = app.world.mark();
  restore.click(); app.settle(4);
  assert.ok(app.world.since(mark).some(c => c.method === 'startCurveRestoreWrite'), 'o toque desfaz');
  app.destroy();
});
