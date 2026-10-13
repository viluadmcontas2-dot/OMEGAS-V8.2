// Refino local no app real (modo demonstração): a aba mostra uma frase e UM botão. Com engasgos registrados, "Corrigir engasgo"
// lê o Mapa K, mostra o que vai fazer (poucas células, só no mapa) e só grava no "Gravar"; depois oferece "Desfazer".
const { chromium } = require('playwright');
const path = require('path');
const assert = require('node:assert/strict');
const INDEX = path.resolve('app/src/main/assets/ui/index.html');

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errors = [];
    page.on('pageerror', e => errors.push(String(e.message || e)));
    await page.goto('file://' + INDEX, { waitUntil: 'load' });
    await page.waitForTimeout(1200);
    await page.locator('.side-nav button[data-route="local"]').click();
    await page.waitForSelector('.local-refino .local-main');
    const empty = await page.evaluate(() => ({ title: document.querySelector('.local-refino h2').textContent, disabled: document.querySelector('.local-main').disabled }));
    assert.match(empty.title, /Nenhum engasgo|Sem ligação/);
    assert.equal(empty.disabled, true, 'sem engasgo o botão não faz nada');

    await page.evaluate(() => {
      const api = window.OmegasUi.AutoCalApi;
      api.available = () => true;
      api.stalls = () => ({ ok: true, stalls: {
        count: 1, events: [{ kind: 'STALL', petrolMs: 2.2, mapBar: 0.3, rpmBefore: 1500 }],
        regions: [{ fromMs: 2.0, toMs: 2.5, count: 1, mapBar: 0.3, rpmBefore: 1500, rpmLow: 1500, rpmHigh: 1500 }],
        nearCount: 4, nearEvents: [{ kind: 'NEAR_STALL', petrolMs: 2.1, mapBar: 0.25, rpmBefore: 1400 }],
        nearRegions: [{ fromMs: 2.0, toMs: 2.5, count: 4, mapBar: 0.25, rpmBefore: 1450, rpmLow: 1350, rpmHigh: 1700 }],
      } });
      window.OmegasApp.localRefino.refresh(true);
    });
    const withStalls = await page.evaluate(() => ({ title: document.querySelector('.local-refino h2').textContent, button: document.querySelector('.local-main').textContent, disabled: document.querySelector('.local-main').disabled }));
    assert.match(withStalls.title, /O carro engasgou 5 vezes no GNV/);
    assert.equal(withStalls.button, 'Corrigir engasgo');
    assert.equal(withStalls.disabled, false);

    // O demo recusa escrita (de propósito). Aqui a ponte é trocada por uma que confirma, para provar o fluxo da tela.
    await page.evaluate(() => {
      const api = window.OmegasApp.api;
      window.__writes = [];
      api.writeMap = (items, a, b, reason) => { window.__writes.push({ items, reason }); return { ok: true, started: true, startedAt: 1 }; };
      api.mapWriteOperation = () => ({ ok: true, state: 'BATCH_CONFIRMED', readbackValid: true, busy: false, startedAt: 1 });
    });
    await page.locator('.local-main').click();
    await page.waitForSelector('[data-local-action="write"]', { timeout: 15000 });
    const ready = await page.evaluate(() => document.querySelector('.local-refino').textContent);
    assert.match(ready, /Vou reforçar o GNV em \d+ células? do Mapa K \(\+\d+%\)/);
    assert.match(ready, /O resto do mapa não muda/);
    assert.equal(await page.evaluate(() => location.hash || ''), '', 'não navega para outra aba');
    assert.equal(await page.evaluate(() => document.querySelector('.screen.active')?.dataset.screen), 'local');

    await page.locator('[data-local-action="write"]').click();
    await page.waitForSelector('[data-local-action="undo"]', { timeout: 15000 });
    const done = await page.evaluate(() => document.querySelector('.local-refino h2').textContent);
    assert.match(done, /Gravado e conferido na ECU/);
    await page.locator('[data-local-action="undo"]').click();
    await page.waitForFunction(() => /Desfeito e conferido/.test(document.querySelector('.local-refino h2')?.textContent || ''), null, { timeout: 15000 });
    const writes = await page.evaluate(() => window.__writes);
    assert.equal(writes.length, 2, 'uma gravação e um desfazer');
    assert.ok(writes[0].items.length >= 1 && writes[0].items.length <= 6, 'poucas células: ' + writes[0].items.length);
    assert.ok(writes[0].items.every(i => i.target > i.current), 'reforço só aumenta');
    assert.deepEqual(writes[1].items.map(i => [i.row, i.column, i.target]), writes[0].items.map(i => [i.row, i.column, i.current]), 'desfazer volta ao valor de antes');
    assert.deepEqual(errors, [], 'sem erros de página');
    console.log('REFINO_LOCAL=PASS', JSON.stringify({ ready: ready.slice(0, 120) }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
