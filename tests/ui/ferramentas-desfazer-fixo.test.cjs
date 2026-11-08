'use strict';
// Contrato de fluxo (R4) nas Ferramentas: o "Desfazer" de cada cartão fica SEMPRE no lugar; sem o que desfazer, fica cinza com o motivo.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');

const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

const info = (page, sel) => page.evaluate(s => {
  const els = [...document.querySelectorAll(s)];
  return els.map(e => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, off: e.disabled && e.getAttribute('aria-disabled') === 'true', title: e.title }; });
}, sel);

test('Desfazer dos cartões fica sempre visível, cinza com motivo, e no mesmo lugar depois de gravar', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true, scale: 1 });
  const { page } = app;
  try {
    await T.route(app, 'tools'); await T.pump(page, 3);
    // cartões de interruptor na tela inicial
    const flags = await info(page, '#toolAdvanced [data-adv-undo-flag]');
    const switches = await page.locator('#toolAdvanced [data-adv-switch]').count();
    assert.ok(switches > 0 && flags.length === switches, `um Desfazer por interruptor (${flags.length}/${switches})`);
    for (const f of flags) { assert.ok(f.off, 'cinza (disabled + aria-disabled)'); assert.match(f.title, /Nada para desfazer/); }
    // anti-apagão (5 valores)
    await T.tap(page, '#toolAdvanced [data-adv-open="valores"]'); await T.pump(page, 3);
    let u = await info(page, '#toolAdvanced [data-adv-undo-values]');
    assert.equal(u.length, 1, 'Desfazer dos 5 valores sempre presente'); assert.ok(u[0].off); assert.match(u[0].title, /Nada para desfazer/);
    await T.tap(page, '#toolAdvanced [data-adv-step="0:1"]'); await T.pump(page, 4);
    const after = await info(page, '#toolAdvanced [data-adv-undo-values]');
    assert.equal(after.length, 1); assert.equal(after[0].off, false, 'habilitado depois de gravar');
    assert.ok(Math.abs(after[0].x - u[0].x) <= 2 && Math.abs(after[0].y - u[0].y) <= 2, 'não mudou de lugar');
    await T.tap(page, '#toolAdvanced [data-adv-back]'); await T.pump(page, 2);
    // volta à gasolina
    await T.tap(page, '#toolAdvanced [data-adv-open="volta"]'); await T.pump(page, 3);
    const v0 = await info(page, '#toolAdvanced [data-volta-undo]');
    assert.equal(v0.length, 1, 'Desfazer da volta sempre presente'); assert.ok(v0[0].off); assert.match(v0[0].title, /Nada para desfazer/);
    await T.tap(page, '#toolAdvanced [data-volta-step="segundos:1"]'); await T.pump(page, 4);
    const v1 = await info(page, '#toolAdvanced [data-volta-undo]');
    assert.equal(v1.length, 1); assert.equal(v1[0].off, false);
    assert.ok(Math.abs(v1[0].x - v0[0].x) <= 2 && Math.abs(v1[0].y - v0[0].y) <= 2, 'não mudou de lugar');
  } finally { await app.context.close(); }
});
