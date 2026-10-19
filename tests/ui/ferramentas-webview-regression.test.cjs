'use strict';
// Regressão do WebView: Ferramentas não pode depender da leitura auxiliar para aparecer.
// O ensaio de integração sem navegador é obrigatório; o visual roda quando Chromium está disponível.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const T = require('./helpers/browser.cjs');

function runBridgeFailure(appSource, { incompletePanel = false } = {}) {
  const tasks = [];
  const host = { innerHTML: '', addEventListener() {}, contains() { return false; }, querySelector() { return null; }, dataset: {} };
  const nodes = { toolAdvanced: host };
  const element = id => nodes[id] || null;
  class Store {
    constructor(initial) { this.state = initial; this.subscribers = []; }
    get() { return this.state; }
    patch(next) { Object.assign(this.state, next); for (const fn of this.subscribers) fn(this.state); }
    subscribe(fn, initial) { this.subscribers.push(fn); if (initial) fn(this.state); }
  }
  class Router {
    constructor(store) { this.store = store; }
    restore() { this.store.patch({ route: 'autocal' }); return 'autocal'; }
    navigate(route) {
      const prev = this.store.get().route;
      this.store.patch({ route });
      this.onNavigate?.(route, prev);
    }
  }
  class Scheduler {
    constructor(config) { this.config = config; }
    setCadenceMs() {}
    start() {}
    run() { this.config.onContext(); }
  }
  class NativeApi {
    releaseIdentity() { return {}; }
    isDemo() { return false; }
    status() { return { usbConnected: false }; }
    scienceSnapshotSince() { throw Error('leitura auxiliar indisponível'); }
    sessionStatus() { return {}; }
    logs() { return []; }
    sessions() { return []; }
  }
  class Drawers { render() {} }
  class AdvancedPanel {
    constructor() { this.flag = incompletePanel ? null : {}; }
    refresh() {
      host.innerHTML = '<article class="tool-tile"><h3>Anti-apagão</h3></article>' +
        '<article class="tool-tile"><h3>Marcha lenta</h3></article>';
    }
  }
  const document = {
    head: { appendChild() {} },
    body: { classList: { toggle() {} } },
    activeElement: null,
    createElement: () => ({}),
    getElementById: element,
    querySelectorAll: () => [],
    addEventListener() {},
  };
  const root = {
    document, console, Intl,
    setTimeout(fn) { tasks.push(fn); return tasks.length; },
    clearTimeout() {},
    addEventListener() {},
    dispatchEvent() {},
    OmegasUi: { Store, Router, Scheduler, NativeApi, Drawers, AdvancedPanel, createInitialState: () => ({ route: 'autocal', status: {} }) },
  };
  root.window = root;
  vm.runInNewContext(appSource, root, { filename: 'app.js' });
  root.OmegasApp.router.navigate('tools');
  // No WebView, o callback assíncrono de leitura pode lançar. O painel já deve existir,
  // independentemente de quantas tarefas do agendador consigam prosseguir.
  for (const fn of tasks.splice(0, 10)) { try { fn(); } catch (_) {} }
  return host.innerHTML;
}

test('RED histórico: v8 deixa Ferramentas vazia quando a consulta auxiliar falha', () => {
  const current = fs.readFileSync(path.join(T.UI_DIR, 'app.js'), 'utf8');
  const immediatePaint = "if (route === 'tools') {\n        try { if (advanced) advanced.refresh(); else toolsUnavailable(); }";
  const safeRead = "try { science = api.scienceSnapshotSince(scienceRevision) || {}; }\n      catch (error) { console.error('[OMEGAS] Leitura auxiliar indisponível', error); }";
  assert.ok(current.includes(immediatePaint) && current.includes(safeRead), 'seam de pintura/leitura mudou');
  const baseline = current
    .replace(immediatePaint, "if (route === 'tools') {\n        try { /* RED: tela aguardava a leitura */ }")
    .replace(safeRead, "science = api.scienceSnapshotSince(scienceRevision) || {};");
  assert.doesNotMatch(runBridgeFailure(baseline), /Anti-apagão/, 'o mutante reproduz a aba vazia');
});

