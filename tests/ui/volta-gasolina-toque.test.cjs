'use strict';
// "Volta para gasolina" por TOQUE (Chromium, central emulada com o estado do LOGNOVO): o cartão diz a pressão lida (1,25 bar),
// os controles − / + / Espera / Salvar ficam juntos À ESQUERDA na mesma linha, um toque grava (quadro exato do original) e
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

      // controles juntos, à esquerda, na mesma linha, sem buraco entre eles
      const boxes = [];
      for (const sel of ['[data-volta-step="-1"]', '[data-volta-step="1"]', '[data-volta-wait]', '[data-volta-save]']) boxes.push(await page.locator(sel).boundingBox());
      const bar = await page.locator('.volta-actions').boundingBox();
      assert.ok(Math.abs(boxes[0].x - bar.x) <= 2, `o primeiro botão começa na esquerda: ${boxes[0].x} vs ${bar.x}`);
      for (let i = 1; i < boxes.length; i += 1) {
        assert.ok(Math.abs(boxes[i].y - boxes[0].y) <= 2, 'mesma linha');
        const gap = boxes[i].x - (boxes[i - 1].x + boxes[i - 1].width);
        assert.ok(gap >= 0 && gap <= 16, `sem buraco entre botões: ${gap}px`);
      }
      for (const b of boxes) assert.ok(b.height >= 56, `alvo ≥ 56 px: ${b.height}`);
      assert.ok(boxes.every(b => b.x + b.width <= vp.width && b.y + b.height <= vp.height), 'cabe na tela sem rolar');

      // − / + só mudam o rascunho (nada vai à central até Salvar)
      await T.tap(page, '[data-volta-step="1"]'); await T.pump(page, 2);
      assert.match(await view.innerText(), /1,30 bar · não salvo/);
      assert.deepEqual(await writes(page), []);
      await T.tap(page, '[data-volta-step="-1"]'); await T.pump(page, 2);
      assert.equal(await page.locator('[data-volta-save]').isDisabled(), true, 'voltou ao valor da central: nada a salvar');

      // Espera + Salvar: um toque grava o tempo (quadro exato do original) e confere
      await T.tap(page, '[data-volta-wait]'); await T.pump(page, 2);
      assert.match(await page.locator('[data-volta-wait]').innerText(), /1 s/);
      await T.tap(page, '[data-volta-save]'); await T.pump(page, 4);
      const w1 = (await T.frames(page)).log.filter(f => f.kind !== 'READ');
      assert.equal(w1.length, 1, 'um quadro de escrita');
      assert.match(JSON.stringify(w1[0]), /12 3E 00 01 51/);
      assert.match(await page.locator('.volta-status').innerText(), /Salvo e conferido na central/);

      // Desligar (como desmarcar no original: 255) e Desfazer
      await page.evaluate(() => window.__bench.ecu.clearLog());
      await T.tap(page, '[data-volta-toggle]'); await T.pump(page, 4);
      assert.match(JSON.stringify((await T.frames(page)).log.filter(f => f.kind !== 'READ')), /12 3E 00 FF 4F/);
      assert.match(await view.innerText(), /Desligado: se a pressão do gás cair, o carro não volta sozinho/);
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
