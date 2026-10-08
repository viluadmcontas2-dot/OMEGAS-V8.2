'use strict';
// Gráfico durante "Reler gasolina"/"Reler GNV" (desenho aprovado, 2026-10-07): a curva anterior fica esmaecida e
// rotulada "Curva anterior — aguardando a ECU reaprender"; nunca aparece como aquisição atual nem entra em
// cálculo/seleção; domínio, escala e cursor são preservados. A intenção do dono só é liberada por evidência
// posterior correspondente (revisão nova + ECU marcou/avançou a gasolina), nunca pela mesma revisão antiga.
// Classe de prova: 2 (ponte falsa, DOM real do app, sessão real da fixture).
const test = require('node:test'), assert = require('node:assert/strict');
const L = require('./wiring/lib.cjs');

const LABEL = 'Curva anterior — aguardando a ECU reaprender';

function setup() {
  const app = L.boot();
  app.world.projection = { ...app.world.projection, revision: 'r1' };
  app.go('autocal'); app.settle(3);
  const screen = app.win.OmegasApp.autoCalCockpit;
  assert.ok(screen.currentReferencePoints.length > 0, 'a fixture tem referência usável');
  assert.ok(screen.currentAcquiredPoints.some(p => p.fuel === 'PETROL'), 'a fixture tem pontos de gasolina adquiridos');
  const domainBefore = pick(screen.chartScale);
  // Toque do dono: Reler gasolina (um toque, sem confirmação).
  app.$('[data-autocal-action="RESET_PETROL"]').click(); app.settle(1);
  app.world.autocalActionStatus = { busy: true, state: 'SENDING_ACTION', action: 'RESET_PETROL' };
  screen.refresh();
  return { app, screen, domainBefore };
}

function pick(scale) {
  if (!scale) return null;
  const { xMin, xMax, yMin, yMax } = scale;
  return { xMin, xMax, yMin, yMax };
}

function historyNode(app) { return app.$('#autocalReferenceChart [data-autocal-previous-curve="reset"]'); }
function petrolCurrent(screen) { return screen.currentAcquiredPoints.some(p => p.fuel === 'PETROL'); }

test('reset da gasolina: curva anterior esmaecida e rotulada, gasolina some da aquisição atual, domínio e cursor ficam', () => {
  const { app, screen, domainBefore } = setup(); try {
    const host = app.byId('autocalReferenceChart');
    assert.ok(host.querySelector('svg'), 'o gráfico continua um SVG (modo de época), não um vazio');
    const history = historyNode(app);
    assert.ok(history, 'a curva anterior está desenhada');
    assert.ok(history.classList.contains('previous'), 'esmaecida com o estilo de "anterior"');
    assert.match(app.byId('autocalLegend').textContent, new RegExp(LABEL));
    assert.equal(petrolCurrent(screen), false, 'a gasolina antiga não é apresentada como aquisição atual');
    assert.equal(host.querySelectorAll('[data-autocal-point-key^="PETROL:"]').length, 0, 'nenhum ponto antigo de gasolina tocável');
    assert.equal(screen.currentReferencePoints.length, 0, 'histórico não entra em seleção/cálculo');
    assert.deepEqual(pick(screen.epochDomain), domainBefore, 'domínio/escala preservados');
    assert.ok(host.querySelector('.autocal-live-layer'), 'camada do cursor presente');
    L.assertClean(app, 'reset gasolina');
  } finally { app.destroy(); }
});

test('busy=false com a mesma projeção antiga mantém a intenção pendente; projeção nova com evidência libera', () => {
  const { app, screen } = setup(); try {
    app.world.autocalActionStatus = { busy: false, state: 'CONFIRMED', action: 'RESET_PETROL' };
    screen.refresh();
    assert.equal(petrolCurrent(screen), false, 'mesma revisão: a gasolina antiga continua fora');
    assert.ok(historyNode(app), 'curva anterior continua esmaecida');
    // Projeção nova mas sem evidência da gasolina (ex.: só a telemetria/hash mudou): ainda pendente.
    app.world.projection = { ...app.world.projection, revision: 'r2' };
    screen.refresh();
    assert.equal(petrolCurrent(screen), false, 'revisão nova sem evidência correspondente não libera');
    // ECU marcou a gasolina como relida (época nova): libera.
    app.world.projection = {
      ...app.world.projection, revision: 'r3',
      liveAcquisitionEpoch: { available: true, usbSessionId: 9, nativeAutoMatchCount: 1, petrolGeneration: 1, gasGeneration: 1, petrolPending: false, gasPending: false, referencePending: false, petrolReferencePending: false, gasReferencePending: false, comparisonAllowed: true },
    };
    screen.refresh();
    assert.equal(petrolCurrent(screen), true, 'evidência posterior correspondente libera a gasolina');
    assert.equal(historyNode(app), null, 'fora do modo de época não há "curva anterior" esmaecida do reset');
    L.assertClean(app, 'janela busy=false');
  } finally { app.destroy(); }
});

