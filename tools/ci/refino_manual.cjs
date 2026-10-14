// "Refinar por minha conta": com a ECU ainda no automático o Refino mostra o botão; um toque libera as gravações (o Kotlin
// passa a dizer ecuDone) e o botão vira "Esperar a ECU terminar". Com a ECU concluída de verdade o botão some.
const { chromium } = require('playwright');
const path = require('path');
const assert = require('node:assert/strict');
const { fakeBridge } = require('./_ponte_falsa.cjs');
const INDEX = path.resolve('app/src/main/assets/ui/index.html');

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errors = [];
    page.on('pageerror', e => errors.push(String(e.message || e)));
    await page.addInitScript(fakeBridge);
    await page.goto('file://' + INDEX, { waitUntil: 'load' });
    await page.waitForTimeout(1200);
    await page.locator('.side-nav button[data-route="refino"]').click();
    await page.waitForSelector('#refinoChart .ao-svg', { timeout: 8000 });
    const visible = async sel => page.evaluate(s => { const n = document.querySelector(s); return !!n && !n.hidden && getComputedStyle(n).display !== 'none'; }, sel);
    assert.equal(await visible('[data-refino-manual]'), false, 'sem o piloto dizer "ainda no automático", não oferece');
    await page.evaluate(() => {
      const refino = window.OmegasApp.refino;
      const eq = JSON.parse(JSON.stringify(refino.eq || {}));
      eq.autopilot = { ...(eq.autopilot || {}), phase: 'ECU_TRABALHANDO', ecuDone: false, ecuOnline: true, manual: false, autoCalEnabled: 1, autoMatchCount: 1, maxAutomatch: 3,
        action: { kind: 'waiting', label: 'Aguardando a ECU', enabled: false }, proposal: { known: true, count: 0, points: [] }, secondary: [] };
      window.__manual = [];
      const base = refino.api;
      refino.api = Object.assign(Object.create(base), { equivalence: () => eq, setRefinementManual: on => { window.__manual.push(on); eq.autopilot.manual = on; eq.autopilot.ecuDone = on; return { ok: true }; } });
      refino.refresh(true);
    });
    await page.waitForTimeout(300);
    assert.equal(await visible('[data-refino-manual]'), true, 'ECU trabalhando: botão aparece');
    assert.equal(await page.textContent('[data-refino-manual]'), 'Refinar por minha conta');
    const box = await page.evaluate(() => { const r = document.querySelector('[data-refino-manual]').getBoundingClientRect(); return { right: r.right, bottom: r.bottom, w: innerWidth, h: innerHeight }; });
    assert.ok(box.right <= box.w && box.bottom <= box.h, 'botão dentro da tela: ' + JSON.stringify(box));
    await page.click('[data-refino-manual]');
    assert.deepEqual(await page.evaluate(() => window.__manual), [true], 'um toque = assumir');
    await page.waitForTimeout(1200);
    assert.equal(await page.textContent('[data-refino-manual]'), 'Esperar a ECU terminar', 'depois de assumir, o botão volta ao automático');
    await page.click('[data-refino-manual]');
    assert.deepEqual(await page.evaluate(() => window.__manual), [true, false]);
    // ECU concluída de verdade: o botão some.
    await page.evaluate(() => {
      const refino = window.OmegasApp.refino; const eq = JSON.parse(JSON.stringify(refino.api.equivalence()));
      eq.autopilot.ecuDone = true; eq.autopilot.manual = false; eq.autopilot.ecuDoneReason = 'MAX_AUTOMATCH'; eq.autopilot.phase = 'ESTAVEL';
      refino.api = Object.assign(Object.create(refino.api), { equivalence: () => eq }); refino.refresh(true);
    });
    await page.waitForTimeout(300);
    assert.equal(await visible('[data-refino-manual]'), false, 'ECU concluída: sem botão');
    assert.deepEqual(errors, [], 'sem erro de página');
    console.log('REFINO_MANUAL=PASS');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exit(1); });
