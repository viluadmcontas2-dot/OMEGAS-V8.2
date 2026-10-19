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
// Desenho (dono, 2026-10-19): o APK compila direto (rápido) e a suíte completa roda UMA vez, em paralelo, no ci.yml. O APK só vira
// oficial se build + lint + a suíte do MESMO commit estiverem verdes (job liberar-apk). Sem repetir a suíte em fila dentro do apk.yml.
assert.doesNotMatch(apk, /run_checks\.py --full/, 'o APK não repete a suíte em fila (ela roda no ci.yml)');
assert.match(apk, /ci-do-mesmo-commit:/, 'o APK confere a suíte completa do mesmo commit');
assert.match(apk, /workflows\/ci\.yml\/runs\?head_sha=\$PRODUCT_SHA/, 'a conferência é do MESMO commit');
assert.match(apk, /"completed success"\) exit 0/, 'só "completed success" libera');
assert.match(apk, /liberar-apk:\s*\n\s*needs: \[verify-build-publish, lint, ci-do-mesmo-commit\]/, 'o APK oficial exige build, lint e a suíte verdes');
assert.match(apk, /name: omegas-apk-candidato-\$\{\{ env\.PRODUCT_SHA \}\}/, 'o build só guarda um CANDIDATO');
assert.equal((apk.match(/name: omegas-apk-\$\{\{ env\.PRODUCT_SHA \}\}/g) || []).length, 1, 'o nome oficial só é publicado no job liberar-apk');
console.log('AUTOCAL_CI_NO_APK=PASS');
