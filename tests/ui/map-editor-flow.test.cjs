const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const { MapEditor, MAX_SELECTION } = require(path.resolve('app/src/main/assets/ui/map-editor.js'));

function loadedEditor() {
  const editor = new MapEditor();
  editor.load({
    rows: Array.from({ length: 12 }, () => Array(12).fill(120)),
    extraRow: Array(12).fill(0),
    axes: {
      petrolBins: [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18],
      rpmBins: [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500],
    },
    hash: 'test',
  });
  return editor;
}

function applyPreview(editor, targetFactory) {
  const items = editor.selectedCells().map(cell => ({
    ...cell,
    target: targetFactory(cell),
    changed: targetFactory(cell) !== cell.current,
  }));
  return editor.applyNativePreview(items);
}

function submitIntent(editor, api, reason = 'teste') {
  const review = editor.buildReview();
  const result = api.writeMap(review.items, 3, 150, reason);
  if (!result || !result.ok || !result.started) throw new Error(result?.error || 'A escrita não iniciou');
  return { review, result };
}

test('revisão exige alvos exatos da prévia nativa e não escreve por si só', () => {
  const editor = loadedEditor();
  editor.toggle(0, 0);
  editor.setAdjustment('percent', 12);
  assert.throws(() => editor.buildReview(), /prévia nativa/i);
  const review = applyPreview(editor, () => 134);
  assert.equal(review.count, 1);
  assert.deepEqual(review.items[0], {
    row: 0, column: 0, current: 120, target: 134, petrolMs: 2, rpm: 850,
  });
});

test('seleção suporta todas as 144 células graváveis sem limite artificial de 16', () => {
  const editor = loadedEditor();
  assert.equal(MAX_SELECTION, 144);
  assert.equal(editor.selectAll(), 144);
  assert.equal(editor.selectionCount(), 144);
});

test('seleção por faixa, linha e coluna preserva apenas células graváveis', () => {
  const editor = loadedEditor();
  assert.equal(editor.selectRange(1, 2, 3, 4, false), 9);
  assert.equal(editor.selectRow(0, true), 21);
  assert.equal(editor.selectColumn(11, true), 32);
  assert.throws(() => editor.selectRow(12), /Linha inválida/);
  assert.throws(() => editor.selectColumn(12), /Coluna inválida/);
});

test('toque no cabeçalho RPM alterna a coluna inteira sem tocar linha técnica', () => {
  const editor = loadedEditor();
  assert.equal(editor.toggleColumn(3), 12);
  for (let row = 0; row < 12; row += 1) assert.equal(editor.isSelected(row, 3), true);
  assert.equal(editor.toggleColumn(3), 0);
  for (let row = 0; row < 12; row += 1) assert.equal(editor.isSelected(row, 3), false);
  assert.throws(() => editor.toggleColumn(12), /Coluna inválida/);
});

test('toque no cabeçalho Petrol Inj alterna a linha inteira', () => {
  const editor = loadedEditor();
  assert.equal(editor.toggleRow(5), 12);
  for (let column = 0; column < 12; column += 1) assert.equal(editor.isSelected(5, column), true);
  assert.equal(editor.toggleRow(5), 0);
});

test('sugestões persistentes podem preparar alvos exatos distintos numa única revisão', () => {
  const editor = loadedEditor();
  assert.equal(editor.setTargetOverrides([
    { row: 1, column: 2, after: 127 },
    { row: 4, column: 7, after: 113 },
  ]), 2);
  const review = editor.buildReview();
  assert.equal(review.count, 2);
  assert.deepEqual(review.items.map(item => [item.row, item.column, item.current, item.target]), [
    [1, 2, 120, 127],
    [4, 7, 120, 113],
  ]);
});

test('prévia nativa incompleta é recusada', () => {
  const editor = loadedEditor();
  editor.selectRange(0, 0, 0, 1, false);
  editor.setAdjustment('delta', 5);
  assert.throws(() => editor.applyNativePreview([
    { row: 0, column: 0, target: 125 },
  ]), /incompleta/i);
});

test('uma intenção humana envia as 144 células em uma única chamada do adaptador V7', () => {
  const editor = loadedEditor();
  editor.selectAll();
  editor.setAdjustment('delta', 5);
  applyPreview(editor, cell => cell.current + 5);
  const calls = [];
  const api = {
    writeMap(items, maxStep, pauseMs, reason) {
      calls.push({ items, maxStep, pauseMs, reason });
      return { ok: true, started: true, state: 'MAP_K_QUEUED' };
    },
  };
  const sent = submitIntent(editor, api, 'teste real');
  assert.equal(sent.review.count, 144);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].items.length, 144);
  assert.equal(calls[0].items[0].current, 120);
  assert.equal(calls[0].items[0].target, 125);
  assert.equal(calls[0].maxStep, 3);
  assert.equal(calls[0].pauseMs, 150);
});

test('cancelar antes da chamada mantém a interface sem escrita', () => {
  const editor = loadedEditor();
  editor.selectOnly(2, 2);
  editor.setAdjustment('delta', 4);
  applyPreview(editor, () => 124);
  assert.equal(editor.buildReview().count, 1);
  editor.clearSelection();
  assert.equal(editor.selectionCount(), 0);
  assert.equal(editor.targetOverrides.size, 0);
});

test('a linha técnica não faz parte do modelo editável 12 × 12', () => {
  const editor = loadedEditor();
  assert.throws(() => editor.toggle(12, 0), /Célula inválida/);
});

test('reset invalida mapa, seleção, eixos e overrides antes de uma nova leitura', () => {
  const editor = loadedEditor();
  editor.toggle(0, 0);
  editor.setTargetOverride(0, 0, 125);
  const snapshot = editor.reset();
  assert.equal(editor.hasMap(), false);
  assert.equal(editor.selectionCount(), 0);
  assert.equal(editor.targetOverrides.size, 0);
  assert.deepEqual(snapshot.rows, []);
  assert.deepEqual(snapshot.axes, { petrolBins: [], rpmBins: [] });
  assert.deepEqual(snapshot.extraRow, []);
  assert.equal(snapshot.hash, '');
  assert.throws(() => editor.toggle(0, 0), /Leia o Mapa K/);
});
