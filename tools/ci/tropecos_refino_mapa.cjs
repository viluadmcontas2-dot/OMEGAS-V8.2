// Tropeços → Refino → Mapa K no app real (modo demonstração): o cartão aparece com dados de tropeço, o botão leva ao Mapa K,
// a grade mostra a PROPOSTA (nada é gravado) só nas células da faixa × RPM, com o aviso "só no mapa".
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
    await page.locator('.side-nav button[data-route="refino"]').click();
    await page.waitForTimeout(500);
    const shown = await page.evaluate(() => {
      const refino = window.OmegasApp.refino;
      refino.eq = { autopilot: {}, stalls: {
        count: 1, events: [{ kind: 'STALL', petrolMs: 2.2, mapBar: 0.3, rpmBefore: 1500 }],
        regions: [{ fromMs: 2.0, toMs: 2.5, count: 1, mapBar: 0.3, rpmBefore: 1500, rpmLow: 1500, rpmHigh: 1500 }],
        nearCount: 4, nearEvents: [{ kind: 'NEAR_STALL', petrolMs: 2.1, mapBar: 0.25, rpmBefore: 1400, afterCutoff: true }],
        nearRegions: [{ fromMs: 2.0, toMs: 2.5, count: 4, mapBar: 0.25, rpmBefore: 1450, rpmLow: 1350, rpmHigh: 1700, afterCutoff: 3 }],
      } };
      refino.render(true);
      const card = document.getElementById('refinoStalls');
      return { hidden: card.hidden, text: card.textContent, button: card.querySelector('[data-refino-stall]')?.textContent };
    });
    assert.equal(shown.hidden, false, 'cartão visível');
    assert.match(shown.text, /1 apagão\(ões\) e 4 quase-apagão/);
    assert.match(shown.text, /A ECU manda os dados; o app só registra/);
    assert.match(shown.button, /só no mapa, você confirma/);
    await page.locator('[data-refino-stall]').click();
    await page.waitForSelector('#mapGrid .map-k-cell', { timeout: 8000 });
    await page.waitForFunction(() => /Gravar \d+ alterações/.test(document.getElementById('mapReviewButton')?.textContent || ''), null, { timeout: 8000 });
    const state = await page.evaluate(() => ({
      button: document.getElementById('mapReviewButton').textContent,
      toast: document.querySelector('.alert-toast')?.textContent || '',
      proposed: [...document.querySelectorAll('#mapGrid .map-k-cell')].map((c, i) => ({ i, p: c.querySelector('span')?.textContent.trim() })).filter(c => c.p),
    }));
    assert.match(state.toast, /Tropeços no GNV: \+10% em \d+ células do Mapa K/);
    assert.match(state.toast, /Só no mapa, só aqui: nada foi gravado/);
    assert.ok(state.proposed.length >= 2 && state.proposed.length <= 6, 'poucas células propostas: ' + state.proposed.length);
    // todas nas linhas da faixa 2,0–2,5 ms (linhas 0 e 1 do demo) e em até 3 colunas
    const rows = new Set(state.proposed.map(c => Math.floor(c.i / 12)));
    assert.ok([...rows].every(r => r <= 1), 'linhas ' + [...rows]);
    assert.deepEqual(errors, [], 'sem erros de página');
    console.log('TROPECOS_REFINO_MAPA=PASS', JSON.stringify({ proposed: state.proposed.length, button: state.button }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
