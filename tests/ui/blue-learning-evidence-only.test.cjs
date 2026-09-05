'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const learning = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/learning.js'), 'utf8');
const index = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/index.html'), 'utf8');

for (const forbidden of [
  'assistedCalibration',
  'assisted_calibration',
  'mapResidualPredictions',
  'predictedErrorPercent',
  "layer === 'suggestion'",
  'persistentMapSuggestions',
  'learningStability',
]) {
  assert.equal(learning.includes(forbidden), false, `legacy decision fallback still reachable: ${forbidden}`);
}

for (const required of [
  "layer === 'petrol'",
  "layer === 'cng'",
  "layer === 'comparison'",
  'latestComparison',
  'calibrationState',
  'BlueCausalEngine',
]) {
  assert.equal(learning.includes(required), true, `missing evidence-only Blue contract: ${required}`);
}

assert.equal(index.includes('data-learning-layer="suggestion"'), false, 'Learning must not expose suggestion as a measurement layer');
assert.equal(index.includes('data-learning-layer="petrol"'), true);
assert.equal(index.includes('data-learning-layer="cng"'), true);
assert.equal(index.includes('data-learning-layer="comparison"'), true);

console.log('BLUE_LEARNING_EVIDENCE_ONLY=PASS');
