'use strict';
// W2 · canWrite: o status do Kotlin diz se gravar pode e por quê não; a UI apaga os botões de gravar e mostra o motivo.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const { realFrames } = require('./wiring/world.cjs');
const fs = require('node:fs');
const path = require('node:path');

const FRAMES = realFrames('ref_', f => f.fuel === 'GASOLINA' && f.petrol_ms > 2);
const root = path.resolve(__dirname, '../..');
const bridge = fs.readFileSync(path.join(root, 'app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt'), 'utf8');

test('W2 canWrite: o status do Kotlin emite canWrite e writeBlockedReason a partir da CalibrationWriteSafetyPolicy', () => {
  assert.match(bridge, /\.put\("canWrite", writeBlockedReason == null\)/);
  assert.match(bridge, /\.put\("writeBlockedReason", writeBlockedReason \?: ""\)/);
  const service = fs.readFileSync(path.join(root, 'app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt'), 'utf8');
  assert.match(service, /fun writeBlockedReason\(\): String\? \{[\s\S]*?CalibrationWriteSafetyPolicy\.unsafeReason\(status\(\)\)/);
  assert.match(service, /Este aparelho não possui o controle principal"/);
});

function boot(status) {
  const w = new L.World();
  w.setFrame(FRAMES[10]);
  Object.assign(w.status, status);
  const app = L.boot({ world: w });
  app.settle(3);
  return app;
}

test('W2 canWrite=false: botões de gravar ficam apagados e o toque mostra o motivo sem gravar', () => {
  const reason = 'Conecte a ECU antes de gravar';
  const app = boot({ canWrite: false, writeBlockedReason: reason });
  try {
    app.go('map'); app.settle(3);
    const button = app.byId('mapReviewButton');
    assert.equal(button.dataset.writeBlocked, 'true');
    assert.equal(button.getAttribute('aria-disabled'), 'true');
    assert.equal(button.title, reason);
    // O motivo fica escrito ao lado do botão (botão já desabilitado por seleção não dispara clique).
    assert.match(app.$('[data-write-note="map"]').textContent, /Conecte a ECU antes de gravar/);
    const before = app.world.calls.length;
    button.click(); app.settle(2);
    assert.deepEqual(app.world.since(before).map(c => c.method).filter(m => /write|prepare|apply/i.test(m)), []);
    app.go('curve'); app.settle(3);
    assert.equal(app.byId('curveReviewButton').dataset.writeBlocked, 'true');
    app.go('autocal'); app.settle(3);
    for (const selector of ['[data-autocal-toggle]', '[data-autocal-action="RESET_GAS"]', '[data-autocal-action="RESET_PETROL"]']) {
      assert.equal(app.$(selector).dataset.writeBlocked, 'true', selector);
    }
  } finally { app.destroy(); }
});

test('W2 canWrite=true: nenhum botão fica apagado', () => {
  const app = boot({ canWrite: true, writeBlockedReason: '' });
  try {
    for (const route of ['map', 'curve', 'autocal']) {
      app.go(route); app.settle(3);
      assert.equal(app.$$('[data-write-blocked="true"]').length, 0, route);
    }
  } finally { app.destroy(); }
});
