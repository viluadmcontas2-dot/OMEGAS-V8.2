'use strict';
// M7 · Sessões: vazia / lista / exportar / gravando / falha de listagem.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const W = require('./wiring/world.cjs');
const { todo } = require('./wiring/registry.cjs');

const SCREEN = '[data-screen="sessions"]';
const ITEM = (n, extra) => ({
  id: `session_2026-10-0${(n % 9) + 1}_09-3${n}-00`, createdAt: 1790000000000 + n * 3600000, stoppedAt: 1790000000000 + n * 3600000 + 1800000,
  durationMs: 1800000, reason: 'Conexão USB', bytes: 4_500_000 + n, active: false, cngTicks: 600, petrolTicks: 400,
  semanticSummary: { blackouts: n % 3, eventCount: 1000 }, ...(extra || {}),
});

function sessionsApp({ sessions, status, setup } = {}) {
  const w = new W.World();
  w.sessions = sessions === undefined ? [] : sessions;
  if (status) w.sessionStatus = { ...w.sessionStatus, ...status };
  if (setup) setup(w);
  const app = L.boot({ world: w });
  app.go('sessions');
  app.settle(6);
  return app;
}
const items = app => app.$$('.ss-item');

test('M7 vazia: diz que não há sessão e que ela começa sozinha; sem exceção', () => {
  const app = sessionsApp({ sessions: [] });
  L.assertClean(app, 'M7/vazia');
  assert.equal(items(app).length, 0);
  assert.match(app.$(SCREEN).textContent, /Nenhuma sess[aã]o/i);
  assert.doesNotMatch(app.$(SCREEN).textContent, /Lendo as sess/i, 'lista vazia não pode ficar em "Lendo…"');
});

test('M7 lista: uma linha por sessão com data, duração, apagões e % GNV — números vindos do Kotlin, sem NaN', todo('DEFECT-20'), () => {
  const list = [0, 1, 2].map(n => ITEM(n));
  const app = sessionsApp({ sessions: list });
  L.assertClean(app, 'M7/lista');
  assert.equal(items(app).length, 3);
  const first = items(app)[0].textContent;
  assert.match(first, /30 min/i, `duração 1800000 ms: ${first}`);
  assert.match(first, /60% no GNV/);
  assert.match(first, /Download\/Omegas/, 'cada sessão diz onde está a pasta');
  assert.match(first, /Fechada/);
  assert.match(items(app)[1].textContent, /1 apag[aã]o\b/);
  assert.match(items(app)[2].textContent, /2 apag[oõ]es/);
  assert.match(items(app)[0].textContent, /sem apag[oõ]es/, 'zero apagões medido é dito, não "—"');
  list.forEach((s, i) => assert.equal(items(app)[i].dataset.sessionId, s.id));
});

test('M7 mais de 20 sessões: só as 20 mais recentes na tela', () => {
  const app = sessionsApp({ sessions: Array.from({ length: 27 }, (_, n) => ITEM(n)) });
  assert.equal(items(app).length, 20);
});

test('M7 sem botão Exportar ZIP: as sessões já se exportam sozinhas e a tela diz onde ficam', () => {
  const app = sessionsApp({ sessions: [0, 1, 2].map(n => ITEM(n)) });
  assert.equal(app.$$('[data-export-session]').length, 0);
  assert.doesNotMatch(app.$(SCREEN).textContent, /Exportar ZIP/);
  assert.match(app.$(SCREEN).textContent, /salva sozinha em Download\/Omegas/);
});

test('M7 gravando: a sessão ativa aparece "em andamento" e o cartão diz que está gravando', () => {
  const app = sessionsApp({ sessions: [ITEM(0, { active: true, stoppedAt: 0 })], status: { recording: true, events: 1234, megabytes: 3.2, durationMs: 90000 } });
  assert.match(app.$(SCREEN).textContent, /est[aá] sendo gravada/i);
  assert.match(items(app)[0].textContent, /Gravando/);
  L.assertClean(app, 'M7/gravando');
});

