'use strict';
// Regressão de campo: AutoCal recebe telemetria, mas Ferramentas perde o cache do param_snapshot.
// Exercita os scripts reais + ponte de ECU simulada (quadros 29/35 e readback), não apenas HTML.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');

const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

async function connectedWithMissingSnapshot() {
  const app = await T.openApp(browser, { usb: true, viewport: { width: 1280, height: 720 } });
  // O primeiro quadro válido chegou como no AutoCal; o snapshot de parâmetros não ficou no cache.
  const before = await app.page.evaluate(() => {
    const b = window.__bench;
    if (!b.live || !b.usb) throw Error('ECU simulada não conectou nem entregou telemetria');
    b.advRead = false;
    b.params = {};
    b.ecu.clearLog();
    return { rpm: b.live.rpm, usb: b.usb };
  });
  assert.ok(before.rpm > 0 && before.usb, 'AutoCal tem dados da central');
  await T.route(app, 'tools');
  assert.match(await app.page.locator('#toolAdvanced [data-card="anti"]').innerText(), /Anti-apagão/);
  assert.equal(await app.page.evaluate(() => window.__bench.advRead), false);
  return app;
}

test('RED de campo: conectado e com AutoCal vivo, cache vazio não bloqueia leitura no primeiro toque', { skip }, async () => {
  const app = await connectedWithMissingSnapshot();
  try {
    const button = app.page.locator('#toolAdvanced [data-adv-switch]');
    assert.equal(await button.isDisabled(), false, 'o toque deve iniciar leitura ao vivo, mesmo sem snapshot inicial');
    await T.tap(app.page, '[data-adv-switch]');
    await T.pump(app.page, 5);
    await T.pump(app.page, 10);
    const frames = await T.frames(app.page);
    assert.ok(frames.log.some(f => f.kind === 'READ' && f.hex.startsWith('29 FA 00 ')), 'o toque lê FLAG_CONF3 ao vivo antes de escrever');
    const writes = T.writesOf(frames.log);
    assert.equal(writes.length, 1, 'sem atrito: UM toque lê, grava e confere (nenhuma pergunta)');
    assert.match(writes[0].hex, /^35 FA 00 /, 'escreve apenas FLAG_CONF3');
    const words = await app.page.evaluate(() => window.__bench.ecu.get('29 FA 00 23'));
    assert.ok(words && (words[0] & 0x20) !== 0, 'simulador tem bit 0x20 ligado após readback');
    assert.match(await app.page.locator('#toolAdvanced [data-card="anti"]').innerText(), /Ligado/);
  } finally { await app.context.close(); }
});

test('sem cache mas com bit já ligado: lê ao vivo, não produz segunda escrita', { skip }, async () => {
  const app = await connectedWithMissingSnapshot();
  try {
    await app.page.evaluate(() => window.__bench.seed('29 FA 00 23', [0x20, 0, 0, 0]));
    await T.tap(app.page, '[data-adv-switch]');
    await T.pump(app.page, 5);
    const frames = await T.frames(app.page);
    assert.deepEqual(T.writesOf(frames.log), []);
    // Sem atrito (dono, 2026-10-20): "já está ligado" vai na linha do cartão, sem janela "Aviso" nem toque em Fechar.
    assert.match(await app.page.locator('#toolAdvanced [data-card="anti"] .tile-line').innerText(), /já est[aá] ligado/i);
    assert.equal(await app.page.locator('#toolsLayer:not([hidden]) .tool-layer-card').count(), 0);
  } finally { await app.context.close(); }
});

