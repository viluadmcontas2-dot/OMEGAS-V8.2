'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');

assert.doesNotMatch(cockpit, /<summary>Corrigir aquisição<\/summary>/,
  'reaquisição de combustível não deve ficar escondida em menu para uso na multimídia');
// (dono, 2026-10-11: texto para leigo): o botão diz que reaprende e que apaga o que já foi aprendido.
assert.match(cockpit, /class="autocal-reacquire-action" data-autocal-action="RESET_GAS">Reaprender GNV <span class="autocal-action-note">\(apaga o atual\)<\/span><\/button>/,
  'Reset Gas deve aparecer como intenção humana de reaprender GNV');
assert.match(cockpit, /class="autocal-reacquire-action" data-autocal-action="RESET_PETROL">Reaprender gasolina <span class="autocal-action-note">\(apaga o atual\)<\/span><\/button>/,
  'Reset Petrol deve aparecer como intenção humana de reaprender gasolina');
assert.ok(
  cockpit.indexOf('data-autocal-action="RESET_PETROL"') < cockpit.indexOf('<summary>Ações avançadas</summary>'),
  'reaquisição diária deve aparecer antes do reset pesado'
);
assert.match(cockpit, /<summary>Ações avançadas<\/summary>/,
  'AutoMatch manual, Curva K e reset completo devem ficar em complexidade sob demanda');
assert.match(cockpit, /data-reset-scope="advanced"/,
  'ações pesadas devem formar uma unidade semântica separada');
assert.doesNotMatch(cockpit, /MANUAL_AUTOMATCH/); // dono, 2026-10-13: Manual automatch não é exposto
assert.doesNotMatch(cockpit, /data-autocal-action="FINISH_AUTOCAL"/,
  'Finish AutoCal original nasce desabilitado e não deve virar etapa diária');
assert.doesNotMatch(cockpit, /data-autocal-action="FINISH_AUTOMATCH"/,
  'Finish AutoMatch pertence ao PanelDbg oculto do ProgBase e não deve aparecer na superfície diária');
// (dono, 2026-10-11: texto para leigo)
assert.match(cockpit, /A ECU faz os ajustes automáticos sozinha\./,
  'a UX deve explicar que o AutoMatch normal é ECU-owned, separado do AutoMatch manual');
assert.match(cockpit, /data-autocal-action="RESET_K_FACTOR">Curva K toda em 1,0<\/button>/,
  'Reset K deve usar a semântica provada do ProgBase/MUL_ACT');
assert.match(cockpit, /data-autocal-action="RESET_ALL"[^>]*>Recomeçar tudo<\/button>/);
assert.doesNotMatch(cockpit, /AUTO_CAL_ENABLE=0/, 'o texto do menu não mostra código cru');
assert.match(cockpit, /prepare\(action\) \{[^}]*this\.api\.prepare\(action\)[\s\S]{0,420}this\.confirmPrepared\(\);/,
  'backup nunca é gate do reset: um toque prepara e executa (dono, 2026-10-11)');
assert.doesNotMatch(cockpit.slice(cockpit.indexOf('    prepare(action) {'), cockpit.indexOf('    confirmPrepared() {')), /backup/i,
  'o caminho do toque não pede nem espera backup');
assert.ok(
  cockpit.indexOf('data-autocal-action="RESET_GAS"') < cockpit.indexOf('<summary>Ações avançadas</summary>') &&
  cockpit.indexOf('data-autocal-action="RESET_PETROL"') < cockpit.indexOf('<summary>Ações avançadas</summary>'),
  'a interface deve manter a readquisição diária fora do menu avançado'
);
assert.match(cockpit, /RESET_GAS:\s*'Reaprender GNV \(apaga o atual\)'/); // (dono, 2026-10-11: texto para leigo)
assert.match(cockpit, /RESET_PETROL:\s*'Reaprender gasolina \(apaga o atual\)'/);
assert.doesNotMatch(cockpit, />Reset GNV<\/button>/);
assert.doesNotMatch(cockpit, />Reset gasolina<\/button>/);
assert.doesNotMatch(cockpit, /renderReview/, 'reaquisição em um toque, sem tela de revisão (dono, 2026-10-11)');
assert.doesNotMatch(cockpit, /PERSISTING_BACKUP/,
  'reaquisição não pode depender de persistência de backup; READING_BEFORE pode existir como estado de leitura para ações que alteram K');
assert.doesNotMatch(cockpit, /CONFIRMED_WITH_SCOPE_WARNING/);

console.log('AUTOCAL_REACQUISITION_UX=PASS');