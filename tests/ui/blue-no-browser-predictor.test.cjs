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
const nativeApi = read('app/src/main/assets/ui/core/native-api.js');

for (const [name, source] of [
  ['router', router],
  ['store', store],
  ['app', app],
  ['index', index],
  ['native-api', nativeApi],
]) {
  assert.equal(/predictor/i.test(source), false, `${name} still exposes browser Predictor semantics`);
}

const retired = [
  'app/src/main/assets/ui/core/predictor-model.js',
  'app/src/main/assets/ui/screens/predictor.js',
  'app/src/main/assets/ui/components/predictor-current-cell.js',
  'app/src/main/assets/ui/components/curve-prediction-state.js',
];
for (const rel of retired) {
  assert.equal(fs.existsSync(path.join(root, rel)), false, `retired Predictor asset still exists: ${rel}`);
}

assert.equal(router.includes("'dashboard', 'learning', 'map', 'curve', 'obd', 'suggestions', 'tools'"), true);
console.log('BLUE_NO_BROWSER_PREDICTOR=PASS');
