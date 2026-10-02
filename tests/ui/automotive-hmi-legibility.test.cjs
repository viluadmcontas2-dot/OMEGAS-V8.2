'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve('.');
const read = relative => fs.readFileSync(path.join(ROOT, relative), 'utf8');

const map = read('app/src/main/assets/ui/screens/map.js');
const css = read('app/src/main/assets/ui/styles.css');
const refine = read('app/src/main/assets/ui/styles-refine.css');
const obd = read('app/src/main/assets/ui/styles-calibration-obd.css');

assert.doesNotMatch(map, /rowHeader\.innerHTML = `<small>Petrol Inj\.<\/small><b>/, 'Mapa K não pode repetir micro-rótulo Petrol Inj. em todas as 12 linhas');
assert.doesNotMatch(map, /header\.innerHTML = `<small>RPM<\/small><b>/, 'Mapa K não pode repetir micro-rótulo RPM em todas as 12 colunas');
assert.match(map, /corner\.innerHTML = '<small>INJEÇÃO<\/small><b>ms ↓ · RPM →<\/b>'/, 'canto deve explicar os dois eixos uma única vez');

assert.match(refine, /\.map-k-grid-with-axes\s*\{[^}]*grid-template-columns:86px repeat\(12,minmax\(0,1fr\)\)/s);
assert.match(refine, /\.map-ms-header b\s*\{[^}]*font-size:16px/s);
assert.match(refine, /\.map-rpm-header b\s*\{[^}]*font-size:11px/s);
assert.match(refine, /\.map-k-cell b\s*\{[^}]*font-size:13px!important/s);
assert.match(refine, /\.physical-grid-with-axes\s*\{[^}]*grid-template-columns:72px repeat\(12,minmax\(0,1fr\)\)/s);
assert.match(refine, /\.physical-axis-ms\s*\{[^}]*font-size:11px/s);
assert.match(refine, /\.physical-axis-rpm\s*\{[^}]*font-size:10px/s);

assert.match(css, /\.primary,[^{]+\{[^}]*min-height:44px/s, 'controles operacionais compartilhados devem ter alvo mínimo de 44px');
assert.match(css, /\.nudge-row button\s*\{[^}]*min-height:44px/s);
assert.match(css, /\.field-label>span\s*\{[^}]*font-size:11px/s);
assert.match(css, /\.surface-toolbar\s*\{[^}]*font-size:11px/s);
assert.match(css, /\.side-nav button span\s*\{[^}]*font-size:14px/s);
assert.match(css, /\.source-status\s*\{[^}]*min-height:40px[^}]*font-size:11px/s);

assert.match(obd, /\.curve-learning-axis\s*\{[^}]*font-size:11px/s);
assert.match(obd, /\.curve-point-label\s*\{[^}]*font-size:11px/s);
assert.match(obd, /\.curve-nudge-row button\s*\{[^}]*min-height:44px/s);
assert.match(obd, /\.obd-map-grid\s*\{[^}]*grid-template-columns:64px repeat\(12,minmax\(28px,1fr\)\)/s);
assert.match(obd, /\.obd-map-axis,\.obd-map-corner\s*\{[^}]*font-size:10px/s);
assert.match(obd, /\.obd-map-cell b\s*\{[^}]*font-size:11px/s);
assert.match(obd, /\.obd-map-tabs button\s*\{[^}]*min-height:44px[^}]*font-size:11px/s);
assert.match(obd, /\.obd-mode-buttons button\s*\{[^}]*min-height:\s*44px[^}]*font-size:\s*11px/s,
  'seletor da fonte OBD deve ter alvo automotivo de 44px e texto legível');
assert.match(refine, /\.tolerance-profiles button[\s\S]*min-height:\s*44px[\s\S]*font-size:\s*11px/s,
  'perfis de tolerância devem ser tocáveis sem precisão fina');
assert.match(refine, /\.tolerance-controls (?:select|input)[\s\S]*min-height:\s*44px/s,
  'inputs de tolerância devem ter alvo mínimo de 44px');

assert.match(refine, /\.recorder-actions button[\s\S]*min-height:\s*44px[\s\S]*font-size:\s*11px/s,
  'ações do gravador devem ser tocáveis na multimídia');
assert.match(refine, /\.diagnostic-settings > button[\s\S]*min-height:\s*44px[\s\S]*font-size:\s*11px/s,
  'ação principal das configurações de diagnóstico deve ter alvo automotivo');
assert.match(refine, /\.recorded-session-list button[\s\S]*min-height:\s*44px[\s\S]*font-size:\s*11px/s,
  'ações de sessões gravadas não podem exigir toque de precisão');
assert.match(refine, /\.diagnostic-settings-grid select,[\s\S]*min-height:\s*44px/s,
  'campos diagnósticos devem manter piso automotivo de 44px');

console.log('AUTOMOTIVE_HMI_LEGIBILITY=PASS');
