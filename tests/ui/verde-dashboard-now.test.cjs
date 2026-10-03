'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.join(__dirname, '../..');

function normalizeJsSource(text) {
  return text
    .replace(/\\x([0-9A-Fa-f]{2})/g, (_, hex) =>
      String.fromCharCode(parseInt(hex, 16)))
    .replace(/\\u([0-9A-Fa-f]{4})/g, (_, hex) =>
      String.fromCharCode(parseInt(hex, 16)));
}

const dashboard = normalizeJsSource(fs.readFileSync(
  path.join(ROOT, 'app/src/main/assets/ui/screens/dashboard.js'),
  'utf8',
));
const stylePath = path.join(
  ROOT,
  'app/src/main/assets/ui/styles-dashboard-now.css',
);
const styles = fs.existsSync(stylePath)
  ? fs.readFileSync(stylePath, 'utf8')
  : '';

function occurrences(text, token) {
  return (text.match(new RegExp(token, 'g')) || []).length;
}

test('Agora Verde preserva a hierarquia multimídia Blue', () => {
  for (const marker of [
    'multimedia-now-screen',
    'now-dashboard-shell',
    'now-tile-grid',
    'dashHeroPetrol',
    'dashRpm',
    'dashMap',
    'dashFuel',
    'dashLevelsRaw',
    'dashRefino',
    'dashCell',
    'dashHealth',
  ]) {
    assert.match(dashboard, new RegExp(marker), `missing ${marker}`);
  }

  assert.equal(occurrences(dashboard, '>RPM<'), 1);
  assert.equal(occurrences(dashboard, '>MAP<'), 1);
  assert.equal(occurrences(dashboard, '>COMBUSTÍVEL<'), 1);
  assert.equal(occurrences(dashboard, '>LEVELS RAW<'), 1);
  assert.equal(occurrences(dashboard, '>REFINO<'), 1);
  assert.equal(occurrences(dashboard, '>CÉLULA<'), 1);
  assert.doesNotMatch(dashboard, /dashHeroRpm|dashLtft|GAS INJ\./);
});

test('CSS contém somente o recorte Agora', () => {
  assert.match(styles, /\.now-tile-grid[\s\S]*grid-template-columns:\s*repeat\(2/);
  assert.match(styles, /\.now-tile b[\s\S]*font-size:\s*112px/);
  assert.doesNotMatch(styles, /witness-|multimedia-obd|map-screen|curve-screen|learning-screen/);
  assert.doesNotMatch(styles, /@keyframes|animation:|backdrop-filter/);
});

test('dashboard é consumidor Red ou Verde e não carrega Blue', () => {
  assert.doesNotMatch(
    dashboard,
    /Blue|Causal|writeMap|writeCurve|startKWrite|startKBatchWrite|startKFactorWrite/,
  );
  assert.doesNotMatch(dashboard, /\?\.|\?\?|replaceAll\(/);
  assert.match(dashboard, /renderRefino\(\)/);
  assert.match(dashboard, /styles-dashboard-now\.css/);
});


test('dashboard não converte ausência de telemetria em zero físico', () => {
  assert.match(dashboard, /const finite = rules\.finite/);
  assert.doesNotMatch(dashboard, /function finite\(/);
});
