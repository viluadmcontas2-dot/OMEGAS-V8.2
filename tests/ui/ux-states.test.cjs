const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const base = process.env.WU006_ROOT || path.resolve(__dirname, '../..');
function model() {
  const file = path.join(base, 'app/src/main/assets/ui/core/workflow-presentation.js');
  const root = { OmegasUi: {} };
  if (fs.existsSync(file)) vm.runInNewContext(fs.readFileSync(file, 'utf8'), { window: root, globalThis: root });
  assert.ok(root.OmegasUi.WorkflowPresentation, 'estados humanos compartilhados ainda ausentes');
  return root.OmegasUi.WorkflowPresentation;
}
test('todas as telas mostram desconexão sem aproveitar sucesso antigo', () => {
  const m = model();
  for (const route of ['dashboard','autocal','curve','map','suggestions','tools','learning']) {
    assert.equal(m.state({ route, status: {}, telemetry: { k_factor: { state: 'BATCH_CONFIRMED', readbackValid: true } } }).key, 'offline');
  }
});
test('ACK sem readback e readback divergente nunca viram verificado', () => {
  const m = model();
  const state = { route: 'curve', status: { usbConnected: true }, telemetry: {} };
  state.telemetry.k_factor = { state: 'BATCH_CONFIRMED', readbackValid: false };
  assert.equal(m.state(state).key, 'divergent');
  state.telemetry.k_factor = { state: 'BATCH_CONFIRMED', readbackValid: true };
  assert.equal(m.state(state).key, 'verified');
  state.telemetry.k_factor = { state: 'WRITING', busy: true };
  assert.equal(m.state(state).key, 'writing');
  state.telemetry.k_factor = { state: 'READING', busy: true };
  assert.equal(m.state(state).key, 'reading');
});
test('piloto apresenta coleta, falta de dados, proposta e verificação sem percentual inventado', () => {
  const m = model(), state = { route: 'autocal', status: { usbConnected: true } };
  for (const [phase, key] of [['ECU_TRABALHANDO','collecting'],['COLETANDO_NOSSOS','insufficient'],['PROPOSTA_PRONTA','ready'],['VERIFICANDO','collecting'],['ESTAVEL','verified'],['RESTAURAR_TRECHO','divergent']]) {
    assert.equal(m.state(state, { autopilot: { phase } }).key, key);
  }
  assert.equal(m.state(state).key, 'insufficient');
  assert.match(m.state(state).next, /Dirija|Aguarde/);
});