test('GREEN: Ferramentas desenha cartões apesar de falha na leitura auxiliar', () => {
  const current = fs.readFileSync(path.join(T.UI_DIR, 'app.js'), 'utf8');
  const html = runBridgeFailure(current);
  assert.match(html, /Anti-apagão/, 'a aba Ferramentas ficou vazia');
  assert.match(html, /Marcha lenta/, 'outro cartão deve continuar visível');
});

test('se a carga dos cartões falhar parcialmente, o estado não exige nova tentativa manual', () => {
  const current = fs.readFileSync(path.join(T.UI_DIR, 'app.js'), 'utf8');
  const html = runBridgeFailure(current, { incompletePanel: true });
  assert.match(html, /Ferramentas indisponíveis/);
  assert.doesNotMatch(html, /tocar|tente de novo/i);
});

const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

for (const [name, viewport] of Object.entries({
  '1280x720': { width: 1280, height: 720 },
  '1024x600': { width: 1024, height: 600 },
  '800x480': { width: 800, height: 480 },
  '1280x720-HDPI': { width: 640, height: 360 },
})) {
  for (const usb of [false, true]) {
    test(`WebView file:// Ferramentas ${name} ${usb ? 'conectada' : 'desconectada'}: cartões têm área e o comando pode receber toque`, { skip }, async () => {
      const app = await T.openApp(browser, { viewport, usb });
      const { page } = app;
      try {
        // O erro simula uma chamada que só falha na ponte nativa do aparelho.
        await page.evaluate(() => { window.OmegasApp.api.scienceSnapshotSince = () => { throw Error('falha da ponte'); }; });
        await T.tap(page, '.side-nav [data-route="tools"]');
        await page.waitForSelector('#toolAdvanced [data-card="anti"]', { state: 'visible', timeout: 8000 });
        for (const card of ['anti', 'lenta']) {
          const rect = await page.locator(`#toolAdvanced [data-card="${card}"]`).boundingBox();
          assert.ok(rect && rect.height > 0 && rect.width > 0, `cartão ${card} invisível`);
        }
        const button = page.locator('#toolAdvanced [data-adv-switch]');
        const rect = await button.boundingBox();
        assert.ok(rect && rect.width > 0 && rect.height > 0, 'comando de ligar não está visível');
        assert.equal(await button.isDisabled(), !usb, 'sem a central não pode gravar');
        if (usb) {
          await T.tap(page, '[data-adv-switch]');
          await page.waitForSelector('#toolsLayer [data-adv-confirm]');
          const frames = await T.frames(page);
          assert.deepEqual(T.writesOf(frames.log), [], 'primeiro toque nunca grava');
        }
        assert.deepEqual(app.problems.requests, [], 'script ou arquivo não carregado');
      } finally { await app.context.close(); }
    });
  }
}

test('script de cartão permanentemente incompleto: informa erro sem exigir toque manual', { skip }, async () => {
  const dir = T.mutatedUi([['core/cartoes-motor.js', 'ns.CartoesMotor = { build, stateOf };', '/* arquivo chegou incompleto */']]);
  const app = await T.openApp(browser, { ui: dir, viewport: { width: 1280, height: 720 } });
  try {
    await T.tap(app.page, '.side-nav [data-route="tools"]');
    await app.page.waitForSelector('#toolAdvanced [data-card="unavailable"]');
    assert.match(await app.page.locator('#toolAdvanced').innerText(), /Ferramentas indisponíveis/);
    assert.equal(await app.page.locator('#toolAdvanced [data-tools-retry]').count(), 0);
  } finally {
    await app.context.close();
    require('node:fs').rmSync(dir, { recursive: true, force: true });
  }
});

