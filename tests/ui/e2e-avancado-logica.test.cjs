'use strict';
// E2E (lógica, SEM Chromium; roda sempre): o fluxo de toque REAL do Avançado contra a ECU emulada (estado da captura LOGNOVO),
// registrando TODOS os quadros que saem. Prova: (a) UM toque lê, confere e grava (regra sem-atrito do dono; sem 2º toque), (b) quadros exatos, (c) nenhum quadro de
// escrita sozinho em 500 ciclos de dados, falhas honestas, descarte durante a leitura sem enviar nada. Mutações no fim.
const test = require('node:test');
const assert = require('node:assert/strict');
const { makeRig } = require('./helpers/flow-harness.cjs');

const LIGAR = '35 FA 00 20 00 00 00 4F';
const DESLIGAR = '35 FA 00 00 00 00 00 2F';

function ready() { const r = makeRig(); r.bench.plugUsb(); r.cycle(); return r; }

test('sem USB não envia nada; com USB vivo e sem snapshot, o toque lê o estado real e então grava', () => {
  const r = makeRig();
  assert.equal(r.stateText(), '—');
  r.tap();
  assert.equal(r.view().phase, 'idle');
  assert.deepEqual(r.bench.ecu.log, [], 'nenhum quadro antes do USB');
  r.bench.plugUsb();
  assert.equal(r.stateText(), '—', 'USB ligado mas nenhum dado ainda: continua —');
  r.tap();
  assert.equal(r.view().phase, 'preparing', 'o toque lê FLAG_CONF3 diretamente');
  assert.deepEqual(r.writes(), [], 'a leitura não escreve');
  assert.deepEqual(r.bench.ecu.log.filter(x => x.kind === 'READ').map(x => x.hex), ['29 FA 00 23']);
  r.cycle(3);
  assert.equal(r.view().phase, 'done');
  assert.deepEqual(r.writes(), [LIGAR]);
  assert.equal(r.stateText(), 'Ligado');
});

test('SEM ATRITO: UM toque lê, confere o plano e grava o quadro exato; a ECU realmente muda', () => {
  const r = ready();
  r.bench.ecu.clearLog();
  r.tap();                       // o único toque
  assert.deepEqual(r.writes(), [], 'antes de a central responder a leitura, nada foi escrito');
  assert.deepEqual(r.flag3(), [0, 0, 0, 0], 'a ECU ainda não mudou');
  r.cycle(3);
  assert.equal(r.view().phase, 'done');
  assert.deepEqual(r.writes(), [LIGAR], 'quadro exato, uma vez');
  assert.deepEqual(r.flag3(), [0x20, 0, 0, 0], 'a ECU mudou de verdade');
  assert.match(r.view().message, /Anti-apagão ligado e conferido na central/);
  assert.equal(r.stateText(), 'Ligado', 'a leitura de volta bate com o que a tela mostra');
});

test('Desfazer em um toque: grava o quadro de desligar e a ECU volta ao original byte a byte', () => {
  const r = ready();
  const original = r.flag3();
  r.tap(); r.cycle(3);
  assert.equal(r.view().undo.available, true);
  r.bench.ecu.clearLog();
  r.undo(); r.cycle(3);
  assert.deepEqual(r.writes(), [DESLIGAR]);
  assert.deepEqual(r.flag3(), original);
  assert.equal(r.stateText(), 'Desligado');
  assert.match(r.view().message, /Anti-apagão desligado e conferido na central/);
});

for (const [nome, descarta] of [
  ['cancelar', r => r.flow.cancel()],
  ['qualquer outro toque', r => r.flow.otherTouch()],
  ['trocar de aba', r => { r.state.route = 'map'; r.observe(); }],
  ['USB caído', r => { r.bench.unplugUsb(); r.observe(); }],
]) {
  test(`${nome} durante a leitura descarta SEM enviar nada; o toque seguinte recomeça`, () => {
    const r = ready();
    r.bench.ecu.clearLog();
    r.tap();
    assert.equal(r.view().phase, 'preparing');
    descarta(r);
    assert.equal(r.view().phase, 'idle');
    r.cycle(5);
    assert.deepEqual(r.writes(), [], 'nenhuma escrita');
    assert.deepEqual(r.flag3(), [0, 0, 0, 0]);
    if (nome === 'USB caído') return;
    r.state.route = 'tools';
    r.tap(); r.cycle(3);                     // um novo toque faz o trabalho inteiro
    assert.deepEqual(r.writes(), [LIGAR]);
  });
}

test('(c) 500 ciclos de dados em repouso: nenhum quadro de escrita sai sozinho', () => {
  const r = ready();
  r.bench.ecu.clearLog();
  r.cycle(500);
  assert.deepEqual(r.writes(), []);
  const reads = r.bench.ecu.log.filter(f => f.kind === 'READ');
  assert.ok(reads.length >= 500, 'os 500 quadros de telemetria saíram como leitura');
  assert.ok(reads.every(f => ['48 01 49', '29 FA 00 23'].includes(f.hex)), 'só leituras esperadas: ' + [...new Set(reads.map(f => f.hex))]);
});

