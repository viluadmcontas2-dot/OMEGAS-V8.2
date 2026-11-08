'use strict';

// Correção no Mapa K sem sair do Refino (core/map-fix.js): nada grava sem o toque; "gravado" só com BATCH_CONFIRMED + readback;
// o desfazer grava os valores de antes; falhas nunca viram "gravado".
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { MapFix } = require(path.resolve(__dirname, '../../app/src/main/assets/ui/core/map-fix.js'));

function rig({ writeState = 'BATCH_CONFIRMED', readbackValid = true, previewOk = true } = {}) {
  const timers = [];
  const log = { writes: [], previews: [] };
  const editor = {
    selected: [], overrides: [],
    reset() { this.selected = []; }, load() {}, snapshot: () => ({ axes: { petrolBins: [2, 3], rpmBins: [850, 1350] } }),
    clearSelection() { this.selected = []; }, toggle(row, column) { this.selected.push({ row, column }); },
    selectedCells() { return this.selected.slice(); }, setTargetOverrides(list) { this.overrides = list; },
    buildReview() { return { items: this.overrides.map(o => ({ row: o.row, column: o.column, current: 120, target: o.after })) }; },
  };
  const native = {
    startMapRead: () => ({ ok: true, started: true }), mapReadResult: () => ({ ok: true, state: 'COMPLETED' }),
    previewMapAdjustment(cells, mode, delta) { log.previews.push({ cells, mode, delta }); return previewOk ? { ok: true, items: cells.map(c => ({ ...c, target: 120 + delta })) } : { ok: false, error: 'sem prévia' }; },
    writeMap(items, reason) { log.writes.push({ items, reason }); return { ok: true, started: true, startedAt: log.writes.length }; },
    mapWriteOperation: () => ({ ok: true, state: writeState, readbackValid, busy: false, startedAt: log.writes.length }),
  };
  const states = [];
  const fix = new MapFix({ native, editor, onChange: s => states.push(s.phase), env: { subscribe: fn => { timers.push(fn); return () => { const i = timers.indexOf(fn); if (i >= 0) timers.splice(i, 1); }; } } });
  const flush = () => { while (timers.length) timers.shift()(); };
  return { fix, native, log, states, flush };
}
const plan = { ok: true, cells: [{ row: 0, column: 1, deltaPercent: 5 }, { row: 1, column: 1, deltaPercent: 5 }, { row: 2, column: 0, deltaPercent: 10 }], summary: n => `${n} células` };

test('ler → prévia por percentual → resumo; NADA é gravado antes do toque', () => {
  const { fix, log, states, flush } = rig();
  fix.start(() => plan);
  flush();
  assert.deepEqual(states, ['reading', 'ready']);
  assert.equal(log.writes.length, 0, 'sem toque em Gravar, nada vai para a ECU');
  assert.equal(log.previews.length, 2, 'um pedido de prévia por percentual (5% e 10%)');
  assert.deepEqual(log.previews.map(p => p.delta).sort((a, b) => a - b), [5, 10]);
  assert.equal(fix.state.summary, '3 células');
  assert.equal(fix.state.items.length, 3);
});

test('Gravar → só "feito" com BATCH_CONFIRMED + readbackValid; Desfazer grava os valores de antes', () => {
  const { fix, log, states, flush } = rig();
  fix.start(() => plan); flush();
  fix.write('apply'); flush();
  assert.equal(fix.state.phase, 'done');
  assert.equal(log.writes.length, 1);
  assert.equal(log.writes[0].items.length, 3);
  fix.undo(); flush();
  assert.equal(fix.state.phase, 'undone');
  assert.equal(log.writes.length, 2);
  assert.deepEqual(log.writes[1].items.map(i => [i.row, i.column, i.target]), log.writes[0].items.map(i => [i.row, i.column, i.current]));
  assert.ok(states.includes('writing'));
});

test('readback diferente ou lote não confirmado = falha, nunca "gravado"', () => {
  for (const [writeState, readbackValid] of [['BATCH_CONFIRMED', false], ['FAILED', true]]) {
    const { fix, flush } = rig({ writeState, readbackValid });
    fix.start(() => plan); flush();
    fix.write('apply'); flush();
    assert.equal(fix.state.phase, 'failed', `${writeState}/${readbackValid}`);
    assert.match(fix.state.message, /não confirmou|Falha|FAILED|Confira/i);
  }
});

