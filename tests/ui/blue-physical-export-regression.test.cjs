'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const ROOT = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/core/learning-model.js'), 'utf8');

function loadModel() {
  const context = { console };
  context.globalThis = context;
  vm.runInNewContext(source, context, { filename: 'learning-model.js' });
  return context.OmegasUi.LearningModel;
}

test('projected aggregate is not overwritten by a raw physical visit', () => {
  const model = loadModel().buildModel({
    epoch: 1,
    grid: { rows: 1, columns: 1, rpmBins: [850], petrolBins: [4.5] },
    cells: [{
      fuel: 'PETROL', epoch: 0, row: 0, column: 0,
      rpm: 876, map_bar: 0.413, petrol_ms: 4.60,
      samples: 40, visit_count: 4, confidence: 0.82, quality: 0.82,
    }],
    petrol: [{
      fuel: 'GASOLINA', epoch: 0, row: 0, column: 0,
      rpm: 877, map_bar: 0.414, petrol_ms: 4.61,
      frame_count: 10, visits: ['raw-visit'], quality: 0.90,
    }],
  });

  assert.equal(model.cells[0].petrol.samples, 40);
  assert.equal(model.cells[0].petrol.visits, 4);
  assert.equal(model.cells[0].petrol.confidence, 0.82);
  assert.equal(model.cells[0].petrol.petrolMs, 4.60);
});

test('raw Blue export uses frame_count and quality when projected cells are absent', () => {
  const model = loadModel().buildModel({
    epoch: 1,
    grid: { rows: 1, columns: 1, rpmBins: [850], petrolBins: [4.5] },
    petrol: [{
      fuel: 'GASOLINA', epoch: 0, row: 0, column: 0,
      rpm: 876, map_bar: 0.413, petrol_ms: 4.60032,
      frame_count: 10, visits: ['physical-export'],
      quality: 0.7951927120873843,
    }],
  });

  assert.equal(model.cells[0].petrol.samples, 10);
  assert.ok(model.cells[0].petrol.confidence > 0.79);
});
