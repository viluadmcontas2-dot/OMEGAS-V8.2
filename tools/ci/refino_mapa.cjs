// Refino no app real (modo demonstração): com engasgos registrados aparece UM botão "Gravar no Mapa K"; o toque lê o Mapa K da
// ECU e mostra o que vai fazer SEM sair da aba; só o segundo toque grava; depois há "Desfazer". Sem botão desabilitado.
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
    // Sem engasgo e sem ação local: nenhum botão de Mapa K, nenhum botão desabilitado/"Aguardando".
    assert.equal(await visible('[data-refino-map]'), false);
    assert.equal(await page.evaluate(() => [...document.querySelectorAll('.refino-action-bar button')].filter(b => !b.hidden && b.disabled).length), 0);

    // Engasgos registrados (a ponte do demo não tem StallWatch): o Refino lê do piloto; aqui injetamos no estado.
    await page.evaluate(() => {
      const refino = window.OmegasApp.refino;
      const eq = JSON.parse(JSON.stringify(refino.eq || {}));
      eq.stalls = { count: 1, events: [{ kind: 'STALL', petrolMs: 2.2, mapBar: 0.3, rpmBefore: 1500 }],
        regions: [{ fromMs: 2.0, toMs: 2.5, count: 1, mapBar: 0.3, rpmBefore: 1500, rpmLow: 1500, rpmHigh: 1500 }],
        nearCount: 4, nearEvents: [{ kind: 'NEAR_STALL', petrolMs: 2.1, mapBar: 0.25 }],
        nearRegions: [{ fromMs: 2.0, toMs: 2.5, count: 4, mapBar: 0.25, rpmBefore: 1450, rpmLow: 1350, rpmHigh: 1700 }] };
      refino.api = Object.assign(Object.create(refino.api), { equivalence: () => eq });
      const api = window.OmegasApp.api;
      window.__writes = [];
      api.writeMap = (items, a, b, reason) => { window.__writes.push({ items, reason }); return { ok: true, started: true, startedAt: 1 }; };
      api.mapWriteOperation = () => ({ ok: true, state: 'BATCH_CONFIRMED', readbackValid: true, busy: false, startedAt: 1 });
      refino.refresh(true);
    });
    await page.waitForFunction(() => !document.querySelector('[data-refino-map]').hidden, null, { timeout: 4000 });
    const idle = await page.evaluate(() => ({ next: document.getElementById('refinoNext').textContent, button: document.querySelector('[data-refino-map]').textContent }));
    assert.match(idle.next, /O carro engasgou 5 vezes no GNV/);
    assert.match(idle.button, /Gravar no Mapa K\s*reforço do engasgo/);

    await page.locator('[data-refino-map]').click();
    await page.waitForFunction(() => /Pronto para gravar no Mapa K/.test(document.getElementById('refinoHeadline').textContent), null, { timeout: 15000 });
    assert.equal(await page.evaluate(() => document.querySelector('.screen.active').dataset.screen), 'refino', 'não sai da aba');
    assert.equal(await page.evaluate(() => window.__writes.length), 0, 'ainda nada gravado');
    const ready = await page.evaluate(() => document.getElementById('refinoNext').textContent);
    assert.match(ready, /Reforço do GNV em \d+ células? do Mapa K \(\+\d+%\)/);
    assert.match(ready, /O resto do mapa não muda/);
    assert.equal(await visible('[data-refino-cancel-fix]'), true);

    await page.locator('[data-refino-map]').click();
    await page.waitForFunction(() => /Gravado e conferido no Mapa K/.test(document.getElementById('refinoHeadline').textContent), null, { timeout: 15000 });
    assert.equal(await visible('[data-refino-undo]'), true);
    await page.locator('[data-refino-undo]').click();
    await page.waitForFunction(() => /Desfeito e conferido/.test(document.getElementById('refinoHeadline').textContent), null, { timeout: 15000 });
    const writes = await page.evaluate(() => window.__writes);
    assert.equal(writes.length, 2);
    assert.ok(writes[0].items.length >= 1 && writes[0].items.length <= 6, 'poucas células: ' + writes[0].items.length);
    assert.ok(writes[0].items.every(i => i.target > i.current));
    assert.deepEqual(writes[1].items.map(i => [i.row, i.column, i.target]), writes[0].items.map(i => [i.row, i.column, i.current]));
    assert.deepEqual(errors, []);
    console.log('REFINO_MAPA=PASS', JSON.stringify({ celulas: writes[0].items.length }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
