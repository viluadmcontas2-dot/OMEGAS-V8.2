// Curva K (dono, 2026-10-11): relê a ECU a cada entrada na aba, sem perder propostas ainda válidas;
// todos os botões num rodapé único embaixo, à esquerda; "Backups (N)" compacto; nada de sub-rolagem.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const ui = path.join(root, 'app/src/main/assets/ui');
const source = fs.readFileSync(path.join(ui, 'screens/curve.js'), 'utf8');
const index = fs.readFileSync(path.join(ui, 'index.html'), 'utf8');

// Estrutura: rodapé único com os 5 controles, nessa ordem; o topo só tem título e estado.
const curveSection = index.slice(index.indexOf('data-screen="curve"'), index.indexOf('data-screen="autocal"'));
const intro = curveSection.slice(0, curveSection.indexOf('curve-view-stack'));
const footbar = curveSection.slice(curveSection.indexOf('class="curve-footbar"'));
assert.ok(curveSection.includes('class="curve-footbar"'), 'Curva K tem rodapé único');
const order = ['curveBackupSave', 'curveBackupSelect', 'curveBackupRestore', 'curveResetButton'];
const positions = order.map(id => footbar.indexOf(`id="${id}"`));
assert.ok(positions.every(p => p > 0), 'todos os botões da Curva K estão no rodapé');
assert.deepEqual([...positions].sort((a, b) => a - b), positions, 'ordem do rodapé: vista, Salvar, Backups, Restaurar, Resetar');
for (const id of order) assert.equal(intro.includes(`id="${id}"`), false, `${id} não fica mais no topo`);
assert.ok(intro.includes('id="curveSourceStatus"') && intro.includes('id="curveBackupStatus"'), 'estado da leitura e dos backups ficam ao lado do título');
const css = fs.readFileSync(path.join(ui, 'styles-calibration-obd.css'), 'utf8');
assert.match(css, /\.curve-footbar \{[^}]*justify-content: flex-start/, 'botões alinhados à esquerda');
assert.match(css, /\.screen\.curve-screen\.active \{[^}]*grid-template-columns: minmax\(0, 1fr\) 340px;[^}]*"title title" "main side" "foot foot"/,
  'curva colada à esquerda e cartão de edição à direita (dono, 2026-10-11)');
assert.match(css, /\.curve-screen \.proposal-list \{[^}]*overflow: hidden/, 'lista de propostas sem sub-rolagem');

// Comportamento, com DOM mínimo.
function node(id) {
  return {
    id, textContent: '', innerHTML: '', value: '', disabled: false, hidden: false, dataset: {},
    classList: { add() {}, remove() {}, toggle() {} },
    querySelector() { return { textContent: '' }; }, querySelectorAll() { return []; }, addEventListener() {},
  };
}
const nodes = new Map();
const document = {
  getElementById(id) { if (!nodes.has(id)) nodes.set(id, node(id)); return nodes.get(id); },
  querySelector() { return node('root'); },
  querySelectorAll() { return []; },
};
const context = { document, console, confirm: () => true };
context.globalThis = context;
context.window = context;
vm.runInNewContext(source, context);

function points(raws) {
  return raws.map((factorRaw, index) => ({ index, petrolMs: 1.5 + index * 0.35, factorRaw, factor: factorRaw / 16384 }));
}
function makeScreen(api) {
  const alerts = [];
  const store = { state: { curve: {} }, get() { return this.state; }, patch(p) { Object.assign(this.state, p); if (p.alert) alerts.push(p.alert); } };
  const screen = new context.OmegasUi.CurveScreen(store, api);
  return { screen, alerts };
}
const base = Array.from({ length: 30 }, () => 16384);

// 1) Entrar na aba com a curva já lida relê a ECU (antes só lia na primeira vez).
let reads = 0;
let operation = { busy: false, state: 'COMPLETED', ok: true, points: points(base) };
const api = {
  startCurveRead: () => { reads += 1; return { ok: true, started: true }; },
  curveOperation: () => operation,
  curveBackups: () => [],
  previewCurvePoint: () => ({ ok: true }),
};
const { screen, alerts } = makeScreen(api);
screen.onEnter({});
assert.equal(reads, 1, 'primeira entrada lê');
screen.poll();
assert.equal(screen.reading, false);
screen.onEnter({});
assert.equal(reads, 2, 'cada entrada na aba relê a Curva K (dono, 2026-10-11)');

// 2) Propostas feitas sobre o mesmo valor da ECU sobrevivem à releitura; as outras caem.
screen.proposals.set(2, { index: 2, currentRaw: 16384, targetRaw: 16548, currentFactor: 1, targetFactor: 1.01, deltaPercent: 1, petrolMs: 2.2 });
screen.proposals.set(5, { index: 5, currentRaw: 16384, targetRaw: 16548, currentFactor: 1, targetFactor: 1.01, deltaPercent: 1, petrolMs: 3.2 });
const changed = base.slice(); changed[5] = 16500;
operation = { busy: false, state: 'COMPLETED', ok: true, points: points(changed) };
screen.poll();
assert.deepEqual([...screen.proposals.keys()], [2], 'só fica a proposta cujo valor atual a ECU ainda tem');
assert.equal(screen.data.points[5].factorRaw, 16500, 'a curva nova é a da ECU');

