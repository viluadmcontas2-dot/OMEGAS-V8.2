'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');

assert.doesNotMatch(cockpit, /<summary>Corrigir aquisição<\/summary>/,
  'reaquisição de combustível não deve ficar escondida em menu para uso na multimídia');
assert.match(cockpit, /class="autocal-reacquire-action" data-autocal-action="RESET_GAS">Readquirir GNV<\/button>/,
  'Reset Gas deve aparecer como intenção humana de readquirir GNV');
assert.match(cockpit, /class="autocal-reacquire-action" data-autocal-action="RESET_PETROL">Readquirir gasolina<\/button>/,
  'Reset Petrol deve aparecer como intenção humana de readquirir gasolina');
assert.ok(
  cockpit.indexOf('data-autocal-action="RESET_PETROL"') < cockpit.indexOf('<summary>Reset avançado</summary>'),
  'reaquisição diária deve aparecer antes do reset pesado'
);
assert.match(cockpit, /<summary>Reset avançado<\/summary>/,
  'Curva K e reset completo devem ficar em complexidade sob demanda');
assert.match(cockpit, /data-reset-scope="advanced"/,
  'ações pesadas devem formar uma unidade semântica separada');
assert.match(cockpit, /data-autocal-action="RESET_K_FACTOR">Reset Curva K<\/button>/);
assert.match(cockpit, /data-autocal-action="RESET_ALL"[^>]*>Nova aquisição completa<\/button>/);
assert.match(cockpit, /Backup da Curva K é manual/i,
  'backup deve ser opcional e manual, nunca um gate do reset');
assert.match(cockpit, /Readquirir GNV\/Gasolina fica visível acima/i,
  'a interface deve explicitar que a ação diária não está no avançado');
assert.match(cockpit, /RESET_GAS:\s*'Readquirir GNV'/);
assert.match(cockpit, /RESET_PETROL:\s*'Readquirir gasolina'/);
assert.doesNotMatch(cockpit, />Reset GNV<\/button>/);
assert.doesNotMatch(cockpit, />Reset gasolina<\/button>/);
assert.match(cockpit, /Backup não é requisito/);
assert.doesNotMatch(cockpit, /READING_BEFORE/);
assert.doesNotMatch(cockpit, /PERSISTING_BACKUP/);
assert.doesNotMatch(cockpit, /CONFIRMED_WITH_SCOPE_WARNING/);

console.log('AUTOCAL_REACQUISITION_UX=PASS');
