// Curva K toda em 1,0 (dono, 2026-10-11): um toque, sem window.confirm (sem dupla confirmação). A proteção é a cópia
// da curva atual, guardada antes pelo mesmo caminho do botão "Salvar curva atual" (startCurveBackup); o reset só começa
// com essa cópia confirmada (arquivo + hash). Cópia que falha → reset não é chamado e o dono é avisado.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const source = fs.readFileSync(path.join(ui, 'screens/curve.js'), 'utf8');
const index = fs.readFileSync(path.join(ui, 'index.html'), 'utf8');

function harness() {
  const nodes = new Map();
  const node = id => ({
    id, textContent: '', innerHTML: '', value: '', disabled: false, hidden: false, dataset: {}, style: {},
    classList: { add() {}, remove() {}, toggle() {} },
    querySelector() { return { textContent: '' }; }, querySelectorAll() { return []; }, addEventListener() {},
  });
  const document = {
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, node(id)); return nodes.get(id); },
    querySelector() { return node('root'); },
    querySelectorAll() { return []; },
  };
  let confirms = 0;
  const context = { document, console, confirm: () => { confirms += 1; return true; } };
  context.globalThis = context;
  context.window = context;
  vm.runInNewContext(source, context);
  const calls = [];
  let operation = { busy: false, state: 'IDLE', ok: true };
  const api = {
    curveBackups: () => [],
    startCurveRead: () => ({ ok: true, started: true }),
    curveOperation: () => operation,
    startCurveBackup: label => { calls.push(['backup', label]); return api.backupStart; },
    resetCurve: () => { calls.push(['reset']); return { ok: true, started: true, state: 'CURVE_RESET_QUEUED' }; },
    backupStart: { ok: true, started: true, state: 'CURVE_BACKUP_SAVING', startedAt: 111 },
  };
  const alerts = [];
  const store = { state: { curve: {} }, get() { return this.state; }, patch(p) { Object.assign(this.state, p); if (p.alert) alerts.push(p.alert.message); } };
  const screen = new context.OmegasUi.CurveScreen(store, api);
  return {
    screen, api, calls, alerts, nodes,
    confirms: () => confirms,
    setOperation(next) { operation = next; },
  };
}

test('sem window.confirm no código nem no toque; o botão diz "Curva K toda em 1,0"', () => {
  assert.doesNotMatch(source, /window\.confirm|\bconfirm\(/, 'nenhuma dupla confirmação na Curva K');
  assert.match(index, /<button id="curveResetButton" type="button" class="quiet-button">Curva K toda em 1,0<\/button>/);
  const h = harness();
  h.screen.resetCurve();
  assert.equal(h.confirms(), 0, 'o toque não abre diálogo');
});

test('um toque: primeiro a cópia (mesmo caminho do "Salvar curva atual"); o reset só depois da cópia confirmada', () => {
  const h = harness();
  h.screen.resetCurve();
  assert.deepEqual(h.calls.map(c => c[0]), ['backup'], 'só a cópia foi pedida; o reset espera');
  assert.match(h.calls[0][1], /Cópia antes de pôr a Curva K em 1,0/);
  assert.equal(h.screen.backupTask, 'reset-backup');
  // A cópia ainda está sendo gravada: nada de reset.
  h.setOperation({ busy: true, state: 'CURVE_BACKUP_SAVING', startedAt: 111 });
  h.screen.poll();
  assert.deepEqual(h.calls.map(c => c[0]), ['backup']);
  // Cópia confirmada (arquivo + hash): agora o reset começa, uma vez.
  h.setOperation({ busy: false, ok: true, state: 'COMPLETED', startedAt: 111, fileName: 'MANUAL-1-abcd.json', hash: 'abcd1234' });
  h.screen.poll();
  assert.deepEqual(h.calls.map(c => c[0]), ['backup', 'reset']);
  assert.equal(h.screen.writing, true, 'o reset segue o caminho de gravação existente (ACK + readback na ponte)');
  assert.equal(h.nodes.get('curveOperationTitle').textContent, 'Pondo a Curva K toda em 1,0');
});

test('cópia que falha (ou que não é esta) → reset não é chamado e o dono é avisado', () => {
  const failures = [
    ['não começou', null, { ok: false, started: false, error: 'Serviço V8 indisponível' }],
    ['falhou ao gravar', { busy: false, ok: false, state: 'FAILED', startedAt: 111, error: 'Download/Omegas indisponível' }],
    ['sem arquivo/hash', { busy: false, ok: true, state: 'COMPLETED', startedAt: 111 }],
    ['outra operação', { busy: false, ok: true, state: 'COMPLETED', startedAt: 999, fileName: 'x.json', hash: 'h' }],
  ];
  for (const [label, done, start] of failures) {
    const h = harness();
    if (start) h.api.backupStart = start;
    h.screen.resetCurve();
    if (done) {
      h.setOperation(done);
      h.screen.poll();
    }
    assert.equal(h.calls.some(c => c[0] === 'reset'), false, `${label}: reset não pode ser chamado`);
    assert.equal(h.screen.writing, false, `${label}: nada é gravado`);
    assert.match(h.alerts.at(-1) || '', /A Curva K não foi mexida/, `${label}: o dono é avisado`);
  }
});

test('durante leitura ou gravação o toque não faz nada', () => {
  const h = harness();
  h.screen.reading = true;
  h.screen.resetCurve();
  h.screen.reading = false;
  h.screen.writing = true;
  h.screen.resetCurve();
  assert.deepEqual(h.calls, []);
});
