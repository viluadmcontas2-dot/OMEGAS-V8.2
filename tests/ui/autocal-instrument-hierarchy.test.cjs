'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ROOT = path.join(__dirname, '../..');
const js = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const css = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/styles-autocal-cockpit.css'), 'utf8');

test('AutoCal uses acquisition curve as dominant instrument surface', () => {
  assert.match(js, /CURVA DE AQUISIÇÃO/);
  assert.match(js, /Gasolina × GNV/);
  assert.match(js, /id="autocalReferenceChart"/);
  assert.match(js, /id="autocalLiveRpm"/);
  assert.match(js, /id="autocalLivePetrol"/);
  assert.match(js, /id="autocalLiveMap"/);
  assert.doesNotMatch(js, /autocalLiveLevel/);
  assert.doesNotMatch(js, /LEVELS RAW/);
  assert.match(css, /\.autocal-reference-card\s*\{[\s\S]*order:\s*0/);
  assert.match(css, /\.autocal-hero\s*\{\s*order:\s*2/);
  assert.match(css, /\.autocal-reference-card \.autocal-chart-workspace\s*\{\s*order:\s*2/);
  assert.match(css, /\.autocal-reference-card \.autocal-live-strip\s*\{\s*order:\s*3/);
  assert.match(css, /height:\s*clamp\(300px,\s*43vh,\s*390px\)/);
});

test('AutoCal inspector sits below the plot without stealing chart width', () => {
  const finalWorkspace = css.lastIndexOf('.autocal-chart-workspace {');
  const finalInspector = css.lastIndexOf('.autocal-chart-inspector {');
  assert.ok(finalWorkspace >= 0);
  assert.ok(finalInspector > finalWorkspace);
  assert.match(css.slice(finalWorkspace, finalInspector), /grid-template-columns:\s*minmax\(0,\s*1fr\)/);
  assert.match(css.slice(finalInspector), /position:\s*static/);
});

test('AGORA is visually distinct but remains telemetry', () => {
  assert.match(css, /\.autocal-live-point\s*\{[\s\S]*#19daf4/);
  assert.match(js, /Ele nunca vira evidência adquirida/);
  assert.match(js, /id="autocalLiveNarrative"[^>]*hidden/,
    'narrativa duplicada deve ficar fora da superfície principal para preservar a curva em 1280x720');
  assert.match(js, /ageMs > AUTO_CAL_LIVE_STALE_MS/);
});


test('overlapping petrol and GNV curves remain distinguishable without geometric offset', () => {
  assert.match(css, /\.autocal-reference-line\.petrol:not\(\.previous\)\s*\{[\s\S]*stroke-width:\s*4/);
  assert.match(css, /\.autocal-reference-line\.gas:not\(\.previous\)\s*\{[\s\S]*stroke-width:\s*2\.5/);
  assert.match(css, /\.autocal-reference-point\.petrol\s*\{[\s\S]*fill:\s*#07101a[\s\S]*stroke:\s*#78b7ff/);
  assert.match(js, /ΔMAP/);
});
