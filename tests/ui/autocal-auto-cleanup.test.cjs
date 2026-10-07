'use strict';
// Costura P1 rev2 (apagamento automático de ponto fora da curva, GNV e gasolina) × P3 (UI): o ponto que o app apagou sozinho fica
// cinza e intocável como o apagado pelo dono, nunca aparece como "você confirmou", e a linha discreta diz em
// português simples se a limpeza está ligada, quantos pontos reaprendeu e por que pausou.
const test = require('node:test'), assert = require('node:assert/strict');
const L = require('./wiring/lib.cjs');

const TECH = /readback|ECU_|DELETE_POINT|GNV_IDLE|OUTLIER|PETROL_GUARD|OTHER_FUEL|REPEATED|READBACK|automatic|mask|band[as]?\b/i;

function gasPoint(screen) {
  return screen.currentAcquiredPoints.find(p => p.fuel === 'GAS');
}

test('linha discreta: ligada, contagem da sessão e some sem conexão', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const line = app.byId('autocalAutoCleanLine');
    assert.ok(line, 'a linha existe');
    assert.equal(line.closest('details'), null, 'fica à vista, fora dos detalhes');
    assert.equal(line.hidden, false);
    assert.equal(line.textContent, 'Limpeza automática: ligada · nenhum ponto reaprendido nesta sessão');
    app.world.autocalAutoCleanup = { ...app.world.autocalAutoCleanup, relearnedThisSession: 1 };
    app.win.OmegasApp.autoCalCockpit.refresh();
    assert.equal(line.textContent, 'Limpeza automática: ligada · 1 ponto reaprendido nesta sessão');
    app.world.autocalAutoCleanup = { ...app.world.autocalAutoCleanup, relearnedThisSession: 3 };
    app.win.OmegasApp.autoCalCockpit.refresh();
    assert.equal(line.textContent, 'Limpeza automática: ligada · 3 pontos reaprendidos nesta sessão');
    app.world.autocalAutoCleanup = { ...app.world.autocalAutoCleanup, active: false };
    app.win.OmegasApp.autoCalCockpit.refresh();
    assert.equal(line.hidden, true, 'sem conexão USB não há o que dizer');
    app.world.autocalAutoCleanup = { ok: false, error: 'Serviço indisponível' };
    app.win.OmegasApp.autoCalCockpit.refresh();
    assert.equal(line.hidden, true, 'ponte sem resposta: nada inventado');
    L.assertClean(app, 'linha da limpeza');
  } finally { app.destroy(); }
});

test('pausada: motivo simples e o que fazer, sem jargão', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const screen = app.win.OmegasApp.autoCalCockpit;
    const line = app.byId('autocalAutoCleanLine');
    const cases = {
      OTHER_FUEL_GUARD: /outro combustível/i,
      REPEATED_FAILURES: /várias vezes/i,
      READBACK_INEFFECTIVE: /não apagou/i,
      ALGO_NOVO: /inesperado/i,
    };
    for (const [code, reason] of Object.entries(cases)) {
      app.world.autocalAutoCleanup = { ok: true, active: true, enabled: false, pauseCode: code, relearnedThisSession: 2, recentDeletes: [] };
      screen.refresh();
      assert.match(line.textContent, /^Limpeza automática pausada nesta conexão: /, code);
      assert.match(line.textContent, reason, code);
      assert.match(line.textContent, /Reconecte o cabo para tentar de novo\.$/, code);
      assert.doesNotMatch(line.textContent, TECH, code);
      assert.equal(line.dataset.level, 'warn');
    }
    L.assertClean(app, 'limpeza pausada');
  } finally { app.destroy(); }
});

