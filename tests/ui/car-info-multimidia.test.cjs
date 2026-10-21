'use strict';
// Área "Multimídia (Car Info)" em Ferramentas › Mais ferramentas (dono, 2026-10-21: ver no carro, com os próprios olhos,
// se a multimídia entrega velocidade, odômetro etc.). Só leitura: o único controle é ligar/desligar a leitura
// (`setCarInfoEnabled`), num toque, sem janela. Desconhecido = "—", nunca 0.
// Parte 1 (sem navegador): a lógica pura (core/car-info-view.js) e a ponte (core/native-api.js).
// Parte 2 (Chromium, pula sem Playwright): a tela real em 1280x720, 1024x600 e 800x480, sem rolagem.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const T = require('./helpers/browser.cjs');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');
function load(...files) {
  const context = { console, Date, Math, JSON, Intl, Set, Map, Number, String, Array, Object, Proxy };
  context.window = context; context.globalThis = context;
  vm.createContext(context);
  for (const file of files) vm.runInContext(fs.readFileSync(path.join(UI, file), 'utf8'), context, { filename: file });
  return context;
}

const LABELS = ['Velocidade', 'Odômetro', 'Rotação', 'Marcha', 'Consumo agora', 'Consumo médio', 'Autonomia', 'Combustível', 'Temperatura da água'];
const NOW = 1_800_000_000_000;
const SAMPLE = { speed: 62, odo: 48211, rotationRate: 2150, gearShiftPosition: 3, instFuelCont: 7.4, avgFuelCont: 9.1, cruisingRange: 320, fuelPercentage: 55, coolantTemp: 88 };
const conectada = (sample = SAMPLE) => ({ source: 'multimidia', enabled: true, connected: true, state: 'conectado', lastError: '', failures: 0, samples: 120, lastSampleAtMs: NOW - 3000, nextRetryAtMs: null, sample });

function CV() { return load('core/car-info-view.js').OmegasUi.CarInfoView; }

test('cada rótulo aparece, com valor e unidade', () => {
  const C = CV();
  const v = C.view(conectada(), NOW);
  assert.deepEqual(Array.from(v.values, x => x.label), LABELS);   // Array.from: o vm é outro "realm"
  const html = C.html(v);
  for (const label of LABELS) assert.ok(html.includes(label), label);
  const by = Object.fromEntries(v.values.map(x => [x.key, x]));
  assert.equal(by.speed.text, '62'); assert.equal(by.speed.unit, 'km/h');
  assert.equal(by.odo.text, '48.211'); assert.equal(by.odo.unit, 'km');
  assert.equal(by.rotationRate.unit, 'rpm');
  assert.equal(by.fuelPercentage.unit, '%');
  assert.ok(html.includes('Multimídia (Car Info)'));
  assert.ok(html.includes('Ler da multimídia'));
});

test('null vira "—", nunca "0"; zero de verdade continua 0', () => {
  const C = CV();
  const nulos = Object.fromEntries(Object.keys(SAMPLE).map(k => [k, null]));
  const v = C.view(conectada({ ...nulos, speed: 0 }), NOW);
  for (const x of v.values) {
    if (x.key === 'speed') assert.equal(x.text, '0', 'zero real');
    else assert.equal(x.text, '—', x.label);
  }
  const semAmostra = C.view({ ...conectada(), sample: null }, NOW);
  assert.ok(semAmostra.values.every(x => x.text === '—'));
  assert.doesNotMatch(C.html(semAmostra).replace(/\d+ leituras?/, ''), />0</, 'nenhum 0 inventado');
});

test('valor negativo onde não existe (consumo -1,0 do campo) vira "—"', () => {
  const C = CV();
  const v = C.view(conectada({ ...SAMPLE, instFuelCont: -1, speed: -1, coolantTemp: -3 }), NOW);
  const by = Object.fromEntries(v.values.map(x => [x.key, x.text]));
  assert.equal(by.instFuelCont, '—'); assert.equal(by.speed, '—');
  assert.equal(by.coolantTemp, '-3', 'temperatura negativa é possível');
  assert.equal(by.avgFuelCont, '9,1');
});

