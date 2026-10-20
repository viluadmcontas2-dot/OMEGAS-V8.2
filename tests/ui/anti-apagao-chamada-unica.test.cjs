'use strict';
// Regra: gravar o anti-apagão é UMA chamada nativa (ler -> gravar -> reler dentro de uma só etapa), como Mapa K / Curva K.
// O JS não faz mais o passo-a-passo prepare -> sondagem -> confirm -> sondagem entre a tela e a central.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { makeRig } = require('./helpers/flow-harness.cjs');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');
function proibirDoisPassos(bridge, chamadas) {
  for (const nome of ['prepareAntiStalloFlag', 'confirmAntiStalloFlag', 'prepareAntiStalloValues', 'confirmAntiStalloValues']) {
    bridge[nome] = () => { chamadas.push(nome); return JSON.stringify({ ok: false, error: 'passo-a-passo antigo não pode ser usado' }); };
  }
}

test('interruptor: um toque = UMA chamada applyAntiStalloFlag, sem prepare/confirm, grava só o bit 0x20', () => {
  const rig = makeRig(); rig.bench.plugUsb(); rig.cycle();
  const antigos = []; proibirDoisPassos(rig.bench.autocalExtra, antigos);
  const aplicar = []; const orig = rig.bench.autocalExtra.applyAntiStalloFlag;
  rig.bench.autocalExtra.applyAntiStalloFlag = e => { aplicar.push(e); return orig(e); };
  const antes = rig.flag3();
  rig.tap(); rig.cycle(5);
  assert.deepEqual(antigos, [], 'nenhum passo antigo');
  assert.equal(aplicar.length, 1, 'exatamente uma chamada nativa');
  assert.equal(rig.view().phase, 'done', JSON.stringify(rig.view()));
  assert.notDeepEqual(rig.flag3(), antes);
  assert.equal(rig.writes().length, 1, 'um único quadro de escrita no fio');
});

test('interruptor: já no estado pedido = nada enviado e aviso simples (sem erro)', () => {
  const rig = makeRig(); rig.bench.plugUsb(); rig.cycle();
  rig.tap(); rig.cycle(5);                 // liga
  const ligadoViaUndo = rig.writes().length;
  rig.flow.dismiss();
  rig.bench.ecu.clearLog && rig.bench.ecu.clearLog();
  // força o pedido "ligar" de novo apontando para um valor local desatualizado (entrada desconhecida => liga)
  rig.flow.tapSwitch({ state: 'UNKNOWN' }, true); rig.cycle(5);
  assert.equal(rig.view().phase, 'idle', JSON.stringify(rig.view()));
  assert.match(rig.view().message, /já está ligado/i);
  assert.equal(ligadoViaUndo, 1);
  assert.equal(rig.writes().length, 0, 'não reenviou');
});

test('interruptor: erro de leitura vira mensagem de leigo, não fica preso em "gravando"', () => {
  const rig = makeRig(); rig.bench.plugUsb(); rig.cycle();
  const bridge = rig.bench.autocalExtra;
  bridge.applyAntiStalloFlag = () => JSON.stringify({ ok: true, started: true });
  bridge.getAntiStalloFlagStatus = () => JSON.stringify({ state: 'DONE', outcome: 'ERROR', message: 'Não foi possível reler' });
  rig.tap(); rig.cycle(3);
  assert.equal(rig.view().phase, 'error');
  assert.doesNotMatch(rig.view().message, /ACK|0x|status|ECU/);
});

test('interruptor: a ponte antiga (só prepare/confirm) continua funcionando como reserva', () => {
  const rig = makeRig(); rig.bench.plugUsb(); rig.cycle();
  delete rig.bench.autocalExtra.applyAntiStalloFlag;
  rig.tap(); rig.cycle(5);
  assert.equal(rig.view().phase, 'done');
});

function valoresRig(mutations = []) {
  const { createBench } = require('./helpers/ecu-bench.js');
  const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/ecu-estado-lognovo.json'), 'utf8'));
  const ctx = { console, Math, JSON, Number, String, Array, Object, Set, Map }; ctx.window = ctx; ctx.globalThis = ctx;
  vm.createContext(ctx);
  for (const f of ['core/advanced-flags-data.js', 'core/advanced-flags.js', 'core/anti-apagao-valores.js']) {
    let src = fs.readFileSync(path.join(UI, f), 'utf8');
    for (const [alvo, de, para] of mutations) if (alvo === f) { assert.ok(src.includes(de), 'mutação sem alvo'); src = src.replace(de, para); }
    vm.runInContext(src, ctx, { filename: f });
  }
  const V = ctx.OmegasUi.AntiApagaoValores;
  const bench = createBench(fixture); bench.plugUsb(); bench.deliver();
  const hooks = new Set();
  const flow = V.createValuesFlow({ bridge: bench.autocalExtra, addHook: (_, fn) => { hooks.add(fn); return () => hooks.delete(fn); } });
  return { V, bench, flow, cycle(n = 1) { for (let i = 0; i < n; i += 1) { bench.deliver(); [...hooks].forEach(h => h()); } } };
}

test('cinco números: um toque em − / + = UMA chamada applyAntiStalloValues, sem prepare/confirm', () => {
  const r = valoresRig();
  const antigos = []; proibirDoisPassos(r.bench.autocalExtra, antigos);
  const aplicar = []; const orig = r.bench.autocalExtra.applyAntiStalloValues;
  r.bench.autocalExtra.applyAntiStalloValues = j => { aplicar.push(j); return orig(j); };
  assert.equal(r.flow.ask('edit', { 0: 1700 }, true), true);
  r.cycle(5);
  assert.deepEqual(antigos, []);
  assert.deepEqual(aplicar, ['{"0":1700}']);
  assert.equal(r.flow.view().phase, 'done', JSON.stringify(r.flow.view()));
  assert.equal(r.bench.params.ANTI_STALLO.values[0], 1700);
});

test('cinco números: valores iguais = nada enviado; valor fora da faixa nem chega na central', () => {
  const r = valoresRig();
  const atual = r.bench.params.ANTI_STALLO && r.bench.params.ANTI_STALLO.values ? r.bench.params.ANTI_STALLO.values[0] : 1600;
  r.flow.ask('edit', { 0: atual }, true); r.cycle(5);
  assert.equal(r.flow.view().phase, 'idle', JSON.stringify(r.flow.view()));
  const antes = r.bench.ecu.log.filter(f => f.kind !== 'READ').length;
  assert.equal(r.flow.ask('edit', { 0: 999999 }, true), false);
  assert.equal(r.bench.ecu.log.filter(f => f.kind !== 'READ').length, antes);
});

test('mutação: trocar a chamada única dos 5 números por "só preparar" faz a prova FALHAR', () => {
  const rodar = mut => {
    const r = valoresRig(mut);
    r.flow.ask('edit', { 0: 1700 }, true); r.cycle(5);
    return r.bench.params.ANTI_STALLO.values[0];
  };
  assert.equal(rodar([]), 1700);
  assert.notEqual(rodar([['core/anti-apagao-valores.js', 'bridge.applyAntiStalloValues(JSON.stringify(changes))', 'bridge.prepareAntiStalloValues(JSON.stringify(changes))']]), 1700);
});
