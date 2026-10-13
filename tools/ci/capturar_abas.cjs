// Prévias das 6 abas (1280x720) com a barra do topo persistente. Uso: node tools/ci/capturar_abas.cjs <pasta>
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const { fakeBridge } = require('./_ponte_falsa.cjs');
const INDEX = path.resolve('app/src/main/assets/ui/index.html');
const OUT = path.resolve(process.argv[2] || 'build/ui-evidence/abas');
fs.mkdirSync(OUT, { recursive: true });
(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errors = [];
    page.on('pageerror', e => errors.push(String(e.message || e)));
    await page.addInitScript(fakeBridge);
    await page.goto('file://' + INDEX, { waitUntil: 'load' });
    await page.waitForTimeout(1500);
    // Quadro ao vivo de exemplo (o demo não tem ECU): só para a barra e o cursor aparecerem nas prévias.
    await page.evaluate(() => {
      const app = window.OmegasApp;
      const frame = () => ({ valid: true, ageMs: 40, telemetryAgeMs: 40, sequence: Date.now(), live: { rpm: 1170 + Math.round(Math.random() * 20), petrol_ms: 3.44, load_bar: 0.39, fuel: 'GNV', unknown_raw_19: 221 } });
      app.api.presentSnapshot = () => ({ ok: true, revision: Date.now(), data: frame() });
    });
    let n = 1;
    for (const route of ['autocal', 'refino', 'local', 'map', 'curve', 'tools']) {
      await page.locator(`.side-nav button[data-route="${route}"]`).click();
      await page.waitForTimeout(1000);
      await page.evaluate(() => document.querySelectorAll('.alert-toast').forEach(x => x.remove()));
      await page.screenshot({ path: path.join(OUT, `${String(n++).padStart(2, '0')}-${route}.png`) });
      console.log('ok', route);
    }
    if (errors.length) { console.error('erros de página:', errors); process.exit(1); }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
