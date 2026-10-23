// Refino no app real (modo demonstração): UM botão "Ver alterações" abre o cartão "O que vai mudar" (Curva K e/ou Mapa K, em frases);
// as gravações ficam dentro do cartão, uma por destino; nada grava antes do toque; depois há "Desfazer". Sem botão desabilitado.
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
    assert.equal(await visible('[data-refino-preview]'), true, 'há proposta da Curva K no estado de exemplo');
    assert.equal(await visible('[data-refino-map-confirm]'), false);
    assert.equal(await page.evaluate(() => [...document.querySelectorAll('.refino-action-bar button')].filter(b => !b.hidden && b.disabled).length), 0);

    // Engasgos registrados (a ponte do demo não tem StallWatch): o Refino lê do piloto; aqui injetamos no estado.
    await page.evaluate(() => {
      const refino = window.OmegasApp.refino;
      const eq = JSON.parse(JSON.stringify(refino.eq || {}));
      eq.stalls = { count: 1, events: [{ kind: 'STALL', petrolMs: 2.2, mapBar: 0.3, rpmBefore: 1500 }],
        regions: [{ fromMs: 2.0, toMs: 2.5, count: 1, mapBar: 0.3, rpmBefore: 1500, rpmLow: 1500, rpmHigh: 1500 }],
        nearCount: 4, nearEvents: [{ kind: 'NEAR_STALL', petrolMs: 2.1, mapBar: 0.25 }],
        nearRegions: [{ fromMs: 2.0, toMs: 2.5, count: 4, mapBar: 0.25, rpmBefore: 1450, rpmLow: 1350, rpmHigh: 1700 }] };
      // Proposta da Curva K do piloto (o demo manda vazia): 3 pontos para o cartão mostrar as duas seções.
      eq.autopilot = { ...(eq.autopilot || {}), phase: 'PROPOSTA_PRONTA', action: { kind: 'review', label: 'Revisar e gravar 3 pontos', enabled: true },
        proposal: { known: true, count: 3, signature: 'e2e', points: [
          { index: 6, currentRaw: 16384, targetRaw: 16900, referenceTimeMs: 3.0 }, { index: 9, currentRaw: 16384, targetRaw: 17100, referenceTimeMs: 5.5 }, { index: 14, currentRaw: 16384, targetRaw: 16300, referenceTimeMs: 9.0 }] } };
      refino.api = Object.assign(Object.create(refino.api), { equivalence: () => eq });
      const api = window.OmegasApp.api;
      window.__writes = [];
      api.writeMap = (items, reason) => { window.__writes.push({ items, reason }); return { ok: true, started: true, startedAt: 1 }; };
      api.mapWriteOperation = () => ({ ok: true, state: 'BATCH_CONFIRMED', readbackValid: true, busy: false, startedAt: 1 });
      refino.refresh(true);
    });
    await page.waitForFunction(() => !document.querySelector('[data-refino-preview]').hidden, null, { timeout: 4000 });
    const idle = await page.evaluate(() => ({ next: document.getElementById('refinoNext').textContent, button: document.querySelector('[data-refino-preview]').textContent }));
    assert.match(idle.next, /O carro engasgou 5 vezes no GNV/);
    assert.match(idle.next, /Toque em Ver alterações/);
    assert.doesNotMatch(idle.next, /Revisar e gravar/);
    assert.equal(idle.button, 'Ver alterações');

    await page.locator('[data-refino-preview]').click();
    await page.waitForSelector('.refino-card [data-refino-map-confirm]', { timeout: 15000 });
    assert.equal(await page.evaluate(() => document.querySelector('.screen.active').dataset.screen), 'refino', 'não sai da aba');
    assert.equal(await page.evaluate(() => window.__writes.length), 0, 'ainda nada gravado');
    const card = await page.evaluate(() => document.querySelector('.refino-card').textContent);
    assert.match(card, /Nada foi enviado à ECU ainda/);
    assert.match(card, /Curva K[\s\S]*Corrige \d+ pontos?/);
    assert.match(card, /Mapa K[\s\S]*Reforço do GNV em \d+ células? do Mapa K \(\+\d+%\)[\s\S]*O resto do mapa não muda/);
    assert.equal(await visible('[data-refino-preview]'), false, 'com o cartão aberto a barra fica quieta');
    await page.screenshot({ path: 'build/ui-evidence/refino-cartao.png' });

    await page.locator('[data-refino-map-confirm]').click();
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
