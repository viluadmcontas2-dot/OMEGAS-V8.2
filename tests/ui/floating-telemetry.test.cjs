const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/components/floating-telemetry.js'), 'utf8');
const style = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-floating-telemetry.css'), 'utf8');

for (const token of ['RPM', 'PETROL', 'GAS', 'CÉLULA', 'FRESCOR', 'somente observação']) {
  assert.equal(source.includes(token), true, `missing ${token}`);
}
assert.equal(source.includes('this.store.subscribe'), true);
assert.equal(source.includes('setInterval'), false);
assert.equal(source.includes('history'), false);
assert.equal(source.includes('trail'), false);
assert.equal(source.includes('OmegasNative'), false);
assert.equal(source.includes('protocolTransaction'), false);
assert.equal(source.includes('write'), false);
assert.equal(source.includes('gas_ms_diagnostic'), true);
assert.equal(source.includes('telemetryAgeMs'), true);
assert.equal(source.includes('interpolation.cell'), true);
assert.equal(style.includes('position: fixed'), true);
assert.equal(style.includes('width: min(430px'), true);
console.log('FLOATING_TELEMETRY_CONTRACT=PASS');
