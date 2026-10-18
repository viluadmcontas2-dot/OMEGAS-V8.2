'use strict';
// E2E (lógica, SEM Chromium; roda sempre): o fluxo de toque REAL do Avançado contra a ECU emulada (estado da captura LOGNOVO),
// registrando TODOS os quadros que saem. Prova: (a) nenhuma escrita sem o 2º toque, (b) quadros exatos, (c) nenhum quadro de
// escrita sozinho em 500 ciclos de dados, falhas honestas, descarte da prévia sem enviar nada. Mutações no fim.
const test = require('node:test');
const assert = require('node:assert/strict');
const { makeRig } = require('./helpers/flow-harness.cjs');

const LIGAR = '35 FA 00 20 00 00 00 4F';
const DESLIGAR = '35 FA 00 00 00 00 00 2F';

function ready() { const r = makeRig(); r.bench.plugUsb(); r.cycle(); return r; }

test('antes da leitura o estado é "—" e o toque não faz nada; depois do dado chegar vira "Desligado"', () => {
  const r = makeRig();
  assert.equal(r.stateText(), '—');
  r.tap();
  assert.equal(r.view().phase, 'idle');
  assert.deepEqual(r.bench.ecu.log, [], 'nenhum quadro antes do USB');
  r.bench.plugUsb();
  assert.equal(r.stateText(), '—', 'USB ligado mas nenhum dado ainda: continua —');
  r.tap();
  assert.deepEqual(r.bench.ecu.log, [], 'sem dado válido o toque não envia nada');
  r.cycle();
  assert.equal(r.stateText(), 'Desligado');
  assert.deepEqual(r.flag3(), [0, 0, 0, 0]);
});

test('toque 1 só mostra a prévia (antes→depois) e NÃO escreve; toque 2 grava o quadro exato e a ECU realmente muda', () => {
  const r = ready();
  r.bench.ecu.clearLog();
  r.tap();                       // 1º toque
  r.cycle();
  assert.equal(r.view().phase, 'preview');
  assert.match(r.view().preview.text, /Antes 0000 0000 → depois 0020 0000/);
  assert.deepEqual(r.writes(), [], '(a) nada de escrita depois do 1º toque');
  assert.deepEqual(r.flag3(), [0, 0, 0, 0], 'a ECU ainda não mudou');
  r.tap();                       // 2º toque
  r.cycle();
  assert.equal(r.view().phase, 'done');
  assert.deepEqual(r.writes(), [LIGAR], '(b) quadro exato');
  assert.deepEqual(r.flag3(), [0x20, 0, 0, 0], 'a ECU mudou de verdade');
  assert.match(r.view().message, /ECU confirmou: ligado/);
  assert.equal(r.stateText(), 'Ligado', 'a leitura de volta bate com o que a tela mostra');
});

test('Desfazer: prévia e 2º toque; grava o quadro de desligar e a ECU volta ao original byte a byte', () => {
  const r = ready();
  const original = r.flag3();
  r.tap(); r.cycle(); r.tap(); r.cycle();
  assert.equal(r.view().undo.available, true);
  r.bench.ecu.clearLog();
  r.undo(); r.cycle();
  assert.equal(r.view().phase, 'preview');
  assert.deepEqual(r.writes(), [], 'o 1º toque do Desfazer não escreve');
  r.undo(); r.cycle();
  assert.deepEqual(r.writes(), [DESLIGAR]);
  assert.deepEqual(r.flag3(), original);
  assert.equal(r.stateText(), 'Desligado');
  assert.match(r.view().message, /ECU confirmou: desligado/);
});

