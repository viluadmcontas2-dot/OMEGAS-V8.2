'use strict';
// Fix UI · render real em 1280×720 (Chromium + Playwright; pula sozinho sem Chromium). Classe de prova 4 (parcial).
// Aviso fora dos botões principais e ≥ 22 px; trilho ≥ 22 px; faixa legível; AutoCal com menos rolagem.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { open, go, playwright } = require('./render/lib.js');

const pw = playwright();
let browserOk = false;
if (pw) { try { browserOk = fs.existsSync(pw.chromium.executablePath()); } catch (_) { browserOk = false; } }
const skip = browserOk ? false : 'Chromium/Playwright indisponível neste ambiente';

const rect = (page, sel) => page.evaluate(s => { const e = document.querySelector(s); if (!e) return null; const b = e.getBoundingClientRect(); return { l: b.left, t: b.top, r: b.right, b: b.bottom, fs: parseFloat(getComputedStyle(e).fontSize), vis: getComputedStyle(e).display !== 'none' }; }, sel);
const hit = (a, b) => a && b && a.vis && b.vis && a.l < b.r && a.r > b.l && a.t < b.b && a.b > b.t;

test('aviso: ≥ 22 px e nunca sobre um botão principal em nenhuma aba', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected');
  try {
    await page.waitForTimeout(1500);
    const primaries = {
      curve: ['#curveReviewButton', '#curveReadButton', '#curveBackupSave'],
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

test('trilho: ECU e combustível ≥ 22 px; sem dado o selo é "—" sem caixa', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected');
  try {
    await page.waitForTimeout(2500);
    assert.ok((await rect(page, '#globalEcu')).fs >= 22);
    assert.ok((await rect(page, '#globalFuel')).fs >= 22);
    const rail = await rect(page, '.rail-status');
    const nav = await rect(page, '.side-nav');
    assert.ok(rail.t >= nav.b - 1, 'o status do trilho não invade a navegação');
    assert.ok(rail.b <= 721, 'o status do trilho cabe na tela');
  } finally { await browser.close(); }
  const off = await open(pw.chromium, 'disconnected');
  try {
    await off.page.waitForTimeout(2500);
    const bg = await off.page.evaluate(() => { const e = document.getElementById('globalFuel'); const cs = getComputedStyle(e); return [e.textContent, cs.backgroundColor]; });
    assert.equal(bg[0], '—');
    assert.equal(bg[1], 'rgba(0, 0, 0, 0)');
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

test('Refino: o botão principal reserva o lugar escondido (cabeçalho não pula)', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected', { scn: { phase: 'COLETANDO_NOSSOS', proposal: false } });
  try {
    await go(page, 'refino');
    await page.waitForTimeout(3000);
    const r = await page.evaluate(() => { const b = document.querySelector('[data-refino-primary]'); const cs = getComputedStyle(b); return { hidden: b.hidden, display: cs.display, vis: cs.visibility, w: b.getBoundingClientRect().width }; });
    if (r.hidden) { assert.equal(r.vis, 'hidden'); assert.notEqual(r.display, 'none'); assert.ok(r.w > 100, `reserva ${r.w}px`); }
  } finally { await browser.close(); }
});
