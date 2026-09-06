'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.join(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const read = rel => fs.readFileSync(path.join(ROOT, rel), 'utf8');
const index = read('app/src/main/assets/ui/index.html');
const multimedia = read('app/src/main/assets/ui/styles-witness-multimedia.css');
const dashboard = read('app/src/main/assets/ui/screens/dashboard.js');
const obd = read('app/src/main/assets/ui/screens/obd.js');

function declaredPx(css, selector, property = 'font-size') {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = css.match(new RegExp(`${escaped}\\s*\\{[^}]*${property}\\s*:\\s*(\\d+(?:\\.\\d+)?)px`, 's'));
  return match ? Number(match[1]) : null;
}

function expectAtLeast(css, selector, minimum, label) {
  const value = declaredPx(css, selector);
  assert.notEqual(value, null, `${label}: faltou regra explícita para ${selector}`);
  assert.ok(value >= minimum, `${label}: ${value}px < ${minimum}px`);
}

test('stylesheet multimídia é parte estática do APK, não depende de abrir dashboard primeiro', () => {
  assert.match(index, /href="styles-witness-multimedia\.css"/);
});

test('nenhum CSS embarcado declara texto abaixo de 10 px', () => {
  const cssFiles = fs.readdirSync(UI).filter(name => name.endsWith('.css'));
  const tiny = [];
  for (const name of cssFiles) {
    const css = fs.readFileSync(path.join(UI, name), 'utf8');
    const regex = /font-size\s*:\s*(\d+(?:\.\d+)?)px/g;
    for (const match of css.matchAll(regex)) {
      const value = Number(match[1]);
      if (value < 10) tiny.push(`${name}:${value}px`);
    }
  }
  assert.deepEqual(tiny, [], `tipografia minúscula ainda embarcada: ${tiny.join(', ')}`);
});

test('Mapa K possui tipografia própria para leitura à distância em 1280x720', () => {
  expectAtLeast(multimedia, '.map-screen .map-axis-corner small', 11, 'nome do eixo Mapa K');
  expectAtLeast(multimedia, '.map-screen .map-axis-header small', 11, 'nome dos pins Mapa K');
  expectAtLeast(multimedia, '.map-screen .map-rpm-header b', 13, 'valores RPM do Mapa K');
  expectAtLeast(multimedia, '.map-screen .map-ms-header b', 13, 'valores ms do Mapa K');
  expectAtLeast(multimedia, '.map-screen .map-k-cell b', 15, 'valor K da célula');
  expectAtLeast(multimedia, '.map-screen .map-k-cell span', 10, 'delta/apoio da célula');
});

test('HTML embarcado não conserva dashboard/OBD scanner antigos por baixo do runtime atual', () => {
  for (const forbidden of [
    'id="dashLtft"',
    'id="dashPetrolContext"',
    'id="obdIndependentMap"',
    'data-obd-view="map"',
    'data-obd-panel="map"',
    'id="obdSensorList"',
  ]) {
    assert.equal(index.includes(forbidden), false, `markup legado ainda embarcado: ${forbidden}`);
  }
  assert.equal((dashboard.match(/id="dashRpm"/g) || []).length, 1, 'runtime deve ter um único RPM no dashboard');
  assert.doesNotMatch(dashboard, /dashLtft/);
  assert.doesNotMatch(obd, /obdIndependentMap|data-obd-cell-key|GNV direto · alvo STFT 0%/);
});
