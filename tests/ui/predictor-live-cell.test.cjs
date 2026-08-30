const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/components/predictor-current-cell.js'), 'utf8');
const style = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-predictor-live.css'), 'utf8');

assert.equal(source.includes('this.store.subscribe'), true);
assert.equal(source.includes('setInterval'), false);
assert.equal(source.includes('history'), false);
assert.equal(source.includes('trail'), false);
assert.equal(source.includes("classList.remove('current')"), true);
assert.equal(source.includes("classList.add('current')"), true);
assert.equal(source.includes('state.telemetry?.interpolation'), true);
assert.equal(style.includes("content: 'AGORA'"), true);
console.log('PREDICTOR_LIVE_CELL_CONTRACT=PASS');
