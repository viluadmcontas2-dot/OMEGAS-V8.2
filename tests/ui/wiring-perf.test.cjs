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
