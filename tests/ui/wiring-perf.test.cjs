'use strict';
// Desempenho de uso: nenhum desenhista de quadro (rAF) sobrevive fora da aba que o precisa, e valor igual não escreve no DOM.
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const W = require('./wiring/world.cjs');

function boot() {
  const w = new W.World();
  w.setFrame(W.realFrames('automatch_', f => f.fuel === 'GNV' && f.petrol_ms > 2)[10]);
  w.equivalence = W.equivalenceFor('PROPOSTA_PRONTA');
  w.refined = W.refinedAnalysis(true, w.curve);
  w.projection = W.realProjection('automatch_', 11);
  return L.boot({ world: w });
}
const hooks = app => app.App.scheduler.frameHooks.size;

test('rAF: Refino registra o quadro ao entrar e solta ao sair (nenhum gancho sobra em outra aba)', () => {
  const app = boot();
  app.settle(4);
  assert.equal(hooks(app), 0, 'no Agora não há desenhista de quadro');
  app.go('refino'); app.settle(4);
  assert.equal(hooks(app), 1, 'no Refino há exatamente um');
  app.go('dashboard'); app.settle(4);
  assert.equal(hooks(app), 0, 'saiu do Refino: o gancho foi solto');
  app.go('refino'); app.settle(4);
  assert.equal(hooks(app), 1, 'ao voltar, registra de novo (sem duplicar)');
  app.go('autocal'); app.settle(4);
  assert.ok(hooks(app) <= 1, 'no AutoCal só o do AutoCal');
  app.go('dashboard'); app.settle(4);
  assert.equal(hooks(app), 0);
});

test('DOM: telemetria igual repetida não muda atributo nem texto (o estado do cabeçalho só escreve quando muda)', () => {
  const root = path.resolve(__dirname, '..', '..', 'app/src/main/assets/ui');
  const rules = fs.readFileSync(path.join(root, 'core/display-rules.js'), 'utf8');
  assert.match(rules, /function setAttrIfChanged/);
  const strip = fs.readFileSync(path.join(root, 'components/vehicle-status-strip.js'), 'utf8');
  assert.doesNotMatch(strip, /node\.dataset\.state\s*=/, 'a faixa não escreve dataset.state sem comparar');
  const cockpit = fs.readFileSync(path.join(root, 'screens/autocal-cockpit.js'), 'utf8');
  assert.doesNotMatch(cockpit, /node\.dataset\.current\s*=|bandLayer\.setAttribute\(/, 'o cursor/zonas do AutoCal escrevem só com comparação');
});

test('troca AutoCal↔Refino: um refresh só ao entrar e nenhum redesenho do gráfico na volta', () => {
  const app = boot();
  try {
    app.go('autocal'); app.settle(4);
    app.go('refino'); app.settle(4);
    const enter = route => {
      const mark = app.world.mark();
      app.$(`.side-nav [data-route="${route}"]`).click();
      app.flush();
      return app.world.since(mark);
    };
    const autocalCalls = enter('autocal');
    assert.equal(autocalCalls.filter(c => c.method === 'getUiProjection').length, 1, 'AutoCal: uma leitura da projeção ao entrar');
    const autocalNode = app.$('#autocalReferenceChart .curve-chart-shared');
    const refinoCalls = enter('refino');
    assert.equal(refinoCalls.filter(c => c.method === 'getEquivalence' || c.method === 'getEquivalenceFresh').length, 1, 'Refino: uma leitura ao entrar');
    const renders = app.win.OmegasUi.CurveChart.shared.renders;
    enter('autocal'); enter('refino'); enter('autocal');
    assert.equal(app.win.OmegasUi.CurveChart.shared.renders, renders, 'voltar a uma aba não refaz o SVG');
    if (autocalNode) assert.equal(app.$('#autocalReferenceChart .curve-chart-shared'), autocalNode, 'o nó do AutoCal é reaproveitado');
  } finally { app.destroy(); }
});

test('P1-7: Ferramentas e Sessões não se refazem só porque o relógio andou; o Agora não relê a equivalência a cada 3 s', () => {
  const app = boot();
  try {
    app.go('tools'); app.settle(4);
    const tools = app.chartWrites('toolDiagnosticsWorkspace');
    app.settle(40); // 10 s de relógio: a idade do último dado muda, o resto não
    assert.equal(app.chartWrites('toolDiagnosticsWorkspace'), tools, 'Ferramentas: idade muda no lugar, sem refazer (a rolagem fica)');
    app.go('sessions'); app.settle(4);
    const sessions = app.chartWrites('sessionsHost');
    app.settle(80); // 20 s: a duração da gravação anda
    assert.equal(app.chartWrites('sessionsHost'), sessions, 'Sessões: duração no lugar, o seletor não fecha');
    app.go('dashboard'); app.settle(4);
    const mark = app.world.mark();
    app.settle(40); // 10 s sem a evidência mudar
    const reads = app.world.since(mark).filter(c => c.method === 'getEquivalence').length;
    assert.ok(reads <= 1, `Agora: no máximo uma leitura da equivalência em 10 s sem evidência nova (leu ${reads})`);
  } finally { app.destroy(); }
});

test('P1-8: erro repetido numa tela vira aviso tocável; extensão que não carrega avisa na tela', () => {
  const app = boot();
  try {
    app.go('dashboard'); app.settle(2);
    const scheduler = app.App.scheduler;
    const quiet = app.win.console.error; app.win.console.error = () => {};
    const off = scheduler.addHook('fast', () => { throw new Error('quebrado'); });
    app.settle(6);
    app.win.console.error = quiet;
    off();
    const banner = app.byId('screenErrorBanner');
    assert.ok(banner, 'aviso aparece depois de 3 falhas seguidas');
    assert.equal(banner.textContent, 'Algo falhou nesta tela; toque para recarregar');
    const router = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/core/router.js'), 'utf8');
    assert.match(router, /Esta tela não abriu\. Feche e abra o app\./);
    const api = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/core/autocal-api.js'), 'utf8');
    assert.match(api, /console\.warn\(`\[OMEGAS AutoCalApi\] \$\{name\} falhou:`/);
  } finally { app.destroy(); }
});

// Revisto (W2): sem grupo "Avançado"; as oito abas são botões de primeiro nível (AutoCal incluído).
test('W2 navegação: AutoCal é botão de primeiro nível na barra e as oito abas estão visíveis', () => {
  const app = boot();
  try {
    app.settle(2);
    const routes = app.$$('.side-nav > button[data-route]').map(b => b.dataset.route);
    assert.deepEqual(routes, ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools', 'diagnostico']);
    assert.equal(app.$('[data-nav-advanced]'), null);
    assert.equal(app.$$('.side-nav .nav-advanced-item').length, 0);
    app.$('.side-nav > [data-route="autocal"]').click(); app.settle(2);
    assert.equal(app.route(), 'autocal');
    assert.equal(app.$('.side-nav > [data-route="autocal"]').classList.contains('active'), true);
    assert.equal(app.$('.side-nav [data-route="refino"]').textContent.trim(), 'Ajuste GNV');
  } finally { app.destroy(); }
});
