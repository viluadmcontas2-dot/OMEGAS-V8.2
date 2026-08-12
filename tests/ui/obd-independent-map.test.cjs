'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(
  path.join(__dirname, '../../app/src/main/assets/ui/screens/obd.js'),
  'utf8',
);

function buildScreen() {
  const nodes = {
    obdIndependentMap: { innerHTML: '' },
    obdMapSummary: { textContent: '' },
    obdCellDetail: { innerHTML: '' },
  };
  const document = {
    getElementById: id => nodes[id] || null,
    querySelectorAll: () => [],
  };
  const context = { console, Date, Math, JSON, Intl, document };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(source, context, { filename: 'obd.js' });
  const screen = Object.create(context.OmegasUi.ObdScreen.prototype);
  screen.mapLayer = 'comparison';
  screen.selectedCellKey = null;
  screen.mapSignature = '';
  return { screen, nodes };
}

function maps() {
  const rpmBins = [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500];
  const petrolMsBins = [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18];
  return {
    rpmBins,
    petrolMsBins,
    updatedAt: 123,
    gasoline: {
      '5:6': { stft: { mean: 2.0, physicalSamples: 4 }, ltft: { mean: 0.5 }, coolant: { mean: 88 }, speed: { mean: 45 }, qualified: 4 },
    },
    gnv: {
      '5:6': { stft: { mean: 8.0, physicalSamples: 5 }, ltft: { mean: 0.8 }, coolant: { mean: 89 }, speed: { mean: 52 }, qualified: 5 },
    },
    validation: {
      '5:6': {
        gasoline: 2.0,
        gnv: 8.0,
        gasolineSamples: 4,
        gnvSamples: 5,
        comparisonReady: true,
        status: 'AUMENTAR_GNV',
      },
    },
  };
}

test('camada principal OBD usa STFT GNV na mesma célula RPM x Petrol Inj', () => {
  const { screen, nodes } = buildScreen();
  screen.renderMap(maps(), { status: { rpm: 3500, petrolMs: 8.0 } });

  assert.match(nodes.obdIndependentMap.innerHTML, /\+8,0%/);
  assert.match(nodes.obdIndependentMap.innerHTML, /RPM 3500 · Petrol Inj\. 8,0 ms/);
  assert.match(nodes.obdMapSummary.textContent, /GNV direto · alvo STFT 0%/);
  assert.match(nodes.obdMapSummary.textContent, /1 células/);
  assert.match(nodes.obdCellDetail.innerHTML, /AUMENTAR_GNV/);
});

test('camada gasolina mostra STFT próprio sem calcular correção', () => {
  const { screen, nodes } = buildScreen();
  screen.mapLayer = 'gasoline';
  screen.renderMap(maps(), { status: { rpm: 3500, petrolMs: 8.0 } });

  assert.match(nodes.obdIndependentMap.innerHTML, /\+2,0%/);
  assert.doesNotMatch(nodes.obdIndependentMap.innerHTML, /Gravar|Aplicar|startK/i);
});

test('arquivo OBD não usa carga como eixo nem expõe escrita ECU', () => {
  assert.doesNotMatch(source, /loadBins|calculatedLoadPct|deltaStft/);
  assert.match(source, /petrolMsBins/);
  assert.match(source, /RPM × Petrol Inj\./);
  assert.doesNotMatch(source, /startKWrite|startKBatchWrite|startKFactorWrite|writeMap\(|writeCurve\(/);
});
