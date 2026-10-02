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

const model = context.OmegasUi?.AutoCalUxModel;
assert.ok(model, 'AutoCalUxModel precisa expor transição de seleção para teste');

const pending = ['GAS:4', 'PETROL:7'];

const inFlight = model.pointSelectionTransition(
  pending,
  { action: 'DELETE_POINT', state: 'READING_AFTER' },
  { resetSelection: true, referenceChanged: true, sessionChanged: false },
);
assert.equal(inFlight.clear, false, 'mudança de referência durante o lote não pode apagar intenção antes do ACK/readback final');
assert.equal(inFlight.preserve, true);
assert.deepEqual(Array.from(inFlight.pending), pending);
assert.deepEqual(Array.from(inFlight.restore), pending);
assert.equal(inFlight.reason, 'IN_FLIGHT');

const failed = model.pointSelectionTransition(
  pending,
  { action: 'DELETE_POINT', state: 'FAILED' },
  { resetSelection: false, sessionChanged: false },
);
assert.equal(failed.clear, false, 'falha assíncrona deve preservar a intenção do operador');
assert.equal(failed.preserve, true);
assert.deepEqual(Array.from(failed.restore), pending);
assert.deepEqual(Array.from(failed.pending), []);
assert.equal(failed.reason, 'FAILED_RETAIN_INTENT');

const confirmed = model.pointSelectionTransition(
  pending,
  { action: 'DELETE_POINT', state: 'CONFIRMED' },
  { resetSelection: false, sessionChanged: false },
);
assert.equal(confirmed.clear, true, 'seleção só é descartada depois que a ECU confirma a operação');
assert.deepEqual(Array.from(confirmed.pending), []);
assert.equal(confirmed.reason, 'CONFIRMED');

const staleSession = model.pointSelectionTransition(
  pending,
  { action: 'DELETE_POINT', state: 'SENDING_ACTION' },
  { resetSelection: true, sessionChanged: true },
);
assert.equal(staleSession.clear, true, 'troca de sessão invalida seleção pendente');
assert.deepEqual(Array.from(staleSession.restore), []);
assert.equal(staleSession.reason, 'SESSION_CHANGED');

const requestStart = source.indexOf('\n    requestSelectedPointReacquisition() {');
const requestEnd = source.indexOf('\n    inspectReferencePoint(index)', requestStart);
const requestBody = source.slice(requestStart, requestEnd);
assert.match(requestBody, /pendingPointReacquisitionKeys = new Set/);
assert.doesNotMatch(requestBody, /selectedAcquiredPoints\.clear\(\)/,
  'início assíncrono não pode apagar a seleção antes do resultado final');
assert.match(source, /A seleção só será limpa após confirmação da ECU/);

console.log('AUTOCAL_BATCH_RECOVERY_INTENT=PASS');
