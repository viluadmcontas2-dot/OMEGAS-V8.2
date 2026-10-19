'use strict';
// Regra do dono (2026-10-19): TODO texto precisa ser entendido por um usuário extremamente leigo. Aqui o que dá para provar por máquina:
// nunca "Rotação máxima" solta (sempre "Rotação máxima para agir"), nunca número sem dizer de quê, nenhuma sigla técnica na tela de valores.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const U = require('./helpers/ux-cenarios.cjs');
const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });
const SIGLAS = /\b(ECU|ANTI_STALLO|FLAG|CONF3|SetVector|u16|ACK|NAK|hex|0x[0-9A-F]+|serial|readback|backpressure)\b/i;

test('textos da tela de valores: sem ambiguidade e sem sigla (em todas as abas da explicação)', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    const coletar = () => page.evaluate(() => [...document.querySelectorAll('.values-view p, .values-view li, .values-view button, .values-view label, .values-view caption')].map(e => e.innerText.trim()).filter(Boolean));
    const blocos = [];
    blocos.push(...await coletar());
    const textos = [await U.text(page, '.values-view')];
    await T.tap(page, '[data-adv-open-view="info"]'); await T.pump(page, 2);
    const abas = await page.locator('[data-adv-info-tab]').count();
    for (let i = 0; i < abas; i++) { await page.locator('[data-adv-info-tab]').nth(i).click(); await T.pump(page, 1); textos.push(await U.text(page, '.vv-info-full')); blocos.push(...await coletar()); }
    const tudo = textos.join('\n');
    for (const m of tudo.matchAll(/Rotação máxima(?! para agir)/g)) assert.fail('"Rotação máxima" solta (rotação de quê?): …' + tudo.slice(Math.max(0, m.index - 30), m.index + 60));
    assert.doesNotMatch(tudo, SIGLAS, 'sigla técnica na tela');
    for (const frase of blocos.join('\n').split(/(?<=[.!?])\s+/)) assert.ok(frase.length < 260, 'frase longa demais para leigo: ' + frase.slice(0, 80));
    // todo "até N rpm" aponta para o número certo
    assert.match(tudo, /aumente o número «Rotação máxima para agir» \(o primeiro card\) até 2\.000 rpm/);
  } finally { await app.context.close(); }
});
