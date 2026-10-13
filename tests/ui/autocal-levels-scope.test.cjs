'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ROOT = path.join(__dirname, '../..');

const autocal = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');

test('LEVELS RAW não aparece no AutoCal (a aba Agora foi podada em 2026-10-13)', () => {
  assert.doesNotMatch(autocal, /LEVELS RAW/);
  assert.doesNotMatch(autocal, /NÍVEL DO GNV/);
  assert.doesNotMatch(autocal, /autocalLiveLevel/);
  assert.doesNotMatch(autocal, /levelRaw/);
  assert.doesNotMatch(autocal, /level_raw/);
});

test('AutoCal AGORA rail is limited to relevant acquisition context', () => {
  // RPM, Petrol Inj., MAP e tensão moram na barra global persistente (components/live-bar.js), não mais no AutoCal.
  for (const id of ['autocalLiveRpm', 'autocalLivePetrol', 'autocalLiveMap', 'autocalLiveVolt']) {
    assert.doesNotMatch(autocal, new RegExp(id));
  }
  assert.match(autocal, /autocalLiveZone/);
});
