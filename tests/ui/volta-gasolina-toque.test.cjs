'use strict';
// "Volta para gasolina" por TOQUE (Chromium, central emulada com o estado do LOGNOVO): o cartão diz a pressão lida (1,25 bar),
// cada valor no seu cartão com − / + à esquerda (página de grupo, 2026-10-20), um toque grava (quadro exato do original) e
// Desfazer volta. Abrir a tela nunca grava. Sem Chromium PULA.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');

const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

const writes = async page => T.writesOf((await T.frames(page)).log).map(f => f.hex || f.request || JSON.stringify(f));

for (const [nome, vp] of Object.entries({ '1280x720': { width: 1280, height: 720 }, '800x480': { width: 800, height: 480 } })) {
  test(`volta para gasolina por toque @${nome}`, { skip }, async () => {
    const app = await T.openApp(browser, { viewport: vp, usb: true });
    const { page } = app;
    try {
      await T.route(app, 'tools');
      const tile = page.locator('#toolAdvanced [data-card="volta"]');
      assert.match(await tile.innerText(), /abaixo de 1,25 bar/);
      assert.doesNotMatch(await tile.innerText(), /rota|bomba/i);
      await page.evaluate(() => window.__bench.ecu.clearLog());
      await T.tap(page, '[data-adv-open="volta"]');
      await T.pump(page, 3);
      const view = page.locator('#toolAdvanced .volta-view');
      assert.match(await view.innerText(), /Se a pressão do gás cair abaixo de 1,25 bar, o carro volta sozinho para a gasolina\./);
      assert.deepEqual(await writes(page), [], 'abrir a tela nunca grava');

      // página do grupo: ligar/desligar, pressão e espera, cada um no seu cartão, com − / + visíveis (≥ 58 px) e juntos à esquerda
      for (const item of ['volta-mbar', 'volta-espera']) {
        const card = await page.locator(`[data-item="${item}"]`).boundingBox();
        const minus = await page.locator(`[data-item="${item}"] .step-btn`).first().boundingBox();
        const plus = await page.locator(`[data-item="${item}"] .step-btn`).last().boundingBox();
        assert.ok(minus.x - card.x <= 16, `${item}: o − começa à esquerda do cartão`);
        for (const b of [minus, plus]) assert.ok(b.height >= 58 && b.width >= 58, `${item}: alvo ≥ 58 px (${b.width}x${b.height})`);
        assert.ok(Math.abs(minus.y - plus.y) <= 2, `${item}: − e + na mesma linha`);
        assert.ok(card.x + card.width <= vp.width, `${item}: cabe na largura`);
      }

      // + na espera: UM toque grava (quadro exato do original) e confere; sem "Salvar"
      await T.tap(page, '[data-volta-step="segundos:1"]'); await T.pump(page, 4);
      const w1 = (await T.frames(page)).log.filter(f => f.kind !== 'READ');
      assert.equal(w1.length, 1, 'um quadro de escrita');
      assert.match(JSON.stringify(w1[0]), /12 3E 00 01 51/);
      assert.match(await page.locator('#toolAdvanced .grp-status').innerText(), /Salvo e conferido na central/);
      assert.match(await page.locator('[data-item="volta-espera"] .grp-val').innerText(), /1 s/);
      assert.equal(await page.locator('[data-volta-save]').count(), 0, 'não existe mais o botão "Salvar"');

      // Desligar (como desmarcar no original: 255) e Desfazer
      await page.evaluate(() => window.__bench.ecu.clearLog());
      await T.tap(page, '[data-volta-toggle]'); await T.pump(page, 4);
      assert.match(JSON.stringify((await T.frames(page)).log.filter(f => f.kind !== 'READ')), /12 3E 00 FF 4F/);
      assert.match(await view.innerText(), /Desligado: se a pressão do gás cair, o carro não volta sozinho/);
      assert.equal(await page.locator('[data-volta-step="segundos:1"]').isDisabled(), true, 'desligada: a espera não muda');
      await page.evaluate(() => window.__bench.ecu.clearLog());
      await T.tap(page, '[data-volta-undo]'); await T.pump(page, 4);
      assert.match(JSON.stringify((await T.frames(page)).log.filter(f => f.kind !== 'READ')), /12 3E 00 01 51/);
      assert.match(await view.innerText(), /cair abaixo de 1,25 bar/);
      await T.shot(page, `volta-gasolina-${nome}`);
      assert.deepEqual(app.problems.page, []);
      assert.deepEqual(app.problems.console, []);
    } finally { await app.context.close(); }
  });
}
