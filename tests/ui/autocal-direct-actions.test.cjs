const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const curve = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/curve.js'), 'utf8');
const index = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/index.html'), 'utf8');

assert.match(cockpit, /Ativar Auto Calibration/);
assert.match(cockpit, /Desativar Auto Calibration/);
assert.equal(cockpit.includes('type="checkbox" data-autocal-toggle'), false);
assert.equal(cockpit.includes('window.confirm('), false);
assert.equal(cockpit.includes('Deseja continuar?'), false);

assert.match(curve, /curveReviewButton.*applyChanges/s);
assert.match(curve, /applyChanges\(\)/);
assert.match(curve, /this\.api\.writeCurve\(points,/);
assert.equal(curve.includes('openReview()'), false);
assert.equal(curve.includes('writeReview()'), false);

assert.equal(index.includes('review-layer'), false);
assert.equal(index.includes('curveWriteButton'), false);
assert.equal(index.includes('curveReviewBack'), false);
assert.match(index, /Uma ação explícita aplica as alterações/);
assert.match(index, /ACK \+ readback da ECU/);

console.log('AUTOCAL_DIRECT_ACTIONS_CONTRACT=PASS');
