'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.join(__dirname, '../..');
const dashboard = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/dashboard.js'), 'utf8');
const obd = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/obd.js'), 'utf8');
const stylePath = path.join(ROOT, 'app/src/main/assets/ui/styles-witness-multimedia.css');
const styles = fs.existsSync(stylePath) ? fs.readFileSync(stylePath, 'utf8') : '';
const service = fs.readFileSync(path.join(ROOT, 'app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt'), 'utf8');

function occurrences(text, token) {
  return (text.match(new RegExp(token, 'g')) || []).length;
}

test('dashboard 16:9 usa um único template grande sem repetir telemetria', () => {
  for (const marker of [
    'dashHeroPetrol', 'dashHeroStatus', 'dashRpm', 'dashMap', 'dashFuel', 'dashStft', 'dashCell',
    'now-metric-grid', 'now-metric-card',
  ]) assert.match(dashboard, new RegExp(marker), `dashboard missing ${marker}`);

  assert.equal(occurrences(dashboard, '>RPM<'), 1, 'RPM must appear exactly once in dashboard runtime markup');
  assert.equal(occurrences(dashboard, '>MAP<'), 1, 'MAP must appear exactly once in dashboard runtime markup');
  assert.equal(occurrences(dashboard, '>COMBUSTÍVEL<'), 1, 'fuel must appear exactly once in dashboard runtime markup');
  assert.equal(occurrences(dashboard, '>STFT<'), 1, 'STFT must appear exactly once in dashboard runtime markup');
  assert.equal(occurrences(dashboard, '>CÉLULA<'), 1, 'cell must appear exactly once in dashboard runtime markup');
  assert.doesNotMatch(dashboard, /dashHeroRpm|dashLtft|LTFT|GAS INJ\./);
  assert.match(dashboard, /text\('dashStft'/);
  assert.match(dashboard, /text\('dashHeroStatus'/);
  assert.doesNotMatch(dashboard, /dashHeroStatus[\s\S]{0,180}RPM/);

  assert.match(styles, /\.now-metric-grid\s*\{[^}]*grid-template-columns\s*:\s*repeat\(5/si);
  assert.match(styles, /\.now-hero-value[^}]*font-size\s*:\s*(?:9[2-9]|[1-9][0-9]{2})px/si);
  assert.match(styles, /\.now-metric-card[^}]*min-height\s*:\s*(?:9[0-9]|[1-9][0-9]{2})px/si);
});

test('tela OBD vira witness STFT e elimina scanner legado do runtime', () => {
  for (const marker of [
    'data-obd-view="observe"', 'data-obd-view="setup"',
    'obdLiveStft', 'obdWitnessState', 'obdGasolineReference', 'obdGnvStft', 'obdResidual',
    'obdPairedRpm', 'obdPairedMap', 'obdPairedPetrol', 'obdWitnessQuality', 'obdWitnessSamples',
    'obdConnectionCenter', 'obdSensorList',
  ]) assert.match(obd, new RegExp(marker), `OBD witness missing ${marker}`);

  assert.doesNotMatch(obd, /data-obd-view="map"|data-obd-panel="map"|Mapa OBD|LTFT|CARGA|PEDAL|MAF|ÁGUA|TENSÃO ECU|alvo 0%/i);
  assert.match(obd, /Gasolina[^<`]{0,100}referência física|referência física[^<`]{0,100}Gasolina/i);
  assert.match(obd, /OBD[^<`]{0,100}não escreve[^<`]{0,40}K/i);

  assert.match(obd, /this\.api\.fullSnapshot\(\)/);
  assert.match(obd, /obd_witness/);
  assert.doesNotMatch(obd, /obdMaps\(|renderMap\(|mapLayer|longTermFuelTrim|calculatedLoad|throttle|mafGps|coolant|moduleVoltage/i);
  for (const forbidden of ['writeMap', 'writeCurve', 'startKWrite', 'startKBatchWrite', 'startKFactorWrite']) {
    assert.equal(obd.includes(forbidden), false, `${forbidden} cannot exist in OBD witness screen`);
  }
});

test('witness usa o snapshot nativo read-only já existente', () => {
  assert.match(service, /\.put\("obd_witness"/);
  assert.match(service, /fun obdWitnessStatusJson\(\): String/);
  assert.match(service, /gasolineReferencePct/);
  assert.match(service, /gnvStftPct/);
  assert.match(service, /residualPp/);
});
