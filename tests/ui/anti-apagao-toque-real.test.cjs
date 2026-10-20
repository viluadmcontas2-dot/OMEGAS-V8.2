'use strict';
// Contrato da ação PRINCIPAL em WebView real: toque humano, ECU emulada e bytes no fio.
// Regressão separada: o card visível precisa ser acionável sem caçar um interruptor escondido.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

test('SEM ATRITO (regra do dono): UM toque liga e UM toque desliga; nenhuma janela de pergunta; só o bit 0x20 muda', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await page.evaluate(() => {
      window.__bench.seed('29 FA 00 23', [0x10, 0, 0x34, 0x12]);
      window.__bench.readAdvanced();
      window.__bench.ecu.clearLog();
      // Vigia: a janela de confirmação NUNCA pode aparecer durante ligar/desligar.
      window.__janelaVista = false;
      const layer = document.getElementById('toolsLayer');
      new MutationObserver(() => { if (!layer.hidden && layer.querySelector('[data-adv-confirm],[data-adv-cancel]')) window.__janelaVista = true; })
        .observe(layer, { attributes: true, childList: true, subtree: true });
    });
    await T.route(app, 'tools');
    const card = page.locator('[data-card="anti"]');
    assert.match(await card.innerText(), /Ligar anti-apagão/, 'a tela mostra a AÇÃO');

    await T.tap(page, '[data-card="anti"] h3');            // UM toque
    await T.pump(page, 10);
    let bytes = await page.evaluate(() => window.__bench.ecu.get('29 FA 00 23'));
    assert.deepEqual(bytes, [0x30, 0, 0x34, 0x12], 'um toque grava; apenas o bit 0x20 muda');
    assert.match(await card.innerText(), /Desligar anti-apagão/);
    assert.equal(await page.evaluate(() => window.__janelaVista), false, 'nenhuma janela de pergunta ao ligar');

    await T.tap(page, '[data-card="anti"] h3');            // UM toque
    await T.pump(page, 10);
    bytes = await page.evaluate(() => window.__bench.ecu.get('29 FA 00 23'));
    assert.deepEqual(bytes, [0x10, 0, 0x34, 0x12], 'desligar só apaga 0x20');
    assert.equal(await page.evaluate(() => window.__janelaVista), false, 'nenhuma janela de pergunta ao desligar');
    const writes = T.writesOf((await T.frames(page)).log);
    assert.equal(writes.length, 2, 'uma escrita por toque, nenhuma extra');
    assert.ok(writes.every(w => w.hex.startsWith('35 FA 00 ')), 'somente FLAG_CONF3 é escrito');
    assert.deepEqual(app.problems.page, [], 'JavaScript no navegador não pode lançar');
  } finally { await app.context.close(); }
});

test('cinco parâmetros anti-stallo aparecem como números com unidade, não como interruptores', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await T.route(app, 'tools');
    await T.tap(page, '[data-adv-open="valores"]');
    await T.pump(page, 3);
    const inputs = page.locator('#toolAdvanced input[data-adv-field]');
    assert.equal(await inputs.count(), 5);
    for (let i = 0; i < 5; i++) {
      assert.equal(await inputs.nth(i).isDisabled(), false, 'campo numérico '+i+' precisa aceitar edição');
      assert.match(await page.locator('[data-field="'+i+'"]').textContent(), /rpm|ms/i);
    }
    assert.equal(await page.locator('.values-view .vv-card .tool-switch').count(), 0,
      'números não podem virar opções binárias');
  } finally { await app.context.close(); }
});
