'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// CI cobre todos os grupos; o APK depende de autorização manual em workflow próprio.
const workflow = name => fs.readFileSync(path.resolve(__dirname, '../../.github/workflows', name), 'utf8');
const ci = workflow('ci.yml');
assert.match(ci, /workflow_dispatch:\s*(?:\r?\n|$)/, 'workflow pode ser iniciado manualmente');
assert.match(ci, /glob\("test_\*\.py"\)/, 'todos os contratos Python são descobertos');
assert.match(ci, /glob\("\*\.test\.cjs"\)/, 'todos os contratos JavaScript são descobertos');
assert.match(ci, /@requires-gradle-test-results/, 'contratos dependentes da JVM têm execução própria');
assert.match(ci, /run:\s*\.\/gradlew testDebugUnitTest/, 'testes JVM precisam executar');
assert.match(ci, /run:\s*(?:sh )?\.\/gradlew lintDebug/, 'lint Android precisa executar');
for (const job of ['javascript', 'python', 'kotlin', 'lint', 'grafo']) {
  assert.ok(ci.includes(`needs.${job}.result`), `integração exige resultado de ${job}`);
}
assert.match(ci, /tools\/auditoria\.py --check/, 'grafo verifica integridade das evidências');
assert.match(ci, /docs\/handoff\/AUDITORIA\.json/, 'auditoria fica disponível como artefato');
assert.doesNotMatch(ci,
  /\bbuild_apk:|name:\s*(?:Build APK|Hash APK|Publish APK artifact)|\bassemble(?:Debug|Release)\b/,
  'CI não gera nem publica APK');
const apk = workflow('apk.yml');
assert.match(apk, /if: \$\{\{ inputs\.build_apk == true \}\}/, 'APK exige autorização explícita');
assert.doesNotMatch(apk, /^\s+(?:push|pull_request|schedule):/m, 'APK não tem gatilho automático');
assert.match(apk, /playwright@1\.56\.1/, 'testes do APK têm navegador instalado');
assert.match(apk, /export NODE_PATH=/, 'testes do APK conseguem carregar o navegador');
assert.match(apk, /tools\/run_checks\.py --full/, 'APK inclui contratos que dependem dos resultados JVM');
console.log('AUTOCAL_CI_NO_APK=PASS');
