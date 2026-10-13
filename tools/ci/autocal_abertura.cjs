// Quanto demora para abrir cada aba (toque → tela desenhada) e quantas tarefas longas (> 50 ms) o toque causa.
// Dono, 2026-10-13: "ao clicar em AutoCal tem um leve lag". Mede no Chromium com 4x de lentidão de CPU (a multimídia do carro é fraca).
// Uso: node tools/ci/autocal_abertura.cjs [limite-ms]   (sai com erro se alguma aba passar do limite)
const { chromium } = require('playwright');
const path = require('path');
const { fakeBridge } = require('./_ponte_falsa.cjs');
const INDEX = path.resolve('app/src/main/assets/ui/index.html');
const LIMIT = Number(process.argv[2] || 0);
const ROUTES = ['autocal', 'refino', 'local', 'map', 'curve', 'tools'];

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const out = [];
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    await page.addInitScript(fakeBridge);
    await page.addInitScript(() => {
      window.__long = [];
      new PerformanceObserver(list => list.getEntries().forEach(e => window.__long.push(Math.round(e.duration)))).observe({ entryTypes: ['longtask'] });
    });
    await page.goto('file://' + INDEX, { waitUntil: 'load' });
    await page.waitForTimeout(2500);
    const cdp = await page.context().newCDPSession(page);
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
    for (const round of [1, 2]) {
      for (const route of [...ROUTES, 'autocal']) {
        await page.evaluate(() => { window.__long.length = 0; });
        const ms = await page.evaluate(route => new Promise(resolve => {
          const t0 = performance.now();
          document.querySelector(`.side-nav button[data-route="${route}"]`).click();
          requestAnimationFrame(() => requestAnimationFrame(() => setTimeout(() => resolve(Math.round(performance.now() - t0)), 0)));
        }), route);
        await page.waitForTimeout(900);
        const long = await page.evaluate(() => window.__long.slice());
        out.push({ round, route, ms, longTasks: long });
        console.log(`volta ${round} · ${route}: ${ms} ms até pintar · tarefas longas: ${long.join(', ') || 'nenhuma'}`);
      }
    }
  } finally { await browser.close(); }
  if (LIMIT) {
    const bad = out.filter(r => r.ms > LIMIT);
    if (bad.length) { console.error('acima do limite:', JSON.stringify(bad)); process.exit(1); }
  }
})().catch(error => { console.error(error); process.exit(1); });
