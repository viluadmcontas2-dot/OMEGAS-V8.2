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

test('nomes leigos: nenhum jargão e a unidade é palavra simples', () => {
  const proibido = /ciclo|flag|\bbit\b|palavra|0x|FLAG_CONF|ECU|SerialCode|histerese|delta/i;
  for (const f of V.FIELDS) {
    for (const t of [f.label, f.help, f.unit]) assert.doesNotMatch(t, proibido, t);
    assert.ok(['rpm', 'ms'].includes(f.unit), f.unit);
  }
  same(V.FIELDS.map(f => f.label), ['Rotação máxima para agir', 'Queda de rotação para agir', 'Espera para sair do gás', 'Folga para não ficar trocando', 'Queda para parar de agir']);
});

test('quadro: SetVector estendido 37 33 0C 01 (e não 3B), igual à conta independente', () => {
  const p = V.plan([1700, 100, 1500, 2, 20], { 0: 1600 });
  assert.equal(p.frameHex, '37 33 0C 01 40 06 64 00 DC 05 02 00 14 00 18');
  assert.equal(p.frame[0], 0x37);
  assert.equal(p.frame.length, 15);
  assert.equal(p.frame.slice(0, -1).reduce((a, b) => a + b, 0) & 255, p.frame[14]);
  assert.equal(V.frameForVector(0x0161, [0x1234, 0xABCD, 0x4000]).map(b => b.toString(16)).join(' '), '37 61 8 1 34 12 cd ab 0 40 9f', 'mesmo quadro do AutoCalProtocolTest (3 × u16)');
  assert.equal(V.frameForVector(0x0161, [0x1234, 0xABCD]).length, 8, 'até 2 × u16 continua compacto (0x35)');
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
    assert.equal(p.ok, false); assert.equal(p.frame, undefined, 'fora da faixa nunca vira quadro');
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

test('1º toque só PEDE (lê a central e mostra antes → depois); nada é gravado', () => {
  const e = ambiente();
  assert.equal(e.flow.ask('edit', { 0: 1700 }, true), true);
  same(e.chamadas, [['prepare', '{"0":1700}']]);
  assert.equal(e.flow.view().phase, 'preparing');
  e.setStatus(pronto(F, { 0: 1700 })); e.emit();
  const v = e.flow.view();
  assert.equal(v.phase, 'preview');
  same(v.plan.before, F); same(v.plan.after, [1700, 100, 1500, 2, 20]);
  assert.equal(e.count('confirm'), 0);
  assert.equal(e.hooks.size, 0, 'o gancho de dados solta quando a prévia chega');
});

test('2º toque confirma com o token da prévia; só depois da leitura de volta igual vira "ok"', () => {
  const e = ambiente();
  e.flow.ask('edit', { 0: 1700 }, true); e.setStatus(pronto(F, { 0: 1700 }, 'tkA')); e.emit();
  e.flow.confirm();
  same(e.chamadas.filter(c => c[0] === 'confirm'), [['confirm', 'tkA']]);
  assert.equal(e.flow.view().phase, 'writing');
  e.setStatus({ state: 'WRITING' }); e.emit();
  assert.equal(e.flow.view().phase, 'writing');
  e.setStatus({ state: 'DONE', outcome: 'CONFIRMED', readback: [1700, 100, 1500, 2, 20], undo: { available: true, values: F } }); e.emit();
  const v = e.flow.view();
  assert.equal(v.phase, 'done'); assert.equal(v.level, 'ok');
  same(v.undo, { available: true, values: F });
});

test('confirmar sem prévia não faz nada; toque fora cancela; nada grava sozinho', () => {
  const e = ambiente();
  e.flow.confirm();
  assert.equal(e.count('confirm'), 0);
  e.flow.ask('edit', { 0: 1700 }, true); e.setStatus(pronto(F, { 0: 1700 })); e.emit();
  e.flow.otherTouch();
  assert.equal(e.count('cancel'), 1); assert.equal(e.flow.view().phase, 'idle');
  e.flow.confirm();
  assert.equal(e.count('confirm'), 0, 'depois de cancelar, o 2º toque não grava');
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
    [{ outcome: 'NOT_ACKED', valueChanged: true }, /Não sei como ficou/, true],
    [{ outcome: 'READBACK_FAILED' }, /Não sei como ficou/, true],
    [{ outcome: 'READBACK_DIVERGED', readback: [1, 1, 1, 1, 1] }, /Não sei como ficou/, true],
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
  assert.equal(e.flow.view().phase, 'error'); assert.match(e.flow.view().message, /cabo soltou.*Não sei como ficou/s);
});

test('sair da aba ou perder o USB na prévia descarta; Desfazer e Restaurar usam o mesmo caminho de 2 toques', () => {
  const e = ambiente();
  e.flow.ask('edit', { 0: 1700 }, true); e.setStatus(pronto(F, { 0: 1700 })); e.emit();
  e.flow.observe({ route: 'map', usbConnected: true });
  assert.equal(e.flow.view().phase, 'idle');
  e.flow.ask('factory', V.factoryChanges([1800, 100, 1500, 5, 20]), true);
  assert.equal(e.chamadas.at(-1)[1], '{"0":1600,"3":2}');
  e.setStatus(pronto([1800, 100, 1500, 5, 20], { 0: 1600, 3: 2 })); e.emit();
  assert.equal(e.flow.view().phase, 'preview'); assert.equal(e.flow.view().kind, 'factory');
  assert.equal(e.count('confirm'), 0);
});

test('mutação: confirmação automática (sem o 2º toque) faria o teste "nada grava sozinho" falhar', () => {
  const M = carregar([["s.plan = mine; s.token = status.token;\n      set('preview', '', '');", "s.plan = mine; s.token = status.token;\n      set('preview', '', ''); confirm();"]]);
  const chamadas = [];
  const bridge = { prepareAntiStalloValues: () => '{"ok":true}', confirmAntiStalloValues: t => { chamadas.push(t); return '{"ok":true}'; }, cancelAntiStalloValues: () => '{}',
    getAntiStalloValuesStatus: () => JSON.stringify(pronto(F, { 0: 1700 })) };
  let fn; const flow = M.createValuesFlow({ bridge, addHook: (_, f) => { fn = f; return () => {}; } });
  flow.ask('edit', { 0: 1700 }, true); fn();
  assert.equal(chamadas.length, 1, 'o mutante grava sozinho: é exatamente o que o teste de verdade proíbe');
});

test('mutação: faixa estragada (max+1 aceito) é pega pelo teste de limites', () => {
  const M = carregar([['value > field.max', 'value > field.max + 1']].map(() => ['value < field.min || value > field.max', 'value < field.min || value > field.max + 1']));
  assert.equal(M.validate({ 0: 6001 }).ok, true, 'mutante aceita fora da faixa');
  assert.equal(V.validate({ 0: 6001 }).ok, false);
});
