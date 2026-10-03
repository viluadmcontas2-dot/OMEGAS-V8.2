'use strict';
// M1 · conexão: sem cabo / conectando / conectado / telemetria velha (> 1.5 s) / ECU muda.
// Falha quando o app se comporta mal EM USO: botão congelado, número 0 no lugar de "—", exceção, estado indistinguível.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const { todo } = require('./wiring/registry.cjs');
const { realFrames } = require('./wiring/world.cjs');

const FRAMES = realFrames('ref_', f => f.fuel === 'GASOLINA' && f.petrol_ms > 2);

function scenario(name) {
  const w = new L.World();
  w.setFrame(FRAMES[10]);
  switch (name) {
    case 'sem cabo':
      Object.assign(w.status, { usbConnected: false, engineRunning: false, fuelState: '--', usbDevice: '', ecuState: 'DISCONNECTED' });
      w.telemetry = { sequence: 1, updatedAt: 0, valid: false, live: {} };
      break;
    case 'conectando':
      Object.assign(w.status, { usbConnected: false, usbPermissionPending: true, engineRunning: false, fuelState: '--', usbDevice: 'MP48', ecuState: 'PERMISSION' });
      w.telemetry = { sequence: 1, updatedAt: 0, valid: false, live: {} };
      break;
    case 'conectado': break;
    case 'telemetria velha': w.staleBy = 3000; break;
    case 'ECU muda':
      Object.assign(w.status, { usbConnected: true, engineRunning: true, ecuState: 'NO_RESPONSE', fuelState: '--' });
      w.telemetry = { sequence: 1, updatedAt: 0, valid: false, live: {} };
      break;
    default: throw new Error(name);
  }
  return w;
}
const STATES = ['sem cabo', 'conectando', 'conectado', 'telemetria velha', 'ECU muda'];

function prepared(name, route = 'dashboard') {
  const w = scenario(name);
  const app = L.boot({ world: w });
  app.go(route);
  if (w.staleBy) app.advance(w.staleBy);
  app.settle(2);
  return app;
}

const ZERO = /^\s*0([.,]0+)?\s*(ms|bar)?\s*$/;
/** Valores numéricos do Agora que NÃO podem mostrar 0 quando a fonte é desconhecida (ids estáveis do dashboard, se existirem). */
const UNKNOWN_NUMBER_IDS = ['dashHeroPetrol', 'dashPetrol', 'dashRpm', 'dashMap', 'dashGas', 'dashLevelsRaw', 'dashCell', 'dashAge'];

for (const name of STATES) {
  test(`M1 ${name}: tela limpa, sem exceção e com o estado certo no trilho`, () => {
    const app = prepared(name);
    L.assertClean(app, `M1/${name}`);
    const ecu = app.byId('globalEcu');
    assert.ok(ecu, 'o trilho tem o indicador de ECU (#globalEcu)');
    const online = name === 'conectado' || name === 'telemetria velha' || name === 'ECU muda';
    assert.equal(ecu.dataset.online, online ? 'true' : 'false', `#globalEcu data-online em "${name}"`);
    assert.match(ecu.textContent, online ? /online/i : /offline/i);
  });
}

test('M1 sem telemetria (sem cabo / conectando / ECU muda): nenhum número desconhecido vira 0', () => {
  let checked = 0;
  for (const name of ['sem cabo', 'conectando', 'ECU muda']) {
    const app = prepared(name);
    for (const id of UNKNOWN_NUMBER_IDS) {
      const node = app.byId(id);
      if (!node) continue;
      checked += 1;
      assert.doesNotMatch(node.textContent, ZERO, `#${id} mostra "${node.textContent}" em "${name}" (desconhecido deve ser —)`);
    }
    app.destroy();
  }
  assert.ok(checked >= 3, `a verificação precisa enxergar ao menos 3 números do Agora (viu ${checked})`);
});

