'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');

assert.doesNotMatch(cockpit, /<summary>Corrigir aquisição<\/summary>/,
  'reaquisição de combustível não deve ficar escondida em menu para uso na multimídia');
assert.match(cockpit, /class="autocal-reacquire-action" data-autocal-action="RESET_GAS">Ler o GNV de novo<\/button>/,
  'Reset Gas deve aparecer como intenção humana de readquirir GNV');
assert.match(cockpit, /class="autocal-reacquire-action" data-autocal-action="RESET_PETROL">Ler a gasolina de novo<\/button>/,
  'Reset Petrol deve aparecer como intenção humana de readquirir gasolina');
assert.ok(
  cockpit.indexOf('data-autocal-action="RESET_PETROL"') < cockpit.indexOf('<summary>Ações avançadas</summary>'),
  'reaquisição diária deve aparecer antes do reset pesado'
);
assert.match(cockpit, /<summary>Ações avançadas<\/summary>/,
  'Curva K fica em complexidade sob demanda');
assert.match(cockpit, /data-reset-scope="advanced"/,
  'ações pesadas devem formar uma unidade semântica separada');
assert.doesNotMatch(cockpit, /data-autocal-action="MANUAL_AUTOMATCH"/);
assert.doesNotMatch(cockpit, /data-autocal-action="FINISH_AUTOCAL"/,
  'Finish AutoCal original nasce desabilitado e não deve virar etapa diária');
assert.doesNotMatch(cockpit, /data-autocal-action="FINISH_AUTOMATCH"/,
  'Finish AutoMatch pertence ao PanelDbg oculto do ProgBase e não deve aparecer na superfície diária');
assert.match(cockpit, /O AutoMatch é automático e decidido pela ECU\./,
  'a UX deve explicar que o AutoMatch normal é ECU-owned, separado do AutoMatch manual');
assert.match(cockpit, /data-autocal-action="RESET_K_FACTOR">Resetar Curva K para 1,000<\/button>/,
  'Reset K deve usar a semântica provada do ProgBase/MUL_ACT');
assert.doesNotMatch(cockpit, /data-autocal-action="RESET_ALL"/);
assert.match(cockpit, /Salvar uma foto antes é opcional/i,
  'backup deve ser opcional e manual, nunca um gate do reset');
assert.ok(
  cockpit.indexOf('data-autocal-action="RESET_GAS"') < cockpit.indexOf('<summary>Ações avançadas</summary>') &&
  cockpit.indexOf('data-autocal-action="RESET_PETROL"') < cockpit.indexOf('<summary>Ações avançadas</summary>'),
  'a interface deve manter a readquisição diária fora do menu avançado'
);
assert.match(cockpit, /RESET_GAS:\s*'Ler o GNV de novo'/);
assert.match(cockpit, /RESET_PETROL:\s*'Ler a gasolina de novo'/);
assert.doesNotMatch(cockpit, />Reset GNV<\/button>/);
assert.doesNotMatch(cockpit, />Reset gasolina<\/button>/);
assert.match(cockpit, /Salvar uma foto antes é opcional/);
assert.doesNotMatch(cockpit, /PERSISTING_BACKUP/,
  'reaquisição não pode depender de persistência de backup; READING_BEFORE pode existir como estado de leitura para ações que alteram K');
assert.doesNotMatch(cockpit, /CONFIRMED_WITH_SCOPE_WARNING/);

console.log('AUTOCAL_REACQUISITION_UX=PASS');