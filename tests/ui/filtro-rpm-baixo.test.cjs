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
  assert.match(cockpit, /filter\.step\(/);
  assert.match(cockpit, /clearer\?\.request\(targets/);
  assert.match(fs.readFileSync(path.join(root, 'app/src/main/assets/ui/index.html'), 'utf8'), /core\/ponto-limpeza\.js/);
  assert.match(fs.readFileSync(path.join(root, 'app/src/main/assets/ui/index.html'), 'utf8'), /core\/low-rpm-filter\.js/);
});

test('limite configurável: função devolve o valor atual a cada passo', () => {
  let limite = 1000;
  const f = create({ minRpm: () => limite });
  f.step(snap(zeros(), zeros()));
  f.observeRpm(1200);
  assert.deepEqual(plain(f.step(snap([1, ...zeros().slice(1)], zeros()))), []);
  limite = 1500;
  f.observeRpm(1200);
  assert.deepEqual(plain(f.step(snap([2, ...zeros().slice(1)], zeros()))), [{ fuel: 'GAS', index: 0 }]);
});

test('limite 0 desliga o filtro (nunca apaga)', () => {
  const f = create({ minRpm: () => 0 });
  f.step(snap(zeros(), zeros()));
  f.observeRpm(0);
  assert.deepEqual(plain(f.step(snap([1, ...zeros().slice(1)], zeros()))), []);
});

test('preferência minRpmLearn: padrão 1000, cartão no grupo Aprendizado, limites 0 a 3000', () => {
  const P = require(path.join(root, 'app/src/main/assets/ui/core/autocal-preferencias.js'));
  assert.equal(P.DEFAULTS.minRpmLearn, 1000);
  const html = P.panelHtml({ prefs: P.DEFAULTS, values: {}, drafts: {}, notes: {} });
  assert.match(html, /data-pref="minRpmLearn"/);
  assert.match(html, /RPM mínimo/);
});

test('RPM mínimo sem teclado: valor só leitura, atalhos de um toque e frase do efeito', () => {
  const P = require(path.join(root, 'app/src/main/assets/ui/core/autocal-preferencias.js'));
  const html = P.panelHtml({ prefs: P.DEFAULTS, values: {}, drafts: {}, notes: {} });
  const campo = html.match(/<input[^>]*data-pref="minRpmLearn"[^>]*>/)[0];
  assert.match(campo, /readonly/);
  assert.match(campo, /inputmode="none"/);
  for (const v of [0, 800, 1000, 1200, 1500]) assert.match(html, new RegExp('data-pref-local-set="minRpmLearn:' + v + '"'));
  assert.match(html, /Apaga o ponto que entrar abaixo de 1000 rpm/);
  assert.match(P.panelHtml({ prefs: Object.assign({}, P.DEFAULTS, { minRpmLearn: 0 }), values: {}, drafts: {}, notes: {} }), /Desligado: nenhum ponto é apagado/);
});

// ---- Rodada de causa raiz (dono: "não funciona direito e é lento") ----
const nul = () => new Array(18).fill(null);

test('contador desconhecido (null) que passa a ser conhecido NÃO conta como subida (desconhecido nunca é 0)', () => {
  const f = create();
  f.step(snap(nul(), nul()));
  f.observeRpm(300);
  const g = nul(); g[6] = 4;
  assert.deepEqual(plain(f.step(snap(g, nul()))), []);
});

test('mesma leitura repetida não gasta a janela de RPM: o mergulho de RPM espera a leitura nova', () => {
  const f = create();
  f.step(snap(zeros(), zeros()), { id: 'a' });
  f.observeRpm(600);
  assert.deepEqual(plain(f.step(snap(zeros(), zeros()), { id: 'a' })), []); // repetida: não consome
  const g = zeros(); g[3] = 2;
  assert.deepEqual(plain(f.step(snap(g, zeros()), { id: 'b' })), [{ fuel: 'GAS', index: 3 }]);
});

test('leitura nova sem subida zera a janela: RPM baixo antigo não acusa subida de agora', () => {
  const f = create();
  f.step(snap(zeros(), zeros()), { id: 'a' });
  f.observeRpm(600);
  f.step(snap(zeros(), zeros()), { id: 'b' }); // chegou leitura, nada subiu: janela termina aqui
  f.observeRpm(2500);
  const g = zeros(); g[3] = 2;
  assert.deepEqual(plain(f.step(snap(g, zeros()), { id: 'c' })), []);
});

test('troca de geração/sessão da central reinicia a linha de base (nada é apagado)', () => {
  const f = create();
  f.step(snap(zeros(), zeros()), { id: 'a', epoch: { GAS: 's1:1', PETROL: 's1:1' } });
  f.observeRpm(300); // motor dando partida
  const g = zeros(); g[1] = 9; // acumulou enquanto o app estava fora
  assert.deepEqual(plain(f.step(snap(g, zeros()), { id: 'b', epoch: { GAS: 's2:1', PETROL: 's2:1' } })), []);
  f.observeRpm(300);
  g[1] = 10;
  assert.deepEqual(plain(f.step(snap(g, zeros()), { id: 'c', epoch: { GAS: 's2:1', PETROL: 's2:1' } })), [{ fuel: 'GAS', index: 1 }]);
});

test('reset() esquece a linha de base (aba fora de vista, USB caiu, leitura indisponível)', () => {
  const f = create();
  f.step(snap(zeros(), zeros()));
  f.reset();
  f.observeRpm(200);
  const g = zeros(); g[0] = 7;
  assert.deepEqual(plain(f.step(snap(g, zeros()))), []);
});

test('a leitura do filtro roda em qualquer aba e o RPM é amostrado no ritmo rápido, não de 1 em 1 s', () => {
  const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
  const fast = cockpit.slice(cockpit.indexOf("addHook('fast'"), cockpit.indexOf("addHook('fast'") + 500);
  assert.ok(fast.indexOf('observeRpm') > 0 && fast.indexOf('observeRpm') < fast.indexOf("route !== 'autocal'"), 'RPM amostrado antes do filtro de aba');
  const status = cockpit.slice(cockpit.indexOf("addHook('status'"), cockpit.indexOf("addHook('status'") + 400);
  assert.match(status, /lowRpmTick\(/, 'o gancho de 1 s chama o filtro mesmo fora da aba AutoCal');
});

test('contadores do filtro vêm com null para desconhecido (nunca 0)', () => {
  const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
  assert.match(cockpit, /function vectorOrNull\(/);
  const a = cockpit.indexOf('lowRpmFeed(projection, actionState) {');
  assert.match(cockpit.slice(a, a + 2500), /vectorOrNull\(snapshot, 'NUM_BUF_UPD_GAS'\)/);
});
