// Captura fixa de estados da interface (sem números que mudam) para comparar antes/depois de uma poda de CSS/JS.
// Uso: node tools/ci/capturar_estados.cjs <pasta>   e depois: python3 tools/ci/comparar_pngs.py <pastaA> <pastaB>
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const { fakeBridge } = require('./_ponte_falsa.cjs');
const OUT = path.resolve(process.argv[2]);
fs.mkdirSync(OUT, { recursive: true });
(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  await page.addInitScript(fakeBridge);
  await page.goto('file://' + path.resolve('app/src/main/assets/ui/index.html'), { waitUntil: 'load' });
  await page.waitForTimeout(1500);
  await page.evaluate(() => {
    window.OmegasApp.api.presentSnapshot = () => ({ ok: true, revision: 1, data: { ok: true, valid: true, sequence: 1, ageMs: 40, telemetryAgeMs: 40, live: { rpm: 1500, petrol_ms: 4.2, load_bar: 0.5, fuel: 'GNV', unknown_raw_19: 221 } } });
  });
  const shot = async name => { await page.waitForTimeout(700); await page.evaluate(() => document.querySelectorAll('.alert-toast').forEach(x => x.remove())); await page.screenshot({ path: path.join(OUT, name + '.png') }); };
  for (const route of ['autocal', 'refino', 'map', 'curve', 'tools']) {
    await page.locator(`.side-nav button[data-route="${route}"]`).click();
    await shot(route);
  }
  await page.locator('.side-nav button[data-route="autocal"]').click();
  await page.locator('.autocal-reset-menu > summary').click().catch(() => {});
  await shot('autocal-acoes-avancadas');
  await page.locator('.side-nav button[data-route="map"]').click();
  await page.waitForSelector('#mapGrid .map-k-cell', { timeout: 8000 });
  await page.locator('#mapGrid .map-k-cell').nth(14).click();
  await shot('map-celula-selecionada');
  await page.locator('#mapFromCurve').click();
  await page.waitForFunction(() => /Gravar \d+ alterações/.test(document.getElementById('mapReviewButton')?.textContent || ''), null, { timeout: 8000 });
  await shot('map-proposta');
  await page.locator('.side-nav button[data-route="curve"]').click();
  await page.waitForSelector('#curveChart [data-curve-dot="5"]', { timeout: 8000 });
  await page.locator('#curveChart [data-curve-dot="5"]').click();
  await shot('curve-ponto-ativo');
  await browser.close();
})().catch(error => { console.error(error); process.exit(1); });
