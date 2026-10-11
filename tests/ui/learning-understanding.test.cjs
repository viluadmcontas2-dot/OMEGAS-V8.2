const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/learning.js'), 'utf8');

for (const label of [
  'DECISÃO DO NÚCLEO',
  'CONDIÇÃO AGORA',
  'ÚLTIMAS DECISÕES OBSERVADAS',
  'COLETA',
  'Gasolina esperada',
  'No GNV agora',
  'Diferença agora',
  'Diferença estável',
  'Tendência recente',
  'Novo valor K sugerido',
]) {
  assert.equal(source.includes(label), true, `missing ${label}`);
}
// Sem lista técnica na célula (dono, 2026-10-11: só usar e validar).
for (const removed of ['Diagnóstico técnico', 'Histórico GNV', 'LIMITES CONFIGURADOS']) assert.equal(source.includes(removed), false, `não deve voltar: ${removed}`);
assert.equal(source.includes('sem par equivalente válido'), true);
assert.equal(source.includes('Nada aqui escreve na ECU'), true);
assert.equal(source.includes('Abrir o editor não escreve na ECU'), true);
assert.equal(source.includes("router.navigate('map'"), true);
console.log('LEARNING_UNDERSTANDING_CONTRACT=PASS');