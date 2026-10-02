'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const obd = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/screens/obd.js'), 'utf8');
const html = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/index.html'), 'utf8');
const api = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/core/native-api.js'), 'utf8');

test('OBD possui três visões compactas sem página operacional longa', () => {
  for (const view of ['observe', 'map', 'setup']) {
    assert.match(html, new RegExp(`data-obd-view="${view}"`));
    assert.match(html, new RegExp(`data-obd-panel="${view}"`));
  }
  assert.match(obd, /setView\(view\)/);
  assert.match(obd, /this\.view === 'map'/);
  assert.match(obd, /this\.view === 'setup'/);
});

test('mapa OBD usa RPM x Petrol Inj e células tocáveis', () => {
  for (const marker of ['obdLiveCell', 'obdPetrol', 'data-obd-cell-key', 'data-obd-map-layer']) {
    assert.match(html + obd, new RegExp(marker));
  }
  assert.match(html, /PETROL INJ\. ↓/);
  assert.match(obd, /maps\?\.rpmBins/);
  assert.match(obd, /maps\?\.petrolMsBins/);
  assert.match(obd, /GNV direto · alvo STFT 0%/);
  assert.doesNotMatch(obd, /loadBins|calculatedLoadPct|deltaStft/);
});

test('conexão, PIDs, bateria e flutuante ficam na visão de configuração', () => {
  for (const marker of [
    'obdConnectionCenter', 'obdSensorList', 'data-obd-connect', 'data-obd-mode',
    'batteryOptimizationStatus', 'requestBatteryOptimizationExemption',
    'overlayStatus', 'requestOverlayPermissionAndEnable', 'setTelemetryOverlayEnabled',
  ]) assert.match(html + obd + api, new RegExp(marker));
  assert.doesNotMatch(obd, /OmegasPower/);
  assert.match(obd, /data-obd-battery-request/);
  assert.match(obd, /data-obd-overlay-request/);
  assert.match(obd, /data-obd-overlay-enable/);
  assert.match(obd, /data-obd-overlay-disable/);
});

test('tela OBD permanece sem timer próprio e sem writer', () => {
  assert.doesNotMatch(obd, /setInterval/);
  for (const forbidden of ['writeMap', 'writeCurve', 'startKWrite', 'startKBatchWrite', 'startKFactorWrite']) {
    assert.equal(obd.includes(forbidden), false, `${forbidden} nao pode existir na tela OBD`);
  }
});
