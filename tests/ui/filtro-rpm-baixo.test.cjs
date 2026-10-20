'use strict';

// Dono, 2026-10-20: ponto registrado com o motor abaixo de 1000 RPM (ou carro parado) é apagado na hora,
// sem pergunta. O app só observa os contadores e manda apagar pela função de lote que já existe.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const context = { console };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(root, 'app/src/main/assets/ui/core/low-rpm-filter.js'), 'utf8'), context);
const { create } = context.OmegasUi.LowRpmFilter;
const zeros = () => new Array(18).fill(0);
const snap = (gas, petr) => ({ GAS: gas, PETROL: petr });
const plain = x => JSON.parse(JSON.stringify(x));

test('primeira leitura só marca a linha de base (nada é apagado)', () => {
  const f = create();
  f.observeRpm(500);
  assert.deepEqual(plain(f.step(snap([3, ...zeros().slice(1)], zeros()))), []);
});

test('contador subiu com RPM abaixo de 1000 → apaga aquela banda', () => {
  const f = create();
  f.step(snap(zeros(), zeros()));
  f.observeRpm(1800); f.observeRpm(900);
  const g = zeros(); g[4] = 1;
  assert.deepEqual(plain(f.step(snap(g, zeros()))), [{ fuel: 'GAS', index: 4 }]);
});

test('carro parado (RPM 0) também apaga, na gasolina e no GNV', () => {
  const f = create();
  f.step(snap(zeros(), zeros()));
  f.observeRpm(0);
  const g = zeros(), p = zeros(); g[2] = 2; p[9] = 1;
  assert.deepEqual(plain(f.step(snap(g, p))), [{ fuel: 'GAS', index: 2 }, { fuel: 'PETROL', index: 9 }]);
});

test('RPM sempre ≥ 1000 na janela → nada é apagado', () => {
  const f = create();
  f.step(snap(zeros(), zeros()));
  f.observeRpm(1000); f.observeRpm(2500);
  const g = zeros(); g[7] = 5;
  assert.deepEqual(plain(f.step(snap(g, zeros()))), []);
});

test('RPM desconhecido nunca é tratado como baixo; contador que desce não apaga', () => {
  const f = create();
  const g0 = zeros(); g0[1] = 4;
  f.step(snap(g0, zeros()));
  f.observeRpm(null); f.observeRpm(NaN);
  const g1 = zeros(); g1[1] = 6; g1[3] = 1;
  assert.deepEqual(plain(f.step(snap(g1, zeros()))), []);
  f.observeRpm(300);
  assert.deepEqual(plain(f.step(snap(zeros(), zeros()))), []);
});

test('a janela de RPM zera a cada passo e o apagar não gera laço', () => {
  const f = create();
  f.step(snap(zeros(), zeros()));
  f.observeRpm(400);
  const g = zeros(); g[0] = 1;
  assert.equal(f.step(snap(g, zeros())).length, 1);
  g[0] = 2; // subiu de novo, mas agora o motor passou de 1000
  f.observeRpm(1500);
  assert.deepEqual(plain(f.step(snap(g, zeros()))), []);
});

test('o cockpit liga o filtro ao caminho de apagar já existente e ele está no index', () => {
  const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
  assert.match(cockpit, /LowRpmFilter/);
  assert.match(cockpit, /this\.lowRpmFilter\.step\(/);
  assert.match(cockpit, /reacquireTargets\(/);
  assert.match(fs.readFileSync(path.join(root, 'app/src/main/assets/ui/index.html'), 'utf8'), /core\/low-rpm-filter\.js/);
});
