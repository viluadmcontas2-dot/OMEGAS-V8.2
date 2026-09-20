'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const context = { console, setTimeout: () => 0, clearTimeout: () => {} };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(source, context, { filename: 'autocal-cockpit.js' });
const model = context.OmegasUi.AutoCalUxModel;

assert.match(source, /Petrol Inj\. \(ms\)/);
assert.match(source, /MAP \(bar\)/);
assert.match(source, /autocal-axis-tick-x/);
assert.match(source, /autocal-axis-tick-y/);
assert.match(source, />SEM REFERÊNCIA</);
assert.equal(/\.concat\(live \? \[live\.mapBar\]/.test(source), false, 'AGORA não pode participar do domínio');

const points = [
  { index: 0, petrolMs: 2, petrolMapBar: 0.30, gasMapBar: 0.34, gasEquivalentMs: 2.2 },
  { index: 1, petrolMs: 10, petrolMapBar: 1.00, gasMapBar: 1.10, gasEquivalentMs: 10.4 },
];
const domain = model.referenceDomain(points, []);
assert.ok(domain.xMin < 2 && domain.xMax > 10.4);
assert.ok(domain.xMax < 12, 'domínio deve vir apenas da referência nativa');

const scale = {
  ...domain,
  xFor: value => value,
  yFor: value => value,
};
const live = model.projectLive({ petrolMs: 50, mapBar: 5 }, scale);
assert.equal(live.outOfRange, true);
assert.equal(live.x, domain.xMax);
assert.equal(live.y, domain.yMax);

const snapshot = {
  fields: [
    { key: 'PETR_INJ_TBP', status: 'VALID', physicalValues: [2, 10] },
    { key: 'PETR_MNFLD_PRESS_RV', status: 'VALID', physicalValues: [0.3, 1.0] },
    { key: 'GAS_MNFLD_PRESS_RV', status: 'VALID', physicalValues: [0.34, 1.1] },
  ],
};
const ref = model.referencePoints(snapshot, { points: [
  { index: 0, gasEquivalentTimeMs: 2.25 },
  { index: 1, gasEquivalentTimeMs: 10.5 },
]});
assert.equal(ref[0].gasEquivalentMs, 2.25);
assert.equal(ref[1].gasEquivalentMs, 10.5);

console.log('AUTOCAL_CHART_SEMANTICS=PASS');
