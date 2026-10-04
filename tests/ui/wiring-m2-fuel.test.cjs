'use strict';
// M2 · combustível: gasolina / GNV / cutoff / desconhecido (+ transição e desligado), com QUADROS REAIS gravados.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const { realFrames } = require('./wiring/world.cjs');

const SESSIONS = ['ref_', 'automatch_', 'gnv_only_'];
const byFuel = {};
for (const s of SESSIONS) {
  for (const f of realFrames(s)) (byFuel[f.fuel] = byFuel[f.fuel] || []).push(f);
}
// Rótulo esperado no trilho (contrato de produto: o dono lê isto no carro).
const LABEL = { GASOLINA: ['GASOLINA'], GNV: ['GNV'], CUTOFF: ['CUTOFF'], DESCONHECIDO: ['—', 'DESCONHECIDO'], TRANSICAO: ['TRANSIÇÃO', 'TRANSICAO'], DESLIGADO: ['DESLIGADO'] };
const ptBR = (n, d) => Number(n).toLocaleString('pt-BR', { minimumFractionDigits: d, maximumFractionDigits: d });

function withFrame(frame, extra) {
  const w = new L.World();
  w.setFrame(frame, extra);
  const app = L.boot({ world: w });
  app.settle(3);
  return app;
}

for (const fuel of Object.keys(LABEL)) {
  test(`M2 ${fuel}: o trilho e o Agora mostram o combustível certo e os números do quadro REAL`, () => {
    assert.ok(byFuel[fuel] && byFuel[fuel].length, `fixture real sem quadros ${fuel}`);
    const frames = byFuel[fuel].filter(f => f.rpm > 0 && f.load_bar != null).slice(0, 3);
    for (const frame of frames.length ? frames : byFuel[fuel].slice(0, 2)) {
      const app = withFrame(frame);
      L.assertClean(app, `M2/${fuel}`);
      const label = app.byId('globalFuel').textContent.trim();
      assert.ok(LABEL[fuel].includes(label), `#globalFuel="${label}" para ${fuel} (esperado ${LABEL[fuel]})`);
      assert.equal(app.byId('globalFuel').dataset.fuel, label);
      // alimentado: RPM e MAP do quadro chegam ao Agora (ids do Agora atual; se sumirem, o teste avisa)
      const rpm = app.byId('dashRpm');
      assert.ok(rpm, '#dashRpm ausente: o Agora perdeu o RPM (ou o id mudou)');
      if (frame.rpm > 0) assert.equal(rpm.textContent, ptBR(Math.round(frame.rpm), 0), `RPM do quadro ${frame.rpm}`);
      const map = app.byId('dashMap');
      if (map && frame.load_bar != null) assert.equal(map.textContent, ptBR(frame.load_bar, 3), 'MAP do quadro (3 casas, GLOSSARIO)');
      const petrol = app.byId('dashHeroPetrol');
      if (petrol && frame.petrol_ms != null) assert.equal(petrol.textContent, ptBR(frame.petrol_ms, 2), 'Petrol Inj. do quadro');
      app.destroy();
    }
  });
}

test('M2 campos desconhecidos (null no quadro E no status) mostram — e nunca 0', () => {
  const base = byFuel.GNV.find(f => f.rpm > 1000);
  for (const field of ['petrol_ms', 'load_bar', 'rpm']) {
    const w = new L.World();
    w.setFrame(base);
    w.telemetry.live[field] = null;
    const statusKey = { petrol_ms: 'petrolMs', load_bar: 'mapBar', rpm: 'rpm' }[field];
    w.status[statusKey] = null;
    const app = L.boot({ world: w });
    app.settle(3);
    const id = { petrol_ms: 'dashHeroPetrol', load_bar: 'dashMap', rpm: 'dashRpm' }[field];
    const node = app.byId(id);
    assert.ok(node, `#${id} ausente`);
    assert.equal(node.textContent.trim(), '—', `${field}=null apareceu como "${node.textContent}" em #${id}`);
    L.assertClean(app, `M2/${field} nulo`);
    app.destroy();
  }
});

test('M2 níveis (level_raw) desconhecidos são — e não 0', () => {
  const w = new L.World();
  w.setFrame(byFuel.GNV[40], { level_raw: null });
  const app = L.boot({ world: w });
  app.settle(3);
  const node = app.byId('dashLevelsRaw');
  if (node) assert.equal(node.textContent.trim(), '—');
});

test('M2 troca de combustível ao vivo com sessão real: o rótulo acompanha cada troca sem exceção (600 quadros)', () => {
  const frames = realFrames('automatch_').slice(300, 900);
  const w = new L.World();
  w.setFrame(frames[0]);
  const app = L.boot({ world: w });
  app.settle(2);
  const seen = new Set();
  let checked = 0;
  frames.forEach((frame, i) => {
    w.setFrame(frame);
    app.advance(200);
    if (i % 3 === 0) {
      const label = app.byId('globalFuel').textContent.trim();
      seen.add(label);
      assert.ok(LABEL[frame.fuel].includes(label), `quadro ${i}: ${frame.fuel} apareceu como "${label}"`);
      checked += 1;
    }
  });
  assert.ok(seen.size >= 2, `a sessão real tem de trocar de combustível (viu ${[...seen]})`);
  L.assertClean(app, 'M2/replay');
  assert.ok(checked > 150);
});

test('M2 o mesmo quadro aplicado duas vezes não muda o DOM (idempotência)', () => {
  const w = new L.World();
  w.setFrame(byFuel.GNV[10]);
  const app = L.boot({ world: w });
  app.settle(3);
  const scheduler = app.win.OmegasApp.scheduler;
  scheduler.run();
  const once = L.serialize(app.doc.body);
  scheduler.run(); scheduler.run();
  assert.equal(L.serialize(app.doc.body), once);
});

for (const fuel of ['GASOLINA', 'GNV', 'CUTOFF', 'DESCONHECIDO']) {
  test(`M2 ${fuel}: nenhum botão do Agora/trilho congelado`, () => {
    const frame = byFuel[fuel].find(f => f.rpm > 800) || byFuel[fuel][0];
    const r = L.sweep({
      prepare: () => withFrame(frame),
      allow: (desc, el, app) => (el.attrs.get('data-route') === app.route() && /\bactive\b/.test(el.attrs.get('class') || '') ? 'aba já ativa' : ''),
    });
    assert.deepEqual(r.failures, []);
  });
}

test('M2 fuzz do combustível e dos números do quadro real: nunca exceção/NaN, recupera', () => {
  const frames = realFrames('ref_').filter(f => f.fuel === 'GNV' || f.fuel === 'GASOLINA');
  const { cases, effective, failures } = L.fuzz({
    prepare: () => withFrame(frames[20]),
    methods: ['getPresentSnapshotIfChanged', 'getPresentSnapshot', 'getStatus'],
    perTick: app => app.world.setFrame(frames[(app.world.telemetry.sequence * 11) % frames.length]),
    maxPathsPerMethod: 45,
  });
  assert.ok(effective > cases * 0.25, `fuzz inócuo: só ${effective}/${cases} casos mudaram a resposta`);
  assert.ok(cases > 150, `casos ${cases}`);
  assert.deepEqual(failures.slice(0, 6), [], `${failures.length} falhas em ${cases} casos`);
});
