'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/styles-autocal-cockpit.css'), 'utf8');

assert.ok(cockpit.includes('Documentos/Omegas'), 'a sessão deve comunicar o destino persistente ao motorista');
assert.ok(cockpit.includes('documentsMirror'), 'a UI deve consumir o estado real do espelho em Documentos');
assert.ok(cockpit.includes('Salvo em Documentos/Omegas') || cockpit.includes('Salvando em Documentos/Omegas'),
  'copy principal precisa explicar persistência sem path técnico interno');

// The graph is the primary driving surface. Secondary cards are intentionally
// compact and horizontally scrollable; only always-visible driving telemetry
// keeps the larger automotive type scale.
assert.match(css, /\.autocal-focus-metric b[\s\S]*font-size:\s*21px/);
assert.match(css, /\.autocal-focus-zone b[\s\S]*font-size:\s*18px/);
assert.match(css, /\.autocal-chart-host[\s\S]*height:\s*clamp\(405px,\s*65vh,\s*515px\)/);

const primaryTiny = [
  ['.autocal-human-copy p', 10],
  ['.autocal-human-copy strong', 10],
  ['.autocal-chart-legend', 12],
  ['.autocal-band-legend', 11],
  ['.autocal-command-copy b', 12],
  ['.autocal-command-copy span', 9],
  ['.autocal-live-narrative', 13],
  ['.autocal-session-copy b', 13],
  ['.autocal-session-copy span', 10],
];
function cssDeclarationsFor(selector) {
  const blocks = [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)];
  return blocks
    .filter(match => match[1].split(',').map(item => item.trim()).includes(selector))
    .map(match => match[2]);
}
for (const [selector, min] of primaryTiny) {
  const declarations = cssDeclarationsFor(selector);
  assert.ok(declarations.length, 'CSS ausente para ' + selector);
  const declared = declarations
    .map(body => [...body.matchAll(/font-size:\s*(\d+)px/g)].map(item => Number(item[1])))
    .flat();
  assert.ok(declared.length, 'font-size ausente para ' + selector);
  const effective = declared[declared.length - 1];
  assert.ok(effective >= min, selector + ' pequeno demais para uso automotivo: ' + effective + 'px');
}

const context = { console, setTimeout: () => 0, clearTimeout: () => {} };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(cockpit, context, { filename: 'autocal-cockpit.js' });
const model = context.OmegasUi.AutoCalUxModel;

const durable = model.sessionNarrative({
  recording: true,
  durationMs: 120000,
  droppedEvents: 0,
  documentsMirror: { available: true, lastSyncOk: true, relativeRoot: 'Documents/Omegas/AutoCal' },
  semanticSummary: { autocal: { gasZones: 2, petrolZones: 4, correlatedRegions: [2, 3] } },
});
assert.match(durable.next, /Documentos\/Omegas/);
assert.equal(durable.level, 'ok');

const mirrorFailure = model.sessionNarrative({
  recording: true,
  durationMs: 120000,
  droppedEvents: 0,
  documentsMirror: { available: false, lastSyncOk: false, lastError: 'sem permissão' },
  semanticSummary: { autocal: { gasZones: 2, petrolZones: 4, correlatedRegions: [2, 3] } },
});
assert.equal(mirrorFailure.level, 'warning');
assert.match(mirrorFailure.next, /memória interna|Documentos/i);

console.log('AUTOCAL_DURABLE_HMI=PASS');