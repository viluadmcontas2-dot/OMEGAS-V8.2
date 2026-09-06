'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(
  path.join(__dirname, '../../app/src/main/assets/ui/screens/obd.js'),
  'utf8',
);

test('witness compara GNV contra referência física gasolina, não contra zero', () => {
  for (const marker of [
    'obdGasolineReference', 'obdGnvStft', 'obdResidual', 'obdWitnessState',
    'gasolineReferencePct', 'gnvStftPct', 'residualPp',
  ]) assert.match(source, new RegExp(marker));
  assert.match(source, /Gasolina é a referência física compatível/);
  assert.doesNotMatch(source, /alvo STFT 0%|alvo 0%|zero como referência/i);
});

test('pareamento usa somente condição MP48 exibida no witness', () => {
  for (const marker of ['obdPairedRpm', 'obdPairedMap', 'obdPairedPetrol', 'obdPairedFuel', 'skew_ms']) {
    assert.match(source, new RegExp(marker));
  }
  assert.match(source, /RPM MP48/);
  assert.match(source, /MAP MP48/);
  assert.match(source, /PETROL INJ\. MP48/);
  assert.doesNotMatch(source, /loadBins|calculatedLoadPct|deltaStft/);
});

test('mapa OBD paralelo foi aposentado e nenhuma escrita ECU é alcançável', () => {
  assert.doesNotMatch(source, /renderMap\(|obdMaps\(|data-obd-cell-key|data-obd-map-layer|Mapa OBD/i);
  assert.doesNotMatch(source, /startKWrite|startKBatchWrite|startKFactorWrite|writeMap\(|writeCurve\(/);
});
