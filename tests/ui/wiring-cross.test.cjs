'use strict';
// Transversal: determinismo do replay, ida-e-volta entre abas, listeners estáveis e um "macaco" com semente
// (toques aleatórios + falhas aleatórias de cabo/ECU) que nunca pode derrubar o app nem sujar a tela.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const W = require('./wiring/world.cjs');

const ROUTES = ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools', 'diagnostico'];
const AGE = /\d+([.,]\d+)? ?(ms|s|min|h)\b/g;
// A idade anda com o relógio: "agora" / "há N s" também são idade (palavras do glossário).
const norm = html => html.replace(/(Último dado <b>)[^<]*(<\/b>)/g, '$1#$2').replace(AGE, '#').replace(/class="([^"]*)"/g, (m, c) => `class="${c.split(/\s+/).sort().join(' ')}"`);

function replay(name, from, count) {
  const frames = W.realFrames(name).slice(from, from + count);
  const w = new W.World();
  w.setFrame(frames[0]);
  const app = L.boot({ world: w });
  app.settle(2);
  for (const frame of frames) { w.setFrame(frame); app.advance(200); }
  app.advance(300); app.flush();
  const out = JSON.stringify(app.state(), (k, v) => (typeof v === 'number' && !Number.isFinite(v) ? String(v) : v)) + '|' + norm(L.serialize(app.doc.body));
  const errors = L.errorsSince(app);
  app.destroy();
  return { out, errors };
}

test('replay de sessão REAL duas vezes pela camada de JS: store e tela finais idênticos (determinismo)', () => {
  for (const [name, from] of [['ref_', 200], ['automatch_', 800], ['gnv_only_', 100]]) {
    const a = replay(name, from, 250);
    const b = replay(name, from, 250);
    assert.deepEqual(a.errors, []);
    assert.equal(a.out.length > 2000, true);
    assert.equal(a.out, b.out, `${name}: o mesmo replay deu estados diferentes`);
  }
});

test('ida e volta entre TODAS as abas: A→B→A reproduz a tela de A (7×6 combinações)', () => {
  const w = new W.World();
  w.setFrame(W.realFrames('automatch_', f => f.fuel === 'GNV')[20]);
  w.sessions = [{ id: 'session_2026-10-01_08-00-00', reason: 'USB', durationMs: 60000, bytes: 10, cngTicks: 5, petrolTicks: 5, semanticSummary: { blackouts: 0 } }];
  w.equivalence = W.equivalenceFor('COLETANDO_NOSSOS');
  w.refined = W.refinedAnalysis(true, w.curve);
  w.projection = W.realProjection('automatch_', 11);
  const app = L.boot({ world: w });
  const screen = r => norm(L.serialize(app.$(`[data-screen="${r}"]`)));
  const frame = W.realFrames('automatch_', f => f.fuel === 'GNV')[20];
  const visit = r => { w.setFrame(frame); app.go(r); };
  for (const r of ROUTES) visit(r); // volta de aquecimento: o que depende de tempo (ex.: fase do refino a cada 3 s) assenta
  app.advance(4000);
  for (const a of ROUTES) {
    visit(a);
    const first = screen(a);
    for (const b of ROUTES) {
      if (a === b) continue;
      visit(b); visit(a);
      assert.equal(screen(a), first, `${a}→${b}→${a} mudou a tela de ${a}`);
    }
  }
  L.assertClean(app, 'ida-e-volta');
});

test('reentrar nas abas não empilha listeners (5 voltas completas pelas 7 abas)', () => {
  const app = L.boot({ world: new W.World() });
  for (const r of ROUTES) app.go(r);
  for (const r of ROUTES) app.go(r);
  const base = app.listenerTotal();
  const timers = app.timers.size;
  for (let i = 0; i < 5; i += 1) for (const r of ROUTES) app.go(r);
  assert.equal(app.listenerTotal(), base, 'listeners de DOM crescem a cada volta');
  assert.equal(app.timers.size, timers, 'timers crescem a cada volta');
});

test('um único timer de interface vivo (scheduler) em todas as abas', () => {
  const app = L.boot({ world: new W.World() });
  for (const r of ROUTES) { app.go(r); assert.equal(app.timers.size, 1, `${r}: ${app.timers.size} timers`); }
});

test('tocar duas vezes no mesmo botão do trilho = uma navegação, sem chamada extra de ponte além da leitura da aba', () => {
  const app = L.boot({ world: new W.World() });
  app.go('sessions');
  const mark = app.world.mark();
  const btn = app.$('.side-nav [data-route="tools"]');
  btn.click(); btn.click(); app.flush();
  assert.equal(app.route(), 'tools');
  assert.deepEqual(L.actionCalls(app, mark), []);
});

