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

assert.match(css, /\.primary,[^{]+\{[^}]*min-height:44px/s, 'controles operacionais compartilhados devem ter alvo mínimo de 44px');
assert.match(css, /\.nudge-row button\s*\{[^}]*min-height:44px/s);
assert.match(css, /\.field-label>span\s*\{[^}]*font-size:11px/s);
assert.match(css, /\.surface-toolbar\s*\{[^}]*font-size:11px/s);
// Navegação inferior aprovada pelo dono (2026-10-11): rótulo das abas em 15px, alvo de 64px.
assert.match(css, /\.side-nav button span\s*\{[^}]*font-size:15px/s);
assert.match(css, /\.side-nav button\s*\{[^}]*height:64px/s);
assert.match(css, /\.source-status\s*\{[^}]*min-height:40px[^}]*font-size:11px/s);

assert.match(obd, /\.curve-point-label\s*\{[^}]*font-size:11px/s);
assert.match(obd, /\.curve-nudge-row button\s*\{[^}]*min-height:44px/s);

// Sessões e Ferramentas (2026-10-18): alvos de 56 px e texto de corpo de 18 px, definidos em styles-ux-leigo.css.
const ux = require('node:fs').readFileSync(require('node:path').join(__dirname, '../../app/src/main/assets/ui/styles-ux-leigo.css'), 'utf8');
assert.match(ux, /\.sess-main\s*\{[^}]*min-height:\s*72px/s, 'gravar/parar tocável na multimídia');
assert.match(ux, /\.sess-size \[data-session-settings\]\s*\{[^}]*min-height:\s*60px/s, 'ação principal das configurações com alvo automotivo');
assert.match(ux, /\.recorded-session-item button\s*\{[^}]*min-height:\s*56px/s, 'ações de sessões gravadas não podem exigir toque de precisão');
assert.match(ux, /\.diagnostic-settings-grid select,[^{]*\{[^}]*min-height:\s*56px/s, 'campos diagnósticos mantêm piso automotivo');

console.log('AUTOMOTIVE_HMI_LEGIBILITY=PASS');