for (const [nome, descarta] of [
  ['cancelar', r => r.flow.cancel()],
  ['qualquer outro toque', r => r.flow.otherTouch()],
  ['trocar de aba', r => { r.state.route = 'map'; r.observe(); }],
  ['USB caído', r => { r.bench.unplugUsb(); r.observe(); }],
]) {
  test(`${nome} descarta a prévia SEM enviar nada; o 2º toque seguinte recomeça do 1º`, () => {
    const r = ready();
    r.bench.ecu.clearLog();
    r.tap(); r.cycle();
    assert.equal(r.view().phase, 'preview');
    descarta(r);
    assert.equal(r.view().phase, 'idle');
    assert.equal(r.view().preview, null);
    r.cycle(5);
    assert.deepEqual(r.writes(), [], 'nenhuma escrita');
    assert.deepEqual(r.flag3(), [0, 0, 0, 0]);
    if (nome === 'USB caído') return;
    r.state.route = 'tools';
    r.tap();                                  // um único toque depois do descarte: só prévia de novo
    r.cycle();
    assert.equal(r.view().phase, 'preview');
    assert.deepEqual(r.writes(), []);
  });
}

test('(c) 500 ciclos de dados, em repouso e com a prévia armada: nenhum quadro de escrita sai sozinho', () => {
  const r = ready();
  r.bench.ecu.clearLog();
  r.cycle(250);
  r.tap(); r.cycle();                         // prévia armada
  assert.equal(r.view().phase, 'preview');
  r.cycle(250);
  assert.deepEqual(r.writes(), []);
  const reads = r.bench.ecu.log.filter(f => f.kind === 'READ');
  assert.ok(reads.length >= 500, 'os 500 quadros de telemetria saíram como leitura');
  assert.ok(reads.every(f => ['48 01 49', '29 FA 00 23'].includes(f.hex)), 'só leituras esperadas: ' + [...new Set(reads.map(f => f.hex))]);
  assert.equal(r.view().phase, 'preview', 'a prévia continua aguardando o dono (sem relógio)');
});

test('toque duplo muito rápido: o 2º toque grava UMA vez só (um quadro de escrita)', () => {
  const r = ready();
  r.bench.ecu.clearLog();
  r.tap(); r.tap();                           // 2 toques antes de qualquer dado: o 2º cai em "preparando" e é ignorado
  assert.deepEqual(r.writes(), []);
  r.cycle();
  assert.equal(r.view().phase, 'preview');
  r.tap(); r.tap(); r.tap();                  // rajada no 2º toque
  r.cycle(3);
  assert.deepEqual(r.writes(), [LIGAR]);
});

test('falhas são honestas: NAK, silêncio, ACK sem aplicar e readback diferente nunca viram "Gravado"', () => {
  const casos = [
    ['nak', /não confirmou/, [0, 0, 0, 0]],
    ['noReply', /não confirmou/, [0, 0, 0, 0]],
    ['ignoreWrite', /leitura de volta é diferente/, [0, 0, 0, 0]],
    ['corruptWrite', /leitura de volta é diferente/, [0x20, 0, 0, 1]],
  ];
  for (const [falha, texto, ecuFinal] of casos) {
    const r = ready();
    r.tap(); r.cycle();
    r.bench.setFault(falha);
    r.tap(); r.cycle();
    assert.equal(r.view().phase, 'error', falha);
    assert.match(r.view().message, texto, falha);
    assert.doesNotMatch(r.view().message, /Gravado e conferido|ECU confirmou: ligado/, falha);
    assert.deepEqual(r.flag3(), ecuFinal, 'estado real da ECU: ' + falha);
    assert.equal(r.view().undo.available, false, 'sem Desfazer quando não confirmou: ' + falha);
  }
});

test('USB cai com a gravação em andamento: erro honesto, nada dado como gravado', () => {
  const r = makeRig({ bench: { hold: true } });
  r.bench.plugUsb(); r.cycle();
  r.tap(); r.cycle(); r.tap();
  assert.equal(r.view().phase, 'writing');
  assert.deepEqual(r.writes(), [LIGAR], 'o quadro saiu no 2º toque; a resposta ainda não chegou');
  r.bench.unplugUsb(); r.observe();
  assert.equal(r.view().phase, 'error');
  assert.match(r.view().message, /USB caiu/);
  assert.doesNotMatch(r.view().message, /Gravado e conferido/);
});

