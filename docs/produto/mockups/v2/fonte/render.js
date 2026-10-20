// Tira os PNG 1280x720 e 800x480 e mede: sem rolagem, nada cortado, botões com 58 px e alinhados à esquerda.
// Uso: NODE_PATH=$(npm root -g) node render.js [filtro]
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const b = await chromium.launch({ args: ['--no-sandbox'] });
  const f = process.argv[2] || '';
  const names = fs.readdirSync(__dirname).filter((n) => /^\d\d[a-z]?-.*\.html$/.test(n) && n.includes(f));
  let bad = 0;
  for (const n of names) {
    const sizes = n.startsWith('00-') ? [[1280, 720]] : [[1280, 720], [800, 480]];
    for (const [w, h] of sizes) {
      const p = await b.newPage({ viewport: { width: w, height: h } });
      await p.goto('file://' + path.join(__dirname, n));
      const probs = await p.evaluate(() => {
        const out = [];
        const d = document.documentElement;
        if (d.scrollHeight > innerHeight + 1 || d.scrollWidth > innerWidth + 1) out.push('rolagem da página');
        const vis = (e) => { const r = e.getBoundingClientRect(); const cs = getComputedStyle(e); return r.width > 0 && r.height > 0 && cs.display !== 'none' && cs.visibility !== 'hidden'; };
        const scr = document.querySelector('.screen');
        const tp = document.querySelector('.top'); if (tp) for (const e of tp.querySelectorAll('*')) if (vis(e) && e.getBoundingClientRect().right > innerWidth + 1) { out.push('barra do topo cortada'); break; }
        if (scr) {
          const sr = scr.getBoundingClientRect();
          for (const e of scr.querySelectorAll('*')) if (vis(e)) { const r = e.getBoundingClientRect(); if (r.bottom > sr.bottom + 1 || r.right > Math.min(sr.right, innerWidth) + 1) { out.push('cortado: ' + e.className + ' "' + e.textContent.trim().slice(0, 30) + '"'); break; } }
        }
        for (const e of document.querySelectorAll('.top,.btn-bar,.row,.dr,.si,.g,.fc,.vt,.cell')) if (vis(e) && e.scrollWidth > e.clientWidth + 1) out.push('texto estoura: ' + e.className + ' "' + e.textContent.trim().slice(0, 30) + '"');
        for (const e of document.querySelectorAll('.btn-bar .btn')) if (vis(e) && Math.round(e.getBoundingClientRect().height) !== 58) out.push('botão sem 58 px');
        for (const bb of document.querySelectorAll('.btn-bar')) { const first = [...bb.children].find(vis); if (first && first.getBoundingClientRect().left - bb.getBoundingClientRect().left > 1) out.push('barra não começa à esquerda'); }
        return out;
      });
      if (probs.length) { bad++; console.log('PROBLEMA', n, w, probs.join(' | ')); }
      await p.screenshot({ path: path.join(__dirname, '..', n.replace('.html', `-${w}x${h}.png`)) });
      await p.close();
    }
  }
  await b.close();
  console.log(names.length, 'telas;', bad ? bad + ' com problema' : 'nenhum problema medido');
})();
