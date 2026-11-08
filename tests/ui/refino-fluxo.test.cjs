'use strict';

// Contrato de fluxo no Refino (dono, 2026-10-27: "o botão de gravar aparece, some, aparece o de desfazer; e se eu só quiser
// gravar a próxima?"). Percorre antes → gravando → gravado → desfeito → gravar 2x e compara a ESTRUTURA do painel em cada estado:
// o botão principal, "Última gravação", Desfazer e "Refinar por minha conta" existem sempre, na mesma ordem, nunca hidden.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { documentMinimo } = require('./helpers/dom-minimo.cjs');

const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(ui, file), 'utf8');

function stubNode(id) {
  const attrs = {};
  return { id, textContent: '', innerHTML: '', hidden: false, dataset: {}, isConnected: true, attrs,
    addEventListener() {}, querySelector() { return null; }, querySelectorAll() { return []; },
    setAttribute(k, v) { attrs[k] = String(v); }, getAttribute(k) { return attrs[k] ?? null; },
    getBoundingClientRect() { return { width: 860, height: 300 }; } };
}

function rig({ mapa = false } = {}) {
  const mini = documentMinimo();
  const bar = mini.createElement('div');
  const nodes = new Map([['refinoActions', bar]]);
  const bySel = new Map();
  const document = {
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, stubNode(id)); return nodes.get(id); },
    querySelector(sel) { if (!bySel.has(sel)) bySel.set(sel, stubNode(null)); return bySel.get(sel); },
    querySelectorAll() { return []; },
  };
  const window = { document, console, setTimeout: () => 0 };
  window.window = window; window.globalThis = window;
  for (const file of ['core/write-gate.js', 'components/acao-principal.js', 'core/fluxo.js', 'core/map-fix.js']) vm.runInNewContext(read(file), window);
  if (mapa) window.OmegasMapEditor = { MapEditor: function MapEditor() { Object.assign(this, { reset() {}, load() {}, snapshot: () => ({ axes: { petrolBins: [2, 3], rpmBins: [850, 1350] } }), clearSelection() { this.selected = []; }, toggle(row, column) { this.selected.push({ row, column }); }, selectedCells() { return this.selected.slice(); }, setTargetOverrides(list) { this.overrides = list; }, buildReview() { return { items: this.overrides.map(o => ({ row: o.row, column: o.column, current: 120, target: o.after })) }; }, selected: [], overrides: [] }); } };
  vm.runInNewContext(read('screens/refino.js'), window);
  const hooks = new Set();
  const scheduler = { addHook: (name, fn) => { hooks.add(fn); return () => hooks.delete(fn); } };
  const flush = () => { for (const fn of [...hooks]) fn(); };
  let eq = null;
  window.OmegasUi.AutoCalApi = { available: () => true, equivalence: () => eq, projection: () => ({ ok: false }) };
  const curve = Array.from({ length: 30 }, () => 16384);
  const state = { op: null, writes: [], failNext: false };
  const native = {
    startCurveRead: () => ({ ok: true, started: true }),
    curveOperation: () => state.op || {},
    writeCurve(points, reason) { state.writes.push({ points, reason }); if (!state.failNext) points.forEach(p => { curve[p.index] = p.targetRaw; }); return { ok: true, started: true }; },
  };
  const readDone = () => ({ state: 'COMPLETED', points: curve.map((factorRaw, index) => ({ index, factorRaw })) });
  const storeState = { route: 'refino' };
  const store = { get: () => storeState, patch() {}, subscribe() { return () => {}; } };
  const screen = new window.OmegasUi.RefinoScreen({ store, scheduler, api: native });
  const manual = document.querySelector('[data-refino-manual]');
  const principal = () => bar.querySelector('[data-fluxo="principal"]');
  const ultima = () => bar.querySelector('[data-fluxo="ultima"]');
  const desfazer = () => bar.querySelector('[data-fluxo="desfazer"]');
  return { window, screen, bar, manual, principal, ultima, desfazer, flush, state, curve, native, readDone,
    setEq: v => { eq = v; screen.refresh(true); } };
}

