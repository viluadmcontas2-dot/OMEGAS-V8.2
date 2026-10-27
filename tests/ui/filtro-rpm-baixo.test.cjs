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

// ---- 2026-10-27: melhorias do filtro (PENDENTE.md, "Problemas por impacto"; só lógica do app) ----

test('contador saturado em 10 não sobe, mas a banda que mudou de valor guardado conta como medição nova', () => {
  const f = create();
  const g = zeros(); g[3] = 10;
  const v0 = { GAS: zeros(), PETROL: zeros() }; v0.GAS[3] = 2.1;
  f.step(snap(g, zeros()), { id: 1, values: v0 });
  f.observeRpm(500);
  const v1 = { GAS: zeros(), PETROL: zeros() }; v1.GAS[3] = 2.2;
  assert.deepEqual(plain(f.step(snap(g, zeros()), { id: 2, values: v1 })), [{ fuel: 'GAS', index: 3 }]);
});

test('contador saturado e valor guardado igual → nada a apagar; abaixo de 10 o valor não decide', () => {
  const f = create();
  const g = zeros(); g[3] = 10; g[4] = 6;
  const v = () => ({ GAS: zeros().map((_, i) => (i === 3 ? 2.1 : 0)), PETROL: zeros() });
  f.step(snap(g, zeros()), { id: 1, values: v() });
  f.observeRpm(500);
  assert.deepEqual(plain(f.step(snap(g, zeros()), { id: 2, values: v() })), []);
  // valor mudou numa banda NÃO saturada, sem o contador subir: o contador manda (nada)
  f.observeRpm(500);
  const w = v(); w.GAS[4] = 9;
  assert.deepEqual(plain(f.step(snap(g, zeros()), { id: 3, values: w })), []);
});

test('freio do laço: a mesma banda apagada 3 vezes no mesmo episódio de RPM baixo para de ser apagada e avisa 1 vez', () => {
  const f = create();
  const g = zeros();
  f.step(snap(g, zeros()), { id: 0 });
  const hits = [];
  for (let i = 1; i <= 5; i += 1) {
    f.observeRpm(700);
    g[2] += 1;
    hits.push(f.step(snap(g.slice(), zeros()), { id: i }).length);
  }
  assert.deepEqual(hits, [1, 1, 1, 0, 0]);
  assert.deepEqual(plain(f.takeNotice()), { braked: ['GAS:2'] });
  assert.equal(f.takeNotice(), null, 'avisa uma vez só');
});

test('freio do laço: o motor passar do limite encerra o episódio e libera a banda', () => {
  const f = create();
  const g = zeros();
  f.step(snap(g, zeros()), { id: 0 });
  for (let i = 1; i <= 3; i += 1) { f.observeRpm(700); g[2] += 1; f.step(snap(g.slice(), zeros()), { id: i }); }
  f.observeRpm(2000); g[2] += 1; f.step(snap(g.slice(), zeros()), { id: 4 }); // passou de 1000: episódio acabou
  f.observeRpm(700); g[2] += 1;
  assert.equal(f.step(snap(g.slice(), zeros()), { id: 5 }).length, 1);
});

test('amostras seguidas: com minSamples 3, um mergulho de 1 ou 2 leituras de RPM (troca de marcha) não condena', () => {
  const f = create({ minSamples: 3 });
  const g = zeros();
  f.step(snap(g, zeros()), { id: 0 });
  f.observeRpm(2000); f.observeRpm(800); f.observeRpm(2000); f.observeRpm(800); f.observeRpm(800); f.observeRpm(2000);
  g[1] += 1;
  assert.deepEqual(plain(f.step(snap(g.slice(), zeros()), { id: 1 })), []);
  f.observeRpm(800); f.observeRpm(780); f.observeRpm(760);
  g[1] += 1;
  assert.deepEqual(plain(f.step(snap(g.slice(), zeros()), { id: 2 })), [{ fuel: 'GAS', index: 1 }]);
});

