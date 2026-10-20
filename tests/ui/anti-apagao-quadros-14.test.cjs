'use strict';
// Anti-apagão, os 5 números, no formato do ORIGINAL (docs/progbase/protocolo/anti-apagao-quadros-original.md):
// leitura `29 33 01 5D` e UM `14 33 01 <índice> <u16 LE> <soma>` por campo alterado, cada um com ACK (53 00 53), depois a leitura
// de volta. Aqui: o plano do JS descreve os mesmos quadros do Kotlin; a bancada fala esses quadros; e a tela diz a verdade em
// cada desfecho (sucesso, parcial, leitura diferente, leitura que falhou, central desconectada), sem janela e sem 2º toque.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');
const F = [1600, 100, 1500, 2, 20];
const same = (a, b, m) => assert.equal(JSON.stringify(a), JSON.stringify(b), m);

function carregar(arquivos, mutacoes = []) {
  const ctx = { console, Math, JSON, Number, String, Array, Object, Set, Map, Intl, Proxy };
  ctx.window = ctx; ctx.globalThis = ctx; vm.createContext(ctx);
  for (const f of arquivos) {
    let src = fs.readFileSync(path.join(UI, f), 'utf8');
    for (const [alvo, de, para] of mutacoes) if (alvo === f) { assert.ok(src.includes(de), 'mutação sem alvo: ' + de); src = src.replace(de, para); }
    vm.runInContext(src, ctx, { filename: f });
  }
  return ctx;
}
const V = carregar(['core/anti-apagao-valores.js']).OmegasUi.AntiApagaoValores;

test('plano: um quadro 14 33 01 <índice> lo hi soma por campo ALTERADO (tabela do original)', () => {
  same(V.plan(F, { 0: 1800 }).frames.map(V.hex), ['14 33 01 00 08 07 57']);
  same(V.plan(F, { 0: 1601 }).frames.map(V.hex), ['14 33 01 00 41 06 8F']);
  assert.equal(V.plan(F, { 1: 150 }).frameHex, '14 33 01 01 96 00 DF');
  assert.equal(V.plan(F, { 2: 1000 }).frameHex, '14 33 01 02 E8 03 35');
  assert.equal(V.plan(F, { 3: 5 }).frameHex, '14 33 01 03 05 00 50');
  assert.equal(V.plan(F, { 4: 30 }).frameHex, '14 33 01 04 1E 00 6A');
  // vários campos: em ordem de índice, unidos por " · " (igual ao status do Kotlin)
  assert.equal(V.plan(F, { 4: 30, 1: 150 }).frameHex, '14 33 01 01 96 00 DF · 14 33 01 04 1E 00 6A');
  // campo pedido igual ao lido não sai (o Kotlin só manda os alterados)
  const igual = V.plan(F, { 0: 1600, 3: 5 });
  same(igual.changed, [3]); assert.equal(igual.frameHex, '14 33 01 03 05 00 50');
  const nada = V.plan(F, { 0: 1600 });
  assert.equal(nada.noop, true); same(nada.frames, []); assert.equal(nada.frameHex, '');
  assert.doesNotMatch(fs.readFileSync(path.join(UI, 'core/anti-apagao-valores.js'), 'utf8'), /0x37|37 33 0C/, 'o quadro antigo saiu');
});

// ---------------------------------------------------------------- bancada (ponte + central em JS)
function rig(mutacoes = []) {
  const ctx = carregar(['core/advanced-flags-data.js', 'core/advanced-flags.js', 'core/anti-apagao-valores.js'], mutacoes);
  const Vx = ctx.OmegasUi.AntiApagaoValores;
  const { createBench } = require('./helpers/ecu-bench.js');
  const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/ecu-estado-lognovo.json'), 'utf8'));
  const bench = createBench(fixture); bench.plugUsb(); bench.deliver();
  const hooks = new Set();
  const flow = Vx.createValuesFlow({ bridge: bench.autocalExtra, addHook: (_, fn) => { hooks.add(fn); return () => hooks.delete(fn); } });
  const anti5 = () => { const d = bench.ecu.get('29 33 01 5D'); const out = []; for (let i = 0; i < d.length; i += 2) out.push(d[i] | (d[i + 1] << 8)); return out; };
  return {
    bench, flow, anti5,
    cycle(n = 6) { for (let i = 0; i < n; i += 1) { bench.deliver(); [...hooks].forEach(h => h()); } },
    fio() { return bench.ecu.log.filter(f => f.hex.startsWith('29 33 01') || f.kind === 'WRITE').map(f => f.hex); },
    escritas() { return bench.ecu.log.filter(f => f.kind === 'WRITE').map(f => f.hex); },
  };
}

