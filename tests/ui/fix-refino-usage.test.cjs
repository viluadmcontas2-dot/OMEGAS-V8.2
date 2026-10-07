'use strict';
// Fix UI · Refino (achados 1, 3, 7, 10, 16). Teste de USO: DOM real do index.html + pontes falsas com o JSON EXATO do Kotlin.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const W = require('./wiring/world.cjs');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const UI = process.env.UI_ROOT || path.join(__dirname, '../../app/src/main/assets/ui');
const KT = path.join(__dirname, '../../app/src/main/java/com/omegas/prohub');

function refinoApp(mutate) {
  const w = new W.World();
  w.setFrame(W.realFrames('automatch_', f => f.fuel === 'GNV' && f.petrol_ms > 2)[10]);
  w.equivalence = W.equivalenceFor('COLETANDO_NOSSOS');
  if (mutate) mutate(w);
  w.refined = W.refinedAnalysis(false, w.curve);
  w.projection = W.realProjection('automatch_', 11);
  const app = L.boot({ world: w });
  app.go('refino');
  app.settle(4);
  return app;
}

test('1. contrato Kotlin → JS: index é escalar 0..1 e provisional/coverage são irmãos planos (EquivalenceJson.kt)', () => {
  const kotlin = fs.readFileSync(path.join(KT, 'equivalence/EquivalenceJson.kt'), 'utf8');
  assert.match(kotlin, /\.put\("index", num\(result\.index\)\)/, 'o Kotlin emite index como número');
  assert.match(kotlin, /\.put\("provisional", result\.provisional\)/);
  assert.match(kotlin, /\.put\("coverage", result\.coverage\)/);
  // JSON exatamente como JSONObject.toString() do Kotlin entrega
  const wire = JSON.parse('{"ok":true,"format":"EQ1","available":true,"index":0.74,"coverage":0.62,"provisional":true,"nextAction":{"kind":"COLLECT","text":"Rode no GNV","route":null,"subpage":null,"pointIndexes":[1]},"points":[],"reference":{"frozen":false,"canFreeze":true},"automatic":false}');
  const app = refinoApp(w => { w.equivalence = { ...W.equivalenceFor('COLETANDO_NOSSOS'), ...wire }; });
  const strip = app.win.OmegasUi.RefinoModel.equivalenceStrip;
  assert.equal(strip(app.world.equivalence).indexText, '74% da condução já equivale à gasolina · provisório', 'o modelo lê index escalar 0..1');
  assert.doesNotMatch(app.text(), /da condução já equivale/, 'a tela não mostra mais o cartão de %: uma frase e uma ação');
  app.destroy();
});

test('1. índice null (sem cérebro) mostra "—" e o formato antigo (objeto) nunca vira número', () => {
  let app = refinoApp(w => { w.equivalence = { ...W.equivalenceFor('COLETANDO_NOSSOS'), index: null, provisional: true }; });
  assert.equal(app.win.OmegasUi.RefinoModel.equivalenceStrip(app.world.equivalence).indexText, '— da condução já equivale à gasolina');
  app.destroy();
  app = refinoApp(w => { w.equivalence = { ...W.equivalenceFor('COLETANDO_NOSSOS'), index: { value: 0.5, provisional: false } }; });
  assert.equal(app.win.OmegasUi.RefinoModel.equivalenceStrip(app.world.equivalence).indexText, '— da condução já equivale à gasolina', 'objeto não é o contrato: sem número inventado');
  L.assertClean(app, 'refino índice objeto');
  app.destroy();
  app = refinoApp(w => { w.equivalence = { ...W.equivalenceFor('COLETANDO_NOSSOS'), index: 0.01, provisional: false }; });
  assert.equal(app.win.OmegasUi.RefinoModel.equivalenceStrip(app.world.equivalence).indexText, '1% da condução já equivale à gasolina');
  app.destroy();
});

