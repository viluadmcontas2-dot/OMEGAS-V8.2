'use strict';
// W2 · AutoCal: aba independente de primeiro nível, gráfico sem blocos acima, barra única de três botões, escala que contém o cursor.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const UI = path.resolve(__dirname, '../../app/src/main/assets/ui');
const read = f => fs.readFileSync(path.join(UI, f), 'utf8');

test('W2: AutoCal é botão de primeiro nível na barra, com as outras sete abas', () => {
  const html = read('index.html');
  const nav = html.slice(html.indexOf('<nav class="side-nav"'), html.indexOf('</nav>'));
  const routes = [...nav.matchAll(/<button type="button" data-route="([^"]+)"/g)].map(m => m[1]);
  assert.deepEqual(routes, ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools', 'diagnostico']);
  assert.doesNotMatch(nav, /Avançado|nav-advanced/);
});

test('W2: AutoCal sem cabeçalho/Detalhes/Histórico/Opções e com a barra Pausar · Reler GNV · Reler gasolina (fonte)', () => {
  const src = read('screens/autocal-cockpit.js');
  assert.match(src, /data-autocal-action="RESET_GAS">Reler GNV</);
  assert.match(src, /data-autocal-action="RESET_PETROL">Reler gasolina</);
  assert.doesNotMatch(src, /class="ar-status"|ar-more|data-autocal-history|data-autocal-sessions|autocal-zone-card|autocal-reset-confirm|Mais opções/);
});

test('W2: a escala horizontal do gráfico cresce para conter o cursor (sem "fora da escala")', () => {
  const chart = read('components/curve-chart.js');
  assert.match(chart, /Math\.max\(\.\.\.points\.map\(p => p\.x\), Number\.isFinite\(o\.liveMs\)/);
  const cockpit = read('screens/autocal-cockpit.js');
  assert.match(cockpit, /this\.liveExtentMs = wanted; this\.renderReferenceChart/);
  assert.match(cockpit, /liveMs: this\.liveExtentMs \|\| null/);
});
