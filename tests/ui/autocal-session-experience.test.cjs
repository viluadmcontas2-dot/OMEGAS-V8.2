'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const api = read('app/src/main/assets/ui/core/autocal-api.js');
const cockpit = read('app/src/main/assets/ui/screens/autocal-cockpit.js');
const css = read('app/src/main/assets/ui/styles-autocal-cockpit.css');
const bridge = read('app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt');

assert.match(api, /sessionStatus:\s*\(\)\s*=>\s*invoke\(['"]getSessionLedgerStatus['"]/,
  'AutoCalApi deve consumir o resumo persistente da sessão');
assert.match(api, /sessions:\s*\(\)\s*=>\s*invoke\(['"]listAutoCalSessions['"]/,
  'AutoCalApi deve listar sessões pelo mesmo bridge AutoCal');
assert.match(bridge, /fun\s+getSessionLedgerStatus\s*\(/);
assert.match(bridge, /fun\s+listAutoCalSessions\s*\(/);
assert.match(api, /exportSession:\s*sessionId\s*=>\s*invoke\(['"]exportAutoCalSession['"]/,
  'exportar sessão deve usar o mesmo bridge AutoCal');
assert.match(bridge, /fun\s+exportAutoCalSession\s*\(/);

// Revisto (W2): o AutoCal não tem mais atalho nem cartão de sessão (a aba Sessões já existe).
assert.equal(cockpit.includes('data-autocal-sessions'), false);
assert.equal(cockpit.includes('autocalSessionDrawer'), false, 'histórico pertence à aba Sessões, sem cartão de rolagem duplicado');
assert.ok(cockpit.includes('renderSessionState()'), 'sessão precisa de renderer explícito');
assert.ok(read('app/src/main/assets/ui/screens/sessions.js').includes('export'), 'a aba Sessões preserva exportação');
const refreshBody = cockpit.slice(cockpit.indexOf('    refresh() {'), cockpit.indexOf('    runOperational(action)'));
assert.equal(refreshBody.includes('this.api.sessions?.()'), false,
  'refresh normal não pode varrer histórico no disco');
assert.ok(cockpit.includes('sessionNarrative'), 'copy da sessão deve ser derivada de estado real');
assert.equal(cockpit.includes('directory'), false, 'path de armazenamento não pertence à UX primária');
assert.equal(cockpit.includes('sessionId.slice'), false, 'ID técnico não deve ser a identidade humana da sessão');
assert.doesNotMatch(css, /\.autocal-session-drawer\s*\{/);

const context = { console, setTimeout: () => 0, clearTimeout: () => {} };
context.globalThis = context;
vm.createContext(context);
require('./_support.cjs').preload(context);
vm.runInContext(cockpit, context, { filename: 'autocal-cockpit.js' });
const model = context.OmegasUi.AutoCalUxModel;

const narrative = model.sessionNarrative({
  recording: true,
  durationMs: 2_520_000,
  droppedEvents: 0,
  semanticSummary: {
    physicalUsbSessionId: 44,
    autocal: { gasZones: 2, petrolZones: 4, correlatedRegions: [4, 8, 11] },
  },
});
assert.match(narrative.title, /Sessão atual/);
assert.match(narrative.detail, /42 min/);
assert.match(narrative.detail, /3 regiões/);
assert.match(narrative.detail, /GNV 2\/4/);
assert.equal(narrative.level, 'ok');

const degraded = model.sessionNarrative({
  recording: true,
  durationMs: 10_000,
  droppedEvents: 4,
  semanticSummary: { autocal: { gasZones: 0, petrolZones: 0, correlatedRegions: [] } },
});
assert.equal(degraded.level, 'warning');
assert.match(degraded.next, /lacuna|gravação|evidência/i);

console.log('AUTOCAL_SESSION_EXPERIENCE=PASS');