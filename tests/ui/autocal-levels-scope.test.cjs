'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ROOT = path.join(__dirname, '../..');

const dashboard = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/dashboard.js'), 'utf8');
const autocal = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');

test('LEVELS RAW belongs to Dashboard/AGORA and is absent from AutoCal', () => {
  assert.match(dashboard, /Nível da ECU/);
  assert.match(dashboard, /dashLevelsRaw/);
  assert.match(fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/core/live-store.js'), 'utf8'), /level_raw/, 'o Agora lê o nível pela leitura única (LiveStore.read)');
  assert.match(dashboard, /reading\.levelRaw/);
  assert.doesNotMatch(autocal, /LEVELS RAW/);
  assert.doesNotMatch(autocal, /autocalLiveLevel/);
  assert.doesNotMatch(autocal, /levelRaw/);
  assert.doesNotMatch(autocal, /level_raw/);
});

test('AutoCal AGORA rail is limited to relevant acquisition context', () => {
  for (const id of ['autocalLiveRpm', 'autocalLivePetrol', 'autocalLiveMap']) {
    assert.match(autocal, new RegExp(id));
  }
});
