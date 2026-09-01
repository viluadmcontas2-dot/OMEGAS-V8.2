const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');

const html = read('app/src/main/assets/ui/index.html');
const curve = read('app/src/main/assets/ui/screens/curve.js');
const learning = read('app/src/main/assets/ui/screens/learning.js');
const api = read('app/src/main/assets/ui/core/native-api.js');
const bridge = read('app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt');
const service = read('app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt');
const styles = read('app/src/main/assets/ui/styles.css');
const refine = read('app/src/main/assets/ui/styles-refine.css');

for (const marker of [
  'data-curve-panel="autocal"',
  'id="autoCalState"',
  'id="autoCalSummary"',
  'id="autoCalRefresh"',
]) {
  assert.equal(html.includes(marker), true, `AutoCal UI missing: ${marker}`);
}

for (const contract of [
  'autoCalStatus',
  'autoCalSnapshot',
  'requestAutoCalSnapshot',
]) {
  assert.equal(api.includes(contract), true, `native API missing: ${contract}`);
}
assert.equal(bridge.includes('getNativeAutoCalStatus'), true);
assert.equal(bridge.includes('getNativeAutoCalSnapshot'), true);
assert.equal(bridge.includes('requestNativeAutoCalSnapshot'), true);
assert.equal(service.includes('nativeRequestAutoCalSnapshot'), true);
assert.equal(curve.includes("view !== 'autocal'"), true);
assert.equal(curve.includes('renderAutoCal'), true);

assert.equal(learning.includes('data-learning-inspector="tolerances"'), false);
assert.equal(learning.includes('LIMITES CONFIGURADOS'), false);
assert.equal(learning.includes('Detalhes técnicos'), true);
for (const label of [
  'Gasolina esperada',
  'GNV observado',
  'Diferença aprendida',
  'Situação',
]) {
  assert.equal(learning.includes(label), true, `primary learning label missing: ${label}`);
}

const css = styles + '\n' + refine;
assert.equal(css.includes('@media(max-height:760px)'), true);
assert.match(css, /min-height:\s*48px/);
assert.equal(css.includes('.autocal-view'), true);

console.log('RED_V82_OPERATIONAL_UX_CONTRACT=PASS');
