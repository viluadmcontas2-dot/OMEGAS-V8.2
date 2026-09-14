'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const appSource = fs.readFileSync(
  path.join(__dirname, '../../app/src/main/assets/ui/app.js'),
  'utf8',
);
const gridSource = fs.readFileSync(
  path.join(__dirname, '../../app/src/main/assets/ui/components/physical-grid.js'),
  'utf8',
);

test('aprendizado rapido nao persegue pesos bilineares no DOM', () => {
  assert.doesNotMatch(appSource, /\.setTrace\s*\(/);
  assert.doesNotMatch(appSource, /weightKey/);
  assert.doesNotMatch(appSource, /continuousWeights\.slice/);
  assert.match(appSource, /function renderLightLiveContext\(state, route\)/);
  assert.match(appSource, /célula \$\{row \+ 1\}×\$\{column \+ 1\}/);
  assert.match(appSource, /route === 'learning' \|\| route === 'map'/);
});

test('grade fisica conserva tracing temporal limitado sem timer nem writer', () => {
  assert.match(gridSource, /setTrace\s*\(/);
  assert.match(gridSource, /traceTrailMs = 1400/);
  assert.match(gridSource, /traceTrailMax = 16/);
  assert.match(gridSource, /live-contributor/);
  assert.match(gridSource, /live-nearest/);
  assert.match(gridSource, /live-trail/);
  assert.match(gridSource, /Date\.now\(/);
  assert.doesNotMatch(gridSource, /setInterval/);
  assert.doesNotMatch(gridSource, /setTimeout/);
  assert.doesNotMatch(gridSource, /writeMap|startMapBatchWrite|protocolTransaction/);
});

test('ciclo rapido quantiza somente texto leve de rpm e petrol inj', () => {
  assert.match(appSource, /\) \/ 25\) \* 25/);
  assert.match(appSource, /\* 20\) \/ 20/);
  assert.doesNotMatch(appSource, /TRACE_MAX_CONTRIBUTORS/);
  assert.doesNotMatch(appSource, /continuousWeights/);
});