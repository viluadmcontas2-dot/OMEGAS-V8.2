'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.resolve(__dirname, '..');
const UI_ROOT = path.join(ROOT, 'app/src/main/assets/ui');

function walk(dir, acc = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, acc);
    else if (entry.isFile() && full.endsWith('.js')) acc.push(full);
  }
  return acc;
}

test('embedded WebView UI assets avoid syntax that can black-screen legacy multimedia WebView before rendering', () => {
  const offenders = [];
  for (const file of walk(UI_ROOT)) {
    const source = fs.readFileSync(file, 'utf8');
    const relative = path.relative(ROOT, file).replace(/\\/g, '/');
    if (/\?\./.test(source)) offenders.push(`${relative}: optional chaining`);
    if (/\?\?/.test(source)) offenders.push(`${relative}: nullish coalescing`);
    if (/\.replaceAll\s*\(/.test(source)) offenders.push(`${relative}: String.replaceAll`);
  }
  assert.deepEqual(offenders, [], `legacy WebView parse/runtime hazards found:\n${offenders.join('\n')}`);
});

console.log('BLUE_WEBVIEW_LEGACY_SYNTAX_CONTRACT=PASS');
