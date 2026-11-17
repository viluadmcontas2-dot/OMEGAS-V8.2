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
    const app = await browser.openApp(chromium, { viewport, usb: true, scale: 1 });
    try {
      const shot = async (route) => {
        fs.mkdirSync(browser.IMG_DIR, { recursive: true });
        await app.page.screenshot({
          path: path.join(browser.IMG_DIR, 'ux-adaptive-' + viewport.width + 'x' + viewport.height + '-' + route + '.png'),
          animations: 'disabled', fullPage: false
        });
      };
      await shot('autocal');
      const state = await app.page.evaluate(() => {
        const rect = selector => {
          const e = document.querySelector(selector);
          const r = e.getBoundingClientRect();
          return { left:r.left,right:r.right,top:r.top,bottom:r.bottom,visible:r.width>0&&r.height>0 };
        };
        return { w: innerWidth, documentWidth: document.documentElement.scrollWidth,
          nav: rect('.side-rail'), live: rect('.live-bar'),
          routes: [...document.querySelectorAll('.side-nav button')].map(e=>({
            route:e.dataset.route, width:e.getBoundingClientRect().width,
            labelFont: parseFloat(getComputedStyle(e.querySelector('span')).fontSize),
            labelFits: (() => { const t=e.querySelector('span'); return t.scrollHeight <= t.clientHeight + 2 && t.scrollWidth <= t.clientWidth + 2; })()
          })) };
      });
      assert.ok(state.documentWidth <= state.w + 2, JSON.stringify(state));
      assert.equal(state.routes.length,5);
      assert.ok(state.routes.every(v=>v.width>=44), JSON.stringify(state));
      if (viewport.width <= 390) {
        assert.ok(state.routes.every(v=>v.labelFont >= 12 && v.labelFits), 'rótulos de navegação devem ser legíveis e inteiros: '+JSON.stringify(state));
      }
      assert.ok(state.live.right <= state.w+2, JSON.stringify(state));
      assert.ok(state.nav.right <= state.w+2, JSON.stringify(state));
      for (const route of ['refino','map','curve','tools']) {
        await app.page.evaluate(r => window.OmegasApp.router.navigate(r), route);
        await browser.pump(app.page,2);
        await shot(route);
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

for (const viewport of [{ width: 360, height: 800 }, { width: 390, height: 844 }]) {
  test('grade K preserva as 13 colunas, eixos e 144 valores no retrato '+viewport.width+'x'+viewport.height, { skip }, async () => {
    const app = await browser.openApp(chromium, { viewport, usb: true, scale: 1 });
    try {
      await browser.route(app, 'map');
      const grid = await app.page.evaluate(() => {
        const table = document.querySelector('#mapGrid .map-k-grid-with-axes');
        if (!table) return { absent: true };
        const rect = el => {
          const r = el.getBoundingClientRect();
          return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width) };
        };
        const cols = getComputedStyle(table).gridTemplateColumns.trim().split(/\s+/);
        const rpm = [...table.querySelectorAll('.map-rpm-header')];
        const ms = [...table.querySelectorAll('.map-ms-header')];
        const cells = [...table.querySelectorAll('.map-k-cell')];
        return {
          columns: cols.length, rpm: rpm.length, ms: ms.length, cells: cells.length,
          corner: rect(table.querySelector('.map-axis-corner')),
          rpmFirst: rect(rpm[0]), rpmLast: rect(rpm[11]),
          msFirst: rect(ms[0]), cell00: rect(table.querySelector('[data-row="0"][data-column="0"]')),
          cell011: rect(table.querySelector('[data-row="0"][data-column="11"]')),
          host: rect(document.querySelector('#mapGrid'))
        };
      });
      assert.equal(grid.columns, 13, JSON.stringify(grid));
      assert.deepEqual([grid.rpm, grid.ms, grid.cells], [12, 12, 144]);
      assert.ok(Math.abs(grid.corner.y - grid.rpmFirst.y) <= 2, JSON.stringify(grid));
      assert.ok(Math.abs(grid.corner.x - grid.msFirst.x) <= 2, JSON.stringify(grid));
      assert.ok(Math.abs(grid.rpmFirst.x - grid.cell00.x) <= 2, JSON.stringify(grid));
      assert.ok(Math.abs(grid.msFirst.y - grid.cell00.y) <= 2, JSON.stringify(grid));
      assert.ok(grid.rpmLast.x > grid.rpmFirst.x && grid.cell011.x > grid.cell00.x, JSON.stringify(grid));
      assert.ok(grid.host.w < grid.cell011.x + grid.cell011.w, 'a grade larga deve rolar apenas dentro do painel');
      assert.equal(app.problems.requests.length, 0, JSON.stringify(app.problems));
    } finally { await app.context.close(); }
  });
}
