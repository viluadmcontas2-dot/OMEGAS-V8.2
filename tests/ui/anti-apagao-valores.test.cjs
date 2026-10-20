'use strict';
// Os 5 números do anti-apagão (ANTI_STALLO, 307): lógica pura do JS (faixa, quadro, ler-modificar-escrever, fluxo de toque).
// Espelha tests/ui/avancado-escrita.test.cjs. Sem relógio: esperar a central = o gancho de dados da ponte.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');
function carregar(extra) {
  const ctx = { console, Math, JSON, Number, String, Array, Object, Set, Map, Intl };
  ctx.window = ctx; ctx.globalThis = ctx; vm.createContext(ctx);
  let src = fs.readFileSync(path.join(UI, 'core/anti-apagao-valores.js'), 'utf8');
  for (const [de, para] of extra || []) { assert.ok(src.includes(de), 'mutação sem alvo: ' + de); src = src.replace(de, para); }
  vm.runInContext(src, ctx);
  return ctx.OmegasUi.AntiApagaoValores;
}
const same = (a, b, m) => assert.equal(JSON.stringify(a), JSON.stringify(b), m); // objetos do vm têm outro protótipo
const V = carregar();
const F = [1600, 100, 1500, 2, 20];

test('endereço 307, 5 campos, fábrica e faixa máxima do DFM', () => {
  assert.equal(V.ADDRESS, 307);
  same(V.FACTORY, F);
  same(V.FIELDS.map(f => f.max), [6000, 1000, 10000, 1000, 1000]);
  same(V.FIELDS.map(f => f.min), [0, 0, 0, 0, 0]);
});

test('nomes leigos: nenhum jargão ("ciclo" é jargão banido pelo dono); unidade curta', () => {
  const proibido = /\bciclos?\b|\bflag\b|\bbit\b|palavra|0x|FLAG_CONF|ECU|SerialCode|histerese|delta/i;
  for (const f of V.FIELDS) {
    for (const t of [f.label, f.help, f.unit, f.tipUp, f.tipDown]) assert.doesNotMatch(t, proibido, t);
    assert.ok(['rpm', 'ms'].includes(f.unit), f.unit);
    assert.ok(f.step > 0 && f.tipUp && f.tipDown, 'botões − / + e dicas para os dois lados: ' + f.label);
  }
  same(V.FIELDS.map(f => f.label), ['Rotação máxima para agir', 'Queda de rotação para agir', 'Espera para sair do gás', 'Folga para não ficar trocando', 'Queda para parar de agir']);
});

test('quadro: formato do original, 14 33 01 <índice> lo hi soma (só o campo alterado), igual à conta independente', () => {
  const p = V.plan([1700, 100, 1500, 2, 20], { 0: 1600 });
  assert.equal(p.frameHex, '14 33 01 00 40 06 8E');
  assert.equal(p.frames.length, 1);
  const q = p.frames[0];
  assert.equal(q[0], 0x14); assert.equal(q.length, 7);
  assert.equal(q.slice(0, -1).reduce((a, b) => a + b, 0) & 255, q[6]);
  assert.equal(V.hex(V.frameFor(2, 0xABCD)), '14 33 01 02 CD AB ' + ((0x14 + 0x33 + 0x01 + 0x02 + 0xCD + 0xAB) & 0xFF).toString(16).toUpperCase());
  assert.throws(() => V.frameFor(5, 1), /quadro inválido/);
  assert.throws(() => V.frameFor(0, 0x10000), /quadro inválido/);
});

test('ler-modificar-escrever: só o campo pedido muda; os outros são os lidos', () => {
  const p = V.plan([1234, 99, 1400, 3, 21], { 2: 2000 });
  same(p.after, [1234, 99, 2000, 3, 21]);
  same(p.changed, [2]);
  assert.equal(p.noop, false);
});

test('mesmo valor = nada a gravar; restaurar fábrica troca só o que difere', () => {
  assert.equal(V.plan(F, { 0: 1600 }).noop, true);
  same(V.factoryChanges([1800, 100, 1500, 5, 20]), { 0: 1600, 3: 2 });
  same(V.factoryChanges(F), {});
  same(V.plan([1800, 100, 1500, 5, 20], V.factoryChanges([1800, 100, 1500, 5, 20])).after, F);
});

