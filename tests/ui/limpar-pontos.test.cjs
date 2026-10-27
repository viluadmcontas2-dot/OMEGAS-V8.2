'use strict';

// Limpar pontos do AutoCal (botões de zona e filtro de RPM baixo): um só caminho, em fila, com conferência.
// Dono: "às vezes não funciona direito" e "é lento". Causas provadas aqui:
//  - 2º toque enquanto a 1ª zona limpava apagava o estado da 1ª (sem aviso nem plano B);
//  - "Zona limpa" era dito sem conferir a contagem; central que não zerou ficava sem aviso;
//  - lote recusado virava até 5 operações seguidas com a mesma causa;
//  - o fim da operação era reconhecido por assinatura do estado (podia nunca "mudar") e deixava o filtro travado.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const ctx = { console };
ctx.globalThis = ctx;
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(root, 'app/src/main/assets/ui/core/ponto-limpeza.js'), 'utf8'), ctx);
const PC = ctx.OmegasUi.PointClear;
const plain = x => JSON.parse(JSON.stringify(x));
const zeros = () => new Array(18).fill(0);

function harness(opts = {}) {
  const calls = [];
  const alerts = [];
  let n = 0;
  let counters = { id: 's0', GAS: zeros(), PETROL: zeros() };
  const api = {
    preparePointDeleteBatch(targets) {
      calls.push(['prepare', plain(targets)]);
      const refused = opts.prepareError && opts.prepareError();
      if (refused) return { ok: false, error: refused };
      n += 1;
      return { ok: true, prepared: true, preparationId: 'P' + n };
    },
    execute(id) { calls.push(['execute', id]); return opts.executeFail ? { ok: false, error: 'usb' } : { ok: true }; },
    cancelPreparation() { calls.push(['cancel']); },
  };
  const c = PC.create({ api, notify: (level, message) => alerts.push([level, message]), counters: () => counters });
  return { c, calls, alerts, api, set: v => { counters = v; }, get: () => counters };
}
const T = (fuel, ...idx) => idx.map(index => ({ fuel, index }));

test('um toque: manda UM lote para a central e avisa na hora', () => {
  const h = harness();
  h.c.request(T('GAS', 5, 6, 7, 8), { kind: 'zone', label: 'GNV · zona 2' });
  assert.deepEqual(h.calls.map(x => x[0]), ['prepare', 'execute']);
  assert.equal(h.alerts[0][0], 'working');
  assert.match(h.alerts[0][1], /Limpando GNV · zona 2/);
});

test('2º toque durante a limpeza entra na fila e roda depois; o 1º não perde o estado', () => {
  const h = harness();
  h.c.request(T('GAS', 0, 1, 2, 3, 4), { kind: 'zone', label: 'GNV · zona 1' });
  h.c.request(T('PETROL', 5, 6, 7, 8), { kind: 'zone', label: 'gasolina · zona 2' });
  assert.equal(h.calls.filter(x => x[0] === 'prepare').length, 1, 'a central só recebe um lote por vez');
  h.c.onActionState({ preparationId: 'P1', state: 'CONFIRMED', busy: false });
  assert.equal(h.calls.filter(x => x[0] === 'prepare').length, 2, 'o 2º toque segue sozinho');
  assert.deepEqual(plain(h.calls.filter(x => x[0] === 'prepare')[1][1]), T('PETROL', 5, 6, 7, 8));
});

test('toque repetido na mesma zona não duplica a limpeza', () => {
  const h = harness();
  h.c.request(T('GAS', 0, 1, 2, 3, 4), { kind: 'zone', label: 'GNV · zona 1' });
  h.c.request(T('GAS', 0, 1, 2, 3, 4), { kind: 'zone', label: 'GNV · zona 1' });
  h.c.onActionState({ preparationId: 'P1', state: 'CONFIRMED', busy: false });
  assert.equal(h.calls.filter(x => x[0] === 'prepare').length, 1);
});

test('central ocupada com outra ação: espera a vez, sem perder o pedido e sem erro', () => {
  let busy = true;
  const h = harness({ prepareError: () => (busy ? 'Outra ação AutoCal está em andamento' : null) });
  h.c.request(T('GAS', 9, 10), { kind: 'zone', label: 'GNV · zona 3' });
  assert.equal(h.alerts.filter(a => a[0] === 'warning').length, 0);
  busy = false;
  h.c.onActionState({ state: 'IDLE', busy: false });
  assert.equal(h.calls.filter(x => x[0] === 'execute').length, 1);
});

test('fim reconhecido pelo número da operação, mesmo que o estado já estivesse igual', () => {
  const h = harness();
  h.c.request(T('GAS', 0, 1), { kind: 'zone', label: 'GNV · zona 1' });
  h.c.onActionState({ preparationId: 'P1', state: 'CONFIRMED', busy: true, updatedAt: 1, message: 'x' });
  assert.equal(h.c.view().active, false);
  assert.equal(h.alerts.at(-1)[0], 'working'); // "Pronto, conferindo": o número ainda vai ser conferido
});

