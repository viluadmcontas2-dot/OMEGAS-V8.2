'use strict';
// Achados da revisão de código (3 revisores independentes) — cada teste falhava antes da correção.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const U = require('./helpers/ux-cenarios.cjs');
const { makeRig } = require('./helpers/flow-harness.cjs');
const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

// ---- 2: fase "gravando" nunca fica presa (erro do nativo, ponte indisponível, exceção)
for (const [nome, resposta] of [
  ['o nativo publica ERROR', () => JSON.stringify({ state: 'ERROR', message: 'x' })],
  ['a ponte responde "serviço indisponível"', () => JSON.stringify({ ok: false, error: 'Serviço indisponível' })],
  ['a ponte lança exceção', () => { throw new Error('boom'); }],
]) {
  test(`interruptor: ${nome} durante a gravação -> erro de leigo (estado incerto), nunca preso em "gravando"`, () => {
    const rig = makeRig(); rig.bench.plugUsb(); rig.cycle();
    const real = rig.bench.autocalExtra.getAntiStalloFlagStatus;
    rig.bench.autocalExtra.applyAntiStalloFlag = () => { rig.bench.autocalExtra.getAntiStalloFlagStatus = resposta; return JSON.stringify({ ok: true, started: true }); };
    rig.tap(); rig.cycle(3);
    assert.equal(rig.view().phase, 'error', JSON.stringify(rig.view()));
    assert.equal(rig.view().uncertain, true, 'não sabe como ficou: nunca diz "nada foi gravado"');
    assert.doesNotMatch(rig.view().message, /ACK|0x|status|ECU|Serviço/);
    rig.bench.autocalExtra.getAntiStalloFlagStatus = real;
    rig.flow.dismiss(); assert.equal(rig.flow.canTap(rig.entry(), true), true, 'o interruptor volta a responder');
  });
}

// ---- 1: gravação "enfileirada" não se repete sozinha depois de erro
test('um toque a mais durante a gravação que FALHA não dispara nova gravação sozinha; o erro fica na tela', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    await page.evaluate(() => window.OmegasApp.scheduler.stop());
    await page.evaluate(() => window.__bench.setFault('nak'));          // a central recusa a escrita
    await U.clearLog(page);
    const mais = '[data-field="0"] [data-adv-step="0:1"]:not([disabled])';
    await page.waitForSelector(mais, { state: 'visible' });
    await T.tap(page, mais); await T.tap(page, mais);                   // 2º toque cai durante a gravação (fica "enfileirado")
    await T.pump(page, 40, { data: true });
    const escritas = await U.ecuWrites(page);
    assert.equal(escritas.length, 1, 'UMA tentativa (a do toque); a falha não gera 2ª gravação sem toque novo: ' + JSON.stringify(escritas));
    assert.match(await U.text(page, '.vv-status'), /não aceitou|Não foi|Tente de novo/i, 'o erro continua visível');
  } finally { await app.context.close(); }
});

test('sair da aba descarta a gravação enfileirada: ao voltar nada é gravado sozinho', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    await page.evaluate(() => window.OmegasApp.scheduler.stop());
    const mais = '[data-field="0"] [data-adv-step="0:1"]:not([disabled])';
    await page.waitForSelector(mais, { state: 'visible' });
    await T.tap(page, mais); await T.tap(page, mais);                   // 1ª grava; a 2ª fica enfileirada
    await T.route(app, 'map');                                          // sai da aba antes de terminar
    await T.pump(page, 40, { data: true });
    await U.clearLog(page);
    await U.abrirValores(app);
    await T.pump(page, 40, { data: true });
    assert.deepEqual(await U.ecuWrites(page), [], 'voltar à aba não grava nada sozinho');
  } finally { await app.context.close(); }
});

// ---- 3: interruptor e os 5 números não gravam ao mesmo tempo
test('tocar em + durante a gravação do interruptor espera a primeira terminar (nunca duas gravações simultâneas)', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await page.evaluate(() => window.OmegasApp.scheduler.stop());
    await page.evaluate(() => {
      window.__simult = 0; window.__emAndamento = 0;
      const b = window.OmegasAutoCal;
      for (const [nome, estado] of [['applyAntiStalloFlag', 'getAntiStalloFlagStatus'], ['applyAntiStalloValues', 'getAntiStalloValuesStatus']]) {
        const apply = b[nome], status = b[estado];
        b[nome] = x => { window.__emAndamento += 1; window.__simult = Math.max(window.__simult, window.__emAndamento); return apply(x); };
        b[estado] = () => { const r = status(); const s = JSON.parse(r); if (s.state === 'DONE' && !s.__contado) { window.__emAndamento = Math.max(0, window.__emAndamento - 1); } return r; };
      }
    });
    await T.tap(page, '[data-adv-switch]');
    await T.tap(page, '[data-adv-open="valores"]').catch(() => {});
    const mais = '[data-field="0"] [data-adv-step="0:1"]:not([disabled])';
    if (await page.locator(mais).count()) await T.tap(page, mais);
    await T.pump(page, 40, { data: true });
    assert.ok(await page.evaluate(() => window.__simult) <= 1, 'duas gravações ao mesmo tempo: ' + await page.evaluate(() => window.__simult));
  } finally { await app.context.close(); }
});
