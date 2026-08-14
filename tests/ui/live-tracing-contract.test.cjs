const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const visual = read('app/src/main/assets/ui/components/predictor-current-cell.js');
const projection = read('app/src/main/java/com/omegas/prohub/learning/LearningGridProjection.kt');
const bridge = read('app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt');

// O fast path do Predictor reutiliza o único Scheduler; não cria relógio próprio.
assert.equal(visual.includes("scheduler.addHook('fast'"), true);
assert.equal(visual.includes("state.route !== 'predictor'"), true);
assert.equal(visual.includes('this.api.telemetry()'), true);
assert.equal(visual.includes('setInterval'), false);

// A geometria/weights vêm do Kotlin. JS apenas substitui o estado visual atual.
assert.equal(projection.includes('fun liveInterpolationJson('), true);
assert.equal(projection.includes('ContinuousLearningMath.bilinearWeights'), true);
assert.equal(projection.includes('.put("continuousWeights"'), true);
assert.equal(projection.includes('.put("affectsLearning", false)'), true);
assert.equal(projection.includes('.put("affectsCalibration", false)'), true);
assert.equal(bridge.includes('LearningGridProjection.liveInterpolationJson('), true);
assert.equal(bridge.includes('.put("interpolation", interpolation)'), true);
assert.equal(visual.includes('cell.continuousWeights'), true);
assert.equal(visual.includes('bilinear'), false);

// Desligar tracing altera somente apresentação; Learning continua independente.
assert.equal(visual.includes('liveTracingEnabled'), true);
assert.equal(visual.includes('O Learning continua coletando normalmente.'), true);
assert.equal(visual.includes('store.patch({ liveTracingEnabled: next })'), true);
assert.equal(visual.includes('learning:'), false);
assert.equal(visual.includes('learningEligible'), false);

// Latest-only: até quatro pesos atuais, remove os anteriores; nenhuma trilha ou writer.
assert.equal(visual.includes('weights.slice(0, 4)'), true);
assert.equal(visual.includes("classList.remove('trace-weight')"), true);
assert.equal(visual.includes("classList.add('trace-weight')"), true);
assert.equal(visual.includes('history'), false);
assert.equal(visual.includes('trail'), false);
assert.equal(visual.includes('setTrace'), false);
assert.equal(visual.includes('writeMap'), false);
assert.equal(visual.includes('startMapBatchWrite'), false);
assert.equal(visual.includes('protocolTransaction'), false);
assert.equal(visual.includes('router.navigate'), false);

console.log('LIVE_TRACING_CONTRACT=PASS');
