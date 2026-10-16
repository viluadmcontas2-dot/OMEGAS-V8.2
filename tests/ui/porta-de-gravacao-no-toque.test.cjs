// Porta de gravação NO TOQUE (auditoria, 2026-10-16). Caso real: proposta montada → dono toca "Reaprender GNV" (a ECU volta ao
// automático) → toca Gravar → antes gravava sem olhar, porque a porta só era consultada ao montar a proposta. Agora o toque que
// dispara bytes consulta a porta de novo, para gravações de PROPOSTA (Curva K → Mapa K, Refino). Edição manual continua livre;
// estado desconhecido (sem piloto) continua liberando (decisão do dono).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(ui, file), 'utf8');

function fakeDocument() {
  const byId = new Map();
  const bySel = new Map();
  const node = id => ({
    id, textContent: '', innerHTML: '', value: '', disabled: false, hidden: false, dataset: {}, style: {}, isConnected: true,
    classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
    querySelector() { return null; }, querySelectorAll() { return []; }, addEventListener() {}, prepend() {}, insertBefore() {}, focus() {},
    getBoundingClientRect() { return { width: 860, height: 300 }; },
  });
  return {
    getElementById(id) { if (!byId.has(id)) byId.set(id, node(id)); return byId.get(id); },
    querySelector(sel) { if (!bySel.has(sel)) bySel.set(sel, node(null)); return bySel.get(sel); },
    querySelectorAll() { return []; }, createElement() { return node('el'); },
  };
}

test('Mapa K: a proposta Curva K → Mapa K passa pela porta no toque em Gravar; edição manual não', () => {
  const context = { document: fakeDocument(), console, setTimeout: () => 0 };
  context.globalThis = context;
  context.window = context;
  vm.runInNewContext(read('map-editor.js'), context);
  vm.runInNewContext(read('core/write-gate.js'), context);
  vm.runInNewContext(read('screens/map.js'), context);
  const alerts = [];
  const writes = [];
  const store = { state: { map: {} }, get() { return this.state; }, patch(p) { Object.assign(this.state, p); if (p.alert) alerts.push(p.alert.message); } };
  const api = { writeMap(...args) { writes.push(args); return { ok: true, started: true, startedAt: writes.length }; }, mapWriteOperation: () => ({ state: 'IDLE' }) };
  const screen = new context.OmegasUi.MapScreen(store, api, null);
  screen.editor.load({
    rows: Array.from({ length: 12 }, (_, r) => Array.from({ length: 12 }, (_, c) => 110 + r + c)),
    axes: { petrolBins: [2, 2.5, 3, 3.5, 4.5, 6, 8, 10, 12, 14, 16, 18], rpmBins: [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500] },
  });
  let autopilot = { ecuDone: false, autoMatchCount: 1, maxAutomatch: 3 }; // ECU voltou ao automático depois da proposta
  context.OmegasUi.AutoCalApi = { equivalence: () => ({ autopilot }) };
  screen.curvePlan = { changes: [{ row: 3, column: 0, target: 125 }], nodes: [{ index: 1, currentRaw: 16384, targetRaw: 16000 }], armed: false };
  screen.editor.setTargetOverrides([{ row: 3, column: 0, target: 125 }]);
  screen.writePrepared();
  assert.equal(writes.length, 0, 'porta fechada no toque: nenhum byte');
  assert.match(alerts.at(-1), /AutoMatch \(1 de 3\)/);
  assert.equal(screen.curvePlan?.armed, false, 'a proposta continua montada para gravar depois');
  // A ECU terminou (ou "Refinar por minha conta"): o mesmo toque grava, no formato novo writeMap(cells, reason).
  autopilot = { ecuDone: true, autoMatchCount: 3, maxAutomatch: 3 };
  screen.writePrepared();
  assert.equal(writes.length, 1);
  assert.equal(writes[0].length, 2);
  assert.equal(writes[0][0][0].target, 125);
  assert.equal(typeof writes[0][1], 'string');
  assert.equal(screen.curvePlan.armed, true, 'a gravação da proposta arma a etapa 2');
  // Edição manual de célula com a porta fechada: livre (não passa pela porta).
  autopilot = { ecuDone: false };
  screen.curvePlan = null;
  screen.editor.setTargetOverrides([{ row: 4, column: 1, target: 130 }]);
  screen.writePrepared();
  assert.equal(writes.length, 2);
  // Estado desconhecido (sem piloto) libera a proposta (decisão do dono).
  autopilot = undefined;
  screen.curvePlan = { changes: [{ row: 6, column: 6, target: 150 }], nodes: [], armed: false };
  screen.editor.setTargetOverrides([{ row: 6, column: 6, target: 150 }]);
  screen.writePrepared();
  assert.equal(writes.length, 3);
});

test('Refino: "Gravar na Curva K" e "Gravar no Mapa K" consultam a porta no toque; o cartão fica para gravar depois', () => {
  const document = fakeDocument();
  const window = { document, console, setTimeout: () => 0 };
  window.window = window;
  window.globalThis = window;
  vm.runInNewContext(read('core/write-gate.js'), window);
  vm.runInNewContext(read('screens/refino.js'), window);
  let eq = null;
  window.OmegasUi.AutoCalApi = { available: () => true, equivalence: () => eq, projection: () => ({ ok: false }) };
  const alerts = [];
  let state = { route: 'refino' };
  const store = { get: () => state, patch(p) { state = { ...state, ...p }; if (p.alert) alerts.push(p.alert.message); }, subscribe() { return () => {}; } };
  const native = { reads: 0, startCurveRead() { native.reads += 1; return { ok: true, started: true }; }, curveOperation: () => ({}), writeCurve: () => ({ ok: false }) };
  const screen = new window.OmegasUi.RefinoScreen({ store, scheduler: { addHook: () => () => {} }, api: native });
  const proposalEq = extra => ({ ok: true, autopilot: {
    phase: 'PROPOSTA_PRONTA', ecuOnline: true, action: { kind: 'review', label: 'Revisar e gravar 1 ponto', enabled: true },
    proposal: { known: true, count: 1, points: [{ index: 4, currentRaw: 16384, targetRaw: 17000, referenceTimeMs: 5.5 }], held: [], signature: '4:16384>17000' },
    ...extra,
  } });
  eq = proposalEq({ ecuDone: false, autoMatchCount: 2, maxAutomatch: 3 });
  screen.refresh(true);
  screen.openPreview();
  assert.match(document.getElementById('refinoReview').innerHTML, /Gravar na Curva K/);
  screen.confirm();
  assert.equal(native.reads, 0, 'porta fechada no toque: nenhuma leitura nem gravação');
  assert.match(alerts.at(-1), /AutoMatch \(2 de 3\)/);
  assert.equal(screen.preview, true, 'o cartão fica aberto');
  assert.equal(screen.review?.points?.length, 1, 'a proposta não some');
  screen.onClick({ target: { closest: sel => (sel === '[data-refino-map-confirm]' ? {} : null) } });
  assert.equal(alerts.length, 2, 'Gravar no Mapa K também consulta a porta');
  assert.equal(screen.preview, true);
  // A ECU terminou: o mesmo toque passa e vai ler a Curva K da ECU antes de gravar (ACK + readback no Kotlin).
  eq = proposalEq({ ecuDone: true });
  screen.confirm();
  assert.equal(native.reads, 1);
  assert.equal(screen.operation.phase, 'reading');
});
