const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/index.html'), 'utf8');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');

for (const label of [
  'Auto-Cal da ECU',
  'O que fazer agora',
  'Atualizar leitura',
  'Coleta da ECU',
  'Progresso do AutoMatch',
  'Reiniciar aprendizado (avançado)',
  'Diagnóstico técnico',
  'precisão da correlação',
]) {
  assert.equal(cockpit.includes(label), true, `missing accessible Auto-Cal label: ${label}`);
}
assert.equal(html.includes('data-curve-view="autocal"'), true, 'Auto-Cal entry must exist before dynamic scripts bind');
assert.equal(cockpit.includes("switcher?.addEventListener('click'"), true, 'tab opening must use stable event delegation');
assert.equal(cockpit.includes('<details class="autocal-advanced">'), true);
assert.equal(cockpit.includes('MÁX. AUTOMATCH'), false);
assert.equal(cockpit.includes('EVENTOS MADUROS'), false);
console.log('AUTOCAL_ACCESSIBILITY_CONTRACT=PASS');
