'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.resolve(__dirname, '../..');
const learning = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/learning.js'), 'utf8');
const app = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/app.js'), 'utf8');

test('Learning usa comparações causais Blue e consegue localizá-las na grade física', () => {
  assert.match(learning, /calibrationState[^\n]{0,120}comparisons|comparisons[^\n]{0,120}calibrationState/s,
    'Desvio medido precisa consumir a lista de comparações do BlueCalibrationCoordinator');
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
