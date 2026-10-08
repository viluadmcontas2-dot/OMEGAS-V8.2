'use strict';
// Regra 17 (dono, 2026-10-08): apagar ponto manualmente ("Reaprender N pontos") NUNCA desarma a limpeza automática.
// Classe de prova: 2 (ponte falsa, DOM real) + contrato da ponte Kotlin real (não chama a intenção manual p/ DELETE_POINT).
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const L = require('./wiring/lib.cjs');

function armCalls(app) { return app.world.calls.filter(c => c.method === 'setAutoCleanupArmed'); }

test('tocar em Reaprender com a limpeza armada não chama desarmar nem muda o botão', () => {
  const app = L.boot(); try {
    app.go('autocal'); app.settle(3);
    const toggle = app.$('[data-autocal-cleanup-toggle]');
    toggle.click(); app.settle(2);
    assert.equal(app.world.autocalAutoCleanup.armed, true);
    assert.equal(armCalls(app).length, 1);
    app.win.OmegasApp.autoCalCockpit.selectedAcquiredPoints = new Set(['GAS:6']);
    app.win.OmegasApp.autoCalCockpit.requestSelectedPointReacquisition(); app.settle(3);
    assert.equal(armCalls(app).length, 1, 'apagar manual não toca no armamento');
    assert.equal(toggle.textContent, 'Desativar limpeza');
    assert.equal(app.world.autocalAutoCleanup.armed, true);
  } finally { app.destroy(); }
});

test('ponte Kotlin: executeNativeAction só desarma quando a ação não é DELETE_POINT', () => {
  const file = path.join(__dirname, '../../app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt');
  const text = fs.readFileSync(file, 'utf8');
  const start = text.indexOf('fun executeNativeAction');
  const body = text.slice(start, text.indexOf('invalidateAnalysis()', start));
  assert.match(body, /!=\s*AutoCalNativeActionManager\.Action\.DELETE_POINT\s*\)\s*\{\s*activityRef[^\n]*onManualAutoCalIntent/);
});
