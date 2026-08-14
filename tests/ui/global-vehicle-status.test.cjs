const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const component = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/components/vehicle-status-strip.js'), 'utf8');
const router = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/core/router.js'), 'utf8');

for (const label of ['SERVIÇO', 'ECU', 'FRESCOR', 'COMBUSTÍVEL', 'RPM', 'PETROL INJ.']) {
  assert.equal(component.includes(label), true, `missing ${label}`);
}
assert.equal(component.includes('app.store'), true);
assert.equal(component.includes('store.subscribe'), true);
assert.equal(component.includes('setInterval'), false);
assert.equal(component.includes('OmegasNative'), false);
assert.equal(component.includes('protocolTransaction'), false);
assert.equal(component.includes('write'), false);
assert.equal(router.includes("components/vehicle-status-strip.js"), true);
assert.equal(component.includes('telemetryAgeMs'), true);
assert.equal(component.includes('directTelemetryAgeMs'), true);
console.log('GLOBAL_VEHICLE_STATUS_CONTRACT=PASS');
