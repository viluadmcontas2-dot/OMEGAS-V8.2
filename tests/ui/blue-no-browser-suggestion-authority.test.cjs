'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const index = read('app/src/main/assets/ui/index.html');
const drawers = read('app/src/main/assets/ui/components/drawers.js');
const app = read('app/src/main/assets/ui/app.js');

assert.equal(fs.existsSync(path.join(root, 'app/src/main/assets/ui/suggestion-model.js')), false,
  'browser suggestion decision model must not exist');
assert.equal(fs.existsSync(path.join(root, 'tests/ui/suggestion-model.test.cjs')), false,
  'stale tests for browser suggestion decision math must not exist');

for (const [name, source] of [['index', index], ['drawers', drawers], ['app', app]]) {
  for (const token of ['OmegasSuggestionModel', 'kFactorSuggestions', 'mapResidualPredictions', 'mapResidualSuggestions']) {
    assert.equal(source.includes(token), false, `${name} still exposes browser suggestion authority token ${token}`);
  }
}

assert.equal(index.includes('suggestion-model.js'), false, 'index still loads retired suggestion model');
assert.equal(app.includes('calibration.suggestionItems'), true,
  'suggestions UI must project native Blue calibrationState.suggestionItems');
assert.equal(app.includes('renderPersistentSuggestions'), true,
  'native Blue suggestion projection must remain available');

console.log('BLUE_NO_BROWSER_SUGGESTION_AUTHORITY=PASS');
