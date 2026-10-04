'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { open, go, playwright } = require('./render/lib.js');
const pw = playwright();
const skip = !pw || !fs.existsSync(pw.chromium.executablePath()) ? 'Chromium indisponível' : false;
test('Diamante: telemetria persistente, navegação inferior e gráfico dominante nas duas aquisições', { skip }, async () => {
  const { browser, page, errors } = await open(pw.chromium, 'connected');
  try {
    for (const route of ['dashboard','map','curve','autocal','refino','sessions','tools','diagnostico']) {
      await go(page, route);
      await page.waitForTimeout(1800);
      const m = await page.evaluate(() => {
        const rect = s => { const b=document.querySelector(s).getBoundingClientRect();return {x:b.x,y:b.y,w:b.width,h:b.height,b:b.bottom}; };
        const chart=document.querySelector('.screen.active .ar-chart-host');
        const b=chart?.getBoundingClientRect();
        return {header:rect('.workspace-head'),nav:rect('.side-nav'),screen:rect('.screen.active'),chart:b ? {w:b.width,h:b.height}:null,
          facts:['fuel','rpm','petrol','gas','map'].map(k=>rect(`[data-vehicle-fact="${k}"]`)),
          labels:[...document.querySelectorAll('.side-nav button')].map(n=>n.textContent.trim()),
          axes:chart?.textContent||''};
      });
      assert.ok(m.header.h>=48 && m.header.h<=80, route+': estado persistente');
      assert.ok(m.nav.y>=620 && m.nav.h>=76 && m.nav.b<=720, route+': navegação inferior');
      assert.equal(m.labels.length,8);
      for (const f of m.facts) assert.ok(f.w>0 && f.h>0, route+': telemetria não some');
      if (['autocal','refino'].includes(route)) {
        assert.ok(m.chart,route+': gráfico presente');
        assert.ok(m.chart.w>=1100, route+': gráfico de ponta a ponta');
        assert.ok(m.chart.h>=340, route+': gráfico dominante');
        assert.match(m.axes,/MAP \(bar\)/);
        assert.match(m.axes,/Injeção de gasolina \(ms\)/);
      }
    }
    assert.deepEqual(errors,[]);
  } finally { await browser.close(); }
});
