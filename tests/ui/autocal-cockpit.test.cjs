const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const cockpitCss = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-autocal-cockpit.css'), 'utf8');
const api = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/core/autocal-api.js'), 'utf8');
const provider = fs.readFileSync(path.join(root, 'app/src/main/java/com/omegas/prohub/autocal/AutoCalBridgeProvider.kt'), 'utf8');
const manager = fs.readFileSync(path.join(root, 'app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt'), 'utf8');

assert.equal(cockpit.includes('autocal-chart-workspace'), true);
assert.equal(cockpit.includes('autocal-live-strip'), true);
assert.equal(cockpit.includes('data-autocal-read-context'), true);
assert.equal(cockpit.includes('data-autocal-chart-action="zoom-in"'), false);
assert.equal(cockpit.includes('data-autocal-chart-action="zoom-out"'), false);
assert.equal(cockpit.includes('data-autocal-chart-action="fit"'), false);
assert.equal(cockpit.includes('chartTransform()'), false);
assert.equal(cockpit.includes('bindChartGestures('), false);
assert.equal(cockpit.includes('Petrol Inj. (ms)'), true);
assert.equal(cockpit.includes('MAP (bar)'), true);
assert.equal(cockpit.includes('const readerReady ='), false);
assert.equal(cockpit.includes('this.api.readerSnapshot()'), false);
assert.equal(cockpit.includes('this.api.acquisitionSnapshot()'), false);
assert.equal(cockpit.includes('AUTOCAL_PROJECTION_UNAVAILABLE'), true);
assert.equal(cockpitCss.includes('touch-action: pan-y'), true);
assert.equal(cockpit.includes('autocal-now-card'), false);
assert.equal(cockpit.includes('autocal-read-card'), false);
assert.equal(cockpitCss.includes('.autocal-chart-workspace'), true);
assert.equal(cockpitCss.includes('.autocal-live-strip'), true);
assert.equal(cockpit.indexOf('autocal-chart-workspace') < cockpit.indexOf('autocalReferenceChart'), true);
assert.equal(cockpit.indexOf('autocalReferenceChart') < cockpit.indexOf('autocalChartInspector'), true);
assert.equal(cockpit.includes("addHook('context'"), true);
assert.equal(cockpit.includes('setInterval'), false);
assert.equal(cockpit.includes('NUM_BUF_UPD_GAS'), true);
assert.equal(cockpit.includes('correlationReason'), true);
assert.equal(cockpit.includes('correlationConfidence'), true);
assert.equal(cockpit.includes("data-autocal-toggle"), true);
assert.equal(cockpit.includes("data-autocal-action=\"ENABLE_AUTO_CAL\""), false);
assert.equal(cockpit.includes("data-autocal-action=\"DISABLE_AUTO_CAL\""), false);
assert.equal(cockpit.includes("data-autocal-action=\"RESET_PETROL\""), true);
assert.equal(cockpit.includes("data-autocal-action=\"RESET_GAS\""), true);
assert.equal(cockpit.includes("data-autocal-action=\"RESET_ALL\""), false);
assert.equal(cockpit.includes('NATIVE_AUTOMATCH'), false);
assert.equal(cockpit.includes('runOperational(action)'), true);
assert.equal(cockpit.includes('setAcquisitionEnabled'), true);
assert.equal(cockpit.includes('prepare('), true);
assert.equal(cockpit.includes('execute(prepared.preparationId)'), true);
assert.equal(cockpit.includes('Continuar para confirmação Android'), true);
assert.equal(api.includes('prepareNativeAction'), true);
assert.equal(api.includes('executeNativeAction'), true);
assert.equal(manager.includes('manualAutoMatchExposed'), false);
assert.equal(manager.includes('NATIVE_AUTOMATCH'), false);
assert.equal(provider.includes('hub/autocal-ui.js'), false);
assert.equal(provider.includes('postDelayed'), false);
assert.equal(provider.includes('addJavascriptInterface'), true);
console.log('AUTOCAL_COCKPIT_CONTRACT=PASS');



assert.match(cockpit, /Ativar Auto Calibration/);
assert.match(cockpit, /Desativar Auto Calibration/);
assert.equal(cockpit.includes('type="checkbox" data-autocal-toggle'), false);
assert.equal(cockpit.includes('window.confirm('), false);
assert.equal(cockpit.includes('Deseja continuar?'), false);


assert.equal(cockpit.includes('data-autocal-technical-toggle'), true);
assert.equal(cockpit.includes('details.open = true'), true);
assert.equal(cockpitCss.includes('.autocal-technical-details[open]'), true);
assert.equal(cockpitCss.includes('.autocal-primary-action[data-action="ENABLE_AUTO_CAL"]'), true);
assert.equal(cockpitCss.includes('.autocal-primary-action[data-action="DISABLE_AUTO_CAL"]'), true);

assert.equal(cockpit.includes('this.inspectReferencePoint(selected)'), true);
assert.equal(cockpit.includes('this.inspectBand(preferred)'), true);
