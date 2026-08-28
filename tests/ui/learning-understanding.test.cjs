const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/learning.js'), 'utf8');

for (const label of [
  'DECISÃO DO NÚCLEO',
  'CONDIÇÃO AGORA',
  'ÚLTIMAS DECISÕES OBSERVADAS',
  'Memória consolidada',
  'Evidência recente',
  'Gasolina — referência',
  'GNV atual — Petrol Inj.',
  'Equivalência',
  'Histórico GNV',
  'Sugestão local',
]) {
  assert.equal(source.includes(label), true, `missing ${label}`);
}
assert.equal(source.includes('stability?.reason || learned?.readinessReason'), true);
assert.equal(source.includes('ainda não existe par equivalente válido'), true);
assert.equal(source.includes('somente consulta'), true);
assert.equal(source.includes('Abrir o editor não escreve na ECU'), true);
assert.equal(source.includes("router.navigate('map'"), true);
console.log('LEARNING_UNDERSTANDING_CONTRACT=PASS');
