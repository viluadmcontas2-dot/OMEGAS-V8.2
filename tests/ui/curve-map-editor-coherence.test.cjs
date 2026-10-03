const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const curve = read('app/src/main/assets/ui/screens/curve.js');
const map = read('app/src/main/assets/ui/screens/map.js');

for (const token of ['writePrepared()', 'readbackValid', 'writeCurve']) assert.equal(curve.includes(token), true, `curve missing ${token}`);
for (const token of ['writePrepared()', 'buildReview()', 'readbackValid', 'previewMapAdjustment']) assert.equal(map.includes(token), true, `map missing ${token}`);
assert.equal(curve.includes('openReview()'), false, 'Curva K não deve exigir uma tela intermediária de revisão');
assert.equal(curve.includes('writeReview()'), false, 'Curva K deve ter uma única confirmação humana');
assert.equal(map.includes('openReview()'), false, 'Mapa K não deve exigir uma tela intermediária de revisão');
assert.equal(map.includes('writeReview()'), false, 'Mapa K deve ter uma única confirmação humana');
assert.equal(curve.includes('Atual da ECU'), false); // label lives in the shared HTML shell, not recalculated in JS
assert.equal(curve.includes('globalTrendRemoved'), false); // global/local split belongs to Kotlin Advisor, not UI
assert.equal(map.includes('Linha técnica 0C protegida'), true); // map shell keeps the protocol-row safety copy next to the editor
console.log('CURVE_MAP_EDITOR_COHERENCE=PASS');