test('falha sem mutação não deixa pendência eterna; sessão nova limpa o histórico', () => {
  const { app, screen } = setup(); try {
    app.world.autocalActionStatus = { busy: false, state: 'FAILED', action: 'RESET_PETROL', mutationMayHaveStarted: false, message: 'sem ACK' };
    screen.refresh();
    assert.equal(petrolCurrent(screen), true, 'nada mudou na ECU: a gasolina volta a ser a atual');
    assert.equal(historyNode(app), null);
    // De novo, e desta vez a sessão USB muda antes de qualquer evidência.
    app.$('[data-autocal-action="RESET_PETROL"]').click(); app.settle(1);
    app.world.autocalActionStatus = { busy: false, state: 'CONFIRMED', action: 'RESET_PETROL' };
    screen.refresh();
    assert.equal(petrolCurrent(screen), false);
    app.world.projection = { ...app.world.projection, sessionId: 'sess-2', revision: 'r9' };
    screen.refresh();
    assert.equal(screen.previousReferencePoints.length, 0, 'sessão nova limpa o histórico');
    assert.equal(petrolCurrent(screen), true, 'sessão nova não arrasta a intenção antiga');
    L.assertClean(app, 'falha e sessão nova');
  } finally { app.destroy(); }
});

test('reset do GNV: mesma curva anterior esmaecida, GNV some da aquisição atual, gasolina continua', () => {
  const app = L.boot(); try {
    app.world.projection = { ...app.world.projection, revision: 'r1' };
    app.go('autocal'); app.settle(3);
    const screen = app.win.OmegasApp.autoCalCockpit;
    app.$('[data-autocal-action="RESET_GAS"]').click(); app.settle(1);
    app.world.autocalActionStatus = { busy: true, state: 'READING_AFTER', action: 'RESET_GAS' };
    screen.refresh();
    assert.ok(historyNode(app));
    assert.equal(screen.currentAcquiredPoints.some(p => p.fuel === 'GAS'), false);
    assert.equal(petrolCurrent(screen), true);
    assert.match(app.byId('autocalLegend').textContent, new RegExp(LABEL));
    L.assertClean(app, 'reset GNV');
  } finally { app.destroy(); }
});

// Revisão 2026-10-07 (achado 6): o histórico fica visível DEPOIS da revisão que confirma o reset (geração nova,
// intenção liberada, referência ainda pendente, curva nova vazia) e durante toda a readquisição, até a referência
// nova ser utilizável. Texto humano acompanha a época sem depender de busy. Histórico nunca entra em cálculo.
test('histórico continua após o ACK: geração nova → bandas incompletas → referência utilizável', () => {
  const { app, screen, domainBefore } = setup(); try {
    const host = app.byId('autocalReferenceChart');
    const readout = () => app.byId('autocalChartInspector')?.textContent || '';
    const check = label => {
      assert.ok(host.querySelector('svg'), label + ': SVG');
      assert.ok(historyNode(app), label + ': curva anterior desenhada');
      assert.match(app.byId('autocalLegend').textContent, new RegExp(LABEL), label + ': legenda');
      assert.ok(host.querySelector('.autocal-live-layer'), label + ': cursor');
      assert.deepEqual(pick(screen.epochDomain), domainBefore, label + ': escala');
      assert.equal(screen.currentReferencePoints.length, 0, label + ': histórico fora do cálculo');
      assert.match(readout(), /gasolina/i, label + ': texto humano fala da gasolina');
      L.assertClean(app, label);
    };
    check('busy');
    // ACK + revisão nova: geração avançou, intenção liberada, referência pendente, curva nova vazia.
    app.world.autocalActionStatus = { busy: false, state: 'CONFIRMED', action: 'RESET_PETROL' };
    app.world.projection = {
      ...app.world.projection, revision: 'r2', referenceUsable: false,
      liveAcquisitionEpoch: { available: true, usbSessionId: 9, nativeAutoMatchCount: 0, petrolGeneration: 2, gasGeneration: 1, petrolPending: true, gasPending: false, referencePending: true, petrolReferencePending: true, gasReferencePending: false, comparisonAllowed: false },
    };
    screen.refresh();
    assert.equal(screen.resetIntent, null, 'intenção liberada pela evidência correspondente');
    assert.equal(petrolCurrent(screen), false, 'curva nova vazia: nada de gasolina antiga como atual');
    check('confirmado');
    // Bandas incompletas: a ECU já não marca pendente, mas a referência ainda não é utilizável.
    app.world.projection = {
      ...app.world.projection, revision: 'r3', referenceUsable: false,
      liveAcquisitionEpoch: { available: true, usbSessionId: 9, nativeAutoMatchCount: 0, petrolGeneration: 2, gasGeneration: 1, petrolPending: false, gasPending: false, referencePending: true, petrolReferencePending: true, gasReferencePending: false, comparisonAllowed: false },
    };
    screen.refresh();
    check('bandas incompletas');
    // Referência nova utilizável: sai do modo de época; o histórico não é mais a "curva anterior" esmaecida do reset.
    app.world.projection = {
      ...app.world.projection, revision: 'r4', referenceUsable: true,
      liveAcquisitionEpoch: { available: true, usbSessionId: 9, nativeAutoMatchCount: 1, petrolGeneration: 2, gasGeneration: 1, petrolPending: false, gasPending: false, referencePending: false, petrolReferencePending: false, gasReferencePending: false, comparisonAllowed: true },
    };
    screen.refresh();
    assert.equal(historyNode(app), null, 'referência utilizável: fim do modo de época');
    assert.ok(screen.currentReferencePoints.length > 0, 'referência nova em uso');
    // Sessão USB nova limpa o histórico.
    app.world.projection = { ...app.world.projection, sessionId: 'sess-2', revision: 'r5' };
    screen.refresh();
    assert.equal(screen.previousReferencePoints.length, 0);
    L.assertClean(app, 'readquisição completa');
  } finally { app.destroy(); }
});
