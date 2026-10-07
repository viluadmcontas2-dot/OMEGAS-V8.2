'use strict';
// F1 da reforma visual (UI/UX premium, tema escuro, dono 2026-10-04): tokens, controles, rolagem e gráficos. Classe de prova 1 (contrato de CSS).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const UI = path.resolve(__dirname, '../../app/src/main/assets/ui');
const read = f => fs.readFileSync(path.join(UI, f), 'utf8');
const tokens = read('tokens.css'), premium = read('styles-premium.css'), floors = read('styles-floors.css'), html = read('index.html');

test('tokens: escala de toque 58/52, fonte de botão >= 18, texto crítico >= 22, tema escuro padrão', () => {
  assert.match(tokens, /--touch-min:\s*58px/);
  assert.match(tokens, /--btn-h:\s*58px/);
  assert.match(tokens, /--btn-h-compact:\s*52px/);
  assert.ok(Number(tokens.match(/--btn-font:\s*(\d+)px/)[1]) >= 18);
  assert.ok(Number(tokens.match(/--text-critical:\s*(\d+)px/)[1]) >= 22);
  assert.match(tokens, /color-scheme:\s*dark/);
  const bg = tokens.match(/--bg:\s*#([0-9a-f]{6})/i)[1];
  assert.ok([0,2,4].every(i=>parseInt(bg.slice(i,i+2),16)<48), "superfície escura");
  for (const t of ['--space-1', '--space-6', '--radius-s', '--radius-l', '--shadow-1', '--shadow-2', '--shadow-3', '--chart-min-h', '--field-border']) assert.ok(tokens.includes(t + ':'), t);
  assert.match(tokens, /--radius:\s*14px/);
});

test('premium antes dos pisos e controles seguem --btn-h', () => {
  const links = [...html.matchAll(/<link rel="stylesheet" href="([^"]+)">/g)].map(m => m[1]);
  assert.ok(links.indexOf('styles-premium.css') > links.indexOf('styles-split-layout.css') && links.indexOf('styles-premium.css') < links.indexOf('styles-floors.css'));
  assert.match(floors, /min-height:\s*var\(--btn-h\)\s*!important/);
  assert.match(floors, /min-height:\s*var\(--btn-h-compact\)\s*!important/);
  const fieldRule = premium.match(/html body input:not\([^{]*\{[^}]*\}/)[0];
  assert.match(fieldRule, /height:\s*var\(--btn-h\)/);
  assert.match(fieldRule, /appearance:\s*none/);
  assert.match(premium, /html body select\s*\{[^}]*data:image\/svg\+xml/s, 'select com seta SVG inline');
  assert.match(premium, /\.segmented\b/);
  assert.match(premium, /input\[type="checkbox"\][^{]*\{[^}]*appearance:\s*none/s, 'toggle próprio');
  assert.match(premium, /input\[type="range"\]/);
});

test('nenhum controle fica com aparência nativa cinza: toda regra de appearance fora do premium é none', () => {
  for (const f of fs.readdirSync(UI).filter(n => n.endsWith('.css'))) {
    for (const m of read(f).matchAll(/(?<![-\w])appearance\s*:\s*([^;}]+)/g)) assert.equal(m[1].trim(), 'none', `${f}: appearance ${m[1]}`);
  }
  assert.doesNotMatch(premium, /background(-color)?:\s*(#(?:ddd|ccc|eee|e0e0e0|d3d3d3)|gr[ae]y|buttonface)\b/i);
  assert.doesNotMatch(premium, /border:\s*[3-9]px/);
});

test('rolagem: a rota ativa rola na vertical; gráficos têm min-height generoso', () => {
  assert.match(premium, /\.screen-host \.screen\.active\s*\{[^}]*overflow-y:\s*auto/s);
  assert.match(premium, /scroll-behavior:\s*smooth/);
  assert.match(tokens, /--chart-min-h:\s*clamp\(\s*(\d+)px\s*,\s*50vh\s*,\s*(\d+)px\s*\)/);
  const [, lo] = tokens.match(/--chart-min-h:\s*clamp\(\s*(\d+)px/);
  assert.ok(Number(lo) >= 280);
  for (const sel of ['.curve-chart', '.map-surface'] /* Revisto (W2): .chart-surface e .autocal-chart-host saíram (CSS podado) */) {
    assert.match(premium, new RegExp(sel.replace('.', '\\.') + '[^{]*\\{[^}]*min-height:\\s*var\\(--chart-min-h\\)'), sel);
  }
  assert.match(premium, /\.screen\.active > \*\s*\{\s*flex-shrink:\s*0/);
});
