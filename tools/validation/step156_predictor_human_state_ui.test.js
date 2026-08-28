'use strict';

const fs = require('fs');
const vm = require('vm');
const path = require('path');
const assert = require('assert');

const root = { OmegasUi: {} };
root.globalThis = root;
const context = vm.createContext(root);
const source = fs.readFileSync(
  path.resolve(__dirname, '../../app/src/main/assets/ui/core/predictor-model.js'),
  'utf8',
);
vm.runInContext(source, context, { filename: 'predictor-model.js' });
const model = root.OmegasUi.PredictorModel;
assert(model, 'PredictorModel must load');

const rawOnly = model.explainCell({
  key: '1:2', row: 1, column: 2, rpm: 2400, petrolMs: 4.5,
  state: 'VALIDADO', currentK: 120, targetK: 132, confidence: 0.99,
  stateReason: 'raw local reconstruction must be ignored',
});
assert.strictEqual(rawOnly.targetK, null, 'missing humanState must hide raw target');
assert.strictEqual(rawOnly.requiresHumanReview, false, 'missing humanState must fail closed');
assert.strictEqual(rawOnly.confidence, 0, 'missing humanState must not reuse raw confidence');
assert.match(rawOnly.reason.toLowerCase(), /estado.*indispon/);

const typed = model.explainCell({
  key: '1:2', row: 1, column: 2, rpm: 2400, petrolMs: 4.5,
  state: 'DESCONHECIDO', currentK: 1, targetK: 2, confidence: 0.01,
  humanState: {
    stateLabel: 'Alvo físico com intervalo',
    targetLabel: 'ALVO',
    reason: 'Authority física e risco calibrado',
    confidence: 0.87,
    currentK: 120,
    targetEstimateK: 132,
    intervalLowerK: 128,
    intervalUpperK: 136,
    authority: 'PHYSICALLY_ANCHORED',
    scientificState: 'DIRECT_CONFIRMED',
    riskState: 'CALIBRATED_ACTIONABLE',
    actionState: 'ACTIONABLE',
    requiresHumanReview: true,
    disclosure: null,
  },
});
assert.strictEqual(typed.stateLabel, 'Alvo físico com intervalo');
assert.strictEqual(typed.targetLabel, 'ALVO');
assert.strictEqual(typed.reason, 'Authority física e risco calibrado');
assert.strictEqual(typed.confidence, 0.87);
assert.strictEqual(typed.currentK, 120);
assert.strictEqual(typed.targetK, 132);
assert.strictEqual(typed.requiresHumanReview, true);
assert.strictEqual(typed.actionState, 'ACTIONABLE');
assert.strictEqual(typed.authority, 'PHYSICALLY_ANCHORED');

const routerCalls = [];
const router = { navigate(route, payload) { routerCalls.push({ route, payload }); return true; } };
assert.strictEqual(model.openMapReview(router, { humanState: { ...typed, requiresHumanReview: false } }), false);
assert.strictEqual(routerCalls.length, 0, 'UI must not reconstruct reviewability from numbers');

console.log('STEP156_UI_CONTRACT_PASS');