test('bancada, sucesso: lê 29 33 01 5D, manda UM 14 por campo alterado (ACK), relê e confere', () => {
  const r = rig(); r.bench.ecu.clearLog();
  r.flow.ask('edit', { 1: 150, 4: 30 }, true); r.cycle();
  same(r.fio(), ['29 33 01 5D', '29 33 01 5D', '14 33 01 01 96 00 DF', '14 33 01 04 1E 00 6A', '29 33 01 5D']);
  const st = JSON.parse(r.bench.autocalExtra.getAntiStalloValuesStatus());
  assert.equal(st.outcome, 'CONFIRMED');
  same(st.frames, ['14 33 01 01 96 00 DF', '14 33 01 04 1E 00 6A']); same(st.written, [1, 4]);
  assert.equal(st.frameHex, '14 33 01 01 96 00 DF · 14 33 01 04 1E 00 6A');
  same(r.anti5(), [1600, 150, 1500, 2, 30]);
  assert.equal(r.flow.view().phase, 'done');
});

test('bancada, PARCIAL: o 2º quadro sem ACK = "Gravei só uma parte (1 de 2)", Desfazer à vista, nunca "Gravado"', () => {
  const r = rig(); r.bench.ecu.clearLog();
  r.bench.setFault('nakAfter', 1);               // a central aceita o 1º e recusa o 2º
  r.flow.ask('edit', { 1: 150, 4: 30 }, true); r.cycle();
  same(r.escritas(), ['14 33 01 01 96 00 DF', '14 33 01 04 1E 00 6A'], 'o 2º saiu e foi recusado; nenhum 3º');
  const st = JSON.parse(r.bench.autocalExtra.getAntiStalloValuesStatus());
  assert.equal(st.outcome, 'PARTIAL'); same(st.written, [1]);
  same(r.anti5(), [1600, 150, 1500, 2, 20]);
  const v = r.flow.view();
  assert.equal(v.phase, 'partial');
  assert.equal(v.message, 'Gravei só uma parte (1 de 2). Toque em Desfazer para voltar ao que era antes.');
  assert.doesNotMatch(v.message, /Gravado|conferem|Pronto/);
  same(v.undo, { available: true, values: F });
  // Desfazer (o mesmo caminho do toque): só o campo que chegou a ser gravado volta, um quadro só.
  r.bench.setFault('nakAfter', null); r.bench.ecu.clearLog();
  const atual = r.anti5();
  const diff = {}; F.forEach((x, i) => { if (x !== atual[i]) diff[i] = x; });
  r.flow.dismiss(); r.flow.ask('undo', diff, true); r.cycle();
  same(r.escritas(), ['14 33 01 01 64 00 AD']);
  same(r.anti5(), F);
  assert.equal(r.flow.view().phase, 'done');
});

test('bancada, leitura de volta diferente: "Não consegui conferir se gravou", nem gravado nem não gravado', () => {
  const r = rig();
  r.bench.setFault('corruptWrite');
  r.flow.ask('edit', { 0: 1800 }, true); r.cycle();
  assert.equal(JSON.parse(r.bench.autocalExtra.getAntiStalloValuesStatus()).outcome, 'READBACK_DIVERGED');
  const v = r.flow.view();
  assert.equal(v.uncertain, true);
  assert.equal(v.message, 'Não consegui conferir se gravou. Espere a leitura voltar e olhe os valores.');
  assert.equal(v.undo.available, false, 'sem Desfazer sobre valor incerto');
});

test('bancada, leitura de volta FALHA depois do ACK: incerto, nunca "Não foi gravado"', () => {
  const r = rig();
  r.bench.setFault('noReadAfterWrite');
  r.flow.ask('edit', { 0: 1800 }, true); r.cycle();
  const st = JSON.parse(r.bench.autocalExtra.getAntiStalloValuesStatus());
  assert.equal(st.outcome, 'READBACK_FAILED'); same(st.written, [0]);
  const v = r.flow.view();
  assert.equal(v.uncertain, true);
  assert.equal(v.message, 'Não consegui conferir se gravou. Espere a leitura voltar e olhe os valores.');
});

test('bancada, central desconectada: nenhum quadro sai e o motivo é dito', () => {
  const r = rig(); r.bench.ecu.clearLog();
  r.bench.unplugUsb();
  assert.equal(r.flow.ask('edit', { 0: 1800 }, false), false);
  assert.equal(r.flow.ask('edit', { 0: 1800 }, true), false, 'a ponte recusa: USB solto');
  same(r.escritas(), []);
  assert.equal(r.flow.view().phase, 'error');
  // A recusa crua da ponte ("USB desconectado") vira frase de leigo com o que fazer; sem "Não gravou." repetido.
  assert.equal(r.flow.view().message, 'Não consegui falar com a central. Nada foi gravado. Confira o cabo e tente de novo.');
  const P = painel({}, r.flow.view());
  assert.equal(P.saveStatus()[1], 'Não consegui falar com a central. Nada foi gravado. Confira o cabo e tente de novo.');
});

