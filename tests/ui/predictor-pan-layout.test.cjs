const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-predictor.css'), 'utf8');
const predictor = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/predictor.js'), 'utf8');

assert.match(css, /\.predictor-surface\s*\{[\s\S]*?overflow:\s*auto;/);
assert.match(css, /\.predictor-surface\s*\{[\s\S]*?touch-action:\s*pan-x pan-y;/);
assert.match(css, /\.predictor-grid\s*\{[\s\S]*?repeat\(12,\s*minmax\(0,\s*1fr\)\)/);
assert.match(css, /\.predictor-grid\s*\{[\s\S]*?min-width:\s*0;/);
assert.match(css, /@media\s*\(max-width:\s*760px\)[\s\S]*?\.predictor-grid\s*\{[\s\S]*?min-width:\s*620px;/);
assert.equal(predictor.includes('setInterval'), false);
assert.equal(predictor.includes('setTimeout'), false);
assert.equal(predictor.includes('pointermove'), false);

console.log('PREDICTOR_PAN_LAYOUT_CONTRACT=PASS');