test('sem proposta, sem prévia ou sem leitura: volta sem gravar e diz por quê', () => {
  let r = rig(); r.fix.start(() => ({ ok: false, reason: 'Nada a corrigir.' })); r.flush();
  assert.equal(r.fix.state.phase, 'idle'); assert.equal(r.fix.state.note, 'Nada a corrigir.');
  r = rig({ previewOk: false }); r.fix.start(() => plan); r.flush();
  assert.equal(r.fix.state.phase, 'failed'); assert.equal(r.log.writes.length, 0);
  r = rig(); r.native.startMapRead = () => ({ ok: false, error: 'ECU offline' }); r.fix.start(() => plan);
  assert.equal(r.fix.state.phase, 'failed'); assert.match(r.fix.state.message, /ECU offline/);
});

test('Cancelar descarta a proposta sem gravar; durante a gravação não cancela nem repete', () => {
  const { fix, log, flush } = rig();
  fix.start(() => plan); flush();
  fix.dismiss();
  assert.equal(fix.state.phase, 'idle');
  assert.equal(log.writes.length, 0);
  fix.start(() => plan); flush();
  fix.write('apply');
  assert.equal(fix.state.phase, 'writing');
  fix.write('apply'); fix.dismiss(); fix.start(() => plan);
  assert.equal(log.writes.length, 1, 'toque repetido não grava de novo');
  assert.equal(fix.state.phase, 'writing');
});

// ---- Fluxo (dono, 2026-10-27): a última gravação vira `last` persistente; dispensar não apaga o Desfazer; gravar a próxima não o exige.
test('last sobrevive a dismiss() e a uma nova leitura; Desfazer funciona depois e devolve true só com leitura de volta igual', async () => {
  const { fix, log, flush } = rig();
  const written = [];
  fix.onWritten = e => written.push(e.kind);
  fix.start(() => plan); flush();
  fix.write('apply'); flush();
  assert.equal(fix.last.count, 3); assert.equal(fix.canUndo, true);
  fix.dismiss();
  assert.equal(fix.state.phase, 'idle');
  assert.equal(fix.canUndo, true, 'dispensar não apaga o Desfazer');
  fix.start(() => plan); flush();
  assert.equal(fix.canUndo, true, 'nova leitura não apaga o Desfazer');
  const undone = fix.undo(); flush();
  assert.equal(await undone, true);
  assert.equal(fix.state.phase, 'undone'); assert.equal(fix.canUndo, false, 'já desfeito não desfaz de novo');
  assert.equal(log.writes.length, 2);
  assert.deepEqual(log.writes[1].items.map(i => i.target), log.writes[0].items.map(i => i.current));
  assert.deepEqual(written, ['apply', 'undo']);
});

test('gravar a próxima NÃO exige desfazer: a segunda gravação substitui o last', async () => {
  const { fix, log, flush } = rig();
  fix.start(() => plan); flush(); fix.write('apply'); flush();
  const first = fix.last;
  fix.start(() => ({ ...plan, cells: plan.cells.slice(0, 1) })); flush();
  fix.write('apply'); flush();
  assert.equal(log.writes.length, 2);
  assert.notEqual(fix.last, first); assert.equal(fix.last.count, 1);
  assert.equal(fix.canUndo, true);
});

test('Desfazer que falha devolve false e mantém o last para tentar de novo', async () => {
  const { fix, native, flush } = rig();
  fix.start(() => plan); flush(); fix.write('apply'); flush();
  native.mapWriteOperation = () => ({ ok: true, state: 'FAILED', busy: false, startedAt: 2 });
  const undone = fix.undo(); flush();
  assert.equal(await undone, false);
  assert.equal(fix.last.undone, false);
});

test('last é espelhado em OmegasFluxo.pending e volta numa instância nova (provisório até o Diário)', () => {
  const store = new Map();
  globalThis.OmegasFluxo = { pending: { save: (k, v) => (store.set(k, JSON.parse(JSON.stringify(v))), true), get: k => store.get(k) || null, clear: k => store.delete(k) } };
  try {
    const a = rig(); a.fix.start(() => plan); a.flush(); a.fix.write('apply'); a.flush();
    const b = rig();
    assert.equal(b.fix.canUndo, true, 'reabrir a tela mantém o Desfazer');
    assert.equal(b.fix.last.count, 3);
    const undone = a.fix.undo(); a.flush();
    return undone.then(() => assert.equal(rig().fix.canUndo, false, 'desfeito não volta'));
  } finally { delete globalThis.OmegasFluxo; }
});
