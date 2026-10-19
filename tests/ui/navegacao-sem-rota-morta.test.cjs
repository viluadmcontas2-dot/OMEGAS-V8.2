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

test('fluxo das seis abas não deixa tela duplicada nem grava por navegação', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await page.evaluate(() => window.__bench.ecu.clearLog());
    for (const route of ['autocal','refino','map','curve','sessions','tools','map','refino','autocal']) {
      await T.route(app, route);
      assert.equal(await page.locator('.screen.active').count(), 1, 'tela duplicada ou vazia ao abrir '+route);
      const visible = await page.locator('.screen.active').getAttribute('data-screen');
      assert.equal(visible, route, 'a aba visível e a rota selecionada divergiram');
    }
    assert.deepEqual(T.writesOf((await T.frames(page)).log), [], 'navegação não é intenção de gravação');
  } finally { await app.context.close(); }
});
