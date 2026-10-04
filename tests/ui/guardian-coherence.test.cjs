'use strict';
// Guardião: nada de rótulo de ensaio na tela do carro e uma só leitura para cada percentual do Refino.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const MAIN = path.resolve(__dirname, '../../app/src/main');
function files(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => {
    const full = path.join(dir, e.name);
    return e.isDirectory() ? files(full) : [full];
  });
}

test('nenhum texto de ensaio (SINTÉTICO) nos assets ou no Kotlin que vão no APK', () => {
  const bad = files(MAIN).filter(f => /\.(js|html|css|kt)$/.test(f) && /SINT[ÉE]TICO/i.test(fs.readFileSync(f, 'utf8')));
  assert.deepEqual(bad, []);
});

test('Refino: dois percentuais com nomes inconfundíveis', () => {
  const refino = fs.readFileSync(path.join(MAIN, 'assets/ui/screens/refino.js'), 'utf8');
  assert.match(refino, /<small>Diferença GNV × gasolina<\/small>/);
  assert.match(refino, /da condução já equivale à gasolina/);
  assert.doesNotMatch(refino, /GNV ≈ gasolina em/);
});

test('aviso de apagão nunca é cortado por reticências', () => {
  const css = fs.readFileSync(path.join(MAIN, 'assets/ui/styles-lote-f.css'), 'utf8');
  const rules = css.split('}').filter(r => /\.refino-stalls/.test(r));
  assert.ok(rules.length > 0);
  for (const rule of rules) assert.doesNotMatch(rule, /line-clamp/, rule.trim().slice(0, 80));
});
