const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const projection = read('app/src/main/java/com/omegas/prohub/calibration/LiveCellProjection.kt');
const bridge = read('app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt');

// A geometria/weights vêm do Kotlin. JS apenas substitui o estado visual atual.
assert.equal(projection.includes('fun liveInterpolationJson('), true);
assert.equal(projection.includes('ContinuousLearningMath.bilinearWeights'), true);
assert.equal(projection.includes('.put("continuousWeights"'), true);
assert.equal(projection.includes('.put("affectsLearning", false)'), true);
assert.equal(projection.includes('.put("affectsCalibration", false)'), true);
assert.equal(bridge.includes('LiveCellProjection.liveInterpolationJson('), true);
assert.equal(bridge.includes('.put("interpolation", interpolation)'), true);

console.log('LIVE_TRACING_CONTRACT=PASS');