test('toque duplo/rajada muito rápido: grava UMA vez só (um quadro de escrita)', () => {
  const r = ready();
  r.bench.ecu.clearLog();
  r.tap(); r.tap(); r.tap();                  // os toques seguintes caem em "lendo/gravando" e são ignorados
  r.cycle(3);
  assert.deepEqual(r.writes(), [LIGAR]);
});

test('falhas são honestas: NAK, silêncio, ACK sem aplicar e readback diferente nunca viram "Gravado"', () => {
  const casos = [
    ['nak', /A central não aceitou/, [0, 0, 0, 0]],
    ['noReply', /A central não aceitou/, [0, 0, 0, 0]],
    ['ignoreWrite', /Não sei como ficou na central/, [0, 0, 0, 0]],
    ['corruptWrite', /Não sei como ficou na central/, [0x20, 0, 0, 1]],
  ];
  for (const [falha, texto, ecuFinal] of casos) {
    const r = ready();
    r.bench.setFault(falha);
    r.tap(); r.cycle(3);
    assert.equal(r.view().phase, 'error', falha);
    assert.match(r.view().message, texto, falha);
    assert.doesNotMatch(r.view().message, /Pronto|conferido na central/, falha);
    assert.deepEqual(r.flag3(), ecuFinal, 'estado real da ECU: ' + falha);
    assert.equal(r.view().undo.available, false, 'sem Desfazer quando não confirmou: ' + falha);
  }
});

test('USB cai com a gravação em andamento: erro honesto, nada dado como gravado', () => {
  const r = makeRig({ bench: { hold: true } });
  r.bench.plugUsb(); r.cycle();
  r.tap(); r.cycle(1);
  assert.equal(r.view().phase, 'writing');
  assert.deepEqual(r.writes(), [LIGAR], 'o quadro saiu; a resposta ainda não chegou');
  r.bench.unplugUsb(); r.observe();
  assert.equal(r.view().phase, 'error');
  assert.match(r.view().message, /cabo soltou/);
  assert.doesNotMatch(r.view().message, /Gravado e conferido/);
});

test('mudança na ECU entre a leitura e a gravação: nada é gravado', () => {
  const r = ready();
  r.tap();
  r.bench.seed('29 FA 00 23', [0x01, 0, 0, 0]);   // outro programa mexeu
  r.bench.ecu.clearLog();
  r.cycle(3);
  assert.equal(r.view().phase, 'error');
  assert.deepEqual(r.writes(), []);
});

// ---------------------------------------------------------------- mutações: o teste tem que FALHAR
const F = 'core/advanced-flags.js';
const VOLTA_2_TOQUES = [[F, "s.plan = plan;\n      s.token = status.token;\n      confirm();", "s.plan = plan;\n      s.token = status.token;\n      set('preview', '');"]];
function umToqueGrava(r) {
  r.bench.ecu.clearLog();
  r.tap(); r.cycle(3);
  assert.deepEqual(r.writes(), [LIGAR]);
}
test('mutação: voltar a exigir o 2º toque faz a prova "um toque grava" FALHAR', () => {
  const ok = makeRig(); ok.bench.plugUsb(); ok.cycle();
  umToqueGrava(ok);                       // íntegro: passa
  const mut = makeRig({ mutations: VOLTA_2_TOQUES });
  mut.bench.plugUsb(); mut.cycle();
  assert.throws(() => umToqueGrava(mut), /deepEqual|Expected values/i);
  assert.deepEqual(mut.writes(), [], 'o mutante não grava no 1º toque (pede o 2º)');
});

test('mutação: máscara errada muda o quadro e o teste de quadro exato falha', () => {
  const mut = makeRig({ mutations: [['core/advanced-flags.js', 'const ANTI_MASK = 0x0020;', 'const ANTI_MASK = 0x0040;']] });
  mut.bench.plugUsb(); mut.cycle();
  mut.tap(); mut.cycle(3);
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
    flow.tapSwitch(r.entry(), true); r.cycle(3);
    return flow.view().phase;
  };
  assert.equal(run([]), 'error', 'íntegro: a tela confere a leitura de volta por conta própria');
  assert.equal(run([[F, 'const ok = words[0] === plan.after[0] && words[1] === plan.after[1];', 'const ok = true;']]), 'done', 'mutante: aceitou o divergente');
});

test('mutação: trocar de aba não desarma a leitura -> o teste de descarte falha', () => {
  const mut = makeRig({ mutations: [[F, "if (away || noUsb) cancel();", "if (noUsb) cancel();"]] });
  mut.bench.plugUsb(); mut.cycle(); mut.tap();
  mut.state.route = 'map'; mut.observe();
  assert.equal(mut.view().phase, 'preparing', 'mutante mantém a leitura ao sair da aba (o íntegro volta a idle)');
  const ok = makeRig(); ok.bench.plugUsb(); ok.cycle(); ok.tap(); ok.state.route = 'map'; ok.observe();
  assert.equal(ok.view().phase, 'idle');
});
