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

test('RED: toque no cartão Anti-apagão liga E desliga, preservando todos os outros bits, com ACK e readback', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    // ECU real de referência: 0x0010 (sobreposição) permanece intacto, assim como a palavra 1.
    await page.evaluate(() => {
      window.__bench.seed('29 FA 00 23', [0x10, 0, 0x34, 0x12]);
      window.__bench.readAdvanced();
      window.__bench.ecu.clearLog();
    });
    await T.route(app, 'tools');
    const card = page.locator('[data-card="anti"]');
    assert.match(await card.innerText(), /Desligado/);
    assert.match(await card.innerText(), /Ligar anti-apagão/,
      'a tela deve mostrar a AÇÃO e não apenas o estado passivo do switch');

    // Toque humano no título inteiro deve abrir a prévia. Não basta ter um botão pequeno lá embaixo.
    await T.tap(page, '[data-card="anti"] h3');
    await T.pump(page, 6);
    await page.waitForSelector('#toolsLayer [data-adv-confirm="flag"]', { state: 'visible', timeout: 8000 });
    assert.deepEqual(T.writesOf((await T.frames(page)).log), [], 'a prévia deve ler, sem gravar');
    await T.tap(page, '#toolsLayer [data-adv-confirm="flag"]');
    await T.pump(page, 6);
    assert.match(await page.locator('#toolsLayer .adv-result').innerText(), /ligado e conferido na central/i);
    let bytes = await page.evaluate(() => window.__bench.ecu.get('29 FA 00 23'));
    assert.deepEqual(bytes, [0x30, 0, 0x34, 0x12], 'apenas o bit 0x20 deve mudar');
    await T.tap(page, '#toolsLayer [data-adv-close]');
    assert.match(await card.innerText(), /Desligar anti-apagão/);

    await T.tap(page, '[data-card="anti"] h3');
    await T.pump(page, 6);
    assert.deepEqual(T.writesOf((await T.frames(page)).log).length, 1, 'só a escrita anterior até confirmar a segunda ação');
    await T.tap(page, '#toolsLayer [data-adv-confirm="flag"]');
    await T.pump(page, 6);
    assert.match(await page.locator('#toolsLayer .adv-result').innerText(), /desligado e conferido na central/i);
    bytes = await page.evaluate(() => window.__bench.ecu.get('29 FA 00 23'));
    assert.deepEqual(bytes, [0x10, 0, 0x34, 0x12], 'desligar só apaga 0x20');
    const writes = T.writesOf((await T.frames(page)).log);
    assert.equal(writes.length, 2, 'ligar e desligar geram uma escrita cada, nenhuma extra');
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
    assert.equal(await page.locator('.values-view .tool-switch').count(), 0,
      'números não podem virar opções binárias');
  } finally { await app.context.close(); }
});
