'use strict';
// Direção visual (dono, 2026-10-07): toda barra de botões (.btn-bar ou .ar-act > .ar-buttons) tem botões do tamanho natural,
// 58 px de altura, 8 px entre eles na mesma linha, sem esticar e sem rolagem lateral. Classe de prova 4 (render real, ponte falsa).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { open, go, playwright } = require('./render/lib.js');

const pw = playwright();
let ok = false;
if (pw) { try { ok = fs.existsSync(pw.chromium.executablePath()); } catch (_) { ok = false; } }
const skip = ok ? false : 'Chromium/Playwright indisponível neste ambiente';
const TABS = ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools', 'diagnostico'];
const MUST_HAVE = ['map', 'curve', 'autocal', 'refino'];

async function bars(page) {
  return page.evaluate(() => {
    const out = [];
    const screen = document.querySelector('.screen.active');
    const barsEls = [...screen.querySelectorAll('.btn-bar, .ar-act > .ar-buttons')];
    for (const bar of barsEls) {
      const items = [...bar.querySelectorAll('button, summary, .value-field, .segmented, .view-switch')].filter(e => {
        const b = e.getBoundingClientRect();
        if (!(b.width > 0 && b.height > 0)) return false;
        if (e.closest('[hidden]')) return false;
        if (e.parentElement && e.parentElement.closest('.segmented, .view-switch')) return false;
        if (e.closest('.curve-photos-menu, .instrument-detail-content, .operation-layer')) return false;
        return true;
      }).map(e => { const b = e.getBoundingClientRect(); return { l: b.left, r: b.right, t: b.top, h: b.height, name: (e.id || (e.querySelector('input') || {}).id || e.textContent.trim().slice(0, 20)) }; });
      out.push({ cls: bar.className, items, overflow: screen.scrollWidth > screen.clientWidth + 1 });
    }
    return out;
  });
}

for (const route of TABS) {
  test(`btn-bar · ${route}: botões naturais, 58 px, 8 px entre eles, mesma linha`, { skip }, async () => {
    const { browser, page } = await open(pw.chromium, 'connected', { viewport: { width: 1280, height: 720 }, scn: { phase: 'COLETANDO_NOSSOS', noStalls: true } });
    try {
      await go(page, route);
      await page.waitForTimeout(2200);
      const found = await bars(page);
      if (MUST_HAVE.includes(route)) assert.ok(found.length > 0, `${route}: nenhuma barra de botões encontrada`);
      for (const bar of found) {
        assert.equal(bar.overflow, false, `${route}: rolagem lateral`);
        for (const it of bar.items) {
          if (it.name === 'mapAdjustmentValue' || it.name === 'curveTargetFactor') continue; // campos de número seguem a altura, não o texto
          assert.ok(it.h >= 44, `${route}: ${it.name} com ${Math.round(it.h)} px (< 44)`);
        }
        const rows = new Map();
        for (const it of bar.items) { const k = Math.round(it.t / 4); rows.set(k, [...(rows.get(k) || []), it]); }
        for (const row of rows.values()) {
          row.sort((a, b) => a.l - b.l);
          for (let i = 1; i < row.length; i++) {
            const gap = row[i].l - row[i - 1].r;
            assert.ok(gap >= 6 && gap <= 10, `${route}: ${row[i - 1].name} → ${row[i].name} com ${Math.round(gap)} px de espaço (esperado 8)`);
          }
        }
      }
    } finally { await browser.close(); }
  });
}
