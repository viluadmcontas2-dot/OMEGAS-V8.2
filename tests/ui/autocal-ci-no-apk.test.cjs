'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// Current Platina CI is test/lint only. APK generation is not part of this workflow;
// the historical opt-in build_apk flag was deliberately removed from ci.yml.
const ci = fs.readFileSync(path.resolve(__dirname, '../../.github/workflows/ci.yml'), 'utf8');
assert.match(ci, /workflow_dispatch:\s*(?:\r?\n|$)/, 'workflow may still be started manually');
assert.match(ci, /run:\s*python3 -B tools\/run_checks\.py/, 'fast contracts must execute');
assert.match(ci, /run:\s*\.\/gradlew testDebugUnitTest/, 'JVM tests must execute');
assert.match(ci, /run:\s*\.\/gradlew lintDebug/, 'Android lint must execute');
assert.doesNotMatch(ci,
  /\bbuild_apk:|name:\s*(?:Build APK|Hash APK|Publish APK artifact)|\bassemble(?:Debug|Release)\b/,
  'a trimmed CI workflow must not build or publish APK');
console.log('AUTOCAL_CI_NO_APK=PASS');
