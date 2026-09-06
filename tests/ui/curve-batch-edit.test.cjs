'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const root = path.resolve(__dirname, '../..');
const curve = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/curve.js'), 'utf8');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/index.html'), 'utf8');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-witness-multimedia.css'), 'utf8');

test('Curva K mantém seleção múltipla explícita e didática', () => {
  for (const token of [
    'this.selectedIndices = new Set()',
    'toggleSelection(index)',
    'clearSelection()',
    'nudgeSelection(delta)',
    'curveClearSelection',
  ]) {
    assert.equal(curve.includes(token) || html.includes(token), true, `faltando contrato de seleção: ${token}`);
  }
  assert.match(html, /Limpar seleção/);
  assert.match(html, /selecione|selecion/i);
  assert.match(css, /\.curve-point\.selected/);
});

test('arrastar sobre pontos seleciona em lote sem tocar no writer', () => {
  for (const eventName of ['pointerdown', 'pointerenter', 'pointerup', 'pointercancel']) {
    assert.equal(curve.includes(eventName), true, `faltando gesto ${eventName}`);
  }
  const dragStart = curve.indexOf('pointerdown');
  const dragEnd = curve.indexOf('pointercancel', dragStart);
  assert.ok(dragStart >= 0 && dragEnd > dragStart, 'bloco de drag não encontrado');
  const dragBlock = curve.slice(dragStart, dragEnd + 240);
  assert.equal(/writeCurve|curveWriteButton|startCurveWrite/.test(dragBlock), false, 'seleção não pode escrever na ECU');
});

test('nudge em lote prepara todos e renderiza apenas depois do loop', () => {
  const start = curve.indexOf('nudgeSelection(delta)');
  assert.ok(start >= 0, 'nudgeSelection ausente');
  const end = curve.indexOf('\n    }', start);
  assert.ok(end > start, 'corpo de nudgeSelection não localizado');
  const block = curve.slice(start, end + 6);
  assert.match(block, /this\.selectedIndices/);
  assert.match(block, /previewCurvePoint/);
  assert.match(block, /acceptPreview\(preview, true\)/);

  const loopStart = block.indexOf('for (');
  const renderChart = block.lastIndexOf('this.renderChart()');
  const renderList = block.lastIndexOf('this.renderProposalList()');
  assert.ok(loopStart >= 0, 'batch precisa iterar pontos selecionados');
  assert.ok(renderChart > loopStart && renderList > loopStart, 'render final precisa ocorrer depois da preparação do lote');
  assert.equal((block.match(/this\.renderChart\(\)/g) || []).length, 1, 'batch deve redesenhar o gráfico uma única vez');
  assert.equal((block.match(/this\.renderProposalList\(\)/g) || []).length, 1, 'batch deve redesenhar a lista uma única vez');
});

console.log('CURVE_BATCH_EDIT_CONTRACT=PASS');