test('amostras seguidas: a sequência baixa vale mesmo atravessando duas leituras do contador', () => {
  const f = create({ minSamples: 3 });
  const g = zeros();
  f.step(snap(g, zeros()), { id: 0 });
  f.observeRpm(800); f.observeRpm(790);
  assert.deepEqual(plain(f.step(snap(g.slice(), zeros()), { id: 1 })), []);
  f.observeRpm(780);
  g[0] += 1;
  assert.deepEqual(plain(f.step(snap(g.slice(), zeros()), { id: 2 })), [{ fuel: 'GAS', index: 0 }]);
});

test('cockpit: o id da leitura é o carimbo dos contadores (não o do snapshot completo) e o filtro pede 3 amostras seguidas', () => {
  const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
  const ctx = { console, setTimeout: () => 0, clearTimeout: () => {} };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(cockpit, ctx);
  const C = ctx.OmegasUi.AutoCalCockpit;
  const steps = [];
  const c = Object.assign(Object.create(C.prototype), {
    clearer: { onActionState() {}, onSnapshot() {}, request() {} },
    lowRpmFilter: { step: (counters, meta) => { steps.push({ counters: plain(counters), meta: plain(meta) }); return []; }, reset() {}, takeNotice: () => null },
    store: { patch() {} },
  });
  const field = (key, at, raw, phys) => ({ key, status: 'VALID', capturedAtMs: at, rawValues: raw, physicalValues: phys });
  const projection = (stampGas, stampPetr) => ({
    ok: true,
    nativeSnapshot: {
      available: true, capturedAtMs: 5000, snapshotHash: 'h',
      fields: [
        field('NUM_BUF_UPD_GAS', stampGas, new Array(18).fill(1)), field('NUM_BUF_UPD_PETR', stampPetr, new Array(18).fill(0)),
        field('PETR_INJ_TBUF_GAS', 1, null, new Array(18).fill(2.5)), field('MNFLD_PRESS_BUF_GAS', 1, null, new Array(18).fill(0.4)),
      ],
    },
  });
  c.lowRpmFeed(projection(7100, 7200), {});
  c.lowRpmFeed(projection(8100, 8200), {});
  assert.equal(steps[0].meta.id, 7200, 'maior carimbo dos dois contadores');
  assert.equal(steps[1].meta.id, 8200, 'muda a cada leitura dos contadores, mesmo com o snapshot completo parado em 5000');
  assert.equal(steps[0].meta.values.GAS[0], '2.5|0.4', 'valor guardado = injeção|MAP da banda');
  assert.equal(steps[0].meta.values.PETROL[0], null, 'sem leitura = desconhecido, nunca 0');
  assert.match(cockpit, /LowRpmFilter\?\.create\?\.\(\{[^}]*minSamples: 3/);
});

test('cockpit: voltar de segundo plano (ou sair) esquece a linha de base do filtro', () => {
  const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
  assert.match(cockpit, /addEventListener\?\.\('visibilitychange'/);
  const ctx = { console, setTimeout: () => 0, clearTimeout: () => {} };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(cockpit, ctx);
  let resets = 0;
  const c = Object.assign(Object.create(ctx.OmegasUi.AutoCalCockpit.prototype), { lowRpmFilter: { reset: () => { resets += 1; } } });
  c.onPageVisibility();
  assert.equal(resets, 1);
});

test('cockpit: o freio do laço avisa o dono uma vez, em português simples', () => {
  const cockpit = fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8');
  const ctx = { console, setTimeout: () => 0, clearTimeout: () => {} };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(cockpit, ctx);
  const alerts = [];
  const c = Object.assign(Object.create(ctx.OmegasUi.AutoCalCockpit.prototype), {
    minRpmLearn: 1000, store: { patch: p => alerts.push(p.alert) },
    clearer: { onActionState() {}, onSnapshot() {}, request() {} },
    lowRpmFilter: { step: () => [], reset() {}, takeNotice: () => ({ braked: ['GAS:2'] }) },
  });
  c.lowRpmFeed({ ok: true, nativeSnapshot: { available: true, capturedAtMs: 1, fields: [] } }, {});
  assert.equal(alerts.length, 1);
  assert.equal(alerts[0].level, 'warning');
  assert.match(alerts[0].message, /Parei de apagar/);
  assert.match(alerts[0].message, /1000 rpm/);
});
