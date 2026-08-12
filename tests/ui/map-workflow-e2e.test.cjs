'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { MapEditor } = require('../../app/src/main/assets/ui/map-editor.js');

const ROOT = path.resolve('.');
const MAP_SCREEN = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/map.js'), 'utf8');
const HTML = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/index.html'), 'utf8');
const NATIVE_API = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/core/native-api.js'), 'utf8');

function editorWithMap() {
  const editor = new MapEditor();
  editor.load({
    ok: true,
    rows: Array.from({ length: 12 }, () => Array(12).fill(120)),
    extraRow: Array(12).fill(0),
    axes: {
      petrolBins: [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18],
      rpmBins: [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500],
    },
    hash: 'flow-map',
  });
  return editor;
}

test('abrir Mapa K inicia leitura automática e leitura nunca chama writer', () => {
  assert.match(MAP_SCREEN, /onEnter\(context\).*?startRead\(true\)/s);
  const startRead = MAP_SCREEN.match(/startRead\(automatic\) \{(.*?)\n    \}/s)?.[1] || '';
  assert.match(startRead, /this\.api\.startMapRead\(\)/);
  assert.doesNotMatch(startRead, /writeMap|startMapBatchWrite|startKBatchWrite/);
});

test('fluxo visual é selecionar, pedir prévia Kotlin, revisar e só então gravar', () => {
  assert.match(MAP_SCREEN, /this\.editor\.toggle/);
  assert.match(MAP_SCREEN, /this\.editor\.selectRange/);
  const applyAdjustment = MAP_SCREEN.match(/applyAdjustment\(\) \{(.*?)\n    \}/s)?.[1] || '';
  const openReview = MAP_SCREEN.match(/openReview\(\) \{(.*?)\n    \}/s)?.[1] || '';
  const writeReview = MAP_SCREEN.match(/writeReview\(\) \{(.*?)\n    \}/s)?.[1] || '';
  assert.match(applyAdjustment, /this\.api\.previewMapAdjustment/);
  assert.match(applyAdjustment, /this\.editor\.applyNativePreview/);
  assert.doesNotMatch(applyAdjustment, /writeMap|startMapBatchWrite|startKBatchWrite/);
  assert.match(openReview, /this\.editor\.buildReview\(\)/);
  assert.doesNotMatch(openReview, /this\.api\.writeMap/);
  assert.match(writeReview, /this\.api\.writeMap\(this\.review\.items/);
});

test('não existe modo oficina nem checkbox de confirmação no fluxo ativo', () => {
  const active = MAP_SCREEN + HTML;
  assert.doesNotMatch(active, /workshopModeButton|workshopRequested|confirmWriteCheckbox/);
  assert.match(HTML, /Gravar alterações na ECU/);
  assert.match(HTML, /Checkpoint, ACK e readback continuam obrigatórios/);
});

test('uma grade completa produz revisão de 144 células após a prévia nativa e antes de qualquer envio', () => {
  const editor = editorWithMap();
  editor.selectAll();
  editor.setAdjustment('delta', 5);
  editor.applyNativePreview(editor.selectedCells().map(cell => ({ ...cell, target: 125, changed: true })));
  const review = editor.buildReview();
  assert.equal(review.count, 144);
  assert.equal(review.items.length, 144);
  assert.deepEqual(review.items[0], { row: 0, column: 0, current: 120, target: 125, petrolMs: 2, rpm: 850 });
});

test('cancelar revisão não possui caminho de escrita', () => {
  const closeReview = MAP_SCREEN.match(/closeReview\(\) \{(.*?)\n    \}/s)?.[1] || '';
  assert.doesNotMatch(closeReview, /writeMap|startMapBatchWrite|startKBatchWrite/);
  assert.match(closeReview, /is-reviewing/);
});

test('resultado só é sucesso com BATCH_CONFIRMED e readbackValid', () => {
  assert.match(MAP_SCREEN, /operation\.state === 'BATCH_CONFIRMED' && operation\.readbackValid === true/);
  assert.match(MAP_SCREEN, /BATCH_PARTIAL_FAILED/);
  assert.match(MAP_SCREEN, /A ECU não confirmou toda a operação/);
});

test('frontend envia uma única intenção ao coordenador V7, sem chunking na escrita', () => {
  assert.match(NATIVE_API, /'startMapBatchWrite'/);
  const writeReview = MAP_SCREEN.match(/writeReview\(\) \{(.*?)\n    \}/s)?.[1] || '';
  assert.match(writeReview, /this\.api\.writeMap\(this\.review\.items/);
  assert.doesNotMatch(writeReview, /slice\(|chunk|16 células|MAX_SELECTION\s*=\s*16/);
});
