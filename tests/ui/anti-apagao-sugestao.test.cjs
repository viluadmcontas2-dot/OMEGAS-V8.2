'use strict';
// "Sugestão para o seu carro" (dono, 2026-10-19): o app diz os valores ideais pelos logs do dono, sem ele adivinhar; aplicar = 1 toque.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const U = require('./helpers/ux-cenarios.cjs');
const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

test('valores já iguais à sugestão: avisa "✔ já são os recomendados" com a evidência do log, sem botão', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    const t = await U.text(page, '.vv-sug');
    assert.match(t, /Seus valores já são os recomendados para o seu carro/);
    assert.match(t, /680 rpm/); assert.match(t, /0,8% do tempo foi em gasolina/);
    await T.tap(page, '[data-adv-open-info-tab="carro"]'); await T.pump(page, 2);
    const c = await U.text(page, '.vv-info-full');
    assert.match(c, /Seu teste/); assert.match(c, /447/); assert.match(c, /1\.600 rpm/); assert.match(c, /2\.000 rpm/); assert.match(c, /baixar não economiza gasolina/); assert.match(c, /aumente o número «Rotação máxima para agir» \(o primeiro card\) até 2\.000 rpm/);
    assert.match(c, /Quase-apagões/);
    assert.equal(await page.locator('[data-adv-use-suggestion]').count(), 0);
  } finally { await app.context.close(); }
});

test('valores diferentes: mostra a sugestão e UM toque em "Usar a sugestão" grava os valores do carro', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await page.evaluate(() => window.__bench.seed('29 33 01 5D', [0xB8, 0x0B, 0x64, 0x00, 0xDC, 0x05, 0x05, 0x00, 0x14, 0x00])); // 3000 100 1500 5 20
    await T.pump(page, 2);
    await page.evaluate(() => { window.__bench.deliver(); }); await T.pump(page, 1);
    await page.evaluate(() => { window.__bench.plugUsb(); window.__bench.deliver(); }); await T.pump(page, 40);
    await U.abrirValores(app);
    assert.match(await U.text(page, '.vv-sug'), /Sugestão para o seu carro/);
    assert.match(await U.text(page, '.vv-sug-vals'), /1\.600 rpm · 100 rpm · 1\.500 ms · 2 rpm · 20 rpm/);
    await U.clearLog(page);
    await T.tap(page, '[data-adv-use-suggestion]'); await T.pump(page, 12);
    assert.equal(await U.camada(page), 0, 'sem janela de pergunta');
    assert.deepEqual(await U.anti5(page), [1600, 100, 1500, 2, 20], 'a central ficou com os valores sugeridos');
    assert.deepEqual(await U.ecuWrites(page), [U.quadroValores([1600, 100, 1500, 2, 20])]);
    assert.match(await U.text(page, '.vv-sug'), /Seus valores já são os recomendados/);
    assert.equal(await page.locator('#advField0').inputValue(), '1.600', 'os cards mostram o que a central tem agora');
  } finally { await app.context.close(); }
});