test('faixa: aceita os dois limites e recusa 1 acima/abaixo, em todos os campos', () => {
  V.FIELDS.forEach(f => {
    assert.equal(V.validate({ [f.index]: f.min }).ok, true);
    assert.equal(V.validate({ [f.index]: f.max }).ok, true);
    assert.equal(V.validate({ [f.index]: f.max + 1 }).ok, false, 'max+1 em ' + f.index);
    assert.equal(V.validate({ [f.index]: f.min - 1 }).ok, false, 'min-1 em ' + f.index);
    const p = V.plan(F, { [f.index]: f.max + 1 });
    assert.equal(p.ok, false); assert.equal(p.frames, undefined, 'fora da faixa nunca vira quadro');
  });
  for (const bad of [1.5, NaN, Infinity, '1700', null, undefined, true]) assert.equal(V.validate({ 0: bad }).ok, false, String(bad));
  assert.equal(V.validate({ 5: 1 }).ok, false);
  assert.equal(V.validate({}).ok, false);
  assert.match(V.validate({ 0: 6001 }).error, /de 0 a 6\.000/);
});

test('valor atual desconhecido não vira plano (nunca 0)', () => {
  for (const before of [null, undefined, [], [1, 2, 3, 4], [1, 2, 3, 4, 5, 6], [1, 2, 3, 4, -1], [1, 2, 3, 4, NaN]]) assert.equal(V.plan(before, { 0: 1 }).ok, false);
  assert.equal(V.valuesOf({ state: 'UNKNOWN', values: null }), null);
  assert.equal(V.valuesOf(undefined), null);
  same(V.valuesOf({ state: 'OK', values: F }), F);
});

test('digitação: 1600 e 1.600 valem; vazio, decimal, letra e sinal são recusados com texto simples', () => {
  same(V.parseInput('1600'), { ok: true, value: 1600 });
  same(V.parseInput(' 1.600 '), { ok: true, value: 1600 });
  same(V.parseInput('0'), { ok: true, value: 0 });
  for (const bad of ['', '  ', '1,5', '1.5', 'abc', '-3', '+3', '1e3', '12 34a', null, undefined]) {
    const r = V.parseInput(bad); assert.equal(r.ok, false, String(bad)); assert.match(r.error, /número|inteiro/i);
  }
});

test('leitura de volta: só vale se os 5 números forem iguais', () => {
  const p = V.plan(F, { 0: 1700 });
  assert.equal(V.verifyReadback(p, [1700, 100, 1500, 2, 20]).ok, true);
  assert.equal(V.verifyReadback(p, F).ok, false);
  assert.equal(V.verifyReadback(p, [1700, 100, 1500, 2, 21]).ok, false);
  assert.equal(V.verifyReadback(p, [1700]).ok, false);
  assert.equal(V.verifyReadback(p, null).ok, false);
});

test('tempo em segundos e aviso de "longe da fábrica" (±50%, INFERIDO) sem bloquear', () => {
  assert.equal(V.secondsText(1500), '1,5 s');
  assert.equal(V.secondsText(null), '—');
  assert.equal(V.farFromFactory(0, 1600), false);
  assert.equal(V.farFromFactory(0, 2400), false);
  assert.equal(V.farFromFactory(0, 2401), true);
  assert.equal(V.farFromFactory(0, 799), true);
  assert.equal(V.validate({ 0: 2401 }).ok, true, 'longe da fábrica ainda é aceito se estiver na faixa do original');
});

// ---------- fluxo de toque ----------
function ambiente(opcoes = {}) {
  const chamadas = []; const hooks = new Set(); let status = { state: 'IDLE' };
  const bridge = {
    prepareAntiStalloValues(json) { chamadas.push(['prepare', json]); return JSON.stringify(opcoes.prepareReply || { ok: true, started: true }); },
    confirmAntiStalloValues(token) { chamadas.push(['confirm', token]); return JSON.stringify(opcoes.confirmReply || { ok: true, started: true }); },
    cancelAntiStalloValues() { chamadas.push(['cancel']); return JSON.stringify({ ok: true }); },
    getAntiStalloValuesStatus() { return JSON.stringify(status); },
  };
  const env = { chamadas, setStatus(s) { status = s; }, emit() { for (const fn of [...hooks]) fn(); }, hooks,
    count: t => chamadas.filter(c => c[0] === t).length };
  env.addHook = (n, fn) => { assert.equal(n, 'fast'); hooks.add(fn); return () => hooks.delete(fn); };
  env.flow = V.createValuesFlow({ bridge, addHook: env.addHook });
  return env;
}
const pronto = (before, changes, token = 'tk1') => { const p = V.plan(before, changes); return { state: 'READY', token, before, after: p.after, changed: p.changed, frameHex: p.frameHex, noop: p.noop }; };

