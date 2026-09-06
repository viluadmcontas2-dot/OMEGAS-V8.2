'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.join(__dirname, '../..');
const html = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/index.html'), 'utf8');
const dashboard = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/dashboard.js'), 'utf8');
const obd = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/obd.js'), 'utf8');
const styles = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/styles.css'), 'utf8') + '\n' +
  fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/styles-calibration-obd.css'), 'utf8');
const api = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/core/native-api.js'), 'utf8');
const bridge = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt'), 'utf8');

function section(name, nextName) {
  const start = html.indexOf(`data-screen="${name}"`);
  assert.notEqual(start, -1, `screen ${name} must exist`);
  const end = nextName ? html.indexOf(`data-screen="${nextName}"`, start) : html.length;
  return html.slice(start, end === -1 ? html.length : end);
}

function occurrences(text, token) {
  return (text.match(new RegExp(token, 'g')) || []).length;
}

test('dashboard 16:9 usa hierarquia grande sem repetir telemetria', () => {
  const now = section('dashboard', 'learning');
  for (const marker of [
    'dashHeroPetrol', 'dashHeroStatus', 'dashRpm', 'dashMap', 'dashFuel', 'dashStft', 'dashCell',
    'now-metric-grid', 'now-metric-card',
  ]) assert.match(now, new RegExp(marker), `dashboard missing ${marker}`);

  assert.equal(occurrences(now, '>RPM<'), 1, 'RPM must appear exactly once in the main dashboard');
  assert.equal(occurrences(now, '>MAP<'), 1, 'MAP must appear exactly once in the main dashboard');
  assert.equal(occurrences(now, '>COMBUSTÍVEL<'), 1, 'fuel must appear exactly once in the main dashboard');
  assert.equal(occurrences(now, '>STFT<'), 1, 'STFT must appear exactly once in the main dashboard');
  assert.equal(occurrences(now, '>CÉLULA<'), 1, 'cell must appear exactly once in the main dashboard');
  assert.doesNotMatch(now, /LTFT|GAS INJ\./);

  assert.doesNotMatch(dashboard, /dashHeroRpm|dashLtft|ensureLayout\(/);
  assert.match(dashboard, /text\('dashStft'/);
  assert.match(dashboard, /text\('dashHeroStatus'/);
  assert.doesNotMatch(dashboard, /dashHeroContext[\s\S]{0,180}RPM/);

  assert.match(styles, /\.now-metric-grid\s*\{[^}]*grid-template-columns\s*:\s*repeat\(5/si);
  assert.match(styles, /\.now-hero-value[^}]*font-size\s*:\s*(?:9[2-9]|[1-9][0-9]{2})px/si);
  assert.match(styles, /\.now-metric-card[^}]*min-height\s*:\s*(?:9[0-9]|[1-9][0-9]{2})px/si);
});

test('tela OBD vira witness STFT e elimina scanner legado', () => {
  const screen = section('obd', 'suggestions');
  for (const marker of [
    'data-obd-view="observe"', 'data-obd-view="setup"',
    'obdLiveStft', 'obdWitnessState', 'obdGasolineReference', 'obdGnvStft', 'obdResidual',
    'obdPairedRpm', 'obdPairedMap', 'obdPairedPetrol', 'obdWitnessQuality', 'obdWitnessSamples',
    'obdConnectionCenter', 'obdSensorList',
  ]) assert.match(screen, new RegExp(marker), `OBD witness missing ${marker}`);

  assert.doesNotMatch(screen, /data-obd-view="map"|data-obd-panel="map"|Mapa OBD|LTFT|CARGA|PEDAL|MAF|ÁGUA|TENSÃO ECU|alvo 0%/i);
  assert.match(screen, /Gasolina[^<]{0,80}referência física|referência física[^<]{0,80}Gasolina/i);
  assert.match(screen, /OBD[^<]{0,80}não escreve[^<]{0,30}K/i);

  assert.match(obd, /this\.api\.obdWitness\(\)/);
  assert.doesNotMatch(obd, /obdMaps\(|renderMap\(|mapLayer|ltft|longTermFuelTrim|calculatedLoad|throttle|mafGps|coolant|moduleVoltage/i);
  for (const forbidden of ['writeMap', 'writeCurve', 'startKWrite', 'startKBatchWrite', 'startKFactorWrite']) {
    assert.equal(obd.includes(forbidden), false, `${forbidden} cannot exist in OBD witness screen`);
  }
});

test('witness possui canal read-only explícito até a UI', () => {
  assert.match(bridge, /@JavascriptInterface fun getObdWitness\(\): String/);
  assert.match(bridge, /obdWitnessStatusJson\(\)/);
  assert.match(api, /obdWitness\(\)/);
  assert.match(api, /getObdWitness/);
  assert.match(api, /gasolineReferencePct/);
  assert.match(api, /gnvStftPct/);
  assert.match(api, /residualPp/);
});
