'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/curve.js'), 'utf8');

for (const forbidden of [
  'assistedCalibration',
  'assisted_calibration',
  'kFactorSuggestions',
  'mapResidualPredictions',
  'predictedErrorPercent',
]) {
  assert.equal(source.includes(forbidden), false, `parallel Curve authority still reachable: ${forbidden}`);
}

for (const required of [
  'maps.comparisons',
  'state.calibrationState',
  'latestComparison',
  'proposal',
  'BlueCausalEngine',
  'startCurveRead',
  'previewCurvePoint',
  'writeCurve',
  'BATCH_CONFIRMED',
  'readbackValid',
]) {
  assert.equal(source.includes(required), true, `missing Curve native/manual contract: ${required}`);
}

assert.equal(source.includes('automaticWrite'), false, 'Curve UI must not add an automatic write path');
console.log('BLUE_CURVE_NATIVE_AUTHORITY=PASS');