const proposalEq = (index = 4, extra = {}) => ({ ok: true, autopilot: {
  phase: 'PROPOSTA_PRONTA', ecuDone: true, ecuOnline: true, action: { kind: 'review', label: 'Revisar e gravar 1 ponto', enabled: true },
  proposal: { known: true, count: 1, points: [{ index, currentRaw: 16384, targetRaw: 17000, referenceTimeMs: 5.5 }], held: [], signature: `${index}:16384>17000` }, ...extra,
} });
const idleEq = (extra = {}) => ({ ok: true, autopilot: { phase: 'VERIFICANDO', ecuDone: true, ecuOnline: true, action: { kind: 'none' }, proposal: { known: true, count: 0, points: [] }, ...extra } });

/** Foto da estrutura: tipos dos controles na ordem, identidade dos elementos e hidden. É isto que não pode mudar entre estados. */
function shape(r) {
  const kinds = r.bar.children.map(x => x.getAttribute('data-fluxo'));
  return { kinds, principal: r.principal(), ultima: r.ultima(), desfazer: r.desfazer(),
    hidden: [r.principal().hidden, r.ultima().hidden, r.desfazer().hidden, r.manual.hidden] };
}
function compararEstados(r, snapshots) {
  const base = snapshots[0];
  snapshots.forEach(([nome, s]) => {
    assert.equal(JSON.stringify(s.kinds), JSON.stringify(base[1].kinds), `${nome}: mesma estrutura e ordem`);
    assert.equal(s.principal, base[1].principal, `${nome}: o botão principal é o mesmo elemento`);
    assert.equal(s.ultima, base[1].ultima, `${nome}: Última gravação é o mesmo bloco`);
    assert.equal(s.desfazer, base[1].desfazer, `${nome}: Desfazer é o mesmo botão`);
    assert.equal(JSON.stringify(s.hidden), JSON.stringify([false, false, false, false]), `${nome}: nada hidden`);
  });
  assert.equal(JSON.stringify(base[1].kinds), JSON.stringify(['ultima', 'principal']), 'Última gravação fica acima do principal');
}

