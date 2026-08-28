const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const curve = read('app/src/main/assets/ui/screens/curve.js');
const map = read('app/src/main/assets/ui/screens/map.js');
const curveState = read('app/src/main/assets/ui/components/curve-prediction-state.js');

for (const token of ['openReview()', 'writeReview()', 'readbackValid', 'writeCurve']) assert.equal(curve.includes(token), true, `curve missing ${token}`);
for (const token of ['openReview()', 'writeReview()', 'readbackValid', 'previewMapAdjustment']) assert.equal(map.includes(token), true, `map missing ${token}`);
assert.equal(curve.includes('Atual da ECU'), false); // label lives in the shared HTML shell, not recalculated in JS
assert.equal(curve.includes('globalTrendRemoved'), false); // global/local split belongs to Kotlin Advisor, not UI
assert.equal(map.includes('Linha técnica 0C protegida'), false); // label lives in HTML shell; writer contract is tested separately
assert.equal(curveState.includes('PREVISAO_OMEGAS'), true);
assert.equal(curveState.includes('OBSERVADO_SEM_PREVISAO'), true);
assert.equal(curveState.includes('SEM_PREVISAO'), true);
assert.equal(curveState.includes('writeCurve'), false);
assert.equal(curveState.includes('protocolTransaction'), false);
assert.equal(curveState.includes('OmegasNative'), false);
console.log('CURVE_MAP_EDITOR_COHERENCE=PASS');
