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
  ['app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt', 48],
  ['app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt', 18],
  ['app/src/main/java/com/omegas/prohub/web/PowerJavascriptBridge.kt', 5],
  ['app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt', 45],
];
const sorted = values => [...values].sort();

function documentedMethods(file) {
  const marker = '### `' + file + '`';
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
test('inventário das quatro pontes não omite nem inventa métodos expostos', () => {
  for (const [file, count] of bridgePaths) {
    const actual = exposedMethods(read(file));
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
  // RED contract: a documented dynamic button must exist in its component and
  // be associated with a real row; app-only controls must never gain ECU capability.
  const manifest = matrix.dynamicButtonInventory;
  assert.equal(manifest?.status, 'PARTIAL_TWO_COMPONENTS_PLUS_SIX_TRACKED_P1', 'dynamic controls must not be silently omitted');
  assert.equal(manifest.sources.length, 2);
  const mapped = new Set(matrix.entries.map(e => e.id));
  for (const source of manifest.sources) {
    const body = read(source.sourcePath);
    const tags = [...body.matchAll(/<button\b[\s\S]*?>/g)].map(m => m[0]);
    assert.equal(tags.length, source.buttonTemplateCount, 'template changed: audit '+source.sourcePath);
    const observed = [...new Set(tags.flatMap(tag => [
      ...[...tag.matchAll(/\bdata-[\w-]+/g)].map(m => m[0]).filter(k => !['data-card','data-state'].includes(k)),
      ...[...tag.matchAll(/\bid="([^"]+)"/g)].map(m => '#'+m[1])
    ]))].sort();
    const documented = source.selectors.map(s => s.selector).sort();
    assert.deepEqual(observed, documented, 'dynamic button selector added/removed: '+source.sourcePath);
    for (const action of source.selectors) {
      assert.ok(body.includes(action.handlerMarker), source.sourcePath+' no handler marker: '+action.selector);
      for (const id of action.matrixIds) assert.ok(mapped.has(id), action.selector+' no matrix row: '+id);
      assert.equal(action.physicalEffectConfirmed, false, 'HTML does not prove physical effect');
      if (action.handlerVerified === false) assert.match(action.interactionClass, /UNVERIFIED/);
    }
  }
  for (const e of matrix.entries.filter(e => e.originClass === 'OBS-COD')) {
    assert.equal(e.kind, 'APP_ONLY');
    assert.equal(e.capability, 'NOT_APPLICABLE_ECU');
    assert.equal(e.ecuRequest, 'NOT_APPLICABLE');
    assert.equal(e.ecuResponse, 'NOT_APPLICABLE');
  }
  const scan = manifest.jsSurfaceScan;
  assert.equal(scan.status, 'ALL_36_JS_FILES_STRUCTURALLY_SCANNED');
  const uiBase = 'app/src/main/assets/ui/';
  const allFiles = [...fs.readdirSync(path.join(root, uiBase)).filter(x => x.endsWith('.js')).map(x => uiBase+x),
    ...['components','core','screens'].flatMap(dir =>
    fs.readdirSync(path.join(root, uiBase, dir)).filter(x => x.endsWith('.js')).map(x => uiBase+dir+'/'+x))].sort();
  assert.deepEqual(scan.files.map(x => x.sourcePath).sort(), allFiles, 'UI JS file added without audit');
  let totalSites = 0;
  for (const file of scan.files) {
    const body = read(file.sourcePath);
    const tags = [...body.matchAll(/<button\b[\s\S]*?>/g)].length;
    const factories = [...body.matchAll(/createElement\(\s*['"\x60]button/g)].length;
    assert.equal(tags, file.buttonTemplateCount, file.sourcePath+' button template drift');
    assert.equal(factories, file.createdButtonCount, file.sourcePath+' button factory drift');
    totalSites += tags + factories;
  }
  assert.equal(totalSites, scan.buttonSites);
  const key = manifest.sources.flatMap(x=>x.selectors).find(x => x.selector === 'data-kbd-open');
  assert.equal(key.handlerVerified, true);
  assert.ok(read(key.delegatedSourcePath).includes(key.delegatedHandlerMarker));
  const resetDecision = matrix.blockingDecisions.find(x=>x.id==='EXPOSED_GENERAL_AUTOCAL_RESET_NOT_AUTHORIZED');
  assert.equal(resetDecision?.unsafeRelease, false, 'exposição do reset geral já removida do código');
  assert.equal(resetDecision?.status, 'FIXED_IN_CODE_PENDING_CI');
  assert.equal(resetDecision?.physicalConfirmed, false, 'ausência de CTA não valida central física');

});

test('P1: todas as fontes de botões realmente usadas têm handler e rastreio de decisão', () => {
  const p = 'docs/qualidade/CONTROLES-P1.json';
  assert.ok(fs.existsSync(path.join(root, p)),
    'RED esperado: fontes além de advanced/drawers ainda não possuem rastreio operacional');
  const registry = JSON.parse(read(p));
  assert.equal(registry.schema, 'omegas-p1-controls-v1');
  const manifest = JSON.parse(read('docs/qualidade/matriz-funcional.json'));
  const jsBase = 'app/src/main/assets/ui/';
  const allJs = [
    ...fs.readdirSync(path.join(root, jsBase)).filter(n => n.endsWith('.js')).map(n => jsBase + n),
    ...['components','core','screens'].flatMap(dir =>
      fs.readdirSync(path.join(root, jsBase, dir)).filter(n => n.endsWith('.js')).map(n => jsBase + dir + '/' + n))
  ].sort();
  assert.deepEqual(manifest.dynamicButtonInventory.jsSurfaceScan.files.map(x => x.sourcePath).sort(), allJs,
    'scanner precisa cobrir inclusive map-editor.js na raiz');
  const sources = registry.sources;
  assert.equal(sources.length, 6, 'faltam seis fontes além de Ferramentas e Sessões');
  const expected = [
    'core/autocal-preferencias.js', 'core/car-info-view.js',
    'screens/autocal-cockpit.js', 'screens/curve.js',
    'screens/map.js', 'screens/refino.js'
  ].map(s => jsBase + s);
  assert.deepEqual(sources.map(x => x.sourcePath).sort(), expected.sort());
  const covered = new Set(manifest.entries.map(e => e.id));
  const aux = new Set(['data-state','data-card','data-kind','data-zone-acquired',
    'data-session-id','data-autocal-reacquire-fuel','data-autocal-reacquire-index',
    'data-pref-local-key']);
  let examined = 0;
  for (const entry of sources) {
    const body = read(entry.sourcePath);
    const tags = [...body.matchAll(/<button\b[\s\S]*?>/g)].map(m => m[0]);
    const factoryCount = [...body.matchAll(/createElement\(\s*['"\x60]button/g)].length;
    assert.equal(tags.length, entry.templateCount, 'templates alterados em '+entry.sourcePath);
    assert.equal(factoryCount, entry.factoryCount, 'fábricas alteradas em '+entry.sourcePath);
    const observed = new Set();
    for (const tag of tags) {
      for (const m of tag.matchAll(/\bdata-[\w-]+(?=[\s=>])/g)) if (!aux.has(m[0])) observed.add(m[0]);
      for (const m of tag.matchAll(/\bid="([a-zA-Z][a-zA-Z0-9_-]+)"/g)) observed.add('#'+m[1]);
    }
    assert.deepEqual([...new Set(entry.controls.map(x => x.selector))].sort(), [...observed].sort(),
      'selector no template sem análise do handler em '+entry.sourcePath);
    for (const action of [...entry.controls,...(entry.factories || [])]) {
      examined += 1;
      assert.ok(action.selector && action.sourceMarker && action.handlerMarker, 'origem ou handler sem marcador');
      assert.ok(body.includes(action.sourceMarker), entry.sourcePath+': controle ausente '+action.selector);
      const handler = read(action.handlerSourcePath || entry.sourcePath);
      assert.ok(handler.includes(action.handlerMarker), entry.sourcePath+': handler ausente '+action.selector);
      assert.ok(covered.has(action.matrixId), entry.sourcePath+': linha funcional ausente '+action.matrixId);
      assert.equal(action.physicalEffectConfirmed, false,
        'botão / handler sozinho não comprova efeito físico');
      assert.match(action.provenance, /^OBS-COD$|^DESCONHECIDO$/);
    }
  }
  assert.ok(examined >= 30, 'o inventário não pode ignorar ações menos visíveis');
});
