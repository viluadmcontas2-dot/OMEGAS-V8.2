'use strict';
// Cenários de TOQUE do Ferramentas > Avançado (Chromium). Reutilizados pelo teste íntegro e pelo mutante.
const assert = require('node:assert/strict');
const T = require('./browser.cjs');

const LIGAR = '35 FA 00 20 00 00 00 4F';
const DESLIGAR = '35 FA 00 00 00 00 00 2F';
const S = { toggle: '#toolAdvancedToggle', state: '#toolAdvanced .adv-main h3', confirm: '[data-adv-confirm]', undo: '[data-adv-undo]',
  cancel: '[data-adv-cancel]', preview: '[data-testid="adv-preview"]', result: '#toolAdvanced .adv-result' };

const text = (page, sel) => page.locator(sel).first().innerText().then(t => t.replace(/\s+/g, ' ').trim());
const count = (page, sel) => page.locator(sel).count();
const ecuWrites = page => T.frames(page).then(f => T.writesOf(f.log).map(x => x.hex));
const flag3 = page => page.evaluate(() => window.__bench.ecu.get('29 FA 00 23'));
const clearLog = page => page.evaluate(() => window.__bench.ecu.clearLog());

async function abrirAvancado(app) {
  await T.route(app, 'tools');
  if (await text(app.page, S.toggle) === 'Avançado') await T.tap(app.page, S.toggle);
  await T.pump(app.page, 2);
}

/** Estado "—" antes da leitura e "Desligado" quando o dado chega. */
async function cenarioLeitura(app, shot) {
  const { page } = app;
  await abrirAvancado(app);
  assert.equal(await text(page, S.state), '—', 'antes de qualquer dado');
  assert.equal(await page.locator(S.confirm).isDisabled(), true, 'botão desabilitado sem leitura');
  if (shot) await shot('1-antes-da-leitura');
  await page.evaluate(() => window.__bench.plugUsb());
  await T.pump(app.page, 12);
  assert.equal(await text(page, S.state), '—', 'USB ligado, mas ainda sem dado válido');
  await page.evaluate(() => window.__bench.deliver());   // chegou o 1º quadro válido da sessão
  await T.pump(app.page, 12);
  assert.equal(await text(page, S.state), 'Desligado');
  assert.equal(await page.locator(S.confirm).isDisabled(), false);
  if (shot) await shot('2-desligado');
}

/** 1º toque = prévia; 2º toque = grava; a ECU muda de verdade. Devolve nada; lança se algo divergir. */
async function cenarioGravar(app, shot) {
  const { page } = app;
  await clearLog(page);
  await T.tap(page, S.confirm);
  await T.pump(page, 4);
  assert.equal(await count(page, S.preview), 1, 'prévia visível após o 1º toque');
  const prev = await text(page, S.preview);
  assert.match(prev, /Desligado\s*→\s*Ligado/);
  assert.match(prev, /Antes 0000 0000 → depois 0020 0000/);
  assert.deepEqual(await ecuWrites(page), [], '(a) nenhum quadro de escrita após o 1º toque');
  assert.deepEqual(await flag3(page), [0, 0, 0, 0]);
  assert.match(await text(page, S.confirm), /Toque de novo para gravar/);
  if (shot) await shot('3-previa');
  await T.tap(page, S.confirm);
  await T.pump(page, 4);
  await page.waitForSelector('.adv-result[data-level="ok"]', { timeout: 3000 });
  assert.match(await text(page, S.result), /ECU confirmou: ligado/);
  assert.equal(await text(page, S.state), 'Ligado');
  assert.deepEqual(await ecuWrites(page), [LIGAR], '(b) quadro exato');
  assert.deepEqual(await flag3(page), [0x20, 0, 0, 0], 'a ECU mudou de verdade e a leitura de volta bate');
  if (shot) await shot('4-ligado-confirmado');
}

