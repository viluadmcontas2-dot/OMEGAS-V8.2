'use strict';
// Fix UI · render real em 1280×720 (Chromium + Playwright; pula sozinho sem Chromium). Classe de prova 4 (parcial).
// Aviso fora dos botões principais e ≥ 22 px; trilho ≥ 22 px; faixa legível; AutoCal com menos rolagem.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { open, go, playwright } = require('./render/lib.js');

const pw = playwright();
let browserOk = false;
if (pw) { try { browserOk = !!require("./render/lib.js").chromiumPath(pw.chromium); } catch (_) { browserOk = false; } }
const skip = browserOk ? false : 'Chromium/Playwright indisponível neste ambiente';

const rect = (page, sel) => page.evaluate(s => { const e = document.querySelector(s); if (!e) return null; const b = e.getBoundingClientRect(); return { l: b.left, t: b.top, r: b.right, b: b.bottom, fs: parseFloat(getComputedStyle(e).fontSize), vis: getComputedStyle(e).display !== 'none' }; }, sel);
const hit = (a, b) => a && b && a.vis && b.vis && a.l < b.r && a.r > b.l && a.t < b.b && a.b > b.t;

test('aviso: ≥ 22 px e nunca sobre um botão principal em nenhuma aba', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected');
  try {
    await page.waitForTimeout(1500);
    const primaries = {
      curve: ['#curveReviewButton', '#curveReadButton'],
      map: ['#mapReviewButton', '#mapReadButton'],
      autocal: ['[data-autocal-toggle]', '[data-autocal-action="RESET_GAS"]', '[data-autocal-action="RESET_PETROL"]'],
      refino: ['[data-refino-primary]', '#refinoEqGo'],
      dashboard: ['[data-usb-allow]'],
      // Ferramentas e Sessões não têm botão principal fixo (lista e blocos rolam); o aviso é passageiro.
    };
    for (const [route, selectors] of Object.entries(primaries)) {
      await go(page, route);
      await page.waitForTimeout(2500);
      await page.evaluate(() => window.OmegasApp.store.patch({ alert: { level: 'warning', message: 'Não deu para calcular este ponto. Toque no ponto e tente de novo.' } }));
      await page.waitForTimeout(400);
      const toast = await rect(page, '#alertToast b');
      assert.ok(toast && toast.vis, `${route}: aviso visível`);
      assert.ok(toast.fs >= 22, `${route}: aviso ${toast.fs}px < 22`);
      const box = await rect(page, '#alertToast');
      for (const s of selectors) assert.ok(!hit(box, await rect(page, s)), `${route}: o aviso cobre ${s}`);
      await page.evaluate(() => document.getElementById('alertToast').classList.remove('show'));
    }
  } finally { await browser.close(); }
});

// Atualizado de propósito (07/10, rodada 2): o trilho lateral saiu (navegação inferior de 8 abas); ECU e combustível vivem na
// faixa única do topo (#vehicleStatusStrip). O teste antigo media .rail-status/.side-nav, que não existem mais na tela.
test('faixa do topo: ECU e combustível ≥ 22 px, acima da navegação; sem dado o combustível é "—" sem caixa', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected');
  try {
    await page.waitForTimeout(2500);
    const r = await page.evaluate(() => {
      const box = s => { const e = document.querySelector(s); if (!e) return null; const b = e.getBoundingClientRect(); return { t: b.top, b: b.bottom, h: b.height }; };
      const vals = [...document.querySelectorAll('#vehicleStatusStrip small')].filter(e => /^(ECU|COMBUSTÍVEL)$/.test(e.textContent.trim())).map(e => ({ rot: e.textContent.trim(), fs: parseFloat(getComputedStyle(e.nextElementSibling).fontSize), h: e.nextElementSibling.getBoundingClientRect().height }));
      return { strip: box('#vehicleStatusStrip'), nav: box('.side-nav'), vals };
    });
    assert.equal(r.vals.length, 2, 'ECU e combustível na faixa do topo');
    for (const v of r.vals) { assert.ok(v.fs >= 22, `${v.rot} ${v.fs}px < 22`); assert.ok(v.h > 0, `${v.rot} sem altura`); }
    assert.ok(r.strip.h > 0 && r.strip.b <= r.nav.t, 'a faixa do topo não invade a navegação');
    assert.ok(r.nav.b <= 721, 'a navegação cabe na tela');
  } finally { await browser.close(); }
  const off = await open(pw.chromium, 'disconnected');
  try {
    await off.page.waitForTimeout(2500);
    const bg = await off.page.evaluate(() => { const e = document.getElementById('dashFuel'); const cs = getComputedStyle(e); return [e.textContent, cs.backgroundColor, getComputedStyle(e.parentElement).backgroundColor]; });
    assert.equal(bg[0], '—');
    assert.equal(bg[1], 'rgba(0, 0, 0, 0)');
    assert.equal(bg[2], 'rgba(0, 0, 0, 0)');
  } finally { await off.browser.close(); }
});

test('AutoCal: texto crítico ≥ 22 px na grade de zonas e rolagem menor que antes (1411 px)', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected');
  try {
    await go(page, 'autocal');
    await page.waitForTimeout(3500);
    const r = await page.evaluate(() => {
      const sc = document.querySelector('[data-screen="autocal"]');
      const f = s => { const e = document.querySelector(s); return e ? parseFloat(getComputedStyle(e).fontSize) : null; };
      return { height: sc.scrollHeight, zoneFuel: f('.autocal-zone-fuel'), zoneB: f('.autocal-zone-cell b'), zoneSmall: f('.autocal-zone-cell small'), inspector: f('.autocal-chart-inspector b') };
    });
    assert.ok(r.height < 1400, `rolagem total ${r.height}px (era 1411)`);
    for (const k of ['zoneFuel', 'zoneB', 'zoneSmall', 'inspector']) assert.ok(r[k] === null || r[k] >= 22, `${k} ${r[k]}px < 22`);
  } finally { await browser.close(); }
});

// Atualizado de propósito (07/10, rodada 2): sem cabeçalho no Ajuste GNV; o rodapé único segue a regra .btn-bar
// ("botão com hidden não ocupa lugar"), então o principal escondido NÃO reserva mais espaço.
test('Refino: o botão principal escondido não ocupa lugar no rodapé único', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected', { scn: { phase: 'COLETANDO_NOSSOS', proposal: false } });
  try {
    await go(page, 'refino');
    await page.waitForTimeout(3000);
    const r = await page.evaluate(() => { const b = document.querySelector('[data-refino-primary]'); const cs = getComputedStyle(b); return { hidden: b.hidden, display: cs.display, w: b.getBoundingClientRect().width, header: !!document.querySelector('.screen.active .ar-status') }; });
    assert.equal(r.header, false, 'o cabeçalho do Ajuste GNV não existe mais');
    if (r.hidden) { assert.equal(r.display, 'none'); assert.equal(r.w, 0); }
  } finally { await browser.close(); }
});
