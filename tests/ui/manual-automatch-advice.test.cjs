'use strict';

// Conselheiro do AutoMatch manual (dono, 2026-10-17: "o manual é como as médias dos quadradinhos são aplicadas depois do
// aprendizado"). Regra PROVISÓRIA e honesta: o corpus real (14 AutoMatches da própria ECU, leituras de 15–217 s antes) mostra
// mediana de 9 bandas do GNV com ≥ 3 amostras quando a ECU decidiu; esse é o "maduro". Erro < 3 % = curva já bate com as médias.
// Nada aqui aperta o botão: só diz se vale e por quê, em português simples.

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');

function load() {
  const context = { console, Math, Number, Array, Object, Set, String, JSON };
  context.window = context;
  context.OmegasUi = {};
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(UI, 'core/manual-automatch-advice.js'), 'utf8'), context, { filename: 'manual-automatch-advice.js' });
  return context.OmegasUi.ManualAutoMatchAdvice;
}

const mature = [0, 0, 10, 10, 10, 10, 10, 10, 10, 10, 6, 5, 10, 0, 0, 0, 0, 0]; // sessão 053 do corpus: 11 bandas ≥ 3
const green = { quotaReached: true, enabled: 1 };

test('indisponível antes da cota automática da ECU ou com o aprendizado pausado', () => {
  const A = load();
  assert.equal(A.advise({ ...green, quotaReached: false, counters: mature, errorBefore: 0.1 }).verdict, 'UNAVAILABLE');
  assert.equal(A.advise({ ...green, enabled: 0, counters: mature, errorBefore: 0.1 }).verdict, 'UNAVAILABLE');
});

test('sem leitura das bandas: desconhecido, nunca "pronto"', () => {
  const A = load();
  for (const counters of [null, undefined, [], [1, 2, 3]]) {
    const advice = A.advise({ ...green, counters, errorBefore: 0.1 });
    assert.equal(advice.verdict, 'UNKNOWN');
    assert.match(advice.reason, /sem leitura/i);
  }
});

test('poucas bandas maduras: esperar mais dados e dizer quantas faltam', () => {
  const A = load();
  const few = [0, 0, 3, 3, 3, 3, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]; // 5 bandas ≥ 3
  const advice = A.advise({ ...green, counters: few, errorBefore: 0.2 });
  assert.equal(advice.verdict, 'WAIT');
  assert.equal(advice.facts.bandsMature, 5);
  assert.equal(advice.facts.bandsNeeded, 9);
  assert.match(advice.reason, /5 de 9/);
});

test('bandas maduras e curva já bate com as médias: não precisa', () => {
  const A = load();
  const advice = A.advise({ ...green, counters: mature, errorBefore: 0.012 });
  assert.equal(advice.verdict, 'NOT_NEEDED');
  assert.match(advice.reason, /1,2 %/);
});

test('bandas maduras e curva longe das médias: vale apertar', () => {
  const A = load();
  const advice = A.advise({ ...green, counters: mature, errorBefore: 0.081 });
  assert.equal(advice.verdict, 'WORTH_IT');
  assert.equal(advice.facts.bandsMature, 11);
  assert.equal(advice.facts.bandsFull, 9);
  assert.match(advice.reason, /8,1 %/);
});

test('erro não calculado: não esconde, avisa que a decisão é do dono', () => {
  const A = load();
  const advice = A.advise({ ...green, counters: mature, errorBefore: null });
  assert.equal(advice.verdict, 'MATURE_NO_ESTIMATE');
  assert.match(advice.reason, /estimativa/i);
});

test('o conselho nunca aperta o botão nem esconde que a regra é provisória', () => {
  const A = load();
  const advice = A.advise({ ...green, counters: mature, errorBefore: 0.081 });
  assert.equal(advice.provisional, true);
  assert.match(advice.basis, /14 AutoMatches/);
  assert.equal(typeof A.advise, 'function');
});

test('nota curta do botão cabe na linha e muda com o veredito', () => {
  const A = load();
  const note = input => A.advise({ ...green, counters: mature, ...input }).shortNote;
  assert.equal(note({ errorBefore: 0.081 }), '(vale: difere 8,1 %)');
  assert.equal(note({ errorBefore: 0.012 }), '(curva já bate: 1,2 %)');
  assert.equal(note({ errorBefore: null }), '(11 bandas maduras)');
  assert.equal(A.advise({ ...green, counters: [0, 0, 3, 3, 3, 3, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], errorBefore: 0.2 }).shortNote, '(ainda cedo: 5 de 9 bandas)');
  assert.equal(A.advise({ ...green, counters: null }).shortNote, '(aplica as médias)');
  for (const input of [{ errorBefore: 0.081 }, { errorBefore: 0.012 }, { errorBefore: null }]) assert.ok(note(input).length <= 32);
});

test('cockpit carrega o conselheiro e atualiza só a nota e o título do botão', () => {
  const router = fs.readFileSync(path.join(UI, 'index.html'), 'utf8');
  assert.match(router, /core\/manual-automatch-advice\.js/);
  const cockpit = fs.readFileSync(path.join(UI, 'screens/autocal-cockpit.js'), 'utf8');
  assert.match(cockpit, /ManualAutoMatchAdvice\.advise/);
  assert.match(cockpit, /button\.title = /);
});
