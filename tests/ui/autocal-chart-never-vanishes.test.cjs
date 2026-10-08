'use strict';
// O gráfico do AutoCal nunca some (relato do dono, sessão real de 08/10/2026): logo depois de "Reler gasolina" e do
// AutoMatch da ECU os 36 contadores voltam a zero e a referência nativa (PETR_MNFLD_PRESS_RV) vem toda zero; o quadro
// virava "SEM REFERÊNCIA — Os vetores recebidos não formam um domínio físico válido". Agora o quadro fica (eixos, escala
// de antes, AGORA) com um aviso em palavras, e os pontos voltam sozinhos quando a ECU manda a leitura seguinte.
// Classe de prova: 2 (ponte falsa, DOM real do app, leitura real zerada da sessão).
const test = require('node:test'), assert = require('node:assert/strict');
const L = require('./wiring/lib.cjs');

function emptied(snapshot) {
  const zero = ['NUM_BUF_UPD_PETR', 'NUM_BUF_UPD_GAS', 'PETR_MNFLD_PRESS_RV', 'GAS_MNFLD_PRESS_RV'];
  return {
    ...snapshot,
    snapshotHash: 'zerado-' + (snapshot.snapshotHash || ''),
    fields: snapshot.fields.map(f => zero.includes(f.key)
      ? { ...f, rawValues: f.rawValues.map(() => 0), physicalValues: (f.physicalValues || f.rawValues).map(() => 0) }
      : f),
  };
}

function project(world, snapshot) {
  const shown = { available: true, ...snapshot };
  world.projection = {
    ...world.projection, revision: snapshot.snapshotHash, snapshotHash: snapshot.snapshotHash, snapshot: shown,
    nativeSnapshot: shown, nativeStatus: { ...world.projection.nativeStatus, latestSnapshot: shown },
  };
}

function boot() {
  const app = L.boot();
  app.world.projection = { ...app.world.projection, revision: 'r1' };
  app.go('autocal'); app.settle(3);
  const screen = app.win.OmegasApp.autoCalCockpit;
  assert.ok(screen.currentAcquiredPoints.length > 0, 'a fixture tem pontos adquiridos');
  return { app, screen, original: app.world.projection.snapshot };
}

const pick = scale => scale && ({ xMin: scale.xMin, xMax: scale.xMax, yMin: scale.yMin, yMax: scale.yMax });

test('leitura zerada (reset/AutoMatch): o quadro fica com eixos, escala de antes, AGORA e um aviso; nada de "SEM REFERÊNCIA"', () => {
  const { app, screen, original } = boot(); try {
    const scaleBefore = pick(screen.chartScale);
    project(app.world, emptied(original));
    screen.refresh(); app.settle(2);
    const host = app.byId('autocalReferenceChart');
    assert.ok(host.querySelector('svg'), 'o gráfico continua um SVG com eixos');
    assert.ok(host.querySelector('.autocal-live-layer'), 'o AGORA continua no quadro');
    const notice = host.querySelector('.chart-empty-overlay');
    assert.ok(notice, 'o aviso vai por cima do quadro');
    assert.match(notice.textContent, /Dirija um pouco/);
    assert.doesNotMatch(host.textContent, /SEM REFERÊNCIA|domínio físico/, 'sem jargão');
    assert.deepEqual(pick(screen.chartScale), scaleBefore, 'a escala de antes é preservada');
    assert.equal(host.querySelectorAll('[data-autocal-point-key]').length, 0, 'sem pontos inventados');
    L.assertClean(app, 'leitura zerada');
  } finally { app.destroy(); }
});

test('os pontos voltam sozinhos quando a ECU manda a leitura seguinte', () => {
  const { app, screen, original } = boot(); try {
    project(app.world, emptied(original));
    screen.refresh(); app.settle(2);
    project(app.world, original);
    screen.refresh(); app.settle(2);
    const host = app.byId('autocalReferenceChart');
    assert.equal(host.querySelector('.chart-empty-overlay'), null, 'o aviso some junto com a causa');
    assert.ok(host.querySelectorAll('[data-autocal-point-key]').length > 0, 'pontos de volta');
    L.assertClean(app, 'retomada');
  } finally { app.destroy(); }
});

test('app recém-aberto com tudo zerado (sem escala anterior) também mostra o quadro, numa faixa típica de condução', () => {
  const app = L.boot();
  try {
    app.world.projection = { ...app.world.projection, revision: 'r0' };
    const base = app.world.projection.snapshot;
    project(app.world, emptied(base));
    app.go('autocal'); app.settle(3);
    const host = app.byId('autocalReferenceChart');
    assert.ok(host.querySelector('svg'), 'quadro presente desde o primeiro desenho');
    assert.ok(host.querySelector('.chart-empty-overlay'));
    const screen = app.win.OmegasApp.autoCalCockpit;
    assert.ok(screen.chartScale && screen.chartScale.xMax > screen.chartScale.xMin, 'escala válida (AGORA consegue se mover)');
    L.assertClean(app, 'primeiro desenho vazio');
  } finally { app.destroy(); }
});