test('conferência: contagem caiu → "N pontos limpos" e só os que tinham medição entram na conta', () => {
  const h = harness();
  const before = { id: 's1', GAS: zeros(), PETROL: zeros() };
  before.GAS[5] = 6; before.GAS[6] = 3; // 7 e 8 já vazios
  h.set(before);
  h.c.request(T('GAS', 5, 6, 7, 8), { kind: 'zone', label: 'GNV · zona 2' });
  h.c.onActionState({ preparationId: 'P1', state: 'CONFIRMED', busy: false });
  const after = { id: 's2', GAS: zeros(), PETROL: zeros() };
  h.set(after); h.c.onSnapshot(after);
  assert.deepEqual(h.alerts.at(-1), ['ok', 'GNV · zona 2 limpa: 2 pontos.']);
  assert.equal(h.c.view().pending, 0);
});

test('conferência: zona já vazia diz isso (não inventa contagem)', () => {
  const h = harness();
  h.c.request(T('PETROL', 0, 1), { kind: 'zone', label: 'gasolina · zona 1' });
  h.c.onActionState({ preparationId: 'P1', state: 'CONFIRMED', busy: false });
  const s = { id: 's2', GAS: zeros(), PETROL: zeros() };
  h.c.onSnapshot(s);
  assert.deepEqual(h.alerts.at(-1), ['ok', 'gasolina · zona 1 já estava vazia.']);
});

test('conferência: contagem desconhecida não vira 0 nem número: diz só que a central confirmou', () => {
  const h = harness();
  h.set({ id: 's1', GAS: new Array(18).fill(null), PETROL: new Array(18).fill(null) });
  h.c.request(T('GAS', 0, 1), { kind: 'zone', label: 'GNV · zona 1' });
  h.c.onActionState({ preparationId: 'P1', state: 'CONFIRMED', busy: false });
  h.c.onSnapshot({ id: 's2', GAS: new Array(18).fill(null), PETROL: zeros() });
  assert.deepEqual(h.alerts.at(-1), ['ok', 'GNV · zona 1: a central confirmou a limpeza.']);
});

test('central não zerou: refaz UMA vez sozinha só o que sobrou e depois avisa o que fazer', () => {
  const h = harness();
  const b = { id: 's1', GAS: zeros(), PETROL: zeros() }; b.GAS[0] = 9; b.GAS[1] = 9;
  h.set(b);
  h.c.request(T('GAS', 0, 1), { kind: 'zone', label: 'GNV · zona 1' });
  h.c.onActionState({ preparationId: 'P1', state: 'CONFIRMED', busy: false });
  const still = { id: 's2', GAS: zeros(), PETROL: zeros() }; still.GAS[0] = 9; // ponto 1 zerou, ponto 0 não
  h.c.onSnapshot(still);
  assert.equal(h.calls.filter(x => x[0] === 'prepare').length, 1, 'espera uma 2ª leitura antes de concluir que falhou');
  const still2 = { id: 's3', GAS: still.GAS.slice(), PETROL: zeros() };
  h.c.onSnapshot(Object.assign({}, still2));
  assert.equal(h.calls.filter(x => x[0] === 'prepare').length, 2);
  assert.deepEqual(plain(h.calls.filter(x => x[0] === 'prepare')[1][1]), T('GAS', 0));
  h.c.onActionState({ preparationId: 'P2', state: 'CONFIRMED', busy: false });
  h.c.onSnapshot({ id: 's4', GAS: still.GAS.slice(), PETROL: zeros() });
  h.c.onSnapshot({ id: 's5', GAS: still.GAS.slice(), PETROL: zeros() });
  const last = h.alerts.at(-1);
  assert.equal(last[0], 'warning');
  assert.match(last[1], /a central não zerou 1 ponto/i);
  assert.match(last[1], /Toque de novo/);
});

test('falha da central: UMA mensagem com o motivo e o que fazer; sem plano B em cascata', () => {
  const h = harness();
  h.c.request(T('GAS', 0, 1, 2, 3, 4), { kind: 'zone', label: 'GNV · zona 1' });
  h.c.onActionState({ preparationId: 'P1', state: 'FAILED', busy: false, message: 'A ECU não confirmou o mask 1/2' });
  assert.equal(h.calls.filter(x => x[0] === 'prepare').length, 1, 'não tenta ponto a ponto');
  assert.equal(h.alerts.at(-1)[0], 'warning');
  assert.match(h.alerts.at(-1)[1], /Não consegui limpar GNV · zona 1/);
  assert.match(h.alerts.at(-1)[1], /Toque de novo/);
  assert.equal(h.c.view().active, false);
});

test('recusa na preparação (USB fora etc.): avisa uma vez com o motivo e libera', () => {
  const h = harness({ prepareError: () => 'USB desconectado' });
  h.c.request(T('GAS', 0), { kind: 'zone', label: 'GNV · zona 1' });
  assert.equal(h.alerts.at(-1)[0], 'warning');
  assert.match(h.alerts.at(-1)[1], /USB desconectado/);
  assert.equal(h.c.view().active, false);
  assert.equal(h.c.view().pending, 0);
});

