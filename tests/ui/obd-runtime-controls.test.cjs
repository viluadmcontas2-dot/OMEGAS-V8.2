'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const obd = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/screens/obd.js'), 'utf8');
const api = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/core/native-api.js'), 'utf8');

test('OBD possui somente witness e conexão como visões runtime', () => {
  for (const view of ['observe', 'setup']) {
    assert.match(obd, new RegExp(`data-obd-view="${view}"`));
    assert.match(obd, new RegExp(`data-obd-panel="${view}"`));
  }
  assert.doesNotMatch(obd, /data-obd-view="map"|data-obd-panel="map"/);
  assert.match(obd, /setView\(view\)/);
  assert.match(obd, /this\.view === 'setup'/);
});

test('visão principal mostra STFT, resultado witness e pareamento MP48', () => {
  for (const marker of [
    'obdLiveStft', 'obdWitnessState', 'obdGasolineReference', 'obdGnvStft', 'obdResidual',
    'obdPairedRpm', 'obdPairedMap', 'obdPairedPetrol', 'obdPairedFuel',
  ]) assert.match(obd, new RegExp(marker));
  assert.match(obd, /this\.api\.fullSnapshot\(\)/);
  assert.match(obd, /obd_witness/);
});

test('conexão, STFT 0106, bateria e flutuante ficam na configuração', () => {
  for (const marker of [
    'obdConnectionCenter', 'obdSensorList', 'data-obd-connect', 'data-obd-mode',
    'batteryOptimizationStatus', 'requestBatteryOptimizationExemption',
    'overlayStatus', 'requestOverlayPermissionAndEnable', 'setTelemetryOverlayEnabled',
  ]) assert.match(obd + api, new RegExp(marker));
  assert.doesNotMatch(obd, /OmegasPower/);
  assert.match(obd, /0106/);
  assert.match(obd, /data-obd-battery-request/);
  assert.match(obd, /data-obd-overlay-request/);
  assert.match(obd, /data-obd-overlay-enable/);
  assert.match(obd, /data-obd-overlay-disable/);
});

test('tela OBD permanece sem timer próprio, scanner paralelo e writer', () => {
  assert.doesNotMatch(obd, /setInterval|obdMaps\(|renderMap\(|data-obd-cell-key/);
  for (const forbidden of ['writeMap', 'writeCurve', 'startKWrite', 'startKBatchWrite', 'startKFactorWrite']) {
    assert.equal(obd.includes(forbidden), false, `${forbidden} nao pode existir na tela OBD`);
  }
});
