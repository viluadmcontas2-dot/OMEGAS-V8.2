'use strict';
// Capturas da nova experiência: cada rota nos 3 tamanhos canônicos, com USB ligado e ponte falsa. Uso: node tools/ci/capturar_novo.cjs [pasta-saida]
const path = require('node:path');
const fs = require('node:fs');
const B = require('../../tests/ui/helpers/browser.cjs');
const OUT = process.argv[2] || path.join(B.ROOT, 'docs/evidence/img');
const SIZES = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };
const ROUTES = ['regular', 'carro', 'autocal', 'refino', 'map', 'curve', 'tools'];
(async () => {
  const why = B.skipReason(); if (why) { console.log('pulado:', why); return; }
  const browser = await B.launch();
  fs.mkdirSync(OUT, { recursive: true });
  const report = [];
  for (const [label, viewport] of Object.entries(SIZES)) {
    const app = await B.openApp(browser, { viewport, usb: true, scale: 1 });
    for (const r of ROUTES) {
      await app.page.evaluate(name => window.OmegasApp.router.navigate(name), r);
      await B.pump(app.page, 4, { data: true });
      const m = await app.page.evaluate(() => {
        const scr = document.querySelector('.screen.active');
        const bad = [];
        if (document.documentElement.scrollHeight > innerHeight + 1 || document.documentElement.scrollWidth > innerWidth + 1) bad.push('página rola');
        if (scr) scr.querySelectorAll('*').forEach(el => { const s = getComputedStyle(el); if (s.display === 'none') return; const b = el.getBoundingClientRect(); if (b.width && (b.right > innerWidth + 1 || b.bottom > innerHeight + 1)) bad.push((el.id || el.className || el.tagName).toString().slice(0, 40)); });
        return { bad: [...new Set(bad)].slice(0, 6), erro: document.querySelectorAll('[data-route-failed]').length };
      });
      report.push(`${label} ${r}: ${m.bad.length || m.erro ? 'PROBLEMA ' + JSON.stringify(m) : 'ok'}`);
      await app.page.screenshot({ path: path.join(OUT, `novo-${label}-${r}.png`) });
    }
    if (app.problems.page.length) report.push(`${label} erros de página: ${app.problems.page.join(' | ')}`);
    await app.context.close();
  }
  await browser.close();
  console.log(report.join('\n'));
})();