test('interruptor: a recusa crua "USB desconectado" também vira frase de leigo na linha do cartão', () => {
  const A = carregar(['core/advanced-flags-data.js', 'core/advanced-flags.js']).OmegasUi.AdvancedFlags;
  const bridge = { applyAntiStalloFlag: () => JSON.stringify({ ok: false, error: 'USB desconectado' }), getAntiStalloFlagStatus: () => '{"state":"IDLE"}' };
  const flow = A.createWriteFlow({ bridge, addHook: () => () => {} });
  flow.tapSwitch(null, true);
  assert.equal(flow.view().phase, 'error');
  assert.equal(flow.view().message, 'Não consegui falar com a central. Nada foi gravado. Confira o cabo e tente de novo.');
});

// ---------------------------------------------------------------- tela (advanced.js), sem navegador
function painel(fv, vv, mutacoes = []) {
  const ctx = carregar(['core/advanced-flags-data.js', 'core/advanced-flags.js', 'core/anti-apagao-valores.js', 'core/cartoes-motor.js', 'components/advanced.js'], mutacoes);
  const P = Object.create(ctx.OmegasUi.AdvancedPanel.prototype);
  P.V = ctx.OmegasUi.AntiApagaoValores; P.A = ctx.OmegasUi.AdvancedFlags; P.M = ctx.OmegasUi.CartoesMotor;
  P.flag = { view: () => ({ phase: 'idle', message: '', undo: { available: false }, ...fv }) };
  P.vals = { view: () => ({ phase: 'idle', message: '', undo: { available: false }, ...vv }) };
  P.reason = () => '';
  return P;
}
const sw = { kind: 'switch', id: 'anti', title: 'Anti-apagão', on: false, line: 'Ajuda o motor a não apagar.', stateText: 'Desligado' };

test('P1: escrita chegou mas a leitura de volta falhou: a tela NÃO diz "Não foi gravado" nem "Não gravou"', () => {
  const P = painel({}, { phase: 'error', uncertain: true, message: 'Não consegui conferir se gravou. Espere a leitura voltar e olhe os valores.' });
  const [nivel, texto] = P.saveStatus();
  assert.equal(texto, 'Não consegui conferir se gravou. Espere a leitura voltar e olhe os valores.');
  assert.doesNotMatch(texto, /Não gravou|Não foi gravado|Gravado/);
  assert.equal(nivel, 'warn');
});

test('PARCIAL na linha de estado: frase exata e Desfazer já visível (sem janela)', () => {
  const msg = 'Gravei só uma parte (1 de 2). Toque em Desfazer para voltar ao que era antes.';
  const P = painel({}, { phase: 'partial', message: msg, undo: { available: true, values: F } });
  const [nivel, texto] = P.saveStatus();
  assert.equal(texto, msg); assert.equal(nivel, 'warn');
});

test('P2: erro do interruptor vai na linha do cartão, com o que fazer; nada de janela com "Fechar"', () => {
  const P = painel({ phase: 'error', message: 'A central não aceitou. Tente de novo.' }, {});
  const html = P.renderHome([sw]);
  assert.match(html, /class="tile-line[^"]*"[^>]*data-level="warn"[^>]*><span class="tile-full">A central não aceitou\. Tente de novo\.<\/span><span class="tile-compact">A central não aceitou\. Tente de novo\.<\/span>/);
  const src = fs.readFileSync(path.join(UI, 'components/advanced.js'), 'utf8');
  assert.doesNotMatch(src, /aria-modal|data-adv-close|Não foi gravado|layerError/, 'sem janela modal de erro');
});

test('P2: erro dos 5 números aparece no cartão "Valores" da página inicial', () => {
  const P = painel({}, { phase: 'error', message: 'A central não aceitou. Os números continuam como estavam. Tente de novo.' });
  const html = P.renderHome([{ kind: 'values', id: 'valores', title: 'Valores do anti-apagão', on: null, stateText: '1.600 rpm', line: 'Cinco números.' }]);
  assert.match(html, /A central não aceitou\. Os números continuam como estavam\. Tente de novo\./);
});

test('mutação: sem o ramo PARCIAL, ou com "Não gravou" no incerto, as provas acima FALHAM', () => {
  const r = rig([['core/anti-apagao-valores.js', "if (status.outcome === 'PARTIAL') {", 'if (false) {']]);
  r.bench.setFault('nakAfter', 1);
  r.flow.ask('edit', { 1: 150, 4: 30 }, true); r.cycle();
  assert.notEqual(r.flow.view().phase, 'partial', 'o mutante não reconhece o parcial');
  assert.notEqual(r.flow.view().message, V.partialText(1, 2));
  const msg = 'Não consegui conferir se gravou. Espere a leitura voltar e olhe os valores.';
  const P = painel({}, { phase: 'error', uncertain: true, message: msg },
    [['components/advanced.js', "if (vv.phase === 'partial' || (vv.phase === 'error' && vv.uncertain)) return ['warn', vv.message || ''];", '']]);
  assert.match(P.saveStatus()[1], /^Não gravou\. /, 'o mutante volta a dizer "Não gravou" no incerto (o bug do Codex)');
});
