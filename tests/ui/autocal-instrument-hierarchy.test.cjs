'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ROOT = path.join(__dirname, '../..');
const js = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const css = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/styles-autocal-cockpit.css'), 'utf8');

test('AutoCal uses graph-first premium hierarchy', () => {
  assert.match(js, /AutoCal · Gasolina e GNV/);
  assert.match(js, /Leitura da ECU · Gasolina × GNV/);
  assert.match(js, /id="autocalReferenceChart"/);
  assert.match(js, /id="autocalLiveRpm"/);
  assert.match(js, /id="autocalLivePetrol"/);
  assert.match(js, /id="autocalLiveMap"/);
  assert.match(js, /id="autocalLiveZone"/);
  assert.doesNotMatch(js, /autocalLiveLevel/);
  assert.match(css, /\.autocal-focus-toolbar\s*\{/);
  assert.match(css, /\.autocal-secondary-stack\s*\{[\s\S]*grid-template-columns:\s*1fr/);
// Actual graph size and visible point context are verified by the rendered layout gate.
});

test('secondary state stays below the graph in one vertical flow', () => {
  assert.match(css, /\.screen\.autocal-route-screen\s*\{[\s\S]*overflow-y:\s*auto/);
  assert.match(css, /\.autocal-secondary-details\s*\{[\s\S]*position:\s*static/);
  assert.match(css, /\.autocal-secondary-card\s*\{[\s\S]*width:\s*100%/);
  assert.match(css, /\.autocal-chart-inspector\s*\{[\s\S]*position:\s*static/);
  assert.match(css, /\.autocal-chart-legend\s*\{[\s\S]*position:\s*static/);
});

test('AGORA is visually distinct but remains telemetry', () => {
  assert.match(css, /\.autocal-live-point\s*\{[\s\S]*var\(--tone-19daf4\)/);
  assert.match(js, /Ele nunca vira ponto lido pela ECU/);
  assert.match(js, /id="autocalLiveNarrative"[^>]*hidden/);
  assert.match(js, /ageMs > AUTO_CAL_LIVE_STALE_MS/);
});

test('overlapping petrol and GNV curves remain distinguishable without geometric offset', () => {
  assert.match(css, /\.autocal-reference-line\.petrol:not\(\.previous\)\s*\{[\s\S]*stroke-width:\s*4/);
  assert.match(css, /\.autocal-reference-line\.gas:not\(\.previous\)\s*\{[\s\S]*stroke-width:\s*2\.5/);
  assert.match(css, /\.autocal-reference-point\.petrol\s*\{[\s\S]*fill:\s*var\(--tone-07101a\)[\s\S]*stroke:\s*var\(--tone-78b7ff\)/);
  assert.match(js, /diferença de MAP/);
});
