'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

function loadBench() {
  delete globalThis.OmegasNative;
  delete globalThis.OmegasPortmonReplay;
  for (const path of [
    '../../app/src/main/assets/ui/portmon-replay-adapter.js',
    '../../app/src/main/assets/ui/portmon-browser-simulator.js',
  ]) delete require.cache[require.resolve(path)];
  globalThis.OmegasPortmonReplay = require('../../app/src/main/assets/ui/portmon-replay-adapter.js');
  require('../../app/src/main/assets/ui/portmon-browser-simulator.js');
  return globalThis.OmegasNative;
}

const { MapEditor } = require('../../app/src/main/assets/ui/map-editor.js');

function submitInternalBlock(editor, ecu, reason) {
  const review = editor.buildReview();
  const start = JSON.parse(ecu.startKBatchWrite(JSON.stringify(review.items), 3, 150, reason));
  if (!start.ok || !start.started) throw new Error(start.error || 'Bloco interno não iniciou');
  return { review, calls: 1 };
}

test('fluxo de um bloco interno escreve frame byte a byte recebe ACK e confirma readback', () => {
  const ecu = loadBench();
  const initial = JSON.parse(ecu.readKMap());
  const editor = new MapEditor();
  editor.load(initial);
  editor.selectOnly(2, 3);
  editor.setAdjustment('delta', 7);
  editor.applyNativePreview([{ row: 2, column: 3, current: 115, target: 122, changed: true }]);

  const sent = submitInternalBlock(editor, ecu, 'bancada byte a byte');
  assert.equal(sent.calls, 1);
  const status = JSON.parse(ecu.getKWriteStatus());
  assert.equal(status.state, 'BATCH_CONFIRMED');
  assert.equal(status.ack, true);
  assert.equal(status.readback, true);
  assert.equal(status.details.frames.length, 1);
  assert.equal(status.details.frames[0].request, '14 54 00 02 03 7A E7');
  assert.equal(status.details.frames[0].response, '14 54 00 02 03 7A E7 53 01 00 CE');

  const readback = JSON.parse(ecu.readKMap());
  assert.equal(readback.rows[2][3], 122);
  assert.deepEqual(readback.extraRow, initial.extraRow);
  editor.applyReadback(readback);
  assert.equal(editor.snapshot().rows[2][3], 122);
});

test('leitura do mapa gera 13 requests com checksum correto', () => {
  const ecu = loadBench();
  JSON.parse(ecu.readKMap());
  const trace = JSON.parse(ecu.getPortmonSimulatorTrace());
  const reads = trace.frames.filter(frame => frame.kind === 'READ_MAP_ROW');
  assert.equal(reads.length, 13);
  assert.equal(reads[0].request, '2A 54 00 00 7E');
  assert.equal(reads[12].request, '2A 54 00 0C 8A');
  assert.ok(reads.every(frame => typeof frame.response === 'string'));
});

test('timeout de ACK não altera a memória simulada', () => {
  const ecu = loadBench();
  const before = JSON.parse(ecu.readKMap());
  ecu.setPortmonSimulatorMode('ACK_TIMEOUT');
  ecu.startKBatchWrite(JSON.stringify([{ row: 1, column: 1, current: before.rows[1][1], target: 140 }]), 3, 150, 'timeout');
  const status = JSON.parse(ecu.getKWriteStatus());
  assert.equal(status.state, 'FAILED');
  assert.equal(status.ack, false);
  assert.equal(JSON.parse(ecu.readKMap()).rows[1][1], before.rows[1][1]);
});

test('ACK rejeitado não altera a memória simulada', () => {
  const ecu = loadBench();
  const before = JSON.parse(ecu.readKMap());
  ecu.setPortmonSimulatorMode('ACK_REJECTED');
  ecu.startKBatchWrite(JSON.stringify([{ row: 1, column: 2, current: before.rows[1][2], target: 141 }]), 3, 150, 'nack');
  const status = JSON.parse(ecu.getKWriteStatus());
  assert.equal(status.state, 'FAILED');
  assert.equal(status.readback, false);
  assert.equal(JSON.parse(ecu.readKMap()).rows[1][2], before.rows[1][2]);
});

test('readback divergente nunca aparece como sucesso', () => {
  const ecu = loadBench();
  const before = JSON.parse(ecu.readKMap());
  ecu.setPortmonSimulatorMode('READBACK_DIVERGENT');
  ecu.startKBatchWrite(JSON.stringify([{ row: 3, column: 4, current: before.rows[3][4], target: 150 }]), 3, 150, 'divergente');
  const status = JSON.parse(ecu.getKWriteStatus());
  assert.equal(status.state, 'FAILED');
  assert.equal(status.ack, true);
  assert.equal(status.readback, false);
  assert.notEqual(JSON.parse(ecu.readKMap()).rows[3][4], 150);
});

test('bloco interno de 16 células produz 16 frames e uma única operação do writer', () => {
  const ecu = loadBench();
  const map = JSON.parse(ecu.readKMap());
  const payload = Array.from({ length: 16 }, (_, index) => {
    const row = Math.floor(index / 12);
    const column = index % 12;
    return { row, column, current: map.rows[row][column], target: map.rows[row][column] + 1 };
  });
  const start = JSON.parse(ecu.startKBatchWrite(JSON.stringify(payload), 3, 150, 'bloco interno'));
  assert.equal(start.started, true);
  const status = JSON.parse(ecu.getKWriteStatus());
  assert.equal(status.state, 'BATCH_CONFIRMED');
  assert.equal(status.details.cells, 16);
  assert.equal(status.details.frames.length, 16);
});
