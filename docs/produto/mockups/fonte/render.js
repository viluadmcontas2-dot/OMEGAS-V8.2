// Tira os PNG 1280x720 e 800x480. Uso: NODE_PATH=... node render.js
const { chromium } = require(process.env.PLAYWRIGHT_DIR || 'playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const names = fs.readdirSync(__dirname).filter((f) => /^\d\d-.*\.html$/.test(f));
  for (const n of names) for (const [w, h] of [[1280, 720], [800, 480]]) {
    const p = await b.newPage({ viewport: { width: w, height: h } });
    await p.goto('file://' + path.join(__dirname, n));
    const over = await p.evaluate(() => document.documentElement.scrollHeight > innerHeight + 1 || document.documentElement.scrollWidth > innerWidth + 1);
    if (over) console.log('ROLAGEM em', n, w);
    await p.screenshot({ path: path.join(__dirname, '..', n.replace('.html', `-${w}x${h}.png`)) });
    await p.close();
  }
  await b.close();
})();
