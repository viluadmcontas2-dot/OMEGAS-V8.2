'use strict';
// M3 · AutoCal: pausado / monitorando / snapshot parcial / leitura atrasada / época nova (snapshots REAIS gravados).
const test = require('node:test');
const L = require('./lib.cjs');
const { assert } = L;
const { realProjection, realSession, realFrames } = require('./world.cjs');

const SESSION = 'automatch_';
const lastSnap = () => realSession(SESSION).snapshots.length - 1;

function projectionFor(name, now) {
  const p = realProjection(SESSION, lastSnap());
  const snap = p.snapshot;
  const withNative = patch => { p.nativeStatus = { ...p.nativeStatus, ...patch }; p.nativeStatus.latestSnapshot = p.nativeSnapshot; };
  switch (name) {
    case 'monitorando': withNative({ state: 'MONITORING', autoCalEnabled: 1 }); break;
    case 'pausado': withNative({ state: 'MONITORING', autoCalEnabled: 0 }); break;
    case 'snapshot parcial': {
      const fields = snap.fields.map((f, i) => (i % 3 === 0 ? { ...f, status: 'MISSING', rawValues: [] } : f));
      p.snapshot = { ...snap, fields, partial: true };
      p.nativeSnapshot = { ...p.nativeSnapshot, fields, partial: true };
      withNative({ state: 'MONITORING', autoCalEnabled: 1 });
      break;
    }
    case 'leitura atrasada': {
      const old = now - 180000;
      const fields = snap.fields.map(f => ({ ...f, capturedAtMs: old }));
      p.snapshot = { ...snap, fields, capturedAtMs: old };
      p.nativeSnapshot = { ...p.nativeSnapshot, fields, capturedAtMs: old };
      p.referenceTimingCoherent = false; p.referenceTimingKnown = true; p.referenceTimingSpanMs = 9000;
      withNative({ state: 'MONITORING', autoCalEnabled: 1, updatedAt: old });
      break;
    }
    case 'época nova': {
      const fields = snap.fields.map(f => (/BUF|ACQUIRED|NUM_BUF/.test(f.key) ? { ...f, status: 'STALE_EPOCH', rawValues: [], physicalValues: [] } : f));
      p.snapshot = { ...snap, fields };
      p.nativeSnapshot = { ...p.nativeSnapshot, fields };
      p.liveAcquisitionEpoch = { available: true, epoch: 2, startedAtMs: now - 5000, reason: 'AUTOMATCH_NATIVO' };
      withNative({ state: 'MONITORING', autoCalEnabled: 1 });
      break;
    }
    default: throw new Error(name);
  }
  return p;
}
const STATES = ['monitorando', 'pausado', 'snapshot parcial', 'leitura atrasada', 'época nova'];
const FRAMES = realFrames(SESSION, f => f.fuel === 'GNV' && f.petrol_ms > 2);

function prepared(name) {
  const w = new L.World();
  w.setFrame(FRAMES[30]);
  w.projection = projectionFor(name, w.clock.now);
  const app = L.boot({ world: w });
  app.go('autocal');
  app.settle(4);
  return app;
}
const SCREEN = '[data-screen="autocal"]';

for (const name of STATES) {
  test(`M3 ${name}: tela limpa, título do estado e gráfico sem NaN`, () => {
    const app = prepared(name);
    L.assertClean(app, `M3/${name}`);
    const text = app.$(SCREEN).textContent.replace(/\s+/g, ' ');
    assert.ok(text.length > 100, 'a aba AutoCal desenhou algo');
    const title = app.byId('autocalHumanTitle');
    assert.ok(title && title.textContent.trim() && title.textContent.trim() !== '—', `título humano do AutoCal vazio em "${name}"`);
    app.destroy();
  });
}

test('M3 pausado × monitorando: o app diz o estado certo e o botão faz a AÇÃO OPOSTA na ponte', () => {
  const on = prepared('monitorando');
  const off = prepared('pausado');
  assert.notEqual(on.byId('autocalHumanTitle').textContent, off.byId('autocalHumanTitle').textContent, 'pausado e adquirindo têm o mesmo título');
  assert.match(off.byId('autocalHumanTitle').textContent, /paus/i);
  assert.doesNotMatch(on.byId('autocalHumanTitle').textContent, /paus/i);
  for (const [app, expected] of [[on, false], [off, true]]) {
    const toggle = app.$('[data-autocal-toggle]');
    assert.ok(toggle, 'botão de alternar aquisição ausente');
    const mark = app.world.mark();
    toggle.click(); app.flush();
    const calls = app.world.since(mark).filter(c => c.method === 'setAcquisitionEnabled');
    assert.equal(calls.length, 1, 'alternar = UMA chamada setAcquisitionEnabled');
    assert.equal(calls[0].args[0], expected, `em ${expected ? 'pausado' : 'monitorando'} o toque deve enviar enabled=${expected}`);
  }
});