test('cartão com defeito exibe aviso sem exigir toque e preserva os outros', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 } });
  try {
    await T.tap(app.page, '.side-nav [data-route="tools"]');
    await app.page.waitForSelector('#toolAdvanced [data-card="anti"]');
    await app.page.evaluate(() => {
      const original = window.OmegasUi.CartoesMotor.build;
      window.OmegasUi.CartoesMotor.build = (...args) => original(...args).map(c =>
        c.id === 'corte' ? Object.defineProperty(c, 'title', { get() { throw Error('cartão falhou'); } }) : c);
      window.__bench.deliver();
    });
    await T.tap(app.page, '[data-adv-open="valores"]');
    await T.tap(app.page, '[data-adv-back]');
    assert.match(await app.page.locator('#toolAdvanced [data-card="corte"]').innerText(), /Leitura indisponível/);
    assert.equal(await app.page.locator('[data-adv-retry], [data-tools-retry]').count(), 0);
    assert.equal(await app.page.locator('#toolAdvanced [data-card="anti"]').count(), 1);
    assert.equal(await app.page.locator('#toolAdvanced [data-card="lenta"]').count(), 1);
  } finally { await app.context.close(); }
});


test('sem param_snapshot, Ferramentas usa a central conectada no primeiro toque e não inventa o estado', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await T.tap(page, '.side-nav [data-route="tools"]');
    // O AutoCal já recebe telemetria, mas o lote auxiliar de parâmetros
    // ainda não chegou (situação do aparelho que o banco antigo não simulava).
    await page.evaluate(() => { window.__bench.params = {}; window.__bench.advRead = false; });
    await T.pump(page, 3);
    assert.equal(await page.locator('#globalEcu').innerText(), 'Central conectada');
    assert.equal(await page.locator('[data-card="anti"] .tile-state').count(), 0);
    assert.equal(await page.locator('#toolAdvanced [data-adv-switch]').isDisabled(), false,
      'USB conectado não pode depender do param_snapshot geral para ler FLAG_CONF3');
    assert.match(await page.locator('[data-card="anti"]').innerText(), /Anti-apagão/);
    assert.match(await page.locator('#toolAdvanced [data-adv-switch]').innerText(), /—/);
    await page.evaluate(() => window.__bench.ecu.clearLog());
    await T.tap(page, '[data-adv-switch]');
    await T.pump(page, 4);
    const first = await T.frames(page);
    assert.deepEqual(T.writesOf(first.log), [], 'primeiro toque é SÓ leitura da ECU');
    await page.waitForSelector('#toolsLayer [data-adv-confirm]', { state: 'visible' });
    await T.tap(page, '#toolsLayer [data-adv-confirm]');
    await T.pump(page, 6);
    await page.waitForSelector('#toolsLayer .adv-result[data-level="ok"]', { state: 'visible' });
    const all = await T.frames(page);
    assert.deepEqual(T.writesOf(all.log).map(x => x.hex), ['35 FA 00 20 00 00 00 4F']);
    assert.deepEqual(await page.evaluate(() => window.__bench.ecu.get('29 FA 00 23')), [32, 0, 0, 0]);
  } finally { await app.context.close(); }
});

test('sem param_snapshot, central com anti-apagão já ligado identifica estado real sem escrita', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await T.tap(page, '.side-nav [data-route="tools"]');
    await page.evaluate(() => {
      window.__bench.seed('29 FA 00 23', [0x20, 0, 0, 0]);
      window.__bench.params = {};
      window.__bench.advRead = false;
    });
    await T.pump(page, 3);
    assert.equal(await page.locator('#toolAdvanced [data-adv-switch]').isDisabled(), false);
    await T.tap(page, '[data-adv-switch]');
    await T.pump(page, 5);
    assert.deepEqual(T.writesOf((await T.frames(page)).log), [], 'consultar nunca desliga a função existente');
    assert.match(await page.locator('[data-card="anti"]').innerText(), /Ligado/);
    assert.equal(await page.locator('#toolsLayer [data-adv-confirm]').count(), 0);
  } finally { await app.context.close(); }
});


