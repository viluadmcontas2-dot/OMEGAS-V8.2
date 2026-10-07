'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');

assert.doesNotMatch(cockpit, /<summary>Corrigir aquisição<\/summary>/,
  'reaquisição de combustível não deve ficar escondida em menu para uso na multimídia');
// Revisto (W2): os botões são "Reler GNV" e "Reler gasolina" (palavras do dono), um toque, sem confirmação, lado a lado
// com Pausar/Retomar numa única barra; sem "Mais opções", sem parágrafos fixos.
assert.match(cockpit, /class="autocal-reacquire-action" data-autocal-action="RESET_GAS">Reler GNV<\/button>/);
assert.match(cockpit, /class="autocal-reacquire-action" data-autocal-action="RESET_PETROL">Reler gasolina<\/button>/);
assert.ok(cockpit.indexOf('data-autocal-toggle') < cockpit.indexOf('data-autocal-action="RESET_GAS"'));
assert.doesNotMatch(cockpit, /Mais opções|Fechar opções|autocal-reset-confirm|Histórico e detalhes/);
assert.doesNotMatch(cockpit, /data-autocal-action="MANUAL_AUTOMATCH"/);
assert.doesNotMatch(cockpit, /data-autocal-action="FINISH_AUTOCAL"/,
  'Finish AutoCal original nasce desabilitado e não deve virar etapa diária');
assert.doesNotMatch(cockpit, /data-autocal-action="FINISH_AUTOMATCH"/,
  'Finish AutoMatch pertence ao PanelDbg oculto do ProgBase e não deve aparecer na superfície diária');
assert.doesNotMatch(cockpit, /data-autocal-action="RESET_K_FACTOR"/,
  'zerar a Curva K fica só na aba Curva K');
assert.doesNotMatch(cockpit, /data-autocal-action="RESET_ALL"/);
// Revisto (P3): o cartão de revisão (onde estava "Salvar uma foto antes é opcional") era código morto; um toque, sem
// cartão (decisão do dono). O backup segue opcional no Kotlin (automaticBackup=false, contrato Python).
assert.doesNotMatch(cockpit, /REVISÃO ANTES DA ECU/, 'sem cartão de revisão antes da ECU');
// Revisto (P3): o mapa de rótulos (actionLabel) só servia ao cartão de revisão morto; os rótulos vivem nos botões.
assert.doesNotMatch(cockpit, /function actionLabel/);
assert.doesNotMatch(cockpit, />Reset GNV<\/button>/);
assert.doesNotMatch(cockpit, />Reset gasolina<\/button>/);

assert.doesNotMatch(cockpit, /PERSISTING_BACKUP/,
  'reaquisição não pode depender de persistência de backup; READING_BEFORE pode existir como estado de leitura para ações que alteram K');
assert.doesNotMatch(cockpit, /CONFIRMED_WITH_SCOPE_WARNING/);

console.log('AUTOCAL_REACQUISITION_UX=PASS');