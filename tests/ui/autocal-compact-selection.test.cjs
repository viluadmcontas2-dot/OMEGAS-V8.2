'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const chart = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/components/curve-chart.js'), 'utf8');
const css = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/styles-autocal-refino.css'), 'utf8') +
  '\n' + fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/styles-autocal-cockpit.css'), 'utf8');

test('AutoCal usa o grafico como superficie de zonas, sem faixa Z1-Z4 duplicada', () => {
  assert.doesNotMatch(cockpit, /autocal-zone-strip/);
  assert.doesNotMatch(cockpit, /id="autocalZoneMeter"/);
  assert.match(chart, /data-autocal-zone-surface/);
  assert.match(chart, /autocal-zone-petrol-edge/);
  assert.match(chart, /autocal-zone-gas-edge/);
  assert.match(chart, /data-base-label="Z\$\{zone\.zone\}"/);
});

test('AutoCal tem uma unica linha operacional visivel por estado', () => {
  assert.match(cockpit, /autocal-command-row/);
  assert.match(cockpit, /data-autocal-toggle/);
  assert.doesNotMatch(cockpit, /data-autocal-reacquire-point/);
  assert.doesNotMatch(cockpit, /data-autocal-toggle-point-selection/);
  assert.match(cockpit, /data-autocal-reacquire-selected/);
  assert.match(cockpit, /data-autocal-clear-point-selection/);
  assert.match(cockpit, /data-autocal-done-points/);
});

test('toque em bolinha nativa alterna selecao diretamente e mantem selecao continua', () => {
  const clickBlock = cockpit.slice(
    cockpit.indexOf("const acquiredPoint = event.target.closest('[data-autocal-acquired-index]')"),
    cockpit.indexOf("const point = event.target.closest('[data-autocal-ref-index]')")
  );
  assert.match(clickBlock, /toggleAcquiredPointSelection/);
  assert.doesNotMatch(clickBlock, /inspectAcquiredPoint\s*\(/);
  assert.match(cockpit, /const editing = count > 0/);
});

test('grafico AutoCal recupera o espaco vertical da faixa removida', () => {
  assert.match(css, /\.ar-shell\.ar-autocal\s+\.ar-chart-host\s*\{[^}]*height:\s*clamp\([^}]*100vh\s*-\s*2[0-6]0px/s);
  assert.doesNotMatch(cockpit, />70%<|>85%</);
});
