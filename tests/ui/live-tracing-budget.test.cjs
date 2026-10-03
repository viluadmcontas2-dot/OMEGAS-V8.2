'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const appSource = fs.readFileSync(
  path.join(__dirname, '../../app/src/main/assets/ui/app.js'),
  'utf8',
);

test('aprendizado rapido nao persegue pesos bilineares no DOM', () => {
  assert.doesNotMatch(appSource, /\.setTrace\s*\(/);
  assert.doesNotMatch(appSource, /weightKey/);
  assert.doesNotMatch(appSource, /continuousWeights\.slice/);
  assert.match(appSource, /function renderLightLiveContext\(state, route\)/);
  assert.match(appSource, /célula \$\{row \+ 1\}×\$\{column \+ 1\}/);
});

test('ciclo rapido quantiza somente texto leve de rpm e petrol inj', () => {
  assert.match(appSource, /\) \/ 25\) \* 25/);
  assert.match(appSource, /\* 20\) \/ 20/);
  assert.doesNotMatch(appSource, /TRACE_MAX_CONTRIBUTORS/);
  assert.doesNotMatch(appSource, /continuousWeights/);
});