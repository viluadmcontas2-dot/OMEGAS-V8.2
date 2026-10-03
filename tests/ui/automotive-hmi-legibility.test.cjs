'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve('.');
const read = relative => fs.readFileSync(path.join(ROOT, relative), 'utf8');

const map = read('app/src/main/assets/ui/screens/map.js');
const css = read('app/src/main/assets/ui/styles.css');
const refine = read('app/src/main/assets/ui/styles-refine.css');

assert.doesNotMatch(map, /rowHeader\.innerHTML = `<small>Petrol Inj\.<\/small><b>/, 'Mapa K não pode repetir micro-rótulo Petrol Inj. em todas as 12 linhas');
assert.doesNotMatch(map, /header\.innerHTML = `<small>RPM<\/small><b>/, 'Mapa K não pode repetir micro-rótulo RPM em todas as 12 colunas');
assert.match(map, /corner\.innerHTML = '<small>ms ↓<\/small><b>RPM →<\/b>'/, 'canto deve explicar os dois eixos uma única vez');

assert.match(refine, /\.map-k-grid-with-axes\s*\{[^}]*grid-template-columns:86px repeat\(12,minmax\(0,1fr\)\)/s);
assert.match(refine, /\.map-ms-header b\s*\{[^}]*font-size:16px/s);
assert.match(refine, /\.map-rpm-header b\s*\{[^}]*font-size:11px/s);
assert.match(refine, /\.map-k-cell b\s*\{[^}]*font-size:13px!important/s);

// F6: o piso de toque/texto (76 px / 24 px) vive em tokens.css + styles-floors.css; a única exceção é a grade do Mapa K (>= 44 px).
const floors = read('app/src/main/assets/ui/styles-floors.css');
assert.match(floors, /\.map-k-cell,\s*html body \.map-axis-header\s*\{[^}]*min-height:\s*var\(--touch-grid\)/s);
assert.match(floors, /\.side-nav button i,\s*\.side-nav button span\s*\{[^}]*font-size:\s*var\(--text-critical\)/s);

// Lote F: células >= 44 px com número de 20 px e texto de apoio >= 16 px; a grade usa a altura toda (13 linhas de 44 px cabem).
const lote = read('app/src/main/assets/ui/styles-lote-f.css');
assert.match(lote, /\.map-screen \.map-k-cell b\s*\{[^}]*font-size:\s*20px/s);
assert.match(lote, /\.map-screen \.map-k-cell span\s*\{[^}]*font-size:\s*16px/s);
assert.match(lote, /grid-template-rows:\s*44px repeat\(12, minmax\(44px, 1fr\)\)/);
console.log('AUTOMOTIVE_HMI_LEGIBILITY=PASS');