test('antes → gravando → gravado → desfeito → gravar 2x: nada aparece, some ou troca de lugar', async () => {
  const r = rig(); const snaps = [];
  const snap = nome => snaps.push([nome, shape(r)]);
  // ANTES: proposta pronta; Desfazer cinza ("Nenhuma gravação ainda"); "Refinar por minha conta" cinza (central já terminou).
  r.setEq(proposalEq(4)); snap('antes');
  assert.equal(r.principal().getAttribute('aria-disabled'), 'false');
  assert.equal(r.principal().textContent, 'Gravar correção');
  assert.equal(r.desfazer().getAttribute('aria-disabled'), 'true');
  assert.match(r.ultima().textContent, /Nenhuma gravação ainda/);
  assert.match(r.manual.textContent, /Refinar por minha conta · A central já terminou/);
  assert.equal(r.manual.getAttribute('aria-disabled'), 'true');
  assert.match(r.screen.curveSummary([{ currentRaw: 16384, targetRaw: 17000, referenceTimeMs: 5.5 }]), /Corrige 1 ponto/);
  assert.match(r.window.document.getElementById('refinoNext').textContent, /Ao gravar: Corrige 1 ponto em 5,5 ms/, 'antes → depois escrito no painel');
  // GRAVANDO: um toque, sem janela; o texto do principal vira "Gravando…" e o resto não se mexe.
  r.principal().click();
  assert.equal(r.screen.operation.phase, 'reading');
  r.state.op = r.readDone(); r.flush();
  assert.equal(r.state.writes.length, 1); assert.equal(r.screen.operation.phase, 'writing');
  r.state.op = { state: 'WRITING', busy: true };
  r.setEq(proposalEq(4)); r.screen.render(true); snap('gravando');
  assert.equal(r.principal().textContent, 'Gravando…'); assert.equal(r.principal().getAttribute('aria-busy'), 'true');
  // GRAVADO: só com BATCH_CONFIRMED + leitura de volta; a central ainda não propõe outra coisa.
  r.state.op = { state: 'BATCH_CONFIRMED', readbackValid: true }; r.flush();
  r.setEq(idleEq()); snap('gravado');
  assert.match(r.ultima().textContent, /Curva K · 1 ponto · Refino/);
  assert.equal(r.desfazer().getAttribute('aria-disabled'), 'false'); assert.equal(r.desfazer().textContent, 'Desfazer');
  assert.equal(r.principal().getAttribute('aria-disabled'), 'true');
  assert.match(r.principal().textContent, /Gravar correção · Nada mais para gravar agora/);
  // DESFEITO: o Desfazer grava os valores de antes; o principal continua no mesmo lugar.
  r.desfazer().click();
  r.state.op = r.readDone(); r.flush();
  assert.equal(r.state.writes.length, 2); assert.equal(JSON.stringify(r.state.writes[1].points), JSON.stringify([{ index: 4, currentRaw: 17000, targetRaw: 16384 }]));
  assert.match(r.state.writes[1].reason, /desfazer/i);
  r.state.op = { state: 'BATCH_CONFIRMED', readbackValid: true }; r.flush();
  await new Promise(resolve => setImmediate(resolve));
  r.setEq(idleEq()); snap('desfeito');
  assert.equal(r.desfazer().getAttribute('aria-disabled'), 'true'); assert.match(r.desfazer().textContent, /Já desfeito/);
  assert.equal(r.curve[4], 16384);
  // GRAVAR 2x seguidas SEM desfazer entre elas: a segunda funciona e "Última gravação" passa a descrever a segunda.
  r.setEq(proposalEq(5));
  r.principal().click(); r.state.op = r.readDone(); r.flush(); r.state.op = { state: 'BATCH_CONFIRMED', readbackValid: true }; r.flush();
  r.setEq(proposalEq(6));
  assert.equal(r.principal().getAttribute('aria-disabled'), 'false', 'a próxima já vem pronta, sem desfazer a anterior');
  r.principal().click(); r.state.op = r.readDone(); r.flush(); r.state.op = { state: 'BATCH_CONFIRMED', readbackValid: true }; r.flush();
  r.setEq(idleEq()); snap('gravar 2x');
  assert.equal(r.state.writes.length, 4);
  assert.equal(JSON.stringify(r.state.writes[3].points.map(p => p.index)), '[6]');
  const history = r.window.OmegasFluxo.history();
  assert.equal(JSON.stringify(history.map(h => h.what)), JSON.stringify(['Curva K · 1 ponto', 'Curva K · 1 ponto', 'Curva K · 1 ponto']));
  assert.equal(r.desfazer().getAttribute('aria-disabled'), 'false', 'a segunda gravação é a última e dá para desfazer');
  compararEstados(r, snaps);
});

test('sem ação: o principal fica cinza com o motivo dentro; "Refinar por minha conta" aparece ativo enquanto a central aprende', () => {
  const r = rig();
  r.setEq(proposalEq(4, { ecuDone: false, autoMatchCount: 2, maxAutomatch: 3, action: { kind: 'waiting', label: 'Aguardando', enabled: false } }));
  assert.equal(r.principal().getAttribute('aria-disabled'), 'true');
  assert.match(r.principal().textContent, /Gravar correção · Espere a central terminar de aprender/);
  assert.equal(r.manual.getAttribute('aria-disabled'), 'false');
  assert.equal(r.manual.textContent, 'Refinar por minha conta');
  assert.equal(r.principal().hidden, false);
  r.setEq({ ok: true });
  assert.match(r.principal().textContent, /Conecte a central/);
  assert.equal(r.manual.hidden, false);
});

test('toque no principal cinza não grava nada', () => {
  const r = rig();
  r.setEq(idleEq());
  r.principal().click();
  assert.equal(r.screen.operation.phase, 'idle'); assert.equal(r.state.writes.length, 0);
});

test('falha mostra o motivo, nada vira "gravado" e o MESMO botão serve para tentar de novo', () => {
  const r = rig();
  r.setEq(proposalEq(4));
  r.principal().click(); r.state.op = r.readDone(); r.flush();
  r.state.op = { state: 'FAILED', error: 'A central recusou.' }; r.flush();
  assert.equal(r.screen.operation.phase, 'failed');
  assert.match(r.window.document.getElementById('refinoHeadline').textContent, /A central recusou/);
  assert.match(r.window.document.getElementById('refinoNext').textContent, /Nada foi dado como gravado\. Toque em Gravar correção para tentar de novo/);
  assert.equal(r.principal().getAttribute('aria-disabled'), 'false');
  assert.match(r.ultima().textContent, /Nenhuma gravação ainda/);
  r.principal().click();
  assert.equal(r.screen.operation.phase, 'reading', 'tentar de novo relê a central');
});

