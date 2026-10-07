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

test('Agora Diamante mostra 4 blocos de direção pela leitura única, intenção e resultado', () => {
  const strip=fs.readFileSync(path.join(ROOT,'app/src/main/assets/ui/components/vehicle-status-strip.js'),'utf8');
  for (const marker of ['multimedia-now-screen','now-dashboard-shell','dashNext','dashEquivalence','dashLevelsRaw','dashRefino','dashHealth']) assert.ok(dashboard.includes(marker),marker);
  for (const marker of ['dashHeroPetrol','dashRpm','dashMap','dashFuel']) {
    assert.ok(strip.includes(marker),marker+' global');
    assert.ok(!dashboard.includes(marker),marker+' sem redundância');
  }
  assert.doesNotMatch(dashboard, /dashHeroRpm|dashLtft/);
  // Revisto (W2): o Agora não tem mais blocos de direção; a leitura única vive só no cabeçalho.
  assert.equal((dashboard.match(/class="now-tile"/g) || []).length, 0);
  assert.doesNotMatch(dashboard, /renderDrive/);
});

test('CSS contém somente o recorte Agora, com resultado, intenção e cobertura', () => {
  assert.match(styles, /\.now-drive[^}]*grid-template-columns:repeat\(4,1fr\)/);
  assert.match(styles, /\.now-equivalence > b[^}]*font-size:64px/);
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
  assert.doesNotMatch(dashboard, /function finite\(/);
  assert.match(dashboard, /ns\.LiveStore\.read\(state\)/);
});
