'use strict';
// Costura de navegação: todos os controles visíveis devem apontar para fluxos reais.
// Usa index.html e MapScreen reais no Chromium. Nenhuma escrita na ECU simulada.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

test('RED: Ajuste local não monta botão de retorno para Aprendizado removido', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  try {
    await T.route(app, 'map');
    await app.page.waitForFunction(() => !!window.OmegasUi?.MapScreen && !!window.OmegasApp?.screens?.map);
    assert.equal(await app.page.locator('#mapBackToLearning').count(), 0,
      'botão legado criado para uma rota que não existe deve ser eliminado, mesmo quando está escondido');
    assert.equal(await app.page.locator('[data-screen="map"] .screen-error').count(), 0);
  } finally { await app.context.close(); }
});

test('cinco áreas principais e Sessões sem rota morta nem gravação por navegação', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await page.evaluate(() => window.__bench.ecu.clearLog());
    assert.equal(await page.locator('.side-nav button').count(), 5);
    for (const route of ['autocal','refino','map','curve','tools','map','refino','autocal']) {
      await T.route(app, route);
      assert.equal(await page.locator('.screen.active').count(), 1, 'tela duplicada ou vazia ao abrir '+route);
      const visible = await page.locator('.screen.active').getAttribute('data-screen');
      assert.equal(visible, route, 'a aba visível e a rota selecionada divergiram');
    }
    await T.route(app, 'tools');
    await T.tap(page, '#toolsPageNext'); await T.pump(page, 2);
    await T.tap(page, '[data-tool-open-sessions]'); await T.pump(page, 3);
    assert.equal(await page.locator('.screen.active').getAttribute('data-screen'), 'sessions');
    assert.equal(await page.locator('.side-nav button[data-route="tools"]').getAttribute('aria-current'), 'page');
    await T.tap(page, '[data-session-back]'); await T.pump(page, 2);
    assert.equal(await page.locator('.screen.active').getAttribute('data-screen'), 'tools');
    assert.equal(await page.locator('#toolsDrawer').getAttribute('data-page'), '2');
    assert.deepEqual(T.writesOf((await T.frames(page)).log), [], 'navegação não é intenção de gravação');
  } finally { await app.context.close(); }
});
