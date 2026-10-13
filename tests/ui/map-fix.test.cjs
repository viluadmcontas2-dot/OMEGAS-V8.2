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
    writeMap(items, a, b, reason) { log.writes.push({ items, reason }); return { ok: true, started: true, startedAt: log.writes.length }; },
    mapWriteOperation: () => ({ ok: true, state: writeState, readbackValid, busy: false, startedAt: log.writes.length }),
  };
  const states = [];
  const fix = new MapFix({ native, editor, onChange: s => states.push(s.phase), env: { setTimeout: fn => { timers.push(fn); return timers.length; }, now: () => 0 } });
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