test('sem resposta na primeira leitura: não escreve, exibe falha e permite tentar novamente', { skip }, async () => {
  const app = await connectedWithMissingSnapshot();
  try {
    await app.page.evaluate(() => window.__bench.setFault('noReadReply', true));
    await T.tap(app.page, '[data-adv-switch]');
    await T.pump(app.page, 5);
    assert.deepEqual(T.writesOf((await T.frames(app.page)).log), []);
    // Sem janela: a falha e o que fazer ficam na linha do cartão; o próximo toque já tenta de novo.
    assert.match(await app.page.locator('#toolAdvanced [data-card="anti"] .tile-line').innerText(), /não consegui falar com a central/i);
    assert.equal(await app.page.locator('#toolsLayer:not([hidden]) .tool-layer-card').count(), 0);
    await app.page.evaluate(() => window.__bench.setFault('noReadReply', false));
    await T.tap(app.page, '[data-adv-switch]');           // de novo: um toque resolve, sem botão "tentar de novo" separado
    await T.pump(app.page, 10);
    assert.equal(T.writesOf((await T.frames(app.page)).log).length, 1);
  } finally { await app.context.close(); }
});

test('desconectada: não oferece escrita e não simula conexão', { skip }, async () => {
  const app = await T.openApp(browser, { usb: false, viewport: { width: 1280, height: 720 } });
  try {
    await T.route(app, 'tools');
    assert.equal(await app.page.locator('#toolAdvanced [data-adv-switch]').isDisabled(), true);
    assert.match(await app.page.locator('#toolAdvanced [data-card="anti"]').innerText(), /conecte a central/i);
    assert.deepEqual(T.writesOf((await T.frames(app.page)).log), []);
  } finally { await app.context.close(); }
});


test('RED: digitar 1550 sem snapshot grava após leitura direta, sem mudar os outros quatro números', { skip }, async () => {
  const app = await connectedWithMissingSnapshot();
  try {
    const { page } = app;
    await T.tap(page, '[data-adv-open="valores"]');
    await T.pump(page, 2);
    const input = page.locator('#advField0');
    assert.equal(await input.isDisabled(), false,
      'com USB conectado, editar não pode depender de param_snapshot geral');
    await input.fill('1550');
    await input.dispatchEvent('change');
    await T.pump(page, 20);
    const state = await page.evaluate(() => {
      const bytes = window.__bench.ecu.get('29 33 01 5D');
      return Array.from({length: 5}, (_, i) => bytes[2 * i] | (bytes[2 * i + 1] << 8));
    });
    assert.deepEqual(state, [1550, 100, 1500, 2, 20], 'somente o valor digitado muda na ECU');
    const log = T.writesOf((await T.frames(page)).log);
    assert.equal(log.length, 1, 'uma alteração humana = uma escrita; sem extras');
    assert.equal(log[0].hex, '14 33 01 00 0E 06 5C', 'formato do original: um 14 33 01 <índice> só do campo alterado');
    assert.match(await page.locator('.vv-status').innerText(), /Gravado e conferido/);
  } finally { await app.context.close(); }
});


test('RED: sem snapshot, abrir Valores relê ANTI_STALLO diretamente e libera os botões −/+', { skip }, async () => {
  const app = await connectedWithMissingSnapshot();
  try {
    const { page } = app;
    await T.tap(page, '[data-adv-open="valores"]');
    await T.pump(page, 12);
    assert.equal(await page.locator('#advField0').inputValue(), '1.600',
      'a tela precisa obter o valor real por uma leitura exclusiva de ANTI_STALLO');
    const minus = page.locator('[data-field="0"] [data-adv-step="0:-1"]');
    assert.equal(await minus.isDisabled(), false, 'botão de ajuste depende da leitura direta, não do snapshot geral');
    await T.tap(page, '[data-field="0"] [data-adv-step="0:-1"]');
    await T.pump(page, 14);
    const bytes = await page.evaluate(() => window.__bench.ecu.get('29 33 01 5D'));
    const values = Array.from({ length: 5 }, (_, i) => bytes[2*i] | (bytes[2*i+1] << 8));
    assert.deepEqual(values, [1550, 100, 1500, 2, 20]);
    const writes = T.writesOf((await T.frames(page)).log);
    assert.equal(writes.length, 1);
  } finally { await app.context.close(); }
});