test('Mapa K: lê e grava no MESMO toque; Desfazer fica no bloco fixo e gravar a próxima não o exige', async () => {
  const r = rig({ mapa: true });
  const log = { writes: [] };
  let writeState = { state: 'QUEUED', busy: true };
  Object.assign(r.native, {
    startMapRead: () => ({ ok: true, started: true }), mapReadResult: () => ({ ok: true, state: 'COMPLETED' }),
    previewMapAdjustment: (cells, mode, delta) => ({ ok: true, items: cells.map(c => ({ ...c, target: 120 + delta })) }),
    writeMap(items, reason) { log.writes.push({ items, reason }); return { ok: true, started: true, startedAt: log.writes.length }; },
    mapWriteOperation: () => ({ ...writeState, startedAt: log.writes.length }),
  });
  const localEq = cells => ({ ok: true, autopilot: { phase: 'ESTAVEL', ecuDone: true, ecuOnline: true, action: { kind: 'stable', enabled: false },
    secondary: [{ kind: 'local', enabled: true, label: 'Ajuste local', cells }] } });
  const cells = [{ row: 0, column: 1, deltaPercent: 5 }, { row: 1, column: 1, deltaPercent: 5 }];
  const snaps = [];
  r.setEq(localEq(cells)); snaps.push(['antes', shape(r)]);
  assert.equal(r.principal().getAttribute('aria-disabled'), 'false');
  r.principal().click();
  r.flush();
  assert.equal(r.screen.mapFix.state.phase, 'writing', 'leu o Mapa K e já gravou no mesmo toque');
  assert.equal(log.writes.length, 1);
  snaps.push(['gravando', shape(r)]);
  writeState = { state: 'BATCH_CONFIRMED', readbackValid: true, busy: false }; r.flush();
  assert.equal(r.screen.mapFix.state.phase, 'done');
  assert.match(r.ultima().textContent, /Mapa K · 2 células · Refino/);
  assert.equal(r.desfazer().getAttribute('aria-disabled'), 'false');
  snaps.push(['gravado', shape(r)]);
  // Dispensar/avançar não apaga o Desfazer (antes, "Pronto" apagava).
  r.screen.mapFix.dismiss(); r.screen.render(true);
  assert.equal(r.desfazer().getAttribute('aria-disabled'), 'false', 'dispensar não apaga o Desfazer');
  // Gravar a próxima (outra proposta) SEM desfazer.
  r.setEq(localEq([{ row: 2, column: 0, deltaPercent: 10 }]));
  r.principal().click(); r.flush(); r.flush();
  assert.equal(log.writes.length, 2);
  assert.match(r.ultima().textContent, /Mapa K · 1 célula · Refino/);
  // Desfazer pelo bloco fixo.
  writeState = { state: 'BATCH_CONFIRMED', readbackValid: true, busy: false };
  r.desfazer().click(); r.flush();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(log.writes.length, 3);
  assert.equal(JSON.stringify(log.writes[2].items.map(i => [i.row, i.column, i.target])), '[[2,0,120]]');
  assert.match(r.desfazer().textContent, /Já desfeito/);
  snaps.push(['desfeito', shape(r)]);
  compararEstados(r, snaps);
});

test('sem mexer na gravação: o código não esconde nem desabilita os controles do contrato', () => {
  const source = read('screens/refino.js');
  assert.doesNotMatch(source, /data-refino-(preview|undo|ok)\b|refinoReview|openPreview|renderPreview|node\.hidden|\.disabled\s*=/);
  assert.doesNotMatch(source, /Ver alterações|Gravar na Curva K|Gravar no Mapa K|>Pronto<|Entendi|\bconfirm\(/);
  assert.match(source, /mountPrimary\(bar, \{ id: 'refino', base: 'Gravar correção' \}\)/);
  assert.match(source, /data-fluxo="conta"[^>]*data-refino-manual/);
});
