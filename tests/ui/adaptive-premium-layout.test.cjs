'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const html = fs.readFileSync(path.join(ui, 'index.html'), 'utf8');
const css = fs.readFileSync(path.join(ui, 'styles-adaptive.css'), 'utf8');

test('preserva as cinco rotas e carrega a camada adaptativa depois do cockpit', () => {
  for (const route of ['autocal','refino','map','curve','tools']) {
    assert.match(html, new RegExp('data-route="' + route + '"'));
  }
  assert.ok(html.indexOf('styles-autocal-cockpit.css') < html.indexOf('styles-adaptive.css'));
  assert.match(html, /data-autocal-cockpit-style="true"/);
  assert.match(css, /orientation:\s*portrait/);
  assert.match(css, /min-width:\s*1600px/);
  assert.match(css, /prefers-reduced-motion:\s*reduce/);
});

test('não altera comandos da central e mantém ações de gravação explícitas', () => {
  for (const id of ['mapReviewButton','curveReviewButton','curveBackupSave','curveBackupRestore']) {
    assert.match(html, new RegExp('id="' + id + '"'));
  }
  assert.doesNotMatch(css, /writeMap|writeCurve|protocolTransaction|RESET_ALL/);
});

const browser = require('./helpers/browser.cjs');
const skip = browser.skipReason() || false;
let chromium;
test.before(async () => { if (!skip) chromium = await browser.launch(); });
test.after(async () => { if (chromium) await chromium.close(); });

const viewports = [
  { width: 360, height: 800 },
  { width: 390, height: 844 },
  { width: 768, height: 1024 },
  { width: 1280, height: 720 },
  { width: 1920, height: 1080 },
  { width: 2560, height: 1080 },
];
for (const viewport of viewports) {
  test('navegação e barra ao vivo sem overflow no visor '+viewport.width+'x'+viewport.height, { skip }, async () => {
    const app = await browser.openApp(chromium, { viewport });
    try {
      const state = await app.page.evaluate(() => {
        const rect = selector => {
          const e = document.querySelector(selector);
          const r = e.getBoundingClientRect();
          return { left:r.left,right:r.right,top:r.top,bottom:r.bottom,visible:r.width>0&&r.height>0 };
        };
        return { w: innerWidth, documentWidth: document.documentElement.scrollWidth,
          nav: rect('.side-rail'), live: rect('.live-bar'),
          routes: [...document.querySelectorAll('.side-nav button')].map(e=>({route:e.dataset.route,width:e.getBoundingClientRect().width})) };
      });
      assert.ok(state.documentWidth <= state.w + 2, JSON.stringify(state));
      assert.equal(state.routes.length,5);
      assert.ok(state.routes.every(v=>v.width>=44), JSON.stringify(state));
      assert.ok(state.live.right <= state.w+2, JSON.stringify(state));
      assert.ok(state.nav.right <= state.w+2, JSON.stringify(state));
      for (const route of ['refino','map','curve','tools']) {
        await app.page.evaluate(r => window.OmegasApp.router.navigate(r), route);
        await browser.pump(app.page,2);
        const fit = await app.page.evaluate(() => ({
          doc: document.documentElement.scrollWidth,
          body: document.body.scrollWidth,
          w:innerWidth,
          visible:!!document.querySelector('.screen.active')
        }));
        assert.ok(fit.visible && fit.doc <= fit.w + 2,route+' '+JSON.stringify(fit));
      }
    } finally { await app.context.close(); }
  });
}
