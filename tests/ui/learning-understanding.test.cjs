const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/learning.js'), 'utf8');

for (const label of [
  'COLETA AUTOMÁTICA',
  'Gasolina esperada',
  'GNV observado',
  'Diferença aprendida',
  'Situação',
  'Detalhes técnicos',
  'Memória consolidada',
  'Evidência recente',
  'Massa local',
  'Predição RPM × MAP',
  'Histórico GNV',
]) {
  assert.equal(source.includes(label), true, `missing ${label}`);
}

assert.equal(source.includes('data-learning-inspector="tolerances"'), false);
assert.equal(source.includes('LIMITES CONFIGURADOS'), false);
assert.equal(source.includes('ainda não existe par equivalente válido'), true);
assert.equal(source.includes('somente consulta'), true);
assert.equal(source.includes('Abrir o editor não escreve na ECU'), true);
assert.equal(source.includes("router.navigate('map'"), true);
assert.equal(source.includes('RPM × MAP define a condição física'), true);
assert.equal(source.includes('O app observa continuamente'), true);
console.log('LEARNING_UNDERSTANDING_CONTRACT=PASS');
