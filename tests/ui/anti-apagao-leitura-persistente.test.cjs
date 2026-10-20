'use strict';
// Causa de campo ("não grava nada"): a leitura dos 5 números era pedida UMA vez por visita. Se falhasse (faixa de leitura ocupada),
// os botões − / + ficavam desligados para sempre. Agora a tela pede de novo a cada quadro novo da central (por evento, sem relógio)
// até os valores chegarem, e nunca grava nada enquanto não os conhece.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const U = require('./helpers/ux-cenarios.cjs');
const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

test('a 1ª e a 2ª leitura dos valores falham em silêncio: a tela tenta de novo a cada quadro novo e libera os botões, sem escrever nada', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await page.evaluate(() => {
      const b = window.__bench; b.advRead = false; b.params = {};
      const real = window.OmegasAutoCal.readAntiStalloValues;
      window.__leituras = 0; window.__carregou = 0;
      const params = window.OmegasAutoCal.getAdvancedParams;
      // enquanto a leitura direta não funcionar, o snapshot geral também não traz ANTI_STALLO (caso de campo)
      window.OmegasAutoCal.getAdvancedParams = () => { window.__carregou += 1; const d = JSON.parse(params()); if (window.__leituras < 3) delete d.params.ANTI_STALLO; return JSON.stringify(d); };
      window.OmegasAutoCal.readAntiStalloValues = () => {
        window.__leituras += 1;
        // as duas primeiras: o nativo aceitou mas a faixa estava ocupada (nada chegou); a terceira funciona
        return window.__leituras < 3 ? JSON.stringify({ ok: true, started: true }) : real();
      };
    });
    await U.abrirValores(app);
    const mais = '[data-field="0"] [data-adv-step="0:1"]';
    assert.equal(await page.locator(mais).isDisabled(), true, 'sem valores lidos os botões ficam desligados (nunca grava no escuro)');
    for (let i = 0; i < 6; i += 1) await T.pump(page, 6, { data: true });   // ~36 quadros: 1 pedido a cada 8
    const dbg = await page.evaluate(() => ({ l: window.__leituras, c: window.__carregou }));
    assert.ok(dbg.l >= 3, 'pediu de novo até conseguir (pedidos: ' + dbg.l + ', cargas: ' + dbg.c + ')');
    await page.waitForSelector(mais + ':not([disabled])', { state: 'visible', timeout: 5000 });
    assert.deepEqual(await U.ecuWrites(page), [], 'ler não escreve');
    await T.tap(page, mais); await T.pump(page, 10, { data: true });
    assert.deepEqual(await U.anti5(page), [1650, 100, 1500, 2, 20], 'depois da leitura, um toque em + grava');
  } finally { await app.context.close(); }
});

test('não martela a central: enquanto uma leitura está em andamento (alreadyReading), não pede outra por cima', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await page.evaluate(() => {
      const b = window.__bench; b.advRead = false; b.params = {};
      window.__chamadas = 0; window.__cargas = 0;
      const params = window.OmegasAutoCal.getAdvancedParams;
      window.OmegasAutoCal.getAdvancedParams = () => { window.__cargas += 1; const d = JSON.parse(params()); delete d.params.ANTI_STALLO; return JSON.stringify(d); };
      window.OmegasAutoCal.readAntiStalloValues = () => { window.__chamadas += 1; return JSON.stringify({ ok: true, alreadyReading: true }); };
    });
    await U.abrirValores(app);
    for (let i = 0; i < 6; i += 1) await T.pump(page, 6, { data: true });
    const { n, c } = await page.evaluate(() => ({ n: window.__chamadas, c: window.__cargas }));
    assert.ok(n >= 1 && n <= c, `pediu ${n} vezes em ${c} atualizações da tela (no máximo 1 por atualização)`);
  } finally { await app.context.close(); }
});
