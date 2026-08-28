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
    distinctTrajectories: 3,
    humanState: {
      visualState: 'PREVISTO', scientificState: 'PREDICTED_SUPPORTED',
      stateLabel: 'Previsto', targetLabel: 'ESTIMATIVA',
      currentK: 120, targetEstimateK: 128, confidence: 0.72,
      authority: 'MODEL_SUPPORTED', riskState: 'CALIBRATED_REVIEW',
      actionState: 'REVIEWABLE', predicted: true,
      requiresHumanReview: true,
    },
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
    row: 4, column: 3,
    humanState: {
      visualState: 'PREVISTO', scientificState: 'PREDICTED_SUPPORTED',
      stateLabel: 'Previsto', currentK: 120, targetEstimateK: 128, confidence: 0.72,
      authority: 'MODEL_SUPPORTED', riskState: 'CALIBRATED_REVIEW',
      actionState: 'REVIEWABLE', predicted: true, requiresHumanReview: true,
    },
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
  assert.equal(model.openMapReview(router, { row: 4, column: 3 }), false);
  assert.equal(model.openMapReview(router, {
    row: 4, column: 3,
    humanState: {
      visualState: 'DESCONHECIDO', scientificState: 'UNKNOWN_ABSTAIN',
      actionState: 'ABSTAIN', currentK: 120, targetEstimateK: null,
      confidence: 0, requiresHumanReview: false,
    },
  }), false);
  assert.equal(calls.length, 0);
}

assert.equal(source.includes('startBatchWrite'), false);
assert.equal(source.includes('writeK'), false);
assert.equal(source.includes('OmegasNative'), false);
assert.equal(source.includes('protocolTransaction'), false);
assert.equal(source.includes("router.navigate('map'"), true);
console.log('PREDICTOR_CONSUMER_CONTRACT=PASS');
