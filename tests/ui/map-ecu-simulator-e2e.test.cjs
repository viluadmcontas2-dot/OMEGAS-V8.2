'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { MapEditor, MAX_SELECTION } = require('../../app/src/main/assets/ui/map-editor.js');

class ScriptedMp48Ecu {
  constructor() {
    this.rows = Array.from({ length: 12 }, (_, row) =>
      Array.from({ length: 12 }, (_, column) => 110 + row + column));
    this.extraRow = Array(12).fill(77);
    this.axes = {
      petrolBins: [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18],
      rpmBins: [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500],
    };
    this.mode = 'NORMAL';
    this.calls = [];
    this.status = { state: 'IDLE', ok: true, ack: false, readback: false };
  }

  setMode(mode) { this.mode = mode; }

  readMap() {
    return {
      ok: true,
      state: 'COMPLETED',
      rows: this.rows.map(row => row.slice()),
      extraRow: this.extraRow.slice(),
      axes: { petrolBins: this.axes.petrolBins.slice(), rpmBins: this.axes.rpmBins.slice() },
      hash: this.hash(),
      writableCells: 144,
    };
  }

  writeIntent(items, maxStep = 3, pauseMs = 150, reason = 'teste') {
    const payload = JSON.parse(JSON.stringify(items));
    this.calls.push({ payload, maxStep, pauseMs, reason });
    this.status = { state: 'WRITING', ok: true, ack: false, readback: false };

    if (this.mode === 'START_REJECTED') {
      this.status = { state: 'FAILED', ok: false, ack: false, readback: false, error: 'rejected' };
      return { ok: false, started: false, error: 'ECU recusou o lote' };
    }
    if (this.mode === 'ACK_TIMEOUT') {
      this.status = { state: 'FAILED', ok: false, ack: false, readback: false, error: 'ACK_TIMEOUT' };
      return { ok: true, started: true };
    }
    if (this.mode === 'ACK_REJECTED') {
      this.status = { state: 'FAILED', ok: false, ack: false, readback: false, error: 'ACK_REJECTED' };
      return { ok: true, started: true };
    }

    const before = this.rows.map(row => row.slice());
    payload.forEach(item => {
      assert.equal(before[item.row][item.column], item.current, 'cliente enviou valor atual divergente');
      this.rows[item.row][item.column] = item.target;
    });
    this.status = { state: 'ACKED', ok: true, ack: true, readback: false };

    if (this.mode === 'READBACK_DIVERGENT') {
      const first = payload[0];
      this.rows[first.row][first.column] = first.target - 1;
      this.status = { state: 'FAILED', ok: false, ack: true, readback: false, error: 'READBACK_DIVERGENT' };
    } else {
      this.status = { state: 'BATCH_CONFIRMED', ok: true, ack: true, readback: true };
    }
    return { ok: true, started: true };
  }

  getWriteStatus() { return JSON.parse(JSON.stringify(this.status)); }
  hash() { return this.rows.flat().join('-'); }
}

function loadEditor(ecu) {
  const editor = new MapEditor();
  editor.load(ecu.readMap());
  return editor;
}

// Colaborador de teste que representa a saída já normalizada do planner Kotlin.
// O objetivo deste arquivo é exercitar revisão -> writer -> ACK -> readback;
// a matemática do planner é coberta separadamente pelos testes Kotlin/contrato.
function applyPlannerPreview(editor, mode, adjustment) {
  editor.setAdjustment(mode, adjustment);
  const items = editor.selectedCells().map(cell => {
    let rawTarget;
    if (mode === 'percent') rawTarget = cell.current * (1 + adjustment / 100);
    else if (mode === 'delta') rawTarget = cell.current + adjustment;
    else rawTarget = adjustment;
    const target = Math.max(100, Math.min(180, Math.round(rawTarget)));
    return { ...cell, target, changed: target !== cell.current };
  });
  editor.applyNativePreview(items);
  return editor.buildReview();
}

function submit(editor, ecu, reason) {
  const review = editor.buildReview();
  const started = ecu.writeIntent(review.items, 3, 150, reason);
  if (!started.ok || !started.started) throw new Error(started.error || 'A escrita não iniciou');
  return review;
}

function applyReadback(editor, ecu) {
  const map = ecu.readMap();
  editor.applyReadback(map);
  return map;
}

