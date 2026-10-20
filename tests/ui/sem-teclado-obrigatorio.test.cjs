'use strict';
// Regra do dono (2026-10-20): ajustar valor = botões − / + ao lado, SEM teclado. O teclado só sobe se o dono tocar no lápis.
// Todo campo numérico tem inputmode="none"; o lápis troca para numérico/decimal e foca; ao sair do campo, volta a "none".
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const T = require('./helpers/browser.cjs');
const U = require('./helpers/ux-cenarios.cjs');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');
const read = f => fs.readFileSync(path.join(UI, f), 'utf8');
const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

const tags = (src, re) => src.match(re) || [];

test('código: todo campo numérico nasce com inputmode="none" (e só o lápis o abre)', () => {
  const adv = read('components/advanced.js');
  const pref = read('core/autocal-preferencias.js');
  const dr = read('components/drawers.js');
  const html = read('index.html');
  for (const [nome, src, re] of [
    ['advanced data-adv-field', adv, /<input[^>]*data-adv-field[^>]*>/g],
    ['prefs data-pref-field', pref, /<input[^>]*data-pref-field[^>]*>/g],
    ['prefs refreshMs', pref, /<input[^>]*data-pref="refreshMs"[^>]*>/g],
    ['prefs minRpmLearn', pref, /<input[^>]*data-pref="minRpmLearn"[^>]*>/g],
    ['sessões maxmb', dr, /<input[^>]*data-session-maxmb[^>]*>/g],
    ['sessões keep', dr, /<input[^>]*data-session-keep[^>]*>/g],
    ['mapa', html, /<input[^>]*id="mapAdjustmentValue"[^>]*>/g],
    ['curva', html, /<input[^>]*id="curveTargetFactor"[^>]*>/g],
  ]) {
    const found = tags(src, re);
    assert.ok(found.length >= 1, nome + ': campo não encontrado');
    for (const t of found) assert.match(t, /inputmode="none"/, nome + ' sem inputmode="none": ' + t.slice(0, 90));
  }
  // o lápis de cada campo declara qual teclado abre (decimal/numérico)
  for (const [nome, src] of [['advanced', adv], ['prefs', pref], ['drawers', dr], ['index', html]]) {
    assert.match(src, /data-kbd-open|class="pencil"/, nome + ': falta o lápis');
  }
  assert.match(dr, /data-session-step/, 'sessões: faltam − / +');
});

test('mapa: − / + muda o valor sem foco; o lápis abre o teclado decimal de propósito e a saída volta a "none"', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await T.route(app, 'map');
    const campo = page.locator('#mapAdjustmentValue');
    assert.equal(await campo.getAttribute('inputmode'), 'none');
    const antes = await campo.inputValue();
    await T.tap(page, '[data-map-nudge="1"]');
    assert.notEqual(await campo.inputValue(), antes, '+1 mudou o valor');
    assert.equal(await page.evaluate(() => document.activeElement?.id === 'mapAdjustmentValue'), false, 'sem foco no campo');
    await T.tap(page, '[data-kbd-open]');
    assert.equal(await campo.getAttribute('inputmode'), 'decimal', 'o lápis abre o teclado');
    assert.equal(await page.evaluate(() => document.activeElement?.id), 'mapAdjustmentValue');
    await page.evaluate(() => document.activeElement.blur());
    assert.equal(await campo.getAttribute('inputmode'), 'none', 'ao sair volta a none');
  } finally { await app.close?.(); }
});

test('curva: o campo "Fator desejado" não abre teclado sozinho; o lápis abre', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await T.route(app, 'curve');
    const campo = page.locator('#curveTargetFactor');
    assert.equal(await campo.getAttribute('inputmode'), 'none');
    await T.tap(page, '#curveTargetFactor');
    assert.equal(await campo.getAttribute('inputmode'), 'none', 'tocar no campo não abre teclado');
    await T.tap(page, '[data-kbd-open]');
    assert.equal(await campo.getAttribute('inputmode'), 'decimal');
  } finally { await app.close?.(); }
});

test('avançado: campos em inputmode none; − / + muda o valor sem foco; lápis abre o teclado', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    const campo = page.locator('[data-adv-field="0"]');
    assert.equal(await campo.getAttribute('inputmode'), 'none');
    const antes = await campo.inputValue();
    await T.tap(page, '[data-adv-step="0:1"]', { allowDisabled: false });
    await T.pump(page, 3);
    assert.notEqual(await page.locator('[data-adv-field="0"]').inputValue(), antes, '+ mudou o valor');
    assert.equal(await page.evaluate(() => document.activeElement?.hasAttribute?.('data-adv-field')), false, 'sem foco no campo');
    await page.locator('[data-adv-field="0"]').evaluate(el => el.closest('.num-box').querySelector('svg.pencil').dispatchEvent(new MouseEvent('click', { bubbles: true })));
    assert.equal(await page.locator('[data-adv-field="0"]').getAttribute('inputmode'), 'numeric');
  } finally { await app.close?.(); }
});

test('sessões: tamanho máximo e quantas guardar têm − / + e não abrem teclado', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await T.route(app, 'sessions');
    await page.evaluate(() => document.querySelectorAll('#sessionsHost details.diagnostic-settings').forEach(d => { d.open = true; }));
    for (const [attr, passo] of [['data-session-maxmb', 64], ['data-session-keep', 1]]) {
      const campo = page.locator(`[${attr}]`);
      assert.equal(await campo.getAttribute('inputmode'), 'none', attr);
      const antes = Number(await campo.inputValue());
      await page.locator(`[data-session-step^="${attr.replace('data-session-', '')}:1"]`).click();
      assert.equal(Number(await campo.inputValue()), antes + passo, attr + ' + soma o passo');
      assert.equal(await page.evaluate(a => document.activeElement?.hasAttribute?.(a), attr), false, 'sem foco');
      await page.locator(`[data-session-step^="${attr.replace('data-session-', '')}:-1"]`).click();
      assert.equal(Number(await campo.inputValue()), antes, attr + ' − volta');
    }
  } finally { await app.close?.(); }
});

test('sessões: tocar no TEXTO do rótulo não mexe no valor (sem label repassando clique ao −)', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await T.route(app, 'sessions');
    await page.evaluate(() => document.querySelectorAll('#sessionsHost details.diagnostic-settings').forEach(d => { d.open = true; }));
    for (const [attr, texto] of [['data-session-maxmb', 'Tamanho máximo (MB)'], ['data-session-keep', 'Quantas guardar']]) {
      const campo = page.locator(`[${attr}]`);
      const antes = await campo.inputValue();
      await page.locator('#sessionsHost .diagnostic-settings-grid span', { hasText: texto }).first().click();
      assert.equal(await campo.inputValue(), antes, attr + ': tocar no texto não pode mudar o valor');
    }
  } finally { await app.close?.(); }
});
