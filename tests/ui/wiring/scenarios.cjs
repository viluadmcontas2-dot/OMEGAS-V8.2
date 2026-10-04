'use strict';
// Construtores de cenário reutilizados pelos módulos e pelas probes de defeito.
const { boot } = require('./harness.cjs');
const W = require('./world.cjs');

function curveApp({ outcome, opPolls, raws, route = 'curve' } = {}) {
  const w = new W.World({ opPolls });
  w.setFrame(W.realFrames('ref_', f => f.fuel === 'GNV' && f.petrol_ms > 2)[5]);
  if (raws) w.curve = raws.slice();
  Object.assign(w.outcome, outcome || {});
  const app = boot({ world: w });
  app.go(route);
  app.settle(4);
  return app;
}
/** Seleciona o ponto `index` e sobe o fator (toque no +0,05). */
function editPoint(app, index, delta = '0.05') {
  app.$(`circle[data-curve-index="${index}"]`).click();
  app.$(`[data-curve-nudge="${delta}"]`).click();
  app.flush();
}
function tapReview(app) { app.byId('curveReviewButton').click(); app.flush(); }

function mapApp({ outcome, opPolls, route = 'map' } = {}) {
  const w = new W.World({ opPolls });
  w.setFrame(W.realFrames('ref_', f => f.fuel === 'GNV' && f.petrol_ms > 2)[5]);
  Object.assign(w.outcome, outcome || {});
  const app = boot({ world: w });
  app.go(route);
  app.settle(4);
  return app;
}

module.exports = { curveApp, editPoint, tapReview, mapApp };
