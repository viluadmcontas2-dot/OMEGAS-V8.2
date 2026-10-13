'use strict';

// Arrasto de nó na Curva K (dono, 2026-10-13). A prova com mouse real está em tools/ci/curva_k_arrasto.cjs.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/screens/curve.js'), 'utf8');
const context = { console, setTimeout, clearTimeout };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(source, context, { filename: 'curve.js' });
const drag = context.OmegasUi.CurveDrag;
const scale = { min: 0.8, max: 1.6, height: 350, padY: 34 };

test('y do ponteiro vira fator K: extremos do gráfico e meio', () => {
  assert.equal(drag.dragFactor(350 - 34, scale), 0.8);      // base do gráfico
  assert.equal(drag.dragFactor(34, scale), 1.6);            // topo do gráfico
  assert.equal(drag.dragFactor(350 / 2, scale), 1.2);       // meio
});

test('o fator é limitado a 0,6–4 (mesmos limites do ajuste fino) e arredondado a 0,001', () => {
  assert.equal(drag.dragFactor(10000, scale), 0.6);
  assert.equal(drag.dragFactor(-10000, scale), 4);
  const value = drag.dragFactor(201.3, scale);
  assert.equal(value, Math.round(value * 1000) / 1000);
});

test('soltar só prepara a prévia: o arrasto nunca chama a escrita', () => {
  const finish = source.slice(source.indexOf('const finish = event'), source.indexOf("host.addEventListener('pointerup', finish)"));
  assert.match(finish, /this\.prepareActivePoint\(\)/);
  assert.doesNotMatch(finish, /writeCurve|writePrepared|api\.write/);
});

test('o gráfico não é redesenhado por baixo do arrasto e o toque não é bloqueado', () => {
  assert.match(source, /renderChart\(\) \{[\s\S]{0,260}if \(this\.drag\) return;/);
  const css = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/styles-calibration-obd.css'), 'utf8');
  assert.match(css, /#curveChart\{touch-action:none/);
});
