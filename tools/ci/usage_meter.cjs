// Medidor de uso no app real (Chromium): toques e trocas de aba viram contagens e saem pela ponte `recordUiUsage` (aqui, uma ponte falsa).
const { chromium } = require('playwright');
const path = require('path');
const assert = require('node:assert/strict');
const { fakeBridge } = require('./_ponte_falsa.cjs');
(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errors = [];
    page.on('pageerror', e => errors.push(String(e.message || e)));
    await page.addInitScript(fakeBridge);
    await page.goto('file://' + path.resolve('app/src/main/assets/ui/index.html'), { waitUntil: 'load' });
    await page.waitForFunction(() => window.OmegasApp?.usage, null, { timeout: 8000 });
    await page.evaluate(() => { window.__sent = []; window.OmegasApp.api.recordUiUsage = delta => { window.__sent.push(delta); return { ok: true }; }; });
    await page.locator('.side-nav button[data-route="refino"]').click();
    await page.waitForTimeout(300);
    await page.locator('.side-nav button[data-route="map"]').click();
    await page.waitForSelector('#mapGrid .map-k-cell');
    await page.locator('#mapGrid .map-k-cell').nth(3).click();
    await page.locator('#mapGrid .map-k-cell').nth(4).click();
    await page.evaluate(() => document.dispatchEvent(Object.assign(new Event('visibilitychange'))));
    const before = await page.evaluate(() => window.OmegasApp.usage.delta());
    assert.ok(before.taps['aba:refino'] >= 1 && before.taps['aba:map'] >= 1, JSON.stringify(before.taps));
    assert.equal(before.taps['mapa:celula'], 2);
    assert.ok(before.routes.refino.visits >= 1 && before.routes.map.visits >= 1);
    const ok = await page.evaluate(() => window.OmegasApp.usage.flush(d => { window.__sent.push(d); return true; }));
    assert.equal(ok, true);
    const sent = await page.evaluate(() => window.__sent);
    assert.equal(sent.length, 1);
    assert.deepEqual(await page.evaluate(() => window.OmegasApp.usage.taps), {}, 'zerou depois do envio');
    assert.deepEqual(errors, []);
    console.log('USAGE_METER=PASS', JSON.stringify(sent[0].taps));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