// 3) Prévia de restauração cai inteira se a curva mudou por baixo dela.
screen.restoreContext = { fileName: 'b.json' };
screen.proposals.set(7, { index: 7, currentRaw: 16384, targetRaw: 16000, currentFactor: 1, targetFactor: 0.97, deltaPercent: -3, petrolMs: 4 });
screen.proposals.set(8, { index: 8, currentRaw: 16384, targetRaw: 16000, currentFactor: 1, targetFactor: 0.97, deltaPercent: -3, petrolMs: 4.3 });
const changed2 = changed.slice(); changed2[8] = 16200;
screen.onEnter({});
operation = { busy: false, state: 'COMPLETED', ok: true, points: points(changed2) };
screen.poll();
assert.equal(screen.restoreContext, null, 'restauração parcial nunca é oferecida');
assert.equal(screen.proposals.size, 0);
assert.match(nodes.get('curveBackupStatus').textContent, /mudou/);

// 4) Sem ECU, a releitura de entrada não vira aviso a cada toque; a curva é dita "última leitura".
const before = alerts.length;
api.startCurveRead = () => ({ ok: false, started: false, error: 'offline' });
screen.onEnter({});
assert.equal(alerts.length, before, 'sem aviso repetido ao entrar na aba sem ECU');
assert.match(nodes.get('curveSourceStatus').textContent, /Última leitura/);
screen.startRead();
assert.equal(alerts.length, before + 1, 'a leitura manual sem ECU avisa');

// 5) Backups compacto: "Backups (N)" e desabilitado sem nenhum.
screen.refreshBackups();
assert.match(nodes.get('curveBackupSelect').innerHTML, /^<option value="">Backups \(0\)<\/option>$/);
assert.equal(nodes.get('curveBackupSelect').disabled, true);
api.curveBackups = () => [{ fileName: 'a.json', createdAt: 1, label: 'Curva K', type: 'MANUAL_SNAPSHOT' }, { fileName: 'b.json', createdAt: 2, label: 'Curva K' }];
screen.refreshBackups();
assert.match(nodes.get('curveBackupSelect').innerHTML, /^<option value="">Backups \(2\)<\/option>/);
assert.equal(nodes.get('curveBackupSelect').disabled, false);

// 6) Lista de propostas sem sub-rolagem: primeiras linhas + "+N pontos · veja no gráfico".
screen.restoreContext = null;
screen.proposals.clear();
for (let i = 0; i < 9; i += 1) screen.proposals.set(i, { index: i, currentRaw: 16384, targetRaw: 16548, currentFactor: 1, targetFactor: 1.01, deltaPercent: 1, petrolMs: 1.5 + i });
screen.renderProposalList();
const list = nodes.get('curveProposalList').innerHTML;
assert.equal((list.match(/<div>/g) || []).length, 4);
assert.match(list, /\+5 pontos · veja no gráfico/);
assert.equal(nodes.get('curveReviewButton').textContent, 'Gravar 9 pontos na ECU', 'o botão diz o total que será gravado');

// 6) Atualização automática (sem botão Reler): ao reconectar a ECU lê mesmo com proposta preparada; a cada 15 s só lê se o
//    dono não está editando; nunca durante gravação; silenciosa (sem a faixa "Lendo…").
{
  let n = 0;
  const auto = { ...api, startCurveRead: () => { n += 1; return { ok: true, started: true }; } };
  const { screen: s2 } = makeScreen(auto);
  s2.autoSync(false);
  assert.equal(n, 1, 'sem curva na tela, a atualização automática lê');
  s2.reading = false; s2.data = { points: points(base) };
  s2.proposals.set(1, { index: 1, currentRaw: 16384, targetRaw: 16500 });
  s2.autoSync(false);
  assert.equal(n, 1, 'editando (proposta preparada): o relógio de 15 s não mexe na tela');
  s2.autoSync(true);
  assert.equal(n, 2, 'ECU reconectada: lê mesmo assim (a proposta antiga só sobrevive se o valor da ECU for o mesmo)');
  s2.reading = false; s2.writing = true;
  s2.autoSync(true);
  assert.equal(n, 2, 'nunca durante gravação');
  s2.writing = false; s2.reading = false; s2.proposals.clear();
  document.getElementById('curveSourceStatus').textContent = 'ECU confirmada · 30 pontos';
  s2.autoSync(false);
  assert.equal(n, 3);
  assert.equal(document.getElementById('curveSourceStatus').textContent, 'ECU confirmada · 30 pontos', 'leitura silenciosa não troca o texto por "Lendo…"');
}

// 7) Gravar nunca sai durante uma leitura.
let writes = 0;
api.writeCurve = () => { writes += 1; return { ok: true, started: true }; };
screen.reading = true;
screen.writePrepared();
assert.equal(writes, 0);
console.log('CURVA_K_RELEITURA_E_RODAPE=PASS');