test('M3 época nova: pontos adquiridos da época velha não viram bolinhas no gráfico', () => {
  const stale = prepared('época nova');
  const ok = prepared('monitorando');
  const dots = app => app.$$('[data-autocal-acquired-index]').length;
  assert.ok(dots(ok) > 0, 'controle: o estado monitorando deve ter pontos adquiridos no gráfico (fixture real)');
  assert.ok(dots(stale) < dots(ok), `época nova mostra ${dots(stale)} pontos adquiridos (monitorando mostra ${dots(ok)}): dado de época velha está sendo desenhado`);
});

test('M3 snapshot parcial: não inventa números (nenhum campo MISSING vira 0 na tela)', () => {
  const app = prepared('snapshot parcial');
  const text = app.$(SCREEN).textContent;
  assert.doesNotMatch(text, /\b0\/0\b/, 'ratio 0/0 na tela');
  L.assertClean(app, 'M3/parcial');
});

test('M3 sem ponte AutoCal (OmegasAutoCal ausente): mostra indisponível, sem exceção', () => {
  const w = new L.World();
  w.autocalAvailable = false;
  const app = L.boot({ world: w });
  app.go('autocal'); app.go('refino'); app.go('autocal');
  app.settle(3);
  L.assertClean(app, 'M3/sem ponte');
  assert.match(app.$(SCREEN).textContent, /indispon|sem liga|bridge/i);
});

test('M3 projeção não confiável (ok:false): estado "sem estado confiável", sem exceção', () => {
  const w = new L.World();
  w.projection = { ok: false, error: 'Monitor sem resposta', source: 'NONE', snapshot: { available: false } };
  const app = L.boot({ world: w });
  app.go('autocal'); app.settle(4);
  L.assertClean(app, 'M3/não confiável');
  assert.match(app.byId('autocalHumanTitle').textContent, /sem estado|indispon|erro|aguard/i);
});

test('M3 idempotência: a mesma projeção duas vezes não redesenha o gráfico nem muda o DOM', () => {
  const app = prepared('monitorando');
  const sched = app.win.OmegasApp.scheduler;
  sched.run();
  const dom = L.serialize(app.doc.body);
  const writes = app.chartWrites('autocalReferenceChart');
  sched.run(); sched.run(); sched.run();
  assert.equal(L.serialize(app.doc.body), dom, 'DOM mudou ao reaplicar a mesma projeção');
  assert.equal(app.chartWrites('autocalReferenceChart'), writes, 'o gráfico foi redesenhado sem dado novo');
});

for (const name of STATES) {
  test(`M3 ${name}: nenhum elemento tocável da aba AutoCal está congelado`, () => {
    const r = L.sweep({ prepare: () => prepared(name), within: SCREEN, allow: () => '' });
    assert.deepEqual(r.failures, [], `${r.exercised}/${r.total} elementos`);
    assert.ok(r.total >= 4);
  });
}

test('M3 reset do AutoCal vai pela Curva K em UM toque (foto antes, depois zera)', () => {
  const app = prepared('monitorando');
  const reset = app.$('[data-autocal-action="RESET_K_FACTOR"]');
  assert.ok(reset, 'ação de reset da Curva K ausente no AutoCal');
  const mark = app.world.mark();
  reset.click();
  app.settle(8);
  assert.equal(app.route(), 'curve', 'o reset do AutoCal precisa levar à Curva K (único caminho)');
  const calls = app.world.since(mark).map(c => c.method).filter(m => /startCurve/.test(m));
  const photo = calls.indexOf('startCurveBackup');
  const zero = calls.indexOf('startCurveReset');
  assert.ok(calls.indexOf('startCurveRead') >= 0, 'lê a curva antes');
  assert.ok(photo >= 0 && zero > photo, `foto antes de zerar: ${calls.join(' > ')}`);
  assert.equal(calls.filter(m => m === 'startCurveReset').length, 1, 'zera uma única vez');
});

test('M3 fuzz da projeção AutoCal: nunca exceção, nunca NaN/undefined na tela, recupera', () => {
  const { cases, effective, failures } = L.fuzz({
    prepare: () => prepared('monitorando'),
    methods: ['getUiProjection', 'getNativeActionStatus'],
    maxPathsPerMethod: 70,
  });
  assert.ok(effective > cases * 0.2, `fuzz inócuo: ${effective}/${cases}`);
  assert.ok(cases > 200, `casos ${cases}`);
  assert.deepEqual(failures.slice(0, 6), [], `${failures.length} falhas em ${cases} casos`);
});
