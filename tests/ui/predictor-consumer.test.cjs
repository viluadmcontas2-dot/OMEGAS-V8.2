const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const sourcePath = path.join(root, 'app/src/main/assets/ui/core/predictor-model.js');
const source = fs.readFileSync(sourcePath, 'utf8');

function loadModel() {
  const context = { console, globalThis: {}, window: null };
  context.window = context.globalThis;
  vm.createContext(context);
  vm.runInContext(source, context, { filename: 'predictor-model.js' });
  return context.globalThis.OmegasUi.PredictorModel;
}

const model = loadModel();

{
  const explanation = model.explainCell({
    key: '4:3', row: 4, column: 3, rpm: 2500, petrolMs: 4.5,
    state: 'PREVISTO', currentK: 120, targetK: 128,
    predictionConfidence: 0.72, predictionReason: 'SUPPORTED_INSIDE_PHYSICAL_HULL',
    predicted: true, distinctTrajectories: 3,
  });
  assert.equal(explanation.stateLabel, 'Previsto');
  assert.equal(explanation.deltaK, 8);
  assert.equal(explanation.automaticWrite, false);
  assert.equal(explanation.requiresHumanReview, true);
}

{
  const calls = [];
  const router = { navigate: (...args) => { calls.push(args); return true; } };
  const opened = model.openMapReview(router, {
    row: 4, column: 3, state: 'PREVISTO', currentK: 120, targetK: 128, predictionConfidence: 0.72,
  });
  assert.equal(opened, true);
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], 'map');
  const context = calls[0][1];
  assert.equal(context.origin, 'predictor');
  assert.equal(context.intent, 'review-only');
  assert.equal(context.automaticWrite, false);
  assert.equal(context.requiresHumanReview, true);
  assert.equal(context.suggestion.target, 'MAP_K');
  assert.equal(context.suggestion.mapChanges.length, 1);
  assert.deepEqual(JSON.parse(JSON.stringify(context.suggestion.mapChanges[0])), {
    row: 4, column: 3, before: 120, after: 128, source: 'PREDICTOR_REVIEW_ONLY',
  });
}

{
  const calls = [];
  const router = { navigate: (...args) => { calls.push(args); return true; } };
  assert.equal(model.openMapReview(router, { row: 4, column: 3, state: 'DESCONHECIDO', currentK: 120, targetK: null }), false);
  assert.equal(model.openMapReview(router, { row: 4, column: 3, state: 'PREVISTO', currentK: null, targetK: 128 }), false);
  assert.equal(model.openMapReview(router, { row: 4, column: 3, state: 'PREVISTO', currentK: 120, targetK: 181 }), false);
  assert.equal(calls.length, 0);
}

assert.equal(source.includes('startBatchWrite'), false);
assert.equal(source.includes('writeK'), false);
assert.equal(source.includes('OmegasNative'), false);
assert.equal(source.includes('protocolTransaction'), false);
assert.equal(source.includes("router.navigate('map'"), true);
console.log('PREDICTOR_CONSUMER_CONTRACT=PASS');
