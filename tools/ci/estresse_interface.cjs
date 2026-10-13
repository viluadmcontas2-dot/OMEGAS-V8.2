// Teste de estresse da interface (dono, 2026-10-13: "garanta que a telemetria é leve e que o medidor de uso não pesa").
// Para AutoCal e Refino, CPU 4x mais lenta: 600 quadros de telemetria a 20 Hz e a 100 Hz (5x o real) com toques simulados nos
// gráficos na 2ª metade (o medidor de uso escuta todos). Refeitura do gráfico só vale na 1ª metade (só telemetria). Mede custo por quadro, tarefas longas, refeitura do gráfico e crescimento de memória.
// Mede também o custo do próprio medidor (por toque; tamanho do envio no pior caso).
// Uso: node tools/ci/estresse_interface.cjs [raiz]   Sai com código 1 se algum limite for estourado.
const { chromium } = require('playwright');
const path = require('path');
const { fakeBridge } = require('./_ponte_falsa.cjs');
const ROOT = path.resolve(process.argv[2] || '.');
const INDEX = path.join(ROOT, 'app/src/main/assets/ui/index.html');
const FRAMES = Number(process.env.FRAMES || 600);
// Limites com a CPU 4x mais lenta (multimídia fraca): p95 por quadro dentro de meio quadro de 60 Hz; nenhuma tarefa longa.
const LIMIT = { p95Ms: 8, maxMs: 40, longTasks: 0, rebuilds: 0, heapGrowthMb: 8, tapUs: 60, payloadKb: 16 };
const HOSTS = { autocal: 'autocalReferenceChart', refino: 'refinoChart' };

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox', '--enable-precise-memory-info'] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  await page.addInitScript(fakeBridge);
  await page.addInitScript(() => {
    window.__long = [];
    new PerformanceObserver(list => list.getEntries().forEach(e => window.__long.push(Math.round(e.duration)))).observe({ entryTypes: ['longtask'] });
    window.__usageSent = [];
  });
  await page.goto('file://' + INDEX, { waitUntil: 'load' });
  await page.waitForTimeout(2500);
  const failures = [];
  const rows = [];

  const meterCost = await page.evaluate(() => {
    const M = window.OmegasUsageMeter;
    const meter = new M.Meter();
    const el = document.querySelector('.side-nav button[data-route="refino"]');
    const t0 = performance.now();
    for (let i = 0; i < 20000; i += 1) meter.tap(M.controlKey(el));
    const tapUs = (performance.now() - t0) / 20000 * 1000;
    const worst = new M.Meter();
    for (let i = 0; i < 5000; i += 1) worst.tap('grafico:ponto:' + i);
    const bytes = JSON.stringify(worst.delta()).length;
    return { tapUs: +tapUs.toFixed(2), keys: Object.keys(worst.delta().taps).length, payloadKb: +(bytes / 1024).toFixed(1) };
  });

  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  for (const route of ['autocal', 'refino']) {
    await page.locator(`.side-nav button[data-route="${route}"]`).click();
    await page.waitForTimeout(1500);
    for (const hz of [20, 100]) {
      await page.evaluate(() => { window.__long.length = 0; });
      const result = await page.evaluate(async ({ frames, hz, hostId }) => {
        const app = window.OmegasApp;
        let seq = 0;
        const frame = () => {
          const t = seq / hz;
          return { valid: true, ageMs: 20, telemetryAgeMs: 20, sequence: ++seq, live: { rpm: 1500 + Math.round(400 * Math.sin(t)), petrol_ms: +(4 + 2.5 * Math.sin(t / 2)).toFixed(3), load_bar: +(0.45 + 0.2 * Math.sin(t / 2)).toFixed(3), fuel: 'GNV', unknown_raw_19: 221 } };
        };
        app.api.presentSnapshot = () => ({ ok: true, revision: seq, data: frame() });
        const host = document.getElementById(hostId);
        let rebuilds = 0;
        const observer = new MutationObserver(list => { rebuilds += list.filter(m => m.type === 'childList' && m.target === host).length; });
        if (host) observer.observe(host, { childList: true });
        const targets = () => [...document.querySelectorAll(`#${hostId} svg rect, #${hostId} svg circle, #${hostId} svg path`)];
        const heap0 = performance.memory ? performance.memory.usedJSHeapSize : 0;
        const costs = [];
        let tapFree = 0;
        for (let i = 0; i < frames; i += 1) {
          const t0 = performance.now();
          app.scheduler.run();
          if (i === Math.floor(frames / 2)) { tapFree = rebuilds; }
          if (i >= frames / 2 && i % 4 === 0) { const list = targets(); if (list.length) list[i % list.length].dispatchEvent(new MouseEvent('click', { bubbles: true })); }
          costs.push(performance.now() - t0);
          await new Promise(resolve => setTimeout(resolve, 1000 / hz));
        }
        observer.disconnect();
        const heap1 = performance.memory ? performance.memory.usedJSHeapSize : 0;
        costs.sort((a, b) => a - b);
        const q = p => +costs[Math.min(costs.length - 1, Math.floor(costs.length * p))].toFixed(2);
        return { p50: q(0.5), p95: q(0.95), p99: q(0.99), max: +costs[costs.length - 1].toFixed(2), rebuilds: tapFree, rebuildsAfterTaps: rebuilds - tapFree, heapGrowthMb: +((heap1 - heap0) / 1048576).toFixed(2), taps: Object.values(app.usage?.taps || {}).reduce((a, b) => a + b, 0) };
      }, { frames: FRAMES, hz, hostId: HOSTS[route] });
      const longTasks = (await page.evaluate(() => window.__long.slice())).length;
      const row = { route, hz, ...result, longTasks };
      rows.push(row);
      if (result.p95 > LIMIT.p95Ms) failures.push(`${route}@${hz}Hz p95 ${result.p95} ms > ${LIMIT.p95Ms}`);
      if (result.max > LIMIT.maxMs) failures.push(`${route}@${hz}Hz max ${result.max} ms > ${LIMIT.maxMs}`);
      if (longTasks > LIMIT.longTasks) failures.push(`${route}@${hz}Hz ${longTasks} tarefas longas`);
      if (result.rebuilds > LIMIT.rebuilds) failures.push(`${route}@${hz}Hz gráfico refeito ${result.rebuilds}x`);
      if (result.heapGrowthMb > LIMIT.heapGrowthMb) failures.push(`${route}@${hz}Hz memória +${result.heapGrowthMb} MB`);
    }
  }
  if (meterCost.tapUs > LIMIT.tapUs) failures.push(`medidor: ${meterCost.tapUs} µs por toque > ${LIMIT.tapUs}`);
  if (meterCost.payloadKb > LIMIT.payloadKb) failures.push(`medidor: envio de ${meterCost.payloadKb} KB > ${LIMIT.payloadKb}`);
  console.log(JSON.stringify({ limites: LIMIT, medidor: meterCost, quadros: rows }, null, 1));
  console.log(failures.length ? 'ESTRESSE_INTERFACE=FAIL ' + JSON.stringify(failures) : 'ESTRESSE_INTERFACE=PASS');
  await browser.close();
  process.exit(failures.length ? 1 : 0);
})().catch(error => { console.error(error); process.exit(1); });
