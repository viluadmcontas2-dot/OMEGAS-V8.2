'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');

assert.match(cockpit, /<summary>Corrigir aquisição<\/summary>/,
  'o menu deve expressar a intenção humana, não o subsistema de reset');
assert.match(cockpit, /data-reset-scope="fuel"/,
  'reaquisição de combustível deve formar uma unidade semântica própria');
assert.match(cockpit, /data-autocal-action="RESET_GAS">Readquirir GNV<\/button>/,
  'Reset Gas deve aparecer como intenção humana de readquirir GNV');
assert.match(cockpit, /data-autocal-action="RESET_PETROL">Readquirir gasolina<\/button>/,
  'Reset Petrol deve aparecer como intenção humana de readquirir gasolina');
assert.match(cockpit, /class="autocal-reset-advanced"/,
  'Curva K e reset completo devem ficar em complexidade sob demanda');
assert.match(cockpit, /data-autocal-action="RESET_K_FACTOR">Reset Curva K<\/button>/);
assert.match(cockpit, /data-autocal-action="RESET_ALL"[^>]*>Nova aquisição completa<\/button>/);
assert.match(cockpit, /Curva K usa outro caminho/i,
  'a interface deve explicar a separação sem expor byte/protocolo no primeiro nível');
assert.match(cockpit, /avisa se observar mudança fora do esperado/i,
  'o produto deve prometer verificação pós-ação, não seletividade não provada');
assert.match(cockpit, /RESET_GAS:\s*'Readquirir GNV'/);
assert.match(cockpit, /RESET_PETROL:\s*'Readquirir gasolina'/);
assert.doesNotMatch(cockpit, />Reset GNV<\/button>/);
assert.doesNotMatch(cockpit, />Reset gasolina<\/button>/);

console.log('AUTOCAL_REACQUISITION_UX=PASS');
