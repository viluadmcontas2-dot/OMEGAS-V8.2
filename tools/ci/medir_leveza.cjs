// Mede a leveza da interface (Chromium headless, ponte falsa). Uso: node tools/ci/medir_leveza.cjs [--json] [--viewport 360x740]
// Saída: JSON com tempo até a primeira tela, scripts/bytes, tarefas longas, nós do DOM, ouvintes, timers vivos,
// mutações por ciclo de dados (por tela, escondida ou não), vazamento em 200 trocas de aba.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const { fakeBridge } = require('./_ponte_falsa.cjs');
const UI = path.resolve(__dirname, '../../app/src/main/assets/ui');
const vp = (process.argv.find(a => /^\d+x\d+$/.test(a)) || '1280x720').split('x').map(Number);
const ROUTES = ['autocal', 'refino', 'map', 'curve', 'tools'];

async function cpu(cdp) {
  const g = Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map(x => [x.name, x.value]));
  return { task: g.TaskDuration, script: g.ScriptDuration, layout: g.LayoutDuration, style: g.RecalcStyleDuration };
}
async function metrics(cdp) {
  await cdp.send('HeapProfiler.collectGarbage');
  const g = Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map(x => [x.name, x.value]));
  return { nodes: g.Nodes, listeners: g.JSEventListeners, heapKB: Math.round(g.JSHeapUsedSize / 1024), documents: g.Documents };
}

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME || '/opt/pw-browsers/chromium', args: ['--no-sandbox', '--js-flags=--expose-gc'] });
  const page = await browser.newPage({ viewport: { width: vp[0], height: vp[1] } });
  const errors = []; const reqs = [];
  page.on('requestfinished', r => { const u = r.url(); if (/\.(js|css)(\?|$)/.test(u)) reqs.push(u); });
  page.on('pageerror', e => errors.push(String(e.message || e)));
  await page.addInitScript(fakeBridge);
  await page.addInitScript(() => {
    window.__long = []; window.__t0 = performance.now();
    new PerformanceObserver(l => l.getEntries().forEach(e => window.__long.push(Math.round(e.duration)))).observe({ entryTypes: ['longtask'] });
    // timers vivos
    const live = { interval: new Set(), timeout: new Set(), raf: 0, rafCalls: 0, timeoutCalls: 0, intervalCalls: 0 };
    window.__timers = live;
    const si = window.setInterval, ci = window.clearInterval, st = window.setTimeout, ct = window.clearTimeout, raf = window.requestAnimationFrame;
    window.setInterval = function (f, ms, ...a) { live.intervalCalls++; const id = si.call(window, f, ms, ...a); live.interval.add(id); return id; };
    window.clearInterval = function (id) { live.interval.delete(id); return ci.call(window, id); };
    window.setTimeout = function (f, ms, ...a) { live.timeoutCalls++; const id = st.call(window, function () { live.timeout.delete(id); return typeof f === 'function' ? f.apply(this, arguments) : undefined; }, ms, ...a); live.timeout.add(id); return id; };
    window.clearTimeout = function (id) { live.timeout.delete(id); return ct.call(window, id); };
    window.requestAnimationFrame = function (f) { live.rafCalls++; return raf.call(window, f); };
    // primeira tela: primeira vez que a aba ativa tem conteúdo
    window.__firstScreen = null;
    const check = () => { const h = document.querySelector('.screen.active'); if (h && h.querySelectorAll('*').length > 8 && window.__firstScreen === null) window.__firstScreen = Math.round(performance.now()); };
    document.addEventListener('DOMContentLoaded', () => { new MutationObserver(check).observe(document.documentElement, { subtree: true, childList: true }); check(); });
  });
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Performance.enable');
  await page.goto('file://' + path.join(UI, 'index.html'), { waitUntil: 'load' });
  await page.waitForFunction(() => window.OmegasApp && document.querySelector('.screen.active'), null, { timeout: 15000 });
  await page.waitForTimeout(2500);
  const out = { viewport: vp.join('x') };
  out.firstScreenMs = await page.evaluate(() => window.__firstScreen);
  const nav = await page.evaluate(() => { const n = performance.getEntriesByType('navigation')[0]; return { dcl: Math.round(n.domContentLoadedEventEnd), load: Math.round(n.loadEventEnd) }; });
  out.domContentLoadedMs = nav.dcl; out.loadMs = nav.load;
  const files = reqs.map(u => decodeURIComponent(u.replace('file://', '')));
  out.scripts = files.filter(f => f.endsWith('.js')).length;
  out.styles = files.filter(f => f.endsWith('.css')).length;
  out.scriptBytes = files.filter(f => f.endsWith('.js')).reduce((s, f) => s + fs.statSync(f).size, 0);
  out.styleBytes = files.filter(f => f.endsWith('.css')).reduce((s, f) => s + fs.statSync(f).size, 0);
  out.scriptFiles = files.filter(f => f.endsWith('.js')).map(f => path.relative(UI, f));
  out.longTasksAtLoad = await page.evaluate(() => window.__long.slice());
  out.initial = await metrics(cdp);

  // DOM e timers por aba
  out.perRoute = {};
  for (const r of ROUTES) {
    await page.locator(`.side-nav button[data-route="${r}"]`).click();
    await page.waitForTimeout(1200);
    const m = await metrics(cdp);
    m.timers = await page.evaluate(() => ({ interval: window.__timers.interval.size, timeout: window.__timers.timeout.size }));
    out.perRoute[r] = m;
  }

  // mutações por ciclo de dados com UM quadro novo por ciclo (telemetria muda); por tela e (des)ativa
  out.cycle = {};
  for (const r of ROUTES) {
    await page.locator(`.side-nav button[data-route="${r}"]`).click();
    await page.waitForTimeout(1200);
    const c0 = await cpu(cdp);
    const res = await page.evaluate(async () => {
      const app = window.OmegasApp; let seq = 5000;
      app.api.presentSnapshot = () => ({ ok: true, revision: seq, data: { valid: true, ageMs: 40, telemetryAgeMs: 40, sequence: ++seq, live: { rpm: 1500 + (seq % 40) * 25, petrol_ms: +(4 + (seq % 7) * 0.1).toFixed(3), load_bar: +(0.45 + (seq % 5) * 0.01).toFixed(3), fuel: 'GNV', unknown_raw_19: 221 } } });
      const per = {}; // mutações por <section data-screen>
      const mo = new MutationObserver(list => { for (const m of list) { const n = m.target.nodeType === 1 ? m.target : m.target.parentElement; const s = n && n.closest && n.closest('[data-screen]'); const k = s ? s.dataset.screen + (s.classList.contains('active') ? '' : '(oculta)') : (n && n.closest && n.closest('#liveBar') ? 'liveBar' : 'shell'); per[k] = (per[k] || 0) + 1; } });
      mo.observe(document.body, { subtree: true, childList: true, attributes: true, characterData: true });
      const t0 = performance.now(); const calls = { present: 0 };
      const orig = app.api.presentSnapshot; app.api.presentSnapshot = (...a) => { calls.present++; return orig(...a); };
      const longs0 = window.__long.length;
      const cycles = 40;
      for (let i = 0; i < cycles; i++) { app.scheduler.run(); await new Promise(r => requestAnimationFrame(() => setTimeout(r, 0))); }
      await new Promise(r => setTimeout(r, 100));
      mo.takeRecords(); mo.disconnect();
      const ms = performance.now() - t0;
      Object.keys(per).forEach(k => per[k] = +(per[k] / cycles).toFixed(2));
      return { mutationsPerCycle: per, msPerCycle: +(ms / cycles).toFixed(2), longTasks: window.__long.length - longs0 };
    });
    const c1 = await cpu(cdp);
    const d = k => +((c1[k] - c0[k]) * 1000 / 40).toFixed(3);
    res.cpuMsPerCycle = { task: d('task'), script: d('script'), layout: d('layout'), style: d('style') };
    out.cycle[r] = res;
  }

  // 200 trocas de aba
  const before = await metrics(cdp);
  for (let i = 0; i < 200; i++) {
    await page.evaluate(r => window.OmegasApp.router.navigate(r), ROUTES[i % ROUTES.length]);
    if (i % 10 === 0) await page.waitForTimeout(30);
  }
  await page.waitForTimeout(1500);
  const after = await metrics(cdp);
  out.switch200 = { before, after, dNodes: after.nodes - before.nodes, dListeners: after.listeners - before.listeners, dHeapKB: after.heapKB - before.heapKB };
  out.timersEnd = await page.evaluate(() => ({ interval: window.__timers.interval.size, timeout: window.__timers.timeout.size, rafCalls: window.__timers.rafCalls, setTimeoutCalls: window.__timers.timeoutCalls, setIntervalCalls: window.__timers.intervalCalls }));
  out.longTasksTotal = await page.evaluate(() => window.__long.slice());
  out.errors = errors;
  console.log(JSON.stringify(out, null, 2));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
