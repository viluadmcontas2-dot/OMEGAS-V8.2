'use strict';

// Regra do dono: a interface não usa roxo/violeta/magenta (gasolina vermelha, GNV verde, K azul). Varre todo CSS e JS da UI atrás de
// cores hex/rgba com matiz entre 238° e 335° e saturação perceptível.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const UI = path.resolve(__dirname, '../../app/src/main/assets/ui');
function files(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? files(path.join(dir, e.name)) : /\.(css|js|html)$/.test(e.name) ? [path.join(dir, e.name)] : []);
}
function hue(r, g, b) {
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  if (d < 40 || d / (max || 1) < 0.25) return null; // cinza/neutro
  let h;
  if (max === r) h = ((g - b) / d) % 6; else if (max === g) h = (b - r) / d + 2; else h = (r - g) / d + 4;
  return (h * 60 + 360) % 360;
}
const purple = h => h !== null && h >= 238 && h <= 335;

test('nenhuma cor roxa/violeta/magenta na interface', () => {
  const found = [];
  for (const file of files(UI)) {
    const text = fs.readFileSync(file, 'utf8');
    for (const m of text.matchAll(/#([0-9a-fA-F]{6})\b/g)) {
      const n = parseInt(m[1], 16);
      if (purple(hue(n >> 16, (n >> 8) & 255, n & 255))) found.push(`${path.basename(file)} #${m[1]}`);
    }
    for (const m of text.matchAll(/rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})/g)) {
      if (purple(hue(+m[1], +m[2], +m[3]))) found.push(`${path.basename(file)} ${m[0]})`);
    }
  }
  assert.deepEqual(found, []);
});