test('estado em uma frase: conectada, procurando, indisponível com motivo, desligada', () => {
  const C = CV();
  assert.equal(C.view(conectada(), NOW).sentence, 'Conectada e lendo');
  const procurando = C.view({ ...conectada(null), connected: false, state: 'indisponivel', lastError: 'Multimídia recusou a ligação', failures: 2, nextRetryAtMs: NOW + 20_000, samples: 0, lastSampleAtMs: null }, NOW);
  assert.equal(procurando.sentence, 'Procurando a multimídia… (tentativa 3, próxima em 20 s)');
  assert.match(procurando.meta, /Multimídia recusou a ligação/);
  const ligando = C.view({ ...conectada(null), connected: false, state: 'conectando', failures: 0, samples: 0, lastSampleAtMs: null }, NOW);
  assert.equal(ligando.sentence, 'Procurando a multimídia… (tentativa 1)');
  const indisponivel = C.view({ ...conectada(null), connected: false, state: 'indisponivel', lastError: 'Ligação com a multimídia morreu', failures: 1, nextRetryAtMs: null }, NOW);
  assert.equal(indisponivel.sentence, 'Indisponível: Ligação com a multimídia morreu');
  const desligada = C.view({ ...conectada(), enabled: false, connected: false, state: 'desligado' }, NOW);
  assert.equal(desligada.sentence, 'Desligada');
  assert.ok(desligada.values.every(x => x.text === '—'), 'desligada não mostra leitura velha');
  assert.equal(C.view(null, NOW).sentence, 'Indisponível: só funciona no app');
});

test('idade da leitura e contador de leituras', () => {
  const C = CV();
  assert.equal(C.view(conectada(), NOW).meta, 'Última leitura há 3 s · 120 leituras');
  assert.equal(C.view({ ...conectada(null), samples: 0, lastSampleAtMs: null }, NOW).meta, 'Nenhuma leitura ainda · 0 leituras');
});

test('dica de diagnóstico só quando conectada e tudo "—"', () => {
  const C = CV();
  const DICA = 'Conectou, mas o carro não mandou velocidade/odômetro: o Peugeot pode usar outro canal.';
  assert.equal(C.view(conectada(null), NOW).hint, DICA);
  assert.ok(C.html(C.view(conectada(null), NOW)).includes(DICA));
  assert.equal(C.view(conectada(), NOW).hint, '');
  assert.equal(C.view({ ...conectada(null), connected: false, state: 'indisponivel' }, NOW).hint, '');
});

test('linha de diagnóstico: canais ativos N de 17, só quando conectada e com contagem', () => {
  const C = CV();
  const info = { ...conectada(), channels: { channelsActive: 3, channelsTotal: 17, tx: { 6: { n: 40, bytes: 76 }, 2: { n: 1, bytes: 92 } } } };
  assert.equal(C.view(info, NOW).diag, 'Canais ativos: 3 de 17');
  assert.ok(C.html(C.view(info, NOW)).includes('Canais ativos: 3 de 17'));
  assert.equal(C.view(conectada(), NOW).diag, '', 'sem contagem da ponte não inventa');
  assert.equal(C.view({ ...info, connected: false, state: 'indisponivel' }, NOW).diag, '');
});

