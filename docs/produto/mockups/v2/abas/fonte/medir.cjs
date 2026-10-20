'use strict';
// Mede (getBoundingClientRect) onde cada botão fica em cada aba, no app REAL (sem injeção) com a bancada. Não altera o app.
// Marca: ALTURA (≠58 px), DIREITA (centro além de 60% da largura), BURACO (vão > 8 px entre vizinhos da mesma linha/pai), CORTADO (passa da tela).
// Uso: NODE_PATH=/opt/node22/lib/node_modules node docs/produto/mockups/v2/abas/fonte/medir.cjs [LARGURAxALTURA]
const path = require('node:path');
const T = require(path.resolve('tests/ui/helpers/browser.cjs'));
const [W, H] = (process.argv[2] || '1280x720').split('x').map(Number);
(async () => {
  const browser = await T.launch();
  const app = await T.openApp(browser, { viewport: { width: W, height: H }, usb: true, scale: 1 });
  const { page } = app;
  await page.evaluate(f => window.__bench.seedCurve(f), Array.from({ length: 30 }, (_, i) => 1 + Math.min(0.14, Math.max(0, (i - 6) * 0.006))));
  console.log(`# Botões no app REAL em ${W}x${H} (px): x,y,LxA | pai | marcas`);
  for (const r of ['autocal', 'refino', 'map', 'curve']) {
    await T.route(app, r); await T.pump(page, 12, { data: true });
    const list = await page.evaluate(() => {
      const vis = e => { const b = e.getBoundingClientRect(); return b.width > 0 && b.height > 0 && getComputedStyle(e).visibility !== 'hidden'; };
      const sel = 'button:not(.map-k-cell):not(.map-axis-header), summary, select';
      return [...document.querySelectorAll(sel)].filter(vis).filter(e => !e.closest('.side-nav') && !e.closest('.operation-layer')).map(e => {
        const b = e.getBoundingClientRect(); const p = e.parentElement;
        return { id: e.id || e.getAttribute('data-map-nudge') || e.getAttribute('data-curve-nudge') || '', txt: (e.innerText || e.getAttribute('aria-label') || '').trim().replace(/\s+/g, ' ').slice(0, 24),
          x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height), par: p.id ? '#' + p.id : '.' + String(p.className).split(' ')[0], iw: innerWidth };
      });
    });
    console.log('== ' + r);
    list.forEach((b, i) => {
      const marks = [];
      if (b.h !== 58) marks.push('ALTURA ' + b.h);
      if (b.x + b.w / 2 > 0.6 * b.iw) marks.push('DIREITA');
      if (b.x + b.w > b.iw) marks.push('CORTADO');
      const prev = list[i - 1];
      if (prev && prev.par === b.par && Math.abs(prev.y - b.y) < 6 && b.x - (prev.x + prev.w) > 8) marks.push('BURACO ' + (b.x - (prev.x + prev.w)) + 'px');
      console.log([b.x, b.y, b.w + 'x' + b.h].join(',') + ' | ' + b.par + ' | ' + (b.txt || b.id) + ' | ' + (marks.join('; ') || 'ok'));
    });
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