test('ECU simulada fecha leitura edição revisão ACK e readback', () => {
  const ecu = new ScriptedMp48Ecu();
  const editor = loadEditor(ecu);
  const originalTechnicalRow = ecu.extraRow.slice();

  editor.selectOnly(2, 3);
  applyPlannerPreview(editor, 'delta', 7);
  submit(editor, ecu, 'teste bancada completo');

  assert.equal(ecu.calls.length, 1);
  assert.deepEqual(ecu.getWriteStatus(), { state: 'BATCH_CONFIRMED', ok: true, ack: true, readback: true });
  const readback = applyReadback(editor, ecu);
  assert.equal(readback.rows[2][3], 122);
  assert.deepEqual(readback.extraRow, originalTechnicalRow, 'linha técnica foi alterada');
  assert.equal(editor.snapshot().rows[2][3], 122);
});

test('todas as 144 células formam uma única intenção e voltam no readback', () => {
  const ecu = new ScriptedMp48Ecu();
  const editor = loadEditor(ecu);
  assert.equal(MAX_SELECTION, 144);
  editor.selectAll();
  const review = applyPlannerPreview(editor, 'percent', 5);
  const expected = new Map(review.items.map(item => [`${item.row}:${item.column}`, item.target]));
  submit(editor, ecu, 'grade completa');
  const readback = applyReadback(editor, ecu);

  assert.equal(ecu.calls.length, 1);
  assert.equal(ecu.calls[0].payload.length, 144);
  expected.forEach((target, key) => {
    const [row, column] = key.split(':').map(Number);
    assert.equal(readback.rows[row][column], target);
  });
});

test('linha técnica permanece fora das 144 células editáveis', () => {
  const ecu = new ScriptedMp48Ecu();
  const editor = loadEditor(ecu);
  editor.selectAll();
  assert.equal(editor.selectionCount(), 144);
  assert.throws(() => editor.toggle(12, 0), /Célula inválida/);
  assert.equal(ecu.calls.length, 0);
});

test('cancelamento antes do envio mantém ECU intacta', () => {
  const ecu = new ScriptedMp48Ecu();
  const before = ecu.readMap();
  const editor = loadEditor(ecu);
  editor.selectOnly(4, 4);
  applyPlannerPreview(editor, 'delta', 10);
  editor.clearSelection();
  const after = ecu.readMap();
  assert.deepEqual(after.rows, before.rows);
  assert.equal(ecu.calls.length, 0);
});

test('falha de ACK não vira sucesso nem altera mapa', () => {
  for (const mode of ['ACK_TIMEOUT', 'ACK_REJECTED']) {
    const ecu = new ScriptedMp48Ecu();
    ecu.setMode(mode);
    const before = ecu.readMap();
    const editor = loadEditor(ecu);
    editor.selectOnly(1, 1);
    applyPlannerPreview(editor, 'delta', 8);
    submit(editor, ecu, mode);
    const status = ecu.getWriteStatus();
    assert.equal(status.state, 'FAILED');
    assert.equal(status.ok, false);
    assert.equal(status.ack, false);
    assert.deepEqual(ecu.readMap().rows, before.rows);
  }
});

test('readback divergente invalida sucesso mesmo após ACK', () => {
  const ecu = new ScriptedMp48Ecu();
  ecu.setMode('READBACK_DIVERGENT');
  const editor = loadEditor(ecu);
  editor.selectOnly(3, 5);
  const expected = applyPlannerPreview(editor, 'delta', 9).items[0].target;
  submit(editor, ecu, 'divergencia');
  const status = ecu.getWriteStatus();
  const readback = applyReadback(editor, ecu);

  assert.equal(status.state, 'FAILED');
  assert.equal(status.ack, true);
  assert.equal(status.readback, false);
  assert.notEqual(readback.rows[3][5], expected);
});

test('recusa imediata da ECU aparece como erro', () => {
  const ecu = new ScriptedMp48Ecu();
  ecu.setMode('START_REJECTED');
  const editor = loadEditor(ecu);
  editor.selectOnly(0, 0);
  applyPlannerPreview(editor, 'delta', 5);
  assert.throws(() => submit(editor, ecu, 'recusa'), /ECU recusou o lote/);
  assert.equal(ecu.getWriteStatus().state, 'FAILED');
});
