// Auditoria de fluxo (2026-10-11): o resultado das ações do AutoCal (Readquirir, Reset, Nova aquisição, ponto,
// Iniciar/Pausar) só ia para um painel oculto na aba. Agora o fim da ação que o dono tocou vira aviso visível, uma vez;
// um "Concluído"/"Não concluído" antigo nunca é mostrado como resultado da ação nova.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SOURCE = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');

function el(id) {
  const classes = new Set();
  return {
    id, dataset: {}, style: { setProperty() {} }, textContent: '', innerHTML: '', value: '', disabled: false, hidden: false, isConnected: true,
    classList: { add: (...c) => c.forEach(x => classes.add(x)), remove: (...c) => c.forEach(x => classes.delete(x)), toggle: () => false, contains: c => classes.has(c) },
    addEventListener() {}, setAttribute() {}, getAttribute() { return null; }, removeAttribute() {}, appendChild(c) { return c; }, prepend() {}, insertBefore() {},
    querySelector() { return el(null); }, querySelectorAll() { return []; }, closest() { return null; }, focus() {},
  };
}

function boot() {
  const byId = new Map();
  const g = { console, Number, String, Array, Map, Set, JSON, Math, Object, Error, Date, Intl, setTimeout: () => 1, clearTimeout() {} };
  g.globalThis = g;
  g.window = g;
  g.document = {
    getElementById(id) { if (!byId.has(id)) byId.set(id, el(id)); return byId.get(id); },
    querySelector() { return el(null); }, querySelectorAll() { return []; }, createElement() { return el(null); }, head: { appendChild() {} },
  };
  const alerts = [];
  let state = { route: 'autocal', telemetry: {} };
  let action = { state: 'IDLE', message: 'Nenhuma ação preparada.' };
  const app = { store: { get: () => state, patch: p => { if (p.alert) alerts.push(p.alert); state = { ...state, ...p }; } }, scheduler: { addHook: () => () => {} } };
  g.OmegasApp = app;
  g.OmegasUi = { AutoCalApi: {
    available: () => true,
    projection: () => ({ ok: true, source: 'NATIVE_MONITOR', referenceUsable: false, snapshot: { available: true, fields: [] },
      nativeStatus: { state: 'READY', autoCalEnabled: 1 }, nativeSnapshot: {}, liveAcquisitionEpoch: { available: false } }),
    actionStatus: () => action, sessionStatus: () => ({}),
  } };
  vm.createContext(g);
  vm.runInContext(SOURCE, g);
  const cockpit = app.autoCalCockpit;
  assert.ok(cockpit, 'tela do AutoCal carregou');
  return { cockpit, alerts, setAction: next => { action = next; } };
}

test('falha antiga, sem toque do dono, não vira aviso', () => {
  const { cockpit, alerts, setAction } = boot();
  setAction({ state: 'FAILED', message: 'Reset GNV antigo', updatedAt: 1 });
  cockpit.refresh();
  assert.equal(alerts.filter(a => /Não concluído|Concluído/.test(a.message)).length, 0);
});

test('falha da ação tocada aparece uma vez, com "Estado incerto" quando a central pode ter mudado', () => {
  const { cockpit, alerts, setAction } = boot();
  setAction({ state: 'FAILED', message: 'Reset GNV antigo', updatedAt: 1 });
  cockpit.refresh();
  cockpit.expectActionResult();
  cockpit.refresh(); // o mesmo estado antigo continua: ainda não é o resultado
  assert.equal(alerts.filter(a => /Não concluído/.test(a.message)).length, 0);
  setAction({ state: 'SENDING_ACTION', message: 'Enviando Reset GNV', updatedAt: 2 });
  cockpit.refresh();
  setAction({ state: 'FAILED', message: 'Reset GNV: AUTO_CAL_ENABLE não voltou a 1', updatedAt: 3, mutationMayHaveStarted: true });
  cockpit.refresh();
  cockpit.refresh();
  const shown = alerts.filter(a => /Não concluído/.test(a.message));
  assert.equal(shown.length, 1, 'um aviso só');
  assert.equal(shown[0].level, 'warning');
  assert.match(shown[0].message, /Estado incerto/);
});

test('ação confirmada pela central aparece como concluída', () => {
  const { cockpit, alerts, setAction } = boot();
  cockpit.refresh();
  cockpit.expectActionResult();
  setAction({ state: 'CONFIRMED', message: 'Readquirir GNV confirmado por readback', updatedAt: 5 });
  cockpit.refresh();
  const shown = alerts.filter(a => /^Concluído/.test(a.message));
  assert.equal(shown.length, 1);
  assert.equal(shown[0].level, 'ok');
});