test('3. motor apagado AGORA: RPM desconhecido ou velho não vira vermelho; a cor acompanha a leitura sem novo render', () => {
  const stalls = { count: 1, nearCount: 0, regions: [], events: [], restartedCount: 0 };
  const app = refinoApp(w => {
    w.equivalence = { ...W.equivalenceFor('COLETANDO_NOSSOS'), stalls };
    w.setFrame({ rpm: 0, load_bar: 0.3, petrol_ms: 0, gas_ms_diagnostic: null, fuel: 'GNV' });
  });
  const node = () => app.byId('refinoStalls');
  app.advance(1200);
  assert.equal(node().dataset.tone, 'now', 'rpm 0 medido e fresco = apagado agora');
  // a leitura para de chegar (valid=true, idade crescendo): RPM deixa de ser conhecido, sem cor de "agora"
  app.advance(5000);
  assert.equal(node().dataset.tone, 'history', 'sem leitura fresca nunca é "apagado agora"');
  // sem nenhuma leitura: RPM desconhecido não é 0
  app.world.telemetry = { ...app.world.telemetry, valid: false, updatedAt: 0 };
  app.advance(1500);
  assert.equal(node().dataset.tone, 'history');
  // volta a ler com o motor ligado
  app.world.setFrame({ rpm: 1800, load_bar: 0.4, petrol_ms: 3.1, gas_ms_diagnostic: null, fuel: 'GNV' });
  app.advance(1500);
  assert.equal(node().dataset.tone, 'history');
  app.destroy();
});

test('7. scheduler.start() rearma o quadro de animação depois do segundo plano (cursores AGORA não congelam)', () => {
  const frames = [];
  const root = { requestAnimationFrame: fn => { frames.push(fn); return frames.length; }, cancelAnimationFrame: () => {}, setInterval: () => 1, clearInterval: () => {} };
  root.window = root;
  vm.createContext(root);
  vm.runInContext(fs.readFileSync(path.join(UI, 'core/scheduler.js'), 'utf8'), root);
  const scheduler = new root.OmegasUi.Scheduler({ intervalMs: 200 });
  let painted = 0;
  scheduler.addFrameHook(() => { painted += 1; });
  assert.equal(frames.length, 1);
  frames.shift()(1);
  assert.equal(painted, 1);
  assert.equal(frames.length, 1, 'quadro seguinte armado');
  scheduler.stop(); // app foi para o segundo plano
  frames.length = 0;
  scheduler.start(); // voltou
  assert.equal(frames.length, 1, 'start() rearma o quadro');
  frames.shift()(2);
  assert.equal(painted, 2, 'o cursor volta a andar');
});

test('7. voltando do segundo plano o cursor AGORA do Refino volta a andar (app inteiro)', () => {
  const app = refinoApp();
  app.advance(600);
  const before = app.win.OmegasApp.scheduler.frameHandle;
  app.doc.hidden = true;
  app.doc.dispatchEvent(new app.win.Event('visibilitychange'));
  assert.equal(app.win.OmegasApp.scheduler.frameHandle, null, 'oculto: sem quadro');
  app.doc.hidden = false;
  app.doc.dispatchEvent(new app.win.Event('visibilitychange'));
  app.advance(900);
  // Revisto (P1-6): o laço de quadros não é mais eterno; com o cursor parado no alvo ele dorme.
  // O que importa é que leitura nova depois do segundo plano acorde o laço e mova o cursor.
  const layer = () => app.$('#refinoChart [data-chart-live]');
  const transformBefore = layer() && layer().style.transform;
  app.world.setFrame({ rpm: 2600, load_bar: 0.72, petrol_ms: 5.4, gas_ms_diagnostic: null, fuel: 'GNV' });
  app.advance(1000);
  assert.ok(layer(), 'camada AGORA do Refino presente');
  assert.notEqual(layer().style.transform, transformBefore, 'visível de novo e leitura nova: o cursor andou');
  const cursor = app.win.OmegasApp.refino.cursor;
  assert.deepEqual([cursor.pos.x, cursor.pos.y], [cursor.target.x, cursor.target.y], 'chegou no alvo');
  assert.equal(app.win.OmegasApp.scheduler.frameHandle, null, 'parado no alvo: o laço de quadros dorme (sem 60 Hz eterno)');
  assert.ok(before !== undefined);
  app.destroy();
});