test('M1 telemetria velha: o app diz que está velha e deixa de parecer ao vivo', () => {
  const fresh = prepared('conectado');
  const stale = prepared('telemetria velha');
  const attrs = app => [...app.$$('[data-level],[data-state],[data-fuel-state]')].map(e => `${e.id || e.localName}:${e.dataset.level || e.dataset.state || e.dataset.fuelState}`).sort().join('|');
  const dash = app => app.$('[data-screen="dashboard"]').textContent.replace(/\s+/g, ' ');
  assert.notEqual(attrs(fresh) + dash(fresh).replace(/\d+[,.]?\d* (ms|s)\b/g, ''), attrs(stale) + dash(stale).replace(/\d+[,.]?\d* (ms|s)\b/g, ''), 'telemetria com 3 s de idade é indistinguível da fresca');
  assert.match(dash(stale), /atras|expir|velh|sem telemetria|antig|parad/i, 'o Agora não avisa que a telemetria está velha');
  assert.doesNotMatch(dash(fresh), /atras|expir|velh|antig/i, 'telemetria fresca não pode avisar atraso');
  // passou mais tempo: piora (nunca volta a "ok")
  stale.advance(7000); stale.settle(1);
  assert.match(dash(stale), /expir|sem telemetria|parad|atras/i);
});

test('M1 ECU muda (conectado sem quadros): o app não finge leitura ao vivo', { ...todo('DEFECT-10') }, () => {
  const app = prepared('ECU muda');
  const txt = app.$('[data-screen="dashboard"]').textContent;
  assert.doesNotMatch(txt, /operação estável|leitura em tempo real/i, 'conectado sem nenhum quadro não pode dizer "operação estável"');
});

test('M1 conectando (permissão USB pendente) é distinguível de sem cabo', { ...todo('DEFECT-1') }, () => {
  const a = prepared('sem cabo');
  const b = prepared('conectando');
  const visible = app => app.doc.body.textContent.replace(/\s+/g, ' ');
  assert.notEqual(visible(a), visible(b), 'usbPermissionPending=true não muda nada na tela: o dono não sabe que falta autorizar o USB');
});

test('M1 recuperação: cabo some e volta, a tela volta ao normal sem recarregar', () => {
  const w = scenario('conectado');
  const app = L.boot({ world: w });
  app.settle(2);
  const before = app.byId('globalEcu').dataset.online;
  Object.assign(w.status, { usbConnected: false });
  w.telemetry = { ...w.telemetry, valid: false, updatedAt: 0 };
  app.settle(4);
  assert.equal(app.byId('globalEcu').dataset.online, 'false');
  Object.assign(w.status, { usbConnected: true });
  w.setFrame(FRAMES[20]);
  app.settle(4);
  assert.equal(app.byId('globalEcu').dataset.online, before);
  L.assertClean(app, 'M1/recuperação');
});

for (const name of STATES) {
  test(`M1 ${name}: nenhum botão do trilho/Agora está congelado`, () => {
    const r = L.sweep({
      prepare: () => prepared(name),
      allow: (desc, el, app) => (el.attrs.get('data-route') === app.route() && /\bactive\b/.test(el.attrs.get('class') || '') ? 'aba já ativa: tocar de novo não muda nada' : ''),
    });
    assert.deepEqual(r.failures, [], `${name}: ${r.exercised}/${r.total} elementos`);
    assert.ok(r.total >= 8, 'o trilho (7 abas) tem que ser enxergado');
  });
}

test('M1 fuzz das respostas de status/telemetria/identidade: nunca exceção, nunca NaN/undefined/null na tela, sempre se recupera', () => {
  const { cases, effective, failures } = L.fuzz({
    prepare: () => prepared('conectado'),
    methods: ['getStatus', 'getPresentSnapshotIfChanged', 'getPresentSnapshot', 'getReleaseIdentity'],
    perTick: app => app.world.setFrame(FRAMES[(app.world.telemetry.sequence * 7) % FRAMES.length]),
  });
  assert.ok(effective > cases * 0.25, `fuzz inócuo: só ${effective}/${cases} casos mudaram a resposta`);
  assert.ok(cases > 100, `casos de fuzz: ${cases}`);
  assert.deepEqual(failures.slice(0, 6), [], `${failures.length} falha(s) em ${cases} casos`);
});
