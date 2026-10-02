'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ci = fs.readFileSync(path.resolve(__dirname, '../../.github/workflows/ci.yml'), 'utf8');
const names = ['Build APK', 'Hash APK', 'Publish APK artifact'];
const apkStepsPresent = names.some(name => ci.includes('- name: ' + name));
if (apkStepsPresent) {
assert.match(ci, /workflow_dispatch:[\s\S]*build_apk:/, 'APK precisa exigir input manual');

for (const name of names) {
  const start = ci.indexOf('- name: ' + name);
  assert.ok(start >= 0, 'step ausente: ' + name);
  const next = ci.indexOf('\n      - name:', start + 1);
  const block = ci.slice(start, next < 0 ? ci.length : next);
  assert.match(block, /if: \$\{\{ github\.event_name == 'workflow_dispatch' && inputs\.build_apk == true \}\}/,
    name + ' sem gate manual explícito');
}
} else {
  // Current Platina may remove the inherited APK pipeline entirely.
  // Accept absence only if it cannot build an APK as a side effect.
  assert.doesNotMatch(ci, /(?:assembleDebug|assembleRelease|bundleDebug|bundleRelease|app-debug\.apk|build_apk\s*:)/,
    'CI sem etapa APK não pode conter geração implícita de APK');
}
console.log('AUTOCAL_CI_NO_APK=PASS');
