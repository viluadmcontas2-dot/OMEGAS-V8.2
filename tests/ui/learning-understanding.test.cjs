const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/learning.js'), 'utf8');

for (const label of [
  'DECISÃO DO NÚCLEO',
  'CONDIÇÃO AGORA',
  'ÚLTIMAS DECISÕES OBSERVADAS',
  'Gasolina — referência agregada',
  'Qualidade da referência',
  'GNV atual — Petrol Inj.',
  'Qualidade do GNV',
  'Desvio medido',
  'Suporte da referência',
  'Histórico GNV',
  'Correção Blue — separada da medição',
  'BlueCausalEngine',
]) {
  assert.equal(source.includes(label), true, `missing ${label}`);
}
for (const forbidden of [
  'Memória consolidada',
  'Predição contínua RPM × MAP',
  'Sugestão local',
  'mapResidualPredictions',
  'assistedCalibration',
  'learningStability',
]) {
  assert.equal(source.includes(forbidden), false, `retired decision semantics remain: ${forbidden}`);
}
assert.equal(source.includes('ainda não existe par equivalente válido'), true);
assert.equal(source.includes('somente consulta'), true);
assert.equal(source.includes('Abrir o editor não escreve na ECU'), true);
assert.equal(source.includes("router.navigate('map'"), true);
assert.equal(source.includes("suggestion: suggestion"), false);
console.log('LEARNING_UNDERSTANDING_CONTRACT=PASS');