test('RED realidade: status auxiliar nativo falha, mas sete cartoes reais continuam operaveis', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  try {
    await app.page.evaluate(() => {
      window.OmegasAutoCal.getAntiStalloFlagStatus = () => { throw Error('status nativo ainda indisponivel'); };
      window.OmegasAutoCal.getAntiStalloValuesStatus = () => { throw Error('leitura auxiliar indisponivel'); };
    });
    await T.tap(app.page, '.side-nav [data-route="tools"]');
    await T.pump(app.page, 3);
    for (const id of ['anti', 'valores', 'corte', 'lenta', 'troca', 'injetores', 'volta']) {
      const card = app.page.locator('#toolAdvanced [data-card="' + id + '"]');
      assert.equal(await card.count(), 1, 'cartao real desapareceu: ' + id);
      const box = await card.boundingBox();
      assert.ok(box && box.width > 0 && box.height > 0, 'cartao fora da tela: ' + id);
    }
    assert.equal(await app.page.locator('[data-tools-retry], [data-adv-retry]').count(), 0, 'nao exigir toque para tentar de novo');
    const frames = await T.frames(app.page);
    assert.deepEqual(T.writesOf(frames.log), [], 'falha de status nunca grava na central');
  } finally { await app.context.close(); }
});

test('RED fluxo: dependencias criticas de Ferramentas fazem parte do carregamento inicial deterministico', () => {
  const html = fs.readFileSync(path.join(T.UI_DIR, 'index.html'), 'utf8');
  const srcs = Array.from(html.matchAll(/<script\s+src="([^"]+)"/g), m => m[1]);
  const deps = ['core/advanced-flags-data.js', 'core/advanced-flags.js', 'core/anti-apagao-valores.js',
    'core/idle-mode.js', 'core/cartoes-motor.js', 'components/drawers.js', 'components/advanced.js'];
  const indices = deps.map(src => srcs.indexOf(src));
  assert.ok(indices.every(i => i >= 0), 'a aba principal nao pode depender de scripts dinamicos no WebView: ' + srcs.join(', '));
  assert.deepEqual(indices, indices.slice().sort((a,b) => a-b), 'dependencias devem carregar na ordem');
  assert.ok(indices.at(-1) < srcs.indexOf('app.js'), 'Ferramentas deve estar pronta antes do shell');
});

test('RED UX: textos explicam contexto, efeito e unidades sem supor conhecimento do GNV', () => {
  const sources = ['core/advanced-flags-data.js', 'core/advanced-flags.js', 'core/anti-apagao-valores.js',
    'core/idle-mode.js', 'core/cartoes-motor.js'];
  const context = { console, Intl, JSON, Number, String, Math };
  context.window = context;
  vm.createContext(context);
  for (const src of sources) vm.runInContext(fs.readFileSync(path.join(T.UI_DIR, src), 'utf8'), context, { filename: src });
  const cards = context.OmegasUi.CartoesMotor.build({});
  const pick = id => cards.find(x => x.id === id);
  assert.match(pick('anti').line, /embreagem|manobra|parar/i);
  assert.match(pick('valores').line, /rota.c.o|limite/i);
  assert.match(pick('troca').line, /gasolina.*(?:g.s|GNV)/i);
  assert.match(pick('corte').line, /(?:retoma|volta).*aceler/i);
  assert.match(pick('lenta').line, /motor ligado|parar|manobra/i);
  assert.match(pick('injetores').line, /(?:acion|aliment).*(?:injetor|g.s)|injetores.*g.s/i);
  assert.match(pick('volta').line, /rota..es|giro alto/i);
  const fields = context.OmegasUi.AntiApagaoValores.FIELDS;
  assert.ok(fields.filter(f => f.unit === 'rpm').length >= 1, 'limites de rotacao devem informar rpm');
  assert.ok(fields.some(f => f.unit === 'ms'), 'o tempo deve indicar ms');
});


test('Ferramentas 800x480: explicações breves permanecem visíveis, não só títulos', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 800, height: 480 }, usb: true });
  try {
    await T.tap(app.page, '.side-nav [data-route="tools"]');
    await app.page.waitForSelector('#toolAdvanced [data-card="troca"]');
    for (const id of ['anti', 'valores', 'troca', 'lenta', 'volta']) {
      const line = app.page.locator('#toolAdvanced [data-card="' + id + '"] .tile-line');
      assert.equal(await line.count(), 1);
      const rect = await line.boundingBox();
      assert.ok(rect && rect.height > 0 && rect.width > 0, 'texto de explicação foi escondido: ' + id);
    }
  } finally { await app.context.close(); }
});