test('mudança na ECU entre a prévia e o 2º toque: nada é gravado', () => {
  const r = ready();
  r.tap(); r.cycle();
  r.bench.seed('29 FA 00 23', [0x01, 0, 0, 0]);   // outro programa mexeu
  r.bench.ecu.clearLog();
  r.tap(); r.cycle();
  assert.equal(r.view().phase, 'error');
  assert.deepEqual(r.writes(), []);
});

// ---------------------------------------------------------------- mutações: o teste tem que FALHAR
function semSegundoToque(r) {            // cenário (a) como função reutilizável
  r.bench.ecu.clearLog();
  r.tap(); r.cycle(3);
  assert.deepEqual(r.writes(), []);
}
const F = 'core/advanced-flags.js';
const MUTANTES = {
  'confirmação automática (sem toque duplo)': [[F, "s.plan = plan;\n      s.token = status.token;\n      set('preview', '');", "s.plan = plan;\n      s.token = status.token;\n      set('preview', '');\n      confirm();"]],
};
test('mutação: remover a exigência do toque duplo faz o cenário (a) FALHAR', () => {
  const ok = makeRig(); ok.bench.plugUsb(); ok.cycle();
  semSegundoToque(ok);                    // íntegro: passa
  const mut = makeRig({ mutations: MUTANTES['confirmação automática (sem toque duplo)'] });
  mut.bench.plugUsb(); mut.cycle();
  assert.throws(() => semSegundoToque(mut), /Expected values to be strictly deep-equal|deepEqual/i);
  assert.deepEqual(mut.writes(), [LIGAR], 'o mutante gravou sozinho, como esperado');
});

test('mutação: máscara errada muda o quadro e o teste de quadro exato falha', () => {
  const mut = makeRig({ mutations: [['core/advanced-flags.js', 'const ANTI_MASK = 0x0020;', 'const ANTI_MASK = 0x0040;']] });
  mut.bench.plugUsb(); mut.cycle();
  mut.tap(); mut.cycle(); mut.tap(); mut.cycle();
  assert.notDeepEqual(mut.writes(), [LIGAR]);
});

test('mutação: readback fraco na TELA (2ª barreira) deixaria "Gravado" quando a ponte mente CONFIRMED com leitura divergente', () => {
  const run = mutations => {
    const r = makeRig({ mutations });
    const real = r.bench.autocalExtra.getAntiStalloFlagStatus;       // ponte que mente: CONFIRMED mas com a leitura de volta errada
    const bridge = { ...r.bench.autocalExtra, getAntiStalloFlagStatus: () => {
      const s = JSON.parse(real());
      return JSON.stringify(s.state === 'DONE' ? { ...s, outcome: 'CONFIRMED', readback: [0x20, 1] } : s);
    } };
    const flow = r.A.createWriteFlow({ bridge, addHook: (_, fn) => { r.hooks.add(fn); return () => r.hooks.delete(fn); }, onChange: () => {} });
    r.bench.plugUsb(); r.cycle();
    flow.tapSwitch(r.entry(), true); r.cycle(); flow.tapSwitch(r.entry(), true); r.cycle();
    return flow.view().phase;
  };
  assert.equal(run([]), 'error', 'íntegro: a tela confere a leitura de volta por conta própria');
  assert.equal(run([[F, 'const ok = words[0] === plan.after[0] && words[1] === plan.after[1];', 'const ok = true;']]), 'done', 'mutante: aceitou o divergente');
});

test('mutação: trocar de aba não desarma a prévia -> o teste de descarte falha', () => {
  const mut = makeRig({ mutations: [[F, "if (away || noUsb) cancel();", "if (noUsb) cancel();"]] });
  mut.bench.plugUsb(); mut.cycle(); mut.tap(); mut.cycle();
  mut.state.route = 'map'; mut.observe();
  assert.equal(mut.view().phase, 'preview', 'mutante mantém a prévia ao sair da aba (o íntegro volta a idle)');
  const ok = makeRig(); ok.bench.plugUsb(); ok.cycle(); ok.tap(); ok.cycle(); ok.state.route = 'map'; ok.observe();
  assert.equal(ok.view().phase, 'idle');
});
