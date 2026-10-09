'use strict';
// TRAVA PERMANENTE autocal-sem-leitura-anterior (dono, 2026-10-08, AGENTS.md regra 16): o AutoCal NUNCA mostra leitura anterior
// (nem curva esmaecida, nem legenda "Leitura anterior", nem botao, nem funcao que a guarde). Nao remover nem enfraquecer.
// Par em Python: tests/test_autocal_sem_leitura_anterior.py. Mutante: autocal-leitura-anterior-volta (tools/wiring/run_ui_mutants.py).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const UI = process.env.UI_ROOT || path.join(__dirname, '../../app/src/main/assets/ui');
const FILES = ['screens/autocal-cockpit.js', 'components/curve-chart.js', 'styles-autocal-cockpit.css', 'styles-autocal-refino.css', 'index.html'];
const FORBIDDEN = [
  /Leitura\s+anterior/i, /Curva\s+anterior/i, /GNV\s+anterior/i, /data-legend="previous"/, /autocal-previous/, /autocal-history/,
  /previousReferencePoints/, /comparisonPinned/, /chartHistoryVisible/, /renderResetComparison/, /referenceComparison/, /autocalResetComparison/,
  /historyCurve/, /\bhistory\b/i, /class="[^"]*\bprevious\b/, /\.autocal-reference-line\.previous/,
];

test('autocal-sem-leitura-anterior: nenhum arquivo do AutoCal contem leitura anterior', () => {
  for (const f of FILES) {
    const src = fs.readFileSync(path.join(UI, f), 'utf8');
    for (const re of FORBIDDEN) assert.doesNotMatch(src, re, `${f}: ${re} (o AutoCal nunca mostra leitura anterior)`);
  }
});

test('autocal-sem-leitura-anterior: o grafico e a legenda ao vivo nao desenham curva anterior', () => {
  const chart = fs.readFileSync(path.join(UI, 'components/curve-chart.js'), 'utf8');
  const legend = chart.slice(chart.indexOf('function legendHtml'), chart.indexOf('function legendHtml') + 600);
  assert.doesNotMatch(legend, /previous|anterior/i);
  const svg = chart.slice(chart.indexOf('function buildSvg'));
  assert.doesNotMatch(svg.slice(0, svg.indexOf('function legendHtml')), /model\.history|\bprevious\b/);
});
