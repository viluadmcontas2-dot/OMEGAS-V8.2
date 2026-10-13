// Custo do tempo real na AutoCal: roda 600 quadros de telemetria (20 Hz, 30 s) com a CPU 4x mais lenta e mede
// (a) tempo de JavaScript por quadro, (b) tarefas longas (> 50 ms), (c) quantas vezes o gráfico inteiro é refeito,
// (d) quantos nós do DOM mudam por quadro. Dono, 2026-10-13: "multimídia lenta; o cursor tem que ser rápido".
// Uso: node tools/ci/desempenho_cursor.cjs [raiz-do-app]   (a raiz traz app/src/main/assets/ui/index.html; padrão = este repositório)
const { chromium } = require('playwright');
const path = require('path');
const { fakeBridge } = require('./_ponte_falsa.cjs');
const ROOT = path.resolve(process.argv[2] || '.');
const INDEX = path.join(ROOT, 'app/src/main/assets/ui/index.html');
const FRAMES = Number(process.env.FRAMES || 600);

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  await page.addInitScript(fakeBridge);
  await page.addInitScript(() => {
    window.__long = [];
    new PerformanceObserver(list => list.getEntries().forEach(e => window.__long.push(Math.round(e.duration)))).observe({ entryTypes: ['longtask'] });
  });
  await page.goto('file://' + INDEX, { waitUntil: 'load' });
  await page.waitForTimeout(2500);
  await page.locator('.side-nav button[data-route="autocal"]').click();
  await page.waitForTimeout(1500);
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  const result = await page.evaluate(async frames => {
    const app = window.OmegasApp;
    let seq = 0;
    const frame = () => {
      const t = seq / 20;
      return { valid: true, ageMs: 40, telemetryAgeMs: 40, sequence: ++seq, live: { rpm: 1500 + Math.round(400 * Math.sin(t)), petrol_ms: +(4 + 2.5 * Math.sin(t / 2)).toFixed(3), load_bar: +(0.45 + 0.2 * Math.sin(t / 2)).toFixed(3), fuel: 'GNV', unknown_raw_19: 221 } };
    };
    app.api.presentSnapshot = () => ({ ok: true, revision: seq, data: frame() });
    let mutations = 0;
    const observer = new MutationObserver(list => { mutations += list.length; });
    observer.observe(document.body, { subtree: true, childList: true, attributes: true, characterData: true });
    const host = document.getElementById('autocalReferenceChart');
    let rebuilds = 0;
    const hostObserver = new MutationObserver(list => { rebuilds += list.filter(m => m.type === 'childList' && m.target === host || m.target.classList?.contains('ao-layout')).length; });
    if (host) hostObserver.observe(host, { childList: true });
    const costs = [];
    for (let i = 0; i < frames; i += 1) {
      const t0 = performance.now();
      app.scheduler.run();
      costs.push(performance.now() - t0);
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    observer.disconnect(); hostObserver.disconnect();
    costs.sort((a, b) => a - b);
    const q = p => +costs[Math.min(costs.length - 1, Math.floor(costs.length * p))].toFixed(2);
    return { frames, p50: q(0.5), p95: q(0.95), p99: q(0.99), max: +costs[costs.length - 1].toFixed(2), avg: +(costs.reduce((a, b) => a + b, 0) / costs.length).toFixed(2), mutationsPerFrame: +(mutations / frames).toFixed(1), chartRebuilds: rebuilds };
  }, FRAMES);
  const long = await page.evaluate(() => window.__long.slice());
  console.log(JSON.stringify({ root: ROOT, ...result, longTasks: long.length, longTaskMax: long.length ? Math.max(...long) : 0 }));
  await browser.close();
})().catch(error => { console.error(error); process.exit(1); });