test('Detalhes mostram o canal de cada valor e as chamadas por canal (escondido por padrão)', () => {
  const C = CV();
  const info = { ...conectada({ ...SAMPLE, rotationRate: 900, sources: { speed: 'tx6', rotationRate: 'tx17', odo: 'tx6' } }), channels: { channelsActive: 2, channelsTotal: 17, tx: { 6: { n: 40, bytes: 76 }, 17: { n: 3, bytes: 60 } } } };
  const v = C.view(info, NOW);
  const html = C.html(v);
  assert.match(html, /<details class="carinfo-details"(?! open)/);
  assert.match(html, /Detalhes/);
  const by = Object.fromEntries(v.details.sources.map(x => [x.key, x.source]));
  assert.equal(by.rotationRate, 'tx17'); assert.equal(by.speed, 'tx6');
  assert.ok(!('instFuelCont' in by) || by.instFuelCont, 'só valores conhecidos têm canal');
  assert.match(html, /Rotação: tx17/);
  assert.match(html, /6: 40/); assert.match(html, /17: 3/);
  assert.match(C.html(v, { detailsOpen: true }), /<details class="carinfo-details" open/);
});

test('dica da velocidade: conectada há mais de 60 s, rotação > 1000, velocidade e odômetro "—"', () => {
  const C = CV();
  const DICA = 'A central não mandou velocidade. Os dados brutos foram gravados na sessão.';
  const base = { ...conectada({ ...SAMPLE, speed: null, odo: null, rotationRate: 1500 }), firstSampleAtMs: NOW - 61_000 };
  assert.equal(C.view(base, NOW).hint, DICA);
  assert.ok(C.html(C.view(base, NOW)).includes(DICA));
  assert.equal(C.view({ ...base, firstSampleAtMs: NOW - 30_000 }, NOW).hint, '', 'antes de 60 s não avisa');
  assert.equal(C.view({ ...base, sample: { ...base.sample, rotationRate: 900 } }, NOW).hint, '', 'rotação baixa: carro pode estar parado');
  assert.equal(C.view({ ...base, sample: { ...base.sample, speed: 40 } }, NOW).hint, '', 'velocidade chegou');
  assert.equal(C.view({ ...base, sample: { ...base.sample, odo: 100 } }, NOW).hint, '', 'odômetro chegou');
  // Rotação da ECU do GNV vale quando a multimídia não mandou a dela (campo: rotação nula).
  const semRpm = { ...base, sample: { ...base.sample, rotationRate: null } };
  assert.equal(C.view(semRpm, NOW).hint, '');
  assert.equal(C.view(semRpm, NOW, 2200).hint, DICA);
  assert.equal(C.view(semRpm, NOW, 0).hint, '');
});

test('a chave chama setCarInfoEnabled com o contrário do estado, num toque', () => {
  const C = CV();
  const calls = [];
  const api = { setCarInfoEnabled: on => { calls.push(on); return { ok: true, enabled: on }; } };
  C.toggle(api, C.view(conectada(), NOW));
  C.toggle(api, C.view({ ...conectada(), enabled: false, state: 'desligado' }, NOW));
  assert.deepEqual(calls, [false, true]);
  assert.equal(C.toggle(api, C.view(null, NOW)), null, 'sem leitura da ponte não chama nada');
  assert.deepEqual(calls, [false, true]);
});

test('ponte: NativeApi lê getCarInfo e repassa setCarInfoEnabled ao OmegasNative', () => {
  const ctx = load('core/native-api.js');
  const calls = [];
  ctx.OmegasNative = {
    getCarInfo: () => JSON.stringify(conectada()),
    setCarInfoEnabled: on => { calls.push(on); return JSON.stringify({ ok: true, enabled: on }); },
  };
  const api = new ctx.OmegasUi.NativeApi();
  assert.equal(api.carInfo().state, 'conectado');
  assert.equal(api.setCarInfoEnabled(false).enabled, false);
  assert.deepEqual(calls, [false]);
  const kotlin = fs.readFileSync(path.join(__dirname, '../../app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt'), 'utf8');
  assert.match(kotlin, /@JavascriptInterface\s+fun getCarInfo\(\)/, 'a ponte Kotlin expõe a leitura do painel');
});

// ---------- Parte 2: tela real ----------
const skip = T.skipReason() || false;
const VIEWPORTS = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };

