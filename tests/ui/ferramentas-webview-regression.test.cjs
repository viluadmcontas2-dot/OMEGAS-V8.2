'use strict';
// Regressão do WebView: Ferramentas não pode depender da leitura auxiliar para aparecer.
// O ensaio de integração sem navegador é obrigatório; o visual roda quando Chromium está disponível.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { execFileSync } = require('node:child_process');
const T = require('./helpers/browser.cjs');

function runBridgeFailure(appSource) {
  const tasks = [];
  const host = { innerHTML: '', addEventListener() {}, contains() { return false; }, dataset: {} };
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
  const baseline = execFileSync('git', ['show', 'eb8c9e8:app/src/main/assets/ui/app.js'], { cwd: T.ROOT, encoding: 'utf8' });
  assert.doesNotMatch(runBridgeFailure(baseline), /Anti-apagão/, 'a regressão precisa detectar o defeito antigo');
});

test('GREEN: Ferramentas desenha cartões apesar de falha na leitura auxiliar', () => {
  const current = fs.readFileSync(path.join(T.UI_DIR, 'app.js'), 'utf8');
  const html = runBridgeFailure(current);
  assert.match(html, /Anti-apagão/, 'a aba Ferramentas ficou vazia');
  assert.match(html, /Marcha lenta/, 'outro cartão deve continuar visível');
});

const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

for (const [name, viewport] of Object.entries({
  '1280x720': { width: 1280, height: 720 },
  '1024x600': { width: 1024, height: 600 },
  '800x480': { width: 800, height: 480 },
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

test('cartão com defeito exibe tentar de novo e preserva os outros', { skip }, async () => {
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
    assert.match(await app.page.locator('#toolAdvanced [data-card="corte"]').innerText(), /Não foi possível abrir esta parte\. Toque para tentar de novo\./);
    assert.equal(await app.page.locator('#toolAdvanced [data-card="anti"]').count(), 1);
    assert.equal(await app.page.locator('#toolAdvanced [data-card="lenta"]').count(), 1);
  } finally { await app.context.close(); }
});
