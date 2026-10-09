'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const { test } = require('node:test');

const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
const planner = fs.readFileSync(path.join(root, 'app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalRefreshPlanner.kt'), 'utf8');

const context = { console, setTimeout: () => 0, clearTimeout: () => {} };
context.globalThis = context;
vm.createContext(context);
require('./_support.cjs').preload(context);
vm.runInContext(source, context, { filename: 'autocal-cockpit.js' });
const model = context.OmegasUi.AutoCalUxModel;

// Cláusula visual: o cursor telemétrico não é confirmação de uma aquisição.
test('cursor destaca apenas a faixa do combustível ativo', () => {
  const s = { fields: [{key: 'MNFLD_PRESS_THD', status: 'VALID', physicalValues: [
    0.10,0.15,0.20,0.25,0.30,0.35,0.40,0.45,0.50,0.55,0.60,
    0.65,0.70,0.75,0.80,0.85,0.90,0.95]}] };
  const gas = model.zoneCursorFlags(s, { fuel: 'GNV', mapBar: 0.81 });
  assert.equal(gas.gasZone, 4);
  assert.equal(gas.petrolZone, null, 'gasolina não pode acender quando o motor está no GNV');
  const petrol = model.zoneCursorFlags(s, { fuel: 'GASOLINA', mapBar: 0.81 });
  assert.equal(petrol.petrolZone, 4);
  assert.equal(petrol.gasZone, null);
  const changing = model.zoneCursorFlags(s, { fuel: 'TRANSICAO', mapBar: 0.81 });
  assert.equal(changing.gasZone, null);
  assert.equal(changing.petrolZone, null);
});

test('lenta e corte não acendem regiões do AutoCal como atuais', () => {
  const s = { fields: [{key: 'MNFLD_PRESS_THD', status: 'VALID', physicalValues: [
    0.10,0.15,0.20,0.25,0.30,0.35,0.40,0.45,0.50,0.55,0.60,
    0.65,0.70,0.75,0.80,0.85,0.90,0.95]}] };
  assert.equal(model.zoneCursorFlags(s, {fuel:'GNV', mapBar:0.05}).gasZone, null);
  assert.equal(model.zoneCursorFlags(s, {fuel:'CUTOFF',mapBar:0.81}).gasZone, null);
});

test('as zonas nativas chegam antes do grupo histórico G3', () => {
  // A ordem do planner faz diferença real: cada grupo ocupa duas rodadas de tick
  // para ler e confirmar sem bloquear a telemetria da ECU.
  const q = planner.match(/if \(acquisition\) \{[\s\S]*?\n\s*\}/)?.[0] || '';
  assert.match(q, /Group\.G2_PETROL_BUFFERS/);
  assert.match(q, /Group\.G4_GAS/);
  assert.match(q, /Group\.G6_ZONES/);
  assert.match(q, /Group\.G3_GAS_PREV/);
  assert.ok(q.indexOf('G4_GAS') < q.indexOf('G3_GAS_PREV'));
  assert.ok(q.indexOf('G6_ZONES') < q.indexOf('G3_GAS_PREV'));
  assert.match(planner, /ACQUISITION_INTERVAL_MS = 2_000L/);
});