test('estado preso: a operação sumiu do status (outra ação assumiu) → libera e avisa', () => {
  const h = harness();
  h.c.request(T('GAS', 0), { kind: 'zone', label: 'GNV · zona 1' });
  for (let i = 0; i < 3; i += 1) h.c.onActionState({ preparationId: 'OUTRA', state: 'CONFIRMED', busy: false });
  assert.equal(h.c.view().active, false);
  assert.equal(h.alerts.at(-1)[0], 'warning');
});

test('filtro de RPM: fica quieto até terminar e diz quantos pontos apagou', () => {
  const h = harness();
  const b = { id: 's1', GAS: zeros(), PETROL: zeros() }; b.GAS[4] = 2;
  h.set(b);
  h.c.request(T('GAS', 4), { kind: 'lowrpm', label: 'pontos abaixo de 1000 rpm' });
  assert.equal(h.alerts.length, 0, 'sem aviso no começo (rodando em segundo plano)');
  h.c.onActionState({ preparationId: 'P1', state: 'CONFIRMED', busy: false });
  h.c.onSnapshot({ id: 's2', GAS: zeros(), PETROL: zeros() });
  assert.deepEqual(h.alerts.at(-1), ['ok', 'Apaguei 1 ponto aprendido abaixo de 1000 rpm.']);
});

test('destaque: bandas em limpeza aparecem em busyKeys até o fim', () => {
  const h = harness();
  h.c.request(T('GAS', 0, 1), { kind: 'zone', label: 'GNV · zona 1' });
  assert.deepEqual([...h.c.busyKeys()].sort(), ['GAS:0', 'GAS:1']);
  h.c.onActionState({ preparationId: 'P1', state: 'CONFIRMED', busy: false });
  h.c.onSnapshot({ id: 's2', GAS: zeros(), PETROL: zeros() });
  assert.equal(h.c.busyKeys().size, 0);
});

test('alvos inválidos são descartados (nunca fora das 18 bandas)', () => {
  const h = harness();
  h.c.request([{ fuel: 'GAS', index: 18 }, { fuel: 'GAS', index: -1 }, { fuel: 'X', index: 2 }, { fuel: 'GAS', index: 1.5 }], { kind: 'zone', label: 'x' });
  assert.equal(h.calls.length, 0);
});

test('filtro de RPM: pedidos que chegam enquanto a central trabalha viram UM lote (menos escrita)', () => {
  const h = harness();
  h.c.request(T('GAS', 0), { kind: 'zone', label: 'GNV · zona 1' }); // ocupa a central
  h.c.request(T('GAS', 7), { kind: 'lowrpm', label: 'pontos abaixo de 1000 rpm' });
  h.c.request(T('PETROL', 9), { kind: 'lowrpm', label: 'pontos abaixo de 1000 rpm' });
  h.c.onActionState({ preparationId: 'P1', state: 'CONFIRMED', busy: false });
  const prepares = h.calls.filter(x => x[0] === 'prepare');
  assert.equal(prepares.length, 2);
  assert.deepEqual(plain(prepares[1][1]), [{ fuel: 'GAS', index: 7 }, { fuel: 'PETROL', index: 9 }]);
});

// ---- 2026-10-27: falha do apagar automático (filtro de giro baixo) não é mais descartada ----
test('filtro de giro baixo: pedido recusado pela central é pedido de novo na próxima leitura nova, com texto próprio, uma vez só', () => {
  let refuse = true;
  const calls = [];
  const alerts = [];
  const api = {
    preparePointDeleteBatch(targets) { calls.push(targets.length); return refuse ? { ok: false, error: 'ECU recusou' } : { ok: true, prepared: true, preparationId: 'p' + calls.length }; },
    execute: () => ({ ok: true }), cancelPreparation() {},
  };
  const c = PC.create({ api, notify: (l, m) => alerts.push(m), counters: () => ({ id: 'x', GAS: zeros(), PETROL: zeros() }) });
  c.request([{ fuel: 'GAS', index: 2 }], { kind: 'lowrpm', limit: 1000 });
  assert.equal(calls.length, 1);
  assert.match(alerts.at(-1), /Tento de novo na próxima leitura/, 'texto próprio, sem "Toque de novo"');
  assert.doesNotMatch(alerts.at(-1), /Toque de novo/);
  refuse = false;
  c.onSnapshot({ id: 'y', GAS: zeros(), PETROL: zeros() });
  assert.equal(calls.length, 2, 'pediu de novo na leitura nova');
  // se falhar outra vez, não insiste para sempre
  refuse = true;
  const c2 = PC.create({ api, notify: () => {}, counters: () => null });
  c2.request([{ fuel: 'GAS', index: 3 }], { kind: 'lowrpm', limit: 1000 });
  const before = calls.length;
  c2.onSnapshot({ id: 'a', GAS: zeros(), PETROL: zeros() });
  c2.onSnapshot({ id: 'b', GAS: zeros(), PETROL: zeros() });
  assert.equal(calls.length - before, 1, 'uma segunda tentativa só');
});