// ---------------------------------------------------------------- macaco com semente
function prng(seed) {
  let a = seed >>> 0;
  return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const OUTCOMES = ['ok', 'ok', 'ok', 'transport', 'nack', 'partial', 'readback'];

function monkey(seed, steps) {
  const rnd = prng(seed);
  const w = new W.World({ opPolls: 1 + Math.floor(rnd() * 3) });
  const frames = W.realFrames('automatch_').filter(f => f.fuel === 'GNV' || f.fuel === 'GASOLINA');
  w.setFrame(frames[0]);
  w.sessions = [{ id: 'session_2026-10-01_08-00-00', reason: 'USB', durationMs: 60000, bytes: 10, cngTicks: 5, petrolTicks: 5, semanticSummary: { blackouts: 0 } }];
  w.equivalence = W.equivalenceFor(W.PHASES[Math.floor(rnd() * W.PHASES.length)], { expiredFrom: 'PROPOSTA_PRONTA' });
  w.refined = W.refinedAnalysis(rnd() > 0.5, w.curve);
  w.projection = W.realProjection('automatch_', 11);
  w.logs = [{ time: '10:00', level: 'INFO', category: 'ECU', message: 'ok' }];
  const app = L.boot({ world: w });
  const log = [];
  const base = app.listenerTotal();
  for (let step = 0; step < steps; step += 1) {
    const r = rnd();
    if (r < 0.1) { w.setFrame(frames[Math.floor(rnd() * frames.length)]); }
    else if (r < 0.16) { for (const k of Object.keys(w.outcome)) w.outcome[k] = OUTCOMES[Math.floor(rnd() * OUTCOMES.length)]; }
    else if (r < 0.19) { w.status.usbConnected = !w.status.usbConnected; }
    else if (r < 0.22) { w.equivalence = W.equivalenceFor(W.PHASES[Math.floor(rnd() * W.PHASES.length)], { expiredFrom: ['PROPOSTA_PRONTA', 'ECU_TRABALHANDO'][Math.floor(rnd() * 2)] }); w.refined = W.refinedAnalysis(rnd() > 0.4, w.curve); }
    else if (r < 0.24) { const m = ['getLastOperation', 'getUiProjection', 'getEquivalence', 'listRecordedSessions'][Math.floor(rnd() * 4)]; if (w.throwOn.has(m)) w.throwOn.delete(m); else w.throwOn.add(m); }
    else if (r < 0.34) { app.advance(100 + Math.floor(rnd() * 1500)); }
    else {
      const els = L.interactives(app);
      if (!els.length) continue;
      const el = els[Math.floor(rnd() * els.length)];
      log.push(`${step}:${L.describe(el)}`);
      const info = L.tap(app, el);
      if (info.error) return { seed, failure: `exceção ao tocar ${info.desc}: ${info.error}`, log: log.slice(-6) };
    }
    const problems = L.pageProblems(app);
    const errs = L.errorsSince(app);
    if (errs.length) return { seed, failure: `exceção: ${errs[0].slice(0, 250)}`, log: log.slice(-6) };
    if (problems.length) return { seed, failure: problems[0], log: log.slice(-6) };
    if (!ROUTES.includes(app.route())) return { seed, failure: `rota inválida ${app.route()}`, log: log.slice(-6) };
    if (require('./wiring/harness.cjs').unhandled.length) return { seed, failure: 'rejeição não tratada', log: log.slice(-6) };
  }
  // depois do caos, com tudo saudável, o app se recupera: tudo verde e ainda responde
  w.throwOn.clear(); for (const k of Object.keys(w.outcome)) w.outcome[k] = 'ok';
  w.status.usbConnected = true; w.setFrame(frames[5]);
  app.advance(5000); app.settle(4);
  const grown = app.listenerTotal() - base;
  return { seed, failure: L.errorsSince(app).length ? `após o caos: ${L.errorsSince(app)[0].slice(0, 200)}` : L.pageProblems(app)[0] || (grown > 600 ? `listeners cresceram ${grown}` : ''), log: log.slice(-6) };
}

for (const seed of [11, 23, 37, 51, 73, 97]) {
  test(`macaco (semente ${seed}): 160 passos de toques e falhas aleatórias — sem exceção, sem NaN/undefined, se recupera`, () => {
    const r = monkey(seed, 160);
    assert.equal(r.failure, '', `${r.failure} | últimos toques: ${r.log.join(' ; ')}`);
  });
}
