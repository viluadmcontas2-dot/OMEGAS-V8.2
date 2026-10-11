'use strict';
// TRAVA autocal-sem-leitura-anterior (dono, 2026-10-11, AGENTS.md): o AutoCal NUNCA mostra leitura anterior
// (nem curva esmaecida, nem legenda, nem botão, nem comparação antes × depois, nem função que a guarde).
// Tudo em tempo real. Par em Python: tests/test_autocal_sem_leitura_anterior.py. Não remover nem enfraquecer.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const UI = process.env.UI_ROOT || path.join(__dirname, '../../app/src/main/assets/ui');
const FILES = ['screens/autocal-cockpit.js', 'styles-autocal-cockpit.css', 'index.html'];
const FORBIDDEN = [
  /Leitura\s+anterior/i, /Curva\s+anterior/i, /GNV\s+anterior/i, /data-legend="previous"/, /autocal-previous/, /autocal-history/,
  /previousReferencePoints/, /comparisonPinned/, /chartHistoryVisible/, /renderResetComparison/, /referenceComparison/, /autocalResetComparison/,
  /historyCurve/, /\bhistory\b/i, /class="[^"]*\bprevious\b/, /\.autocal-reference-line\.previous/, /_GAS_PREV/,
];

test('autocal-sem-leitura-anterior: nenhum arquivo do AutoCal contém leitura anterior', () => {
  for (const f of FILES) {
    const src = fs.readFileSync(path.join(UI, f), 'utf8');
    for (const re of FORBIDDEN) assert.doesNotMatch(src, re, `${f}: ${re} (o AutoCal nunca mostra leitura anterior)`);
  }
});

test('autocal-sem-leitura-anterior: o modelo não guarda nem compara pontos anteriores', () => {
  const ctx = { console, setTimeout: () => 0, clearTimeout: () => {} };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(UI, 'screens/autocal-cockpit.js'), 'utf8'), ctx);
  const model = ctx.OmegasUi.AutoCalUxModel;
  assert.equal(typeof model.referenceComparison, 'undefined');
  const projection = { sessionId: 1, referenceUsable: true };
  const transition = model.referenceTransition(projection, { sessionId: 2, referenceUsable: true }, {}, {}, {});
  assert.equal('previousPoints' in transition, false);
  assert.equal('clearHistory' in transition, false);
});
