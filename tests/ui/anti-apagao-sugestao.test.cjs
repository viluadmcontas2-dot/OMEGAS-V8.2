'use strict';
// Dados observados não viram promessa de economia nem substituem valores do carro.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const U = require('./helpers/ux-cenarios.cjs');
const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

test('sem viagem analisada: desconhecido, sem sugestão constante e sem escrita', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  try {
    await U.abrirValores(app);
    assert.match(await U.text(app.page, '.vv-sug'), /Sem dados|Ainda não há dados/);
    assert.equal(await app.page.locator('[data-adv-use-suggestion]').count(), 0);
    assert.deepEqual(await app.page.evaluate(() => window.OmegasUi.AntiApagaoValores.suggestionChanges([3000,100,1500,5,20])), {});
    await T.tap(app.page, '[data-adv-open-info-tab="carro"]'); await T.pump(app.page, 2);
    assert.match(await U.text(app.page, '.vv-info-full'), /não permitem confirmar/);
    assert.deepEqual(await U.ecuWrites(app.page), []);
  } finally { await app.context.close(); }
});

test('resultado novo do núcleo aparece, sem reduzir configuração nem prometer segurança', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 800, height: 480 }, usb: true });
  try {
    await app.page.evaluate(() => {
      const previous = window.OmegasAutoCal.getAdvancedParams;
      window.OmegasAutoCal.getAdvancedParams = () => {
        const data = JSON.parse(previous());
        data.antiStallSuggestion = {quedas: 2, maiorAjusteComQueda: 1800,
          motivo:'O motor teve 2 quedas fortes nesta viagem. Ainda não há dados para escolher um novo ajuste.'};
        return JSON.stringify(data);
      };
    });
    await U.clearLog(app.page);
    await U.abrirValores(app);
    assert.match(await U.text(app.page, '.vv-sug'), /2 quedas fortes/);
    assert.equal(await app.page.locator('[data-adv-use-suggestion]').count(), 0);
    assert.deepEqual(await U.ecuWrites(app.page), []);
  } finally { await app.context.close(); }
});