test('M7 desconhecido nunca vira 0: duração/tamanho/ticks ausentes mostram — e sem barra de combustível', () => {
  const app = sessionsApp({ sessions: [{ id: 'session_2026-10-02_10-00-00', reason: 'Conexão USB', durationMs: null, bytes: null, cngTicks: 0, petrolTicks: 0 }], status: { durationMs: null, megabytes: null, events: null } });
  const txt = app.$(SCREEN).textContent;
  assert.doesNotMatch(txt, /\b0m 0s\b/, 'duração desconhecida virou 0m 0s');
  assert.doesNotMatch(txt, /0% no GNV|só na gasolina/, 'sem ticks não há percentual');
  assert.equal(app.$$('.session-fuel-bar').length, 0);
  L.assertClean(app, 'M7/desconhecidos');
});

test('M7 falha ao listar (ponte lança): a tela diz o que houve, não fica em "Lendo…" para sempre', todo('DEFECT-19'), () => {
  const app = sessionsApp({ sessions: [], setup: w => w.throwOn.add('listRecordedSessions') });
  app.advance(10000); app.settle(4);
  assert.doesNotMatch(app.$(SCREEN).textContent, /Lendo as sess/i, 'listagem falhou e a tela continua "Lendo as sessões salvas…"');
});

test('M7 idempotência: a mesma lista duas vezes não muda o DOM; A→B→A idêntico; listeners estáveis', () => {
  const app = sessionsApp({ sessions: [0, 1].map(n => ITEM(n)) });
  const sched = app.win.OmegasApp.scheduler;
  sched.run();
  const dom = L.serialize(app.$(SCREEN));
  sched.run(); sched.run();
  assert.equal(L.serialize(app.$(SCREEN)), dom);
  app.go('tools'); app.go('sessions');
  assert.equal(L.serialize(app.$(SCREEN)), dom, 'voltar às Sessões mudou a tela');
  const base = app.listenerTotal();
  for (let i = 0; i < 6; i += 1) { app.go('tools'); app.go('sessions'); }
  assert.equal(app.listenerTotal(), base);
});

for (const name of ['vazia', 'lista', 'gravando']) {
  test(`M7 ${name}: nenhum elemento tocável das Sessões está congelado`, () => {
    const make = () => sessionsApp(name === 'vazia' ? { sessions: [] } : name === 'lista' ? { sessions: [0, 1, 2].map(n => ITEM(n)) } : { sessions: [ITEM(0, { active: true })], status: { recording: true } });
    const r = L.sweep({ prepare: make, within: SCREEN });
    assert.deepEqual(r.failures, [], `${r.exercised}/${r.total}`);
  });
}

test('M7 fuzz das respostas de sessões: nunca exceção/NaN, recupera', () => {
  const { cases, effective, failures } = L.fuzz({
    prepare: () => sessionsApp({ sessions: [0, 1, 2].map(n => ITEM(n)), status: { recording: true, events: 10, megabytes: 1.5, durationMs: 5000 } }),
    methods: ['listRecordedSessions', 'getSessionRecorderStatus'],
    maxPathsPerMethod: 40,
    perTick: app => { app.world.sessions = app.world.sessions.map(s => ({ ...s })); app.advance(1900); },
  });
  assert.ok(cases > 80, `casos ${cases}`);
  assert.ok(effective > cases * 0.2, `fuzz inócuo ${effective}/${cases}`);
  assert.deepEqual(failures.slice(0, 6), [], `${failures.length}/${cases}`);
});

test('M7 texto vindo do Kotlin é mostrado como TEXTO: marcação HTML no motivo da sessão não vira elemento', () => {
  const evil = '<img src=x><b>negrito</b> & aspas';
  const app = sessionsApp({ sessions: [{ id: 'session_2026-10-02_10-00-00', reason: evil, durationMs: 1000, bytes: 1, cngTicks: 1, petrolTicks: 1, semanticSummary: { blackouts: 0 } }] });
  const item = app.$('.ss-item');
  assert.ok(item.querySelector('img') === null, 'HTML injetado virou elemento');
  assert.equal(item.querySelectorAll('b').length, 1, 'só o título da sessão pode ser <b>');
  assert.match(item.textContent, /<img src=x><b>negrito<\/b>/);
});
