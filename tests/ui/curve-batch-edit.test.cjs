'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const root = path.resolve(__dirname, '../..');
const curve = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/curve.js'), 'utf8');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/index.html'), 'utf8');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-witness-multimedia.css'), 'utf8');

function methodBlock(name, nextName) {
  const start = curve.indexOf(`    ${name}(`);
  assert.ok(start >= 0, `${name} ausente`);
  const end = nextName ? curve.indexOf(`\n    ${nextName}(`, start + 1) : -1;
  return curve.slice(start, end > start ? end : start + 1800);
}

test('Curva K possui modo de seleção explícito e feedback antes de editar valor', () => {
  for (const token of [
    'this.selectedIndices = new Set()',
    'this.selectionMode = false',
    'setSelectionMode(enabled)',
    'toggleSelection(index)',
    'clearSelection()',
    'curveSelectionMode',
    'curveSelectionCount',
  ]) {
    assert.equal(curve.includes(token) || html.includes(token), true, `faltando contrato didático: ${token}`);
  }

  assert.match(html, /Seleção[^<]{0,30}(?:OFF|desativada)/i,
    'estado inicial da seleção precisa estar escrito na tela');
  assert.match(html, /Ativar seleção|Seleção ON/i,
    'operador precisa saber como entrar no modo de seleção');

  const selectedRule = css.match(/\.curve-point\.selected\s*\{([^}]*)\}/s);
  assert.ok(selectedRule, 'estilo de ponto selecionado ausente');
  assert.match(selectedRule[1], /stroke-width\s*:\s*(?:5|6|7|8)px/i,
    'seleção precisa de contorno forte, visível na multimídia');
  assert.match(selectedRule[1], /filter\s*:\s*drop-shadow|fill\s*:/i,
    'seleção precisa de halo ou preenchimento perceptível, não só mudança sutil');
});

test('arrastar só vira multi-seleção quando Seleção está ON e nunca escreve ECU', () => {
  for (const eventName of ['pointerdown', 'pointerenter', 'pointerup', 'pointercancel']) {
    assert.equal(curve.includes(eventName), true, `faltando gesto ${eventName}`);
  }
  const dragStart = curve.indexOf('pointerdown');
  const dragEnd = curve.indexOf('pointercancel', dragStart);
  assert.ok(dragStart >= 0 && dragEnd > dragStart, 'bloco de drag não encontrado');
  const dragBlock = curve.slice(dragStart, dragEnd + 360);
  assert.match(dragBlock, /selectionMode/,
    'gesto precisa respeitar modo de seleção explícito');
  assert.equal(/writeCurve|curveWriteButton|startCurveWrite|startCurveBatchWrite/.test(dragBlock), false,
    'seleção não pode escrever na ECU');
});

test('nudge em lote é delta relativo e redesenha uma vez', () => {
  const block = methodBlock('nudgeSelection', 'focusSuggestion');
  assert.match(block, /this\.selectedPointIndices\(\)/);
  assert.match(block, /current\s*\+\s*delta/,
    'botões ±0,01/±0,05 devem continuar somando delta ao fator atual/proposto');
  assert.match(block, /previewCurvePoint/);
  assert.match(block, /acceptPreview\(preview, true\)/);

  const loopStart = block.indexOf('for (');
  const renderChart = block.lastIndexOf('this.renderChart()');
  const renderList = block.lastIndexOf('this.renderProposalList()');
  assert.ok(loopStart >= 0, 'batch precisa iterar pontos selecionados');
  assert.ok(renderChart > loopStart && renderList > loopStart,
    'render final precisa ocorrer depois da preparação do lote');
  assert.equal((block.match(/this\.renderChart\(\)/g) || []).length, 1,
    'batch deve redesenhar o gráfico uma única vez');
  assert.equal((block.match(/this\.renderProposalList\(\)/g) || []).length, 1,
    'batch deve redesenhar a lista uma única vez');
});

test('Definir valor é atribuição absoluta, nunca delta', () => {
  const block = methodBlock('assignSelectionTarget', 'prepareSuggestion');
  assert.match(block, /selectedPointIndices\(\)/);
  assert.match(block, /previewCurvePoint\(index,\s*targetFactor\)/,
    'cada ponto selecionado precisa receber exatamente o fator digitado');
  assert.doesNotMatch(block, /current\s*\+\s*targetFactor|factor\s*\+\s*targetFactor/,
    'valor absoluto não pode passar por matemática de delta');
  assert.match(block, /acceptPreview\(preview, true\)/);
});

console.log('CURVE_BATCH_EDIT_CONTRACT=PASS');
