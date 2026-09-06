'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.resolve(__dirname, '../..');
const learning = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/learning.js'), 'utf8');
const app = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/app.js'), 'utf8');
const hub = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt'), 'utf8');

test('Learning usa comparações causais Blue e consegue localizá-las na grade física', () => {
  assert.match(hub, /calibration\.optJSONArray\("comparisons"\)/,
    'boundary nativo precisa receber as comparações do BlueCalibrationCoordinator');
  assert.match(hub, /learning\.put\("comparisons"/,
    'boundary nativo precisa publicar as comparações causais no payload de Learning');
  assert.match(learning, /maps\.comparisons/,
    'Learning deve consumir somente o payload causal já reconciliado');
  assert.match(learning, /petrolReferenceMs|petrolTargetMs/,
    'comparação Blue precisa usar o Petrol Inj. de referência para localizar a célula visual');
  assert.match(learning, /rpmBins/);
  assert.match(learning, /petrolBins/);
});

test('normal cockpit não expõe perfis manuais de tolerância científica', () => {
  assert.doesNotMatch(learning, /data-learning-inspector=["']tolerances["']/i);
  assert.doesNotMatch(learning, /Muito rigoroso|Muito flexível|data-tolerance-profile|data-tolerance-control/i);
  assert.doesNotMatch(learning, /setLearningToleranceControls|resetLearningToleranceSettings/);
  assert.doesNotMatch(app, /patch\.learningTolerance\s*=\s*api\.learningToleranceSettings\(/);
});

console.log('BLUE_LEARNING_RECOVERY_UI=PASS');
