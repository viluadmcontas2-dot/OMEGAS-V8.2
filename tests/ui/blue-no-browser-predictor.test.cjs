'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const router = read('app/src/main/assets/ui/core/router.js');
const store = read('app/src/main/assets/ui/core/store.js');
const app = read('app/src/main/assets/ui/app.js');
const index = read('app/src/main/assets/ui/index.html');

for (const [name, source] of [['router', router], ['store', store], ['app', app], ['index', index]]) {
  assert.equal(/predictor/i.test(source), false, `${name} still exposes browser Predictor semantics`);
}
for (const retired of [
  'core/predictor-model.js',
  'screens/predictor.js',
  'components/predictor-current-cell.js',
  'components/curve-prediction-state.js',
]) {
  assert.equal(router.includes(retired), false, `router still loads retired module ${retired}`);
}

assert.equal(router.includes("'dashboard', 'learning', 'map', 'curve', 'obd', 'suggestions', 'tools'"), true);
console.log('BLUE_NO_BROWSER_PREDICTOR=PASS');