test('SEM ATRITO: um pedido lê, confere o plano e grava sozinho (sem prévia, sem 2º toque)', () => {
  const e = ambiente();
  assert.equal(e.flow.ask('edit', { 0: 1700 }, true), true);
  same(e.chamadas, [['prepare', '{"0":1700}']]);
  assert.equal(e.flow.view().phase, 'preparing');
  e.setStatus(pronto(F, { 0: 1700 }, 'tkZ')); e.emit();
  assert.equal(e.flow.view().phase, 'writing');
  same(e.chamadas.filter(c => c[0] === 'confirm'), [['confirm', 'tkZ']]);
});

test('gravação: só depois da leitura de volta igual vira "ok"', () => {
  const e = ambiente();
  e.flow.ask('edit', { 0: 1700 }, true); e.setStatus(pronto(F, { 0: 1700 }, 'tkA')); e.emit();
  same(e.chamadas.filter(c => c[0] === 'confirm'), [['confirm', 'tkA']]);
  assert.equal(e.flow.view().phase, 'writing');
  e.setStatus({ state: 'WRITING' }); e.emit();
  assert.equal(e.flow.view().phase, 'writing');
  e.setStatus({ state: 'DONE', outcome: 'CONFIRMED', readback: [1700, 100, 1500, 2, 20], undo: { available: true, values: F } }); e.emit();
  const v = e.flow.view();
  assert.equal(v.phase, 'done'); assert.equal(v.level, 'ok');
  same(v.undo, { available: true, values: F });
});

test('confirmar solto não faz nada; cancelar durante a leitura descarta; nada grava sem pedido', () => {
  const e = ambiente();
  e.flow.confirm();
  assert.equal(e.count('confirm'), 0);
  e.flow.ask('edit', { 0: 1700 }, true);
  e.flow.otherTouch();
  assert.equal(e.count('cancel'), 1); assert.equal(e.flow.view().phase, 'idle');
  e.setStatus(pronto(F, { 0: 1700 })); e.emit();
  assert.equal(e.count('confirm'), 0, 'depois de cancelar, a resposta tardia não grava');
});

test('sem USB, fora da faixa ou ponte ausente: nem pede', () => {
  const e = ambiente();
  assert.equal(e.flow.ask('edit', { 0: 1700 }, false), false);
  assert.equal(e.flow.ask('edit', { 0: 6001 }, true), false);
  assert.equal(e.flow.view().phase, 'error');
  assert.equal(e.count('prepare'), 0);
  const sem = V.createValuesFlow({ bridge: {}, addHook() { return () => {}; } });
  assert.equal(sem.ask('edit', { 0: 1700 }, true), false);
});

test('prévia que não bate com o cálculo da tela é descartada (nada gravado)', () => {
  const e = ambiente();
  e.flow.ask('edit', { 0: 1700 }, true);
  const ruim = pronto(F, { 0: 1700 }); ruim.frameHex = ruim.frameHex.replace('A4', 'A5');
  e.setStatus(ruim); e.emit();
  assert.equal(e.flow.view().phase, 'error'); assert.equal(e.count('cancel'), 1); assert.equal(e.count('confirm'), 0);
});

test('mesmo valor que a central já tem: avisa e não pede confirmação', () => {
  const e = ambiente();
  e.flow.ask('edit', { 0: 1600 }, true); e.setStatus(pronto(F, { 0: 1600 })); e.emit();
  assert.equal(e.flow.view().phase, 'idle'); assert.match(e.flow.view().message, /já estão assim/);
});