async function cenarioDesfazer(app, shot) {
  const { page } = app;
  await clearLog(page);
  assert.equal(await page.locator(S.undo).isDisabled(), false);
  await T.tap(page, S.undo); await T.pump(page, 4);
  assert.equal(await count(page, S.preview), 1);
  assert.deepEqual(await ecuWrites(page), [], 'o 1º toque do Desfazer não escreve');
  assert.match(await text(page, S.undo), /Toque de novo para desfazer/);
  if (shot) await shot('5-desfazer-previa');
  await T.tap(page, S.undo); await T.pump(page, 4);
  await page.waitForSelector('.adv-result[data-level="ok"]', { timeout: 3000 });
  assert.deepEqual(await ecuWrites(page), [DESLIGAR]);
  assert.deepEqual(await flag3(page), [0, 0, 0, 0], 'a ECU voltou ao original');
  assert.equal(await text(page, S.state), 'Desligado');
  assert.match(await text(page, S.result), /ECU confirmou: desligado/);
}

/** Prévia armada e depois descartada por cada caminho; nada pode sair para a ECU. */
async function cenarioDescartes(app, shot) {
  const { page } = app;
  await clearLog(page);
  const armar = async () => { await T.tap(page, S.confirm); await T.pump(page, 4); assert.equal(await count(page, S.preview), 1, 'prévia armada'); };
  // cancelar
  await armar();
  if (shot) await shot('6-previa-antes-de-cancelar');
  await T.tap(page, S.cancel); await T.pump(page, 3);
  assert.equal(await count(page, S.preview), 0, 'cancelar descarta');
  // outro toque qualquer (em "Ver detalhes" e volta)
  await armar();
  await T.tap(page, '[data-adv-details]'); await T.pump(page, 2);
  assert.equal(await count(page, S.preview), 0, 'outro toque descarta');
  await T.tap(page, '[data-adv-back]'); await T.pump(page, 2);
  // trocar de aba
  await armar();
  await T.route(app, 'map');
  await T.route(app, 'tools'); await T.pump(page, 3);
  assert.equal(await count(page, S.preview), 0, 'trocar de aba descarta');
  // USB caído
  await armar();
  await page.evaluate(() => window.__bench.unplugUsb());
  await T.pump(page, 12);
  assert.equal(await count(page, S.preview), 0, 'USB caído descarta');
  assert.deepEqual(await ecuWrites(page), [], 'nenhum quadro de escrita em nenhum descarte');
  if (shot) await shot('7-usb-caido-sem-previa');
  await page.evaluate(() => { window.__bench.plugUsb(); window.__bench.deliver(); });
  await T.pump(page, 12);
}

async function cenario500(app) {
  const { page } = app;
  await clearLog(page);
  await page.evaluate(async () => {
    for (let i = 0; i < 500; i += 1) { window.__bench.deliver(); window.OmegasApp.scheduler.run(); }
  });
  await T.pump(page, 2);
  const f = await T.frames(page);
  assert.deepEqual(T.writesOf(f.log), [], '(c) nenhum quadro de escrita/ação em 500 ciclos de dados');
  assert.ok(f.log.filter(x => x.hex === '48 01 49').length >= 500);
}

async function cenarioFalha(app, falha, regex, shot) {
  const { page } = app;
  await page.evaluate(f => window.__bench.setFault(f), falha);
  await T.tap(page, S.confirm); await T.pump(page, 4);
  await T.tap(page, S.confirm); await T.pump(page, 4);
  await page.waitForSelector('.adv-result[data-level="warning"]', { timeout: 3000 });
  const msg = await text(page, S.result);
  assert.match(msg, regex, falha);
  assert.doesNotMatch(msg, /Gravado e conferido/);
  if (shot) await shot('8-falha-' + falha);
  await page.evaluate(() => { for (const f of ['nak', 'noReply', 'ignoreWrite', 'corruptWrite']) window.__bench.setFault(f, false); });
}

module.exports = { S, LIGAR, DESLIGAR, abrirAvancado, cenarioLeitura, cenarioGravar, cenarioDesfazer, cenarioDescartes, cenario500, cenarioFalha, text, count, ecuWrites, flag3, clearLog };
