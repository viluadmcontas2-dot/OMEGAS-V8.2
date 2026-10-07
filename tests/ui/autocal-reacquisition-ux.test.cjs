'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');

assert.doesNotMatch(cockpit, /<summary>Corrigir aquisição<\/summary>/,
  'reaquisição de combustível não deve ficar escondida em menu para uso na multimídia');
// Revisto (P2 clareza): "Reler GNV/gasolina" virou "Recomeçar aprendizado…" com uma linha que explica o efeito.
assert.match(cockpit, /class="autocal-reacquire-action" data-autocal-action="RESET_GAS">Recomeçar aprendizado do GNV<\/button>/,
  'Reset Gas deve aparecer como intenção humana de reaprender o GNV');
assert.match(cockpit, /class="autocal-reacquire-action" data-autocal-action="RESET_PETROL">Recomeçar aprendizado da gasolina<\/button>/,
  'Reset Petrol deve aparecer como intenção humana de reaprender a gasolina');
assert.match(cockpit, /A ECU esquece o que aprendeu e aprende de novo enquanto você dirige\./);
assert.ok(
  cockpit.indexOf('data-autocal-action="RESET_PETROL"') < cockpit.indexOf('<summary>Mais opções</summary>'),
  'reaquisição diária deve aparecer antes do reset pesado'
);
assert.match(cockpit, /<summary>Mais opções<\/summary>/,
  'Curva K fica em complexidade sob demanda');
assert.match(cockpit, /data-reset-scope="advanced"/,
  'ações pesadas devem formar uma unidade semântica separada');
assert.doesNotMatch(cockpit, /data-autocal-action="MANUAL_AUTOMATCH"/);
assert.doesNotMatch(cockpit, /data-autocal-action="FINISH_AUTOCAL"/,
  'Finish AutoCal original nasce desabilitado e não deve virar etapa diária');
assert.doesNotMatch(cockpit, /data-autocal-action="FINISH_AUTOMATCH"/,
  'Finish AutoMatch pertence ao PanelDbg oculto do ProgBase e não deve aparecer na superfície diária');
assert.match(cockpit, /Os ajustes automáticos são decididos pela ECU\./,
  'a UX deve explicar que o AutoMatch normal é decidido pela ECU, sem jargão');
assert.doesNotMatch(cockpit, /data-autocal-action="RESET_K_FACTOR"/,
  'zerar a Curva K fica só na aba Curva K');
assert.doesNotMatch(cockpit, /data-autocal-action="RESET_ALL"/);
// Revisto (P3): o cartão de revisão (onde estava "Salvar uma foto antes é opcional") era código morto; um toque, sem
// cartão (decisão do dono). O backup segue opcional no Kotlin (automaticBackup=false, contrato Python).
assert.doesNotMatch(cockpit, /REVISÃO ANTES DA ECU/, 'sem cartão de revisão antes da ECU');
assert.ok(
  cockpit.indexOf('data-autocal-action="RESET_GAS"') < cockpit.indexOf('<summary>Mais opções</summary>') &&
  cockpit.indexOf('data-autocal-action="RESET_PETROL"') < cockpit.indexOf('<summary>Mais opções</summary>'),
  'a interface deve manter a readquisição diária fora do menu avançado'
);
// Revisto (P3): o mapa de rótulos (actionLabel) só servia ao cartão de revisão morto; os rótulos vivem nos botões.
assert.doesNotMatch(cockpit, /function actionLabel/);
assert.doesNotMatch(cockpit, />Reset GNV<\/button>/);
assert.doesNotMatch(cockpit, />Reset gasolina<\/button>/);

assert.doesNotMatch(cockpit, /PERSISTING_BACKUP/,
  'reaquisição não pode depender de persistência de backup; READING_BEFORE pode existir como estado de leitura para ações que alteram K');
assert.doesNotMatch(cockpit, /CONFIRMED_WITH_SCOPE_WARNING/);

console.log('AUTOCAL_REACQUISITION_UX=PASS');