test('10. Desfazer do Refino só com o que desfazer: mostra a idade da foto e some quando a ECU mudou por fora', () => {
  const now = () => 1_790_000_000_000;
  const latest = (extra) => ({ status: 'VERIFICADO', photoFile: 'foto-1.json', appliedAt: now() - 12 * 60000, source: 'Refino OMEGAS: curva refinada confirmada', beforeRaw: W.bentRaws(), afterRaw: W.bentRaws().map((v, i) => (i === 10 ? v + 400 : v)), bands: [], ...extra });
  let app = refinoApp(w => { w.equivalence = W.equivalenceFor('COLETANDO_NOSSOS', { latest: latest() }); });
  const undo = () => app.$('[data-refino-undo]');
  assert.ok(undo(), 'há o que desfazer');
  assert.equal(undo().textContent.trim(), 'Desfazer', 'um único botão discreto, sem letras miúdas nem idade da foto');
  app.destroy();
  app = refinoApp(w => { w.equivalence = W.equivalenceFor('COLETANDO_NOSSOS', { latest: latest({ status: 'INTERROMPIDO' }) }); });
  assert.equal(app.$('[data-refino-undo]'), null, 'a ECU mudou a curva por fora: a foto velha não é oferecida');
  assert.equal(app.byId('refinoUndo').hasAttribute('hidden'), true);
  app.destroy();
  app = refinoApp(w => { w.equivalence = W.equivalenceFor('COLETANDO_NOSSOS', { latest: latest({ source: 'Refino OMEGAS: desfazer última gravação' }) }); });
  assert.equal(app.$('[data-refino-undo]'), null, 'o último registro já foi um Desfazer: nada a desfazer');
  app.destroy();
  app = refinoApp(w => { w.equivalence = W.equivalenceFor('COLETANDO_NOSSOS', { latest: null }); });
  assert.equal(app.$('[data-refino-undo]'), null, 'sem registro, sem botão');
  app.destroy();
  app = refinoApp(w => { w.equivalence = W.equivalenceFor('COLETANDO_NOSSOS', { latest: latest({ status: 'FALHA_PARCIAL' }) }); });
  assert.ok(app.$('[data-refino-undo]'), 'falha parcial: Desfazer continua disponível');
  app.destroy();
});

test('16. textos do Refino: "Diferença GNV × gasolina" com sinal e %, "GNV igual à gasolina", cabeçalho sem pulo, eixo MAP com 3 casas', () => {
  const app = refinoApp(w => { w.equivalence = { ...W.equivalenceFor('COLETANDO_NOSSOS'), ratio: 1.034 }; });
  const text = app.text();
  assert.match(text, /Diferença GNV × gasolina/);
  assert.doesNotMatch(text, /foto de há|\d+ min|visitas|intervalos/, 'o leigo não vê regras internas (minutos, visitas, contagens)');
  assert.doesNotMatch(text, /Erro GNV/);
  assert.equal(app.byId('refinoRatio').textContent, '+3,4%');
  const source = fs.readFileSync(path.join(UI, 'screens/refino.js'), 'utf8');
  assert.doesNotMatch(source, /Chegou na gasolina/);
  assert.match(source, /GNV igual à gasolina/);
  const css = fs.readFileSync(path.join(UI, 'styles-autocal-refino.css'), 'utf8');
  assert.match(css, /\[data-refino-primary\]\[hidden\]\s*\{[^}]*visibility:\s*hidden/, 'o botão principal reserva o lugar: o cabeçalho não pula');
  const chart = fs.readFileSync(path.join(UI, 'components/curve-chart.js'), 'utf8');
  assert.match(chart, /autocal-axis-tick-y[^`]*\$\{tick\(v, 3\)\}/, 'eixo MAP com 3 casas');
  app.destroy();
});

test('P1-6: o laço de quadros para quando todos chegam no alvo (devolvem false) e wake() rearma', () => {
  const frames = [];
  const root = { requestAnimationFrame: fn => { frames.push(fn); return frames.length; }, cancelAnimationFrame: () => {}, setInterval: () => 1, clearInterval: () => {} };
  root.window = root;
  vm.createContext(root);
  vm.runInContext(fs.readFileSync(path.join(UI, 'core/scheduler.js'), 'utf8'), root);
  const scheduler = new root.OmegasUi.Scheduler({ intervalMs: 200 });
  let moving = 2;
  scheduler.addFrameHook(() => (moving-- > 0));
  frames.shift()(1); frames.shift()(2);
  assert.equal(frames.length, 1, 'ainda andando: rearma');
  frames.shift()(3);
  assert.equal(frames.length, 0, 'chegou no alvo: o laço dorme');
  moving = 1;
  scheduler.wake();
  assert.equal(frames.length, 1, 'alvo novo: wake() rearma');
  scheduler.wake();
  assert.equal(frames.length, 1, 'sem pedido duplicado');
});
