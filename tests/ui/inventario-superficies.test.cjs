'use strict';
// C0 Task 3: detector de deriva do inventário estático (OBS-COD).
// Não deduz capacidade ECU, suporte físico ou completude de controles JS dinâmicos.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const inventory = read('docs/qualidade/INVENTARIO-SUPERFICIES-OMEGAS82.md');
const html = read('app/src/main/assets/ui/index.html');
const bridgePaths = [
  ['HubJavascriptBridge.kt', 48],
  ['V7JavascriptBridge.kt', 18],
  ['PowerJavascriptBridge.kt', 5],
];
const sorted = values => [...values].sort();

function documentedMethods(file) {
  const marker = '### `app/src/main/java/com/omegas/prohub/web/' + file + '`';
  const after = inventory.split(marker);
  assert.equal(after.length, 2, 'Inventário sem seção da ponte ' + file);
  const line = after[1].split('\n').find(line => line.startsWith('`') && line.includes(' · '));
  assert.ok(line, 'Inventário sem lista de métodos de ' + file);
  return line.replace(/^`|`$/g, '').split(' · ');
}
function exposedMethods(source) {
  // Captura apenas declarações fun anotadas, sem contar comentários com @JavascriptInterface.
  return [...source.matchAll(/@JavascriptInterface\s+(?:(?:public|override|suspend)\s+)*fun\s+(\w+)\s*\(/g)].map(m => m[1]);
}
test('inventário das três pontes não omite nem inventa métodos expostos', () => {
  for (const [file, count] of bridgePaths) {
    const actual = exposedMethods(read('app/src/main/java/com/omegas/prohub/web/' + file));
    const documented = documentedMethods(file);
    assert.equal(actual.length, count, file + ': contagem alterada; auditar consumidor e risco antes de atualizar');
    assert.equal(new Set(actual).size, actual.length, file + ': duplicação de método');
    assert.deepEqual(sorted(documented), sorted(actual), file + ': inventário divergente; não aprovar capacidade automaticamente');
  }
});
test('inventário HTML estático detecta novo controle ou rota não auditada', () => {
  const buttons = [...html.matchAll(/<button\b[^>]*>/g)].map(m => m[0]);
  const ids = buttons.map(tag => /\bid="([^"]+)"/.exec(tag)?.[1]).filter(Boolean);
  const routes = buttons.map(tag => /\bdata-route="([^"]+)"/.exec(tag)?.[1]).filter(Boolean);
  const line = inventory.split('## Botões HTML com ID (16)')[1]?.split('\n').find(s => s.startsWith('`'));
  assert.ok(line, 'Lista estática de IDs ausente');
  const documented = line.replace(/^`|`$/g, '').split(' · ');
  assert.equal(buttons.length, 33, 'Novo botão estático: classificar e documentar');
  assert.equal(ids.length, 16, 'Novo botão com ID: classificar e documentar');
  assert.equal(new Set(ids).size, ids.length);
  assert.deepEqual(sorted(ids), sorted(documented));
  assert.deepEqual(routes, ['autocal', 'refino', 'map', 'curve', 'tools']);
});
test('inventário permanece parcial e não declara prova física por presença de API', () => {
  const matrix = JSON.parse(read('docs/qualidade/matriz-funcional.json'));
  assert.equal(matrix.scope, 'PARTIAL_SEED_NOT_COMPLETE');
  assert.equal(matrix.physicalValidation, 'PENDING');
  assert.match(inventory, /NÃO prova de suporte pela central/);
  assert.match(inventory, /BLOQUEIO_DE_DECISÃO/);
});