test('apagamento automático: ponto cinza, intocável, aviso curto uma vez e liberado na leitura nova', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const screen = app.win.OmegasApp.autoCalCockpit;
    const point = gasPoint(screen);
    assert.ok(point, 'há ponto GNV aprendido na fixture');
    const key = 'GAS:' + point.index;
    const alerts = [];
    const patch = screen.store.patch.bind(screen.store);
    screen.store.patch = value => { if (value && value.alert) alerts.push(value.alert); return patch(value); };
    app.world.autocalAutoCleanup = {
      ok: true, active: true, enabled: true, pauseCode: null, relearnedThisSession: 1,
      recentDeletes: [{ receiptId: 'R-auto-1', fuel: 'GAS', indexes: [point.index], atMs: app.win.Date.now() }],
    };
    screen.refresh();
    assert.equal(screen.recentlyDeleted.has(key), true, 'o automático alimenta o mesmo cinza do manual');
    assert.equal(alerts.length, 1);
    assert.equal(alerts[0].message, `O app pediu para a ECU reaprender o ponto ${point.index + 1} do GNV — ele estava fora da curva.`);
    assert.doesNotMatch(alerts[0].message, /você|confirm/i);
    screen.tapAcquiredPoint('GAS', point.index);
    assert.equal(screen.selectedAcquiredPoints.size, 0, 'ponto apagado sozinho não pode ser marcado');
    assert.ok(app.$(`#autocalReferenceChart [data-autocal-point-key="${key}"]`).classList.contains('recently-deleted'), 'aparece cinza');
    screen.refresh();
    assert.equal(alerts.length, 1, 'o mesmo apagamento não avisa de novo');
    // Leitura da ECU mais nova que o apagamento: o ponto deixa de ser "recém-apagado" (e não volta a ser).
    const next = JSON.parse(JSON.stringify(app.world.projection));
    const bump = f => { if (f && f.capturedAtMs) f.capturedAtMs += 10 * 60000; };
    [next.snapshot, next.nativeSnapshot].filter(Boolean).forEach(snap => { bump(snap); (snap.fields || []).forEach(bump); });
    app.world.projection = next; screen.refresh();
    assert.equal(screen.recentlyDeleted.has(key), false, 'leitura nova libera o ponto');
    L.assertClean(app, 'apagamento automático');
  } finally { app.destroy(); }
});

test('gasolina e GNV: cada apagamento acinzenta o ponto do seu combustível e o aviso diz qual', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const screen = app.win.OmegasApp.autoCalCockpit;
    const gas = gasPoint(screen);
    const petrol = screen.currentAcquiredPoints.find(p => p.fuel === 'PETROL');
    assert.ok(gas && petrol, 'a fixture tem pontos dos dois combustíveis');
    const alerts = [];
    const patch = screen.store.patch.bind(screen.store);
    screen.store.patch = value => { if (value && value.alert) alerts.push(value.alert); return patch(value); };
    const now = app.win.Date.now();
    app.world.autocalAutoCleanup = {
      ok: true, active: true, enabled: true, pauseCode: null, relearnedThisSession: 2,
      recentDeletes: [
        { receiptId: 'R-gnv', fuel: 'GAS', indexes: [gas.index], atMs: now },
        { receiptId: 'R-gas', fuel: 'PETROL', indexes: [petrol.index], atMs: now },
      ],
    };
    screen.refresh();
    assert.equal(screen.recentlyDeleted.has('GAS:' + gas.index), true);
    assert.equal(screen.recentlyDeleted.has('PETROL:' + petrol.index), true, 'recentlyDeleted vale para a gasolina');
    assert.equal(screen.recentlyDeleted.has('PETROL:' + gas.index) && gas.index !== petrol.index, false);
    assert.equal(alerts.length, 1);
    assert.match(alerts[0].message, new RegExp(`o ponto ${gas.index + 1} do GNV — ele estava fora da curva\\.`));
    assert.match(alerts[0].message, new RegExp(`o ponto ${petrol.index + 1} da gasolina — ele estava fora da curva\\.`));
    assert.equal(app.byId('autocalAutoCleanLine').textContent, 'Limpeza automática: ligada · 2 pontos reaprendidos nesta sessão');
    L.assertClean(app, 'apagamento automático nos dois combustíveis');
  } finally { app.destroy(); }
});

test('estado da ação automática da gasolina fala da gasolina', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const screen = app.win.OmegasApp.autoCalCockpit;
    const host = app.byId('autocalActionStatus');
    app.world.autocalActionStatus = {
      state: 'CONFIRMED', busy: false, action: 'DELETE_POINT', automatic: true, humanConfirmed: false,
      message: 'gasolina: 1 ponto(s) fora da curva liberado(s) automaticamente; readback confirmado',
      details: { pointDelete: { fuel: 'PETROL', index: 2 }, details: { fuel: 'PETROL', targets: [{ fuel: 'PETROL', index: 2 }] } },
    };
    screen.refresh();
    assert.equal(host.textContent, 'O app pediu para a ECU reaprender o ponto 3 da gasolina — ele estava fora da curva.');
    assert.doesNotMatch(host.textContent, TECH);
  } finally { app.destroy(); }
});

