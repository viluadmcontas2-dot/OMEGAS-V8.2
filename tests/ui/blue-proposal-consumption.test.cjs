'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');
const curve = read('app/src/main/assets/ui/screens/curve.js');
const map = read('app/src/main/assets/ui/screens/map.js');
const access = read('app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt');
const bridge = read('app/src/main/java/com/omegas/prohub/web/BlueJavascriptBridge.kt');

assert.match(curve, /change\.targetFactor/, 'Curve must consume the exact Kotlin target factor');
assert.match(curve, /change\.targetRaw/, 'Curve must retain the exact normalized Q14 target');
assert.match(curve, /previewCurvePoint\(index, requested\)/, 'Curve target remains a preview');
assert.match(map, /suggestion\.mapChanges/);
assert.match(map, /setTargetOverrides\(changes\)/, 'Map exact changes must enter editor overrides');

assert.match(access, /blue_causal_ledger\.json/, 'runtime ledger must be durable');
assert.match(access, /bluePrepareIntervention/);
assert.match(access, /blueConfirmIntervention/);
assert.match(bridge, /bluePrepareIntervention/);
assert.match(bridge, /blueConfirmIntervention/);

const curvePrepare = bridge.indexOf('bluePrepareIntervention("CURVE"');
const curveWrite = bridge.indexOf('startKFactorWrite');
const curveConfirm = bridge.indexOf('blueConfirmIntervention', curveWrite);
assert.ok(curvePrepare >= 0 && curvePrepare < curveWrite, 'Curve intent must be prepared before writer start');
assert.ok(curveConfirm > curveWrite, 'Curve ledger must close only after writer result');

assert.doesNotMatch(curve + map, /automaticWrite\s*=\s*true|automaticWrite:\s*true/);
console.log('BLUE_PROPOSAL_CONSUMPTION=PASS');
