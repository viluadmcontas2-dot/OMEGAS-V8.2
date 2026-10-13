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
const scale = { min: 0.8, max: 2.4, height: 350, padY: 34 };

test('y do ponteiro vira fator K: extremos do gráfico e meio', () => {
  assert.equal(drag.dragFactor(350 - 34, scale), 0.8);      // base do gráfico
  assert.equal(drag.dragFactor(34, scale), 1.8);            // topo do gráfico (2,4) é cortado no teto 1,8
  assert.equal(drag.dragFactor(350 / 2, scale), 1.6);       // meio
});

test('o fator é limitado a 0,6–1,8 (teto de gravação do dono) e arredondado a 0,001', () => {
  assert.equal(drag.dragFactor(10000, scale), 0.6);
  assert.equal(drag.dragFactor(-10000, scale), 1.8);
  assert.equal(drag.FACTOR_MAX, 1.8);
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

test('teto 1,8: ajuste fino e prévia demonstrativa recusam acima; Kotlin usa o mesmo teto', () => {
  const curve = source;
  assert.match(curve, /Math\.min\(DRAG_FACTOR_MAX, current \+ delta\)/);
  const api = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/core/native-api.js'), 'utf8');
  assert.match(api, /Informe um fator entre 0,60 e 1,80/);
  const protocol = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/java/com/omegas/prohub/ecu/KFactorProtocol.kt'), 'utf8');
  assert.match(protocol, /MAX_WRITE_FACTOR = 1\.8/);
  assert.match(protocol, /MAX_WRITE_RAW = 29_491/);
  assert.equal(Math.floor(1.8 * 16384), 29491);
  for (const file of ['calibration/KFactorManager.kt', 'calibration/KFactorManualPlanner.kt', 'autocal/AutoMatchKFactorDraft.kt', 'autocal/AutoMatchV5Engine.kt']) {
    const kotlin = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/java/com/omegas/prohub', file), 'utf8');
    assert.match(kotlin, /MAX_WRITE_(FACTOR|RAW)/, file);
  }
});