for (const [nome, viewport] of Object.entries(VIEWPORTS)) {
  test(`tela real ${nome}: área visível sem rolagem, valores ao vivo e chave de um toque`, { skip }, async () => {
    const browser = await T.launch();
    try {
      const app = await T.openApp(browser, { viewport, scale: 1 });
      const { page } = app;
      await page.evaluate(({ info }) => {
        let state = info;
        window.__carCalls = [];
        Object.assign(window.OmegasNative, {
          getCarInfo: () => JSON.stringify({ ...state, lastSampleAtMs: Date.now() - 2000 }),
          setCarInfoEnabled: on => { window.__carCalls.push(on); state = { ...state, enabled: on, connected: on, state: on ? 'conectado' : 'desligado' }; return JSON.stringify({ ok: true, enabled: on }); },
        });
      }, { info: { ...conectada(), channels: { channelsActive: 3, channelsTotal: 17, tx: { 2: { n: 1, bytes: 92 }, 6: { n: 40, bytes: 76 }, 17: { n: 3, bytes: 60 } } } } });
      await T.route(app, 'tools');
      await T.tap(page, '#toolsPageNext');
      await T.pump(page, 2);
      await T.tap(page, '[data-tool-open-carinfo]');
      await T.pump(page, 2);
      const view = '#toolDiagnosticsWorkspace [data-card="carinfo"]';
      await page.waitForSelector(view, { state: 'visible' });
      const text = await page.locator(view).innerText();
      for (const label of LABELS) assert.ok(text.includes(label), `${nome}: falta ${label}`);
      assert.match(text, /Conectada e lendo/);
      assert.match(text, /48\.211/);
      assert.match(text, /Última leitura há \d+ s · 120 leituras/);
      // Sem rolagem: tudo dentro da tela e do próprio cartão.
      const fora = await page.evaluate(sel => {
        const host = document.querySelector(sel);
        const r0 = host.getBoundingClientRect();
        const out = [];
        if (host.scrollHeight > host.clientHeight + 1 || host.scrollWidth > host.clientWidth + 1) out.push('a área rola');
        for (const e of host.querySelectorAll('h3, button, summary, [data-field], p')) {
          if (typeof e.checkVisibility === 'function' && !e.checkVisibility()) continue;   // Detalhes fechado: conteúdo oculto não ocupa tela
          const r = e.getBoundingClientRect();
          if (r.bottom > Math.min(innerHeight, r0.bottom) + 1 || r.right > innerWidth + 1 || r.top < 0 || r.left < 0) out.push((e.dataset.field || e.tagName) + ' fora');
        }
        return out;
      }, view);
      assert.deepEqual(fora, [], nome);
      assert.ok(await page.locator(view + ' details.carinfo-details > summary').isVisible(), `${nome}: Detalhes visível`);
      // Detalhes aberto (canais) também cabe, sem rolagem.
      await page.evaluate(() => { document.querySelector('.carinfo-details').open = true; });
      await T.pump(page, 1);
      const aberto = await page.evaluate(sel => {
        const host = document.querySelector(sel);
        const d = document.querySelector('.carinfo-details').getBoundingClientRect();
        return { rola: host.scrollHeight > host.clientHeight + 1, dentro: d.bottom <= Math.min(innerHeight, host.getBoundingClientRect().bottom) + 1 };
      }, view);
      assert.deepEqual(aberto, { rola: false, dentro: true }, `${nome}: Detalhes aberto`);
      // Um toque = desliga, sem janela.
      await T.tap(page, '[data-carinfo-toggle]');
      await T.pump(page, 2);
      assert.deepEqual(await page.evaluate(() => window.__carCalls), [false]);
      assert.match(await page.locator(view).innerText(), /Desligada/);
      assert.equal(await page.locator('#toolsLayer:not([hidden])').count(), 0, 'nenhuma janela');
      assert.deepEqual(app.problems.page, [], 'erro de página');
      await app.context.close();
    } finally { await browser.close(); }
  });
}
