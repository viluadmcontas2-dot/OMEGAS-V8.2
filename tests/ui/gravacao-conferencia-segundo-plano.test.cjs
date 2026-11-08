'use strict';
// Dono, 2026-10-19: gravar é INSTANTÂNEO (ACK = Gravado); a leitura de volta roda em segundo plano, corrige sozinha uma vez
// e só avisa na barra do topo se falhar. Sem relógio: cada passo anda por resposta (poll = resposta da ECU).
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { createBench, H, pedido } = require('./helpers/ecu-bench.js');
const { Tracker } = require('../../app/src/main/assets/ui/core/write-verify.js');
const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/ecu-estado-lognovo.json'), 'utf8'));

function setup() {
  const bench = createBench(fixture); bench.plugUsb();
  const j = s => JSON.parse(s);
  const states = [];
  const tracker = new Tracker(() => j(bench.v7.getWriteVerify()), s => states.push(s.phase));
  return { bench, j, tracker, states };
}
test('Mapa K: Gravado na hora, conferindo discreto, vira "Gravado e conferido" ao confirmar (sem ler de volta antes do ACK)', () => {
  const { bench, j, tracker, states } = setup();
  const cur = readCell(bench, 3, 4);
  const reads0 = bench.ecu.sent('READ').length;
  const started = j(bench.v7.startMapBatchWrite(JSON.stringify([{ row: 3, column: 4, current: cur, target: cur + 5 }])));
  assert.equal(started.started, true);
  const op = j(bench.v7.getLastOperation());
  assert.equal(op.state, 'BATCH_CONFIRMED'); assert.equal(op.acked, true);          // Gravado = ACK
  assert.equal(bench.ecu.sent('READ').length - reads0, 13, 'só a leitura prévia do mapa (13 linhas); nada de readback bloqueante');
  tracker.begin();
  assert.equal(tracker.state.phase, 'checking');                                    // indicador discreto
  tracker.tick();
  assert.equal(tracker.state.phase, 'ok');                                          // confirmou: vira o selo "Gravado e conferido" (I1)
  assert.equal(tracker.state.label, 'Gravado e conferido');
  assert.deepEqual(states, ['checking', 'ok']);
});

test('Mapa K: gravação ignorada uma vez -> autocorreção só da célula divergente -> confirma e some', () => {
  const { bench, j, tracker, states } = setup();
  const cur = readCell(bench, 2, 2);
  bench.ecu.faults.ignoreWrite = true;
  bench.v7.startMapBatchWrite(JSON.stringify([{ row: 2, column: 2, current: cur, target: cur + 3 }]));
  bench.ecu.faults.ignoreWrite = false;                                             // falha transitória
  tracker.begin(); assert.equal(tracker.state.phase, 'checking'); tracker.tick(); assert.equal(tracker.state.phase, 'fixing');
  assert.match(tracker.state.detail, /Gravação diferente na célula L3 C3 — corrigindo/);
  const writes = bench.ecu.sent('WRITE').length;
  tracker.tick(); assert.equal(tracker.state.phase, 'ok');
  assert.equal(bench.ecu.sent('WRITE').length, writes, 'a correção já regravou uma vez só');
  assert.equal(readCell(bench, 2, 2), cur + 3);
});

test('Mapa K: falha persistente -> 1 correção -> aviso âmbar "não confere" que fica (nada de modal)', () => {
  const { bench, j, tracker, states } = setup();
  const cur = readCell(bench, 1, 1);
  bench.ecu.faults.corruptWrite = true;
  bench.v7.startMapBatchWrite(JSON.stringify([{ row: 1, column: 1, current: cur, target: cur + 2 }]));
  tracker.begin(); assert.equal(tracker.state.phase, 'checking'); tracker.tick(); assert.equal(tracker.state.phase, 'fixing');
  tracker.tick(); assert.equal(tracker.state.phase, 'failed');
  assert.equal(tracker.state.label, 'não confere');
  assert.match(tracker.state.detail, /Gravação diferente na célula L2 C2/);
  const w = bench.ecu.sent('WRITE').length; tracker.tick();
  assert.equal(bench.ecu.sent('WRITE').length, w, 'não regrava em laço');
  assert.equal(tracker.state.phase, 'failed');
  // O Refino concluiu ACK + readback válido: limpa o aviso sticky antigo sem novo poll.
  tracker.resolve();
  assert.equal(tracker.state.phase, 'idle');
  assert.equal(tracker.sticky, false);
  assert.equal(tracker.armed, false);
});

test('Curva K: Gravado na hora; divergência corrige e confere', () => {
  const { bench, j, tracker } = setup();
  const curve = j(bench.v7.startCurveRead()); assert.equal(curve.ok, true);
  const op = bench.curveOp; const pt = op.points[5];
  bench.ecu.faults.ignoreWrite = true;
  bench.v7.startCurveBatchWrite(JSON.stringify([{ index: 5, currentRaw: pt.factorRaw, targetRaw: pt.factorRaw + 100 }]));
  bench.ecu.faults.ignoreWrite = false;
  assert.equal(j(bench.v7.getLastOperation()).acked, true);
  tracker.begin(); assert.equal(tracker.state.phase, 'checking'); tracker.tick(); assert.equal(tracker.state.phase, 'fixing');
  tracker.tick(); assert.equal(tracker.state.phase, 'ok');
});

function readCell(bench, row, column) {
  return bench.ecu.get(H(pedido(0x2A, 0x54, 0x00, row)))[column];
}