test('falhas: texto humano (o que houve + o que fazer), sem sigla', () => {
  const casos = [
    [{ outcome: 'NOT_ACKED' }, /não aceitou.*continuam como estavam.*Tente de novo/s, false],
    [{ outcome: 'NOT_ACKED', valueChanged: true }, /^Não consegui conferir se gravou\. Espere a leitura voltar e olhe os valores\.$/, true],
    [{ outcome: 'READBACK_FAILED' }, /^Não consegui conferir se gravou\. Espere a leitura voltar e olhe os valores\.$/, true],
    [{ outcome: 'READBACK_DIVERGED', readback: [1, 1, 1, 1, 1] }, /^Não consegui conferir se gravou\. Espere a leitura voltar e olhe os valores\.$/, true],
    [{ outcome: 'CHANGED_MEANWHILE' }, /mudaram.*Nada foi gravado/s, false],
    [{ outcome: 'ERROR' }, /Não consegui falar com a central/, false],
  ];
  for (const [status, re, incerto] of casos) {
    const e = ambiente();
    e.flow.ask('edit', { 0: 1700 }, true); e.setStatus(pronto(F, { 0: 1700 })); e.emit(); e.flow.confirm();
    e.setStatus({ state: 'DONE', ...status }); e.emit();
    const v = e.flow.view();
    assert.equal(v.phase, 'error', JSON.stringify(status)); assert.match(v.message, re); assert.equal(v.uncertain, incerto);
    assert.doesNotMatch(v.message, /ECU|FLAG|\bbit\b|0x|rpm/i);
  }
});

test('o USB cair durante a gravação: não sabe como ficou', () => {
  const e = ambiente();
  e.flow.ask('edit', { 0: 1700 }, true); e.setStatus(pronto(F, { 0: 1700 })); e.emit(); e.flow.confirm();
  e.flow.observe({ route: 'tools', usbConnected: false });
  assert.equal(e.flow.view().phase, 'error'); assert.match(e.flow.view().message, /cabo soltou.*Não consegui conferir se gravou/s);
});

test('sair da aba ou perder o USB na leitura descarta; Restaurar fábrica também grava em um pedido', () => {
  const e = ambiente();
  e.flow.ask('edit', { 0: 1700 }, true);
  e.flow.observe({ route: 'map', usbConnected: true });
  assert.equal(e.flow.view().phase, 'idle');
  assert.equal(e.count('confirm'), 0);
  e.flow.ask('factory', V.factoryChanges([1800, 100, 1500, 5, 20]), true);
  assert.equal(e.chamadas.at(-1)[1], '{"0":1600,"3":2}');
  e.setStatus(pronto([1800, 100, 1500, 5, 20], { 0: 1600, 3: 2 })); e.emit();
  assert.equal(e.flow.view().phase, 'writing'); assert.equal(e.flow.view().kind, 'factory');
  assert.equal(e.count('confirm'), 1);
});

test('mutação: voltar a exigir o 2º toque faria o teste "um pedido grava" falhar', () => {
  const M = carregar([["s.plan = mine; s.token = status.token;\n      confirm();", "s.plan = mine; s.token = status.token;\n      set('preview', '', '');"]]);
  const chamadas = [];
  const bridge = { prepareAntiStalloValues: () => '{"ok":true}', confirmAntiStalloValues: t => { chamadas.push(t); return '{"ok":true}'; }, cancelAntiStalloValues: () => '{}',
    getAntiStalloValuesStatus: () => JSON.stringify(pronto(F, { 0: 1700 })) };
  let fn; const flow = M.createValuesFlow({ bridge, addHook: (_, f) => { fn = f; return () => {}; } });
  flow.ask('edit', { 0: 1700 }, true); fn();
  assert.equal(chamadas.length, 0, 'o mutante pede 2º toque: o teste verdadeiro (1 confirmação) pegaria');
});

test('mutação: faixa estragada (max+1 aceito) é pega pelo teste de limites', () => {
  const M = carregar([['value > field.max', 'value > field.max + 1']].map(() => ['value < field.min || value > field.max', 'value < field.min || value > field.max + 1']));
  assert.equal(M.validate({ 0: 6001 }).ok, true, 'mutante aceita fora da faixa');
  assert.equal(V.validate({ 0: 6001 }).ok, false);
});
