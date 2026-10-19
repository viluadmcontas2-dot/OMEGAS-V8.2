'use strict';
// Regra do dono (2026-10-19): o usuário que nunca mexeu entende, sabe para que lado mexer e grava em UM toque (sem janela de pergunta).
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

test('cada número explica o efeito, diz para que lado mexer e tem botões − / + (passo pequeno)', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await T.route(app, 'tools');
    await T.tap(page, '[data-adv-open="valores"]');
    await T.pump(page, 3);
    await page.evaluate(() => { document.querySelector('.extras-experimentais').open = true; });
    for (let i = 0; i < 5; i++) {
      const box = page.locator(`[data-field="${i}"]`);
      assert.equal(await box.locator('[data-adv-step]').count(), 2, `campo ${i}: botões − e +`);
      const tips = await box.locator('.num-tips').innerText();
      assert.match(tips, /Se precisar de mais/, `campo ${i}: diz quando aumentar`);
      assert.match(tips, /Se atrapalhar/, `campo ${i}: diz quando diminuir`);
      assert.match(await box.locator('.num-meta').innerText(), /ponto de partida/, `campo ${i}: recomenda o valor de fábrica como início`);
    }
  } finally { await app.context.close(); }
});

test('liga os pontos: explica QUANDO age, o caminho completo e que NÃO é o corte de injeção (com a evidência do log)', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await T.route(app, 'tools');
    await T.tap(page, '[data-adv-open="valores"]');
    await T.pump(page, 3);
    const quando = await page.locator('.como-funciona > p').first().innerText();
    assert.match(quando, /cai de repente/); assert.match(quando, /abaixo de 1\.600 rpm/); assert.match(quando, /embreagem/);
    await page.evaluate(() => { document.querySelector('.como-funciona details').open = true; });
    const tudo = await page.locator('.como-funciona').innerText();
    assert.match(tudo, /Não é o corte de injeção/);
    assert.match(tudo, /Saída do corte/, 'aponta para o cartão do corte');
    assert.match(tudo, /447 rpm/); assert.match(tudo, /680 rpm/);
    await page.evaluate(() => { document.querySelector('.extras-experimentais').open = true; });
    assert.match(await page.locator('[data-field="1"] .num-help').innerText(), /duas voltas/, 'explica a escala do original sem jargão');
  } finally { await app.context.close(); }
});

test('SEM ATRITO: + / − muda o número e "Gravar valores" grava num toque, sem janela de pergunta', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await T.route(app, 'tools');
    await T.tap(page, '[data-adv-open="valores"]');
    await T.pump(page, 3);
    await page.evaluate(() => {
      window.__janelaVista = false;
      const layer = document.getElementById('toolsLayer');
      new MutationObserver(() => { if (!layer.hidden && layer.querySelector('[data-adv-confirm],[data-adv-cancel]')) window.__janelaVista = true; })
        .observe(layer, { attributes: true, childList: true, subtree: true });
      window.__bench.ecu.clearLog();
    });
    const input = page.locator('#advField0');
    const before = Number((await input.inputValue()).replace(/\./g, ''));
    await T.tap(page, '[data-field="0"] [data-adv-step="0:1"]');
    await T.tap(page, '[data-field="0"] [data-adv-step="0:1"]');
    assert.equal(Number((await input.inputValue()).replace(/\./g, '')), before + 100, 'dois toques em + = +100 rpm');
    assert.deepEqual(T.writesOf((await T.frames(page)).log), [], 'mexer no + / − não grava');
    await T.tap(page, '[data-adv-save]');                 // UM toque
    await T.pump(page, 10);
    assert.equal(T.writesOf((await T.frames(page)).log).length, 1, 'um toque em Gravar = uma escrita');
    assert.equal(await page.evaluate(() => window.__janelaVista), false, 'nenhuma janela de pergunta');
    assert.match(await page.locator('.adv-saved').innerText(), /pronto|conferem/i, 'avisa na própria tela');
    assert.deepEqual(app.problems.page, []);
  } finally { await app.context.close(); }
});
