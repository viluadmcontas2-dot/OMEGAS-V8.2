// "Pausar a ECU e refinar agora": com a ECU ainda no automático o Refino mostra esse botão (e nenhum de gravar); um toque pede a
// pausa da aquisição da ECU (ação que já existe) e nada mais é gravado. Com a ECU concluída o botão some.
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
    assert.equal(await visible('[data-refino-pause]'), false, 'sem o piloto dizer "ainda no automático", não oferece pausa');
    await page.evaluate(() => {
      const refino = window.OmegasApp.refino;
      const eq = JSON.parse(JSON.stringify(refino.eq || {}));
      eq.autopilot = { ...(eq.autopilot || {}), phase: 'ECU_TRABALHANDO', ecuDone: false, ecuOnline: true, autoCalEnabled: 1, autoMatchCount: 0, maxAutomatch: 3,
        action: { kind: 'waiting', label: 'Aguardando a ECU', enabled: false }, proposal: { known: true, count: 0, points: [] }, secondary: [] };
      window.__pauses = [];
      const base = refino.api;
      refino.api = Object.assign(Object.create(base), { equivalence: () => eq, setAcquisitionEnabled: enabled => { window.__pauses.push(enabled); return { ok: true }; } });
      refino.refresh(true);
    });
    await page.waitForTimeout(300);
    assert.equal(await visible('[data-refino-pause]'), true, 'ECU trabalhando: botão de pausar aparece');
    assert.equal(await visible('[data-refino-preview]'), false, 'e nenhum botão de gravar');
    const box = await page.evaluate(() => { const r = document.querySelector('[data-refino-pause]').getBoundingClientRect(); return { right: r.right, bottom: r.bottom, w: innerWidth, h: innerHeight }; });
    assert.ok(box.right <= box.w && box.bottom <= box.h, 'botão dentro da tela: ' + JSON.stringify(box));
    await page.click('[data-refino-pause]');
    assert.deepEqual(await page.evaluate(() => window.__pauses), [false], 'um toque = um pedido de pausa da aquisição');
    // ECU concluída (ou já pausada): o botão some.
    await page.evaluate(() => {
      const refino = window.OmegasApp.refino; const eq = JSON.parse(JSON.stringify(refino.api.equivalence()));
      eq.autopilot.ecuDone = true; eq.autopilot.phase = 'ESTAVEL'; eq.autopilot.autoCalEnabled = 0;
      refino.api = Object.assign(Object.create(refino.api), { equivalence: () => eq }); refino.refresh(true);
    });
    await page.waitForTimeout(300);
    assert.equal(await visible('[data-refino-pause]'), false, 'ECU concluída: sem botão');
    assert.deepEqual(errors, [], 'sem erro de página');
    console.log('REFINO_PAUSAR=PASS');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exit(1); });