test('recibo automático no estado da ação já acinzenta antes do resumo chegar (sem aviso duplo)', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const screen = app.win.OmegasApp.autoCalCockpit;
    const point = gasPoint(screen);
    const key = 'GAS:' + point.index;
    const alerts = [];
    const patch = screen.store.patch.bind(screen.store);
    screen.store.patch = value => { if (value && value.alert) alerts.push(value.alert); return patch(value); };
    const at = app.win.Date.now();
    app.world.autocalActionStatus = {
      state: 'CONFIRMED', busy: false, action: 'DELETE_POINT', automatic: true, humanConfirmed: false,
      details: {
        id: 'R-race', finishedAtMs: at, automatic: true,
        details: {
          targets: [{ fuel: 'GAS', index: point.index }, { fuel: 'GAS', index: 17 }],
          effect: [{ index: point.index, result: 'DELETED' }, { index: 17, result: 'AMBIGUOUS' }],
        },
      },
    };
    screen.refresh();
    assert.equal(screen.recentlyDeleted.has(key), true, 'o recibo no estado basta para acinzentar');
    assert.equal(screen.recentlyDeleted.has('GAS:17'), false, 'readback ambíguo não acinzenta');
    app.world.autocalAutoCleanup = { ...app.world.autocalAutoCleanup, relearnedThisSession: 1, recentDeletes: [{ receiptId: 'R-race', indexes: [point.index], atMs: at }] };
    screen.refresh();
    assert.equal(alerts.length, 1, 'mesmo recibo pelas duas vias: um aviso só');
  } finally { app.destroy(); }
});

test('apagamento automático antigo (tela estava fechada) não acinzenta nem avisa', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const screen = app.win.OmegasApp.autoCalCockpit;
    const point = gasPoint(screen);
    const alerts = [];
    const patch = screen.store.patch.bind(screen.store);
    screen.store.patch = value => { if (value && value.alert) alerts.push(value.alert); return patch(value); };
    app.world.autocalAutoCleanup = {
      ok: true, active: true, enabled: true, pauseCode: null, relearnedThisSession: 1,
      recentDeletes: [{ receiptId: 'R-old', indexes: [point.index], atMs: app.win.Date.now() - 5 * 60000 }],
    };
    screen.refresh();
    assert.equal(screen.recentlyDeleted.has('GAS:' + point.index), false);
    assert.equal(alerts.length, 0);
  } finally { app.destroy(); }
});

test('estado da ação automática nunca vira "você confirmou" nem mostra jargão', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const screen = app.win.OmegasApp.autoCalCockpit;
    const host = app.byId('autocalActionStatus');
    const toggleBefore = app.$('[data-autocal-toggle]').textContent;
    app.world.autocalActionStatus = {
      state: 'READING_BEFORE', busy: true, action: 'DELETE_POINT', automatic: true, humanConfirmed: false,
      message: 'Relendo GNV e gasolina antes de apagar', details: { bands: [{ band: 4, point: 5 }] },
    };
    screen.refresh();
    assert.match(host.textContent, /O app está pedindo para a ECU reaprender o ponto 5 do GNV, que estava fora da curva/);
    assert.doesNotMatch(host.textContent, TECH);
    assert.notEqual(app.$('[data-autocal-toggle]').textContent, 'Confirmando ECU…', 'a limpeza não finge que o dono pediu algo');
    assert.equal(app.$('[data-autocal-toggle]').textContent, toggleBefore);
    app.world.autocalActionStatus = {
      state: 'CONFIRMED', busy: false, action: 'DELETE_POINT', automatic: true, humanConfirmed: false,
      message: 'GNV: 1 ponto(s) fora da curva liberado(s) automaticamente; readback confirmado',
      details: { pointDelete: { fuel: 'GAS', index: 4 }, details: { targets: [{ fuel: 'GAS', index: 4 }] } },
    };
    screen.refresh();
    assert.equal(host.textContent, 'O app pediu para a ECU reaprender o ponto 5 do GNV — ele estava fora da curva.');
    assert.doesNotMatch(host.textContent, /Pronto|você|confirm|readback/i);
    app.world.autocalActionStatus = {
      state: 'FAILED', busy: false, action: 'DELETE_POINT', automatic: true, humanConfirmed: false,
      message: 'A ECU não confirmou o commit da readquisição', recovery: { nextAction: 'Reconecte' },
    };
    screen.refresh();
    assert.equal(host.dataset.level, 'neutral', 'falha do automático não assusta o leigo');
    assert.match(host.textContent, /tenta de novo sozinho/);
    assert.doesNotMatch(host.textContent, /Não deu certo|commit|readquisição/i);
    L.assertClean(app, 'estado automático');
  } finally { app.destroy(); }
});

test('seleção manual pendente não é dada como confirmada por um recibo automático', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const screen = app.win.OmegasApp.autoCalCockpit;
    const model = app.win.OmegasUi.AutoCalUxModel;
    const t = model.pointSelectionTransition(['GAS:4'], { state: 'CONFIRMED', automatic: true }, {});
    assert.equal(t.reason, 'SUPERSEDED_BY_AUTOMATIC');
    assert.equal(t.clear, true);
    const inFlight = model.pointSelectionTransition(['GAS:4'], { state: 'READING_BEFORE', busy: true, automatic: true }, {});
    assert.equal(inFlight.reason, 'SUPERSEDED_BY_AUTOMATIC');
    assert.ok(screen);
  } finally { app.destroy(); }
});
