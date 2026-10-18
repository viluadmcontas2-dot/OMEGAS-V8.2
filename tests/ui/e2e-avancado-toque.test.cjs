'use strict';
// E2E de TOQUE de verdade (Chromium + touchscreen) em Ferramentas > Avançado, contra a ECU emulada; SEM Chromium o arquivo é pulado
// (a lógica equivalente roda sempre em e2e-avancado-logica.test.cjs). Viewports: 360x740, 412x915 e o canônico 1280x720.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const C = require('./helpers/avancado-cenarios.cjs');

const skip = T.skipReason();
const VPS = { '360x740': T.VIEWPORTS.pequeno, '412x915': T.VIEWPORTS.grande, '1280x720': { width: 1280, height: 720 } };
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

for (const [nome, vp] of Object.entries(VPS)) {
  test(`Avançado por toque @${nome}: leitura, prévia, gravação, desfazer, descartes, 500 ciclos`, { skip: skip || false }, async () => {
    const app = await T.openApp(browser, { viewport: vp });
    const shot = n => T.shot(app.page, `avancado-${nome}-${n}`);
    try {
      await C.cenarioLeitura(app, shot);
      await C.cenarioGravar(app, shot);
      await C.cenarioDesfazer(app, shot);
      await C.cenarioDescartes(app, shot);
      await C.cenario500(app);
      assert.deepEqual(app.problems.console, [], 'erros de console');
      assert.deepEqual(app.problems.page, [], 'erros de página');
    } finally { await app.context.close(); }
  });

  test(`Avançado: falhas honestas por toque @${nome} (NAK, silêncio, ACK sem aplicar, readback diferente)`, { skip: skip || false }, async () => {
    const app = await T.openApp(browser, { viewport: vp, usb: true });
    try {
      await C.abrirAvancado(app);
      const casos = [['nak', /não confirmou/], ['noReply', /não confirmou/], ['ignoreWrite', /leitura de volta é diferente/], ['corruptWrite', /leitura de volta é diferente/]];
      for (const [falha, re] of casos) {
        await app.page.evaluate(() => window.__bench.seed('29 FA 00 23', [0, 0, 0, 0]));
        await app.page.evaluate(() => window.__bench.plugUsb && 0);
        await C.cenarioFalha(app, falha, re, nome === '412x915' ? n => T.shot(app.page, `avancado-${nome}-${n}`) : null);
        assert.equal(await C.text(app.page, C.S.state), falha === 'corruptWrite' ? 'Ligado' : 'Desligado', 'o estado mostrado é o REAL da ECU: ' + falha);
        // limpa o estado para o próximo caso
        await app.page.evaluate(() => { window.__bench.seed('29 FA 00 23', [0, 0, 0, 0]); window.__bench.params.FLAG_CONF3 = { state: 'OK', values: [0, 0] }; });
        await T.pump(app.page, 4);
      }
    } finally { await app.context.close(); }
  });
}

// ---------------------------------------------------------------- cenário 4: toque duplo rápido, desabilitado, rotação
test('toque duplo RÁPIDO: do repouso só prepara (0 escritas); na prévia, rajada de toques grava UMA vez', { skip: skip || false }, async () => {
  const app = await T.openApp(browser, { usb: true });
  const { page } = app;
  try {
    await C.abrirAvancado(app);
    await C.clearLog(page);
    await T.doubleTap(page, C.S.confirm);          // dois toques sem ciclo de dados entre eles
    await T.pump(page, 4);
    assert.deepEqual(await C.ecuWrites(page), [], 'o 2º toque caiu em "preparando": nada escrito');
    assert.equal(await C.count(page, C.S.preview), 1, 'ficou na prévia');
    await T.shot(page, 'avancado-360x740-9-duplo-rapido-so-previa');
    await T.tap(page, C.S.confirm); await page.touchscreen.tap(...(await centro(page, C.S.confirm)));
    await page.touchscreen.tap(...(await centro(page, C.S.confirm)));
    await T.pump(page, 6);
    assert.deepEqual(await C.ecuWrites(page), [C.LIGAR], 'rajada de 3 toques no 2º passo = exatamente 1 quadro');
    assert.deepEqual(app.problems.page, []);
  } finally { await app.context.close(); }
});
async function centro(page, sel) { const b = await page.locator(sel).first().boundingBox(); return [b.x + b.width / 2, b.y + b.height / 2]; }

test('toque em botão DESABILITADO (sem ECU e sem leitura): nada muda, nada sai, sem erro', { skip: skip || false }, async () => {
  const app = await T.openApp(browser);
  const { page } = app;
  try {
    await C.abrirAvancado(app);
    assert.equal(await page.locator(C.S.confirm).isDisabled(), true);
    assert.equal(await page.locator(C.S.undo).isDisabled(), true);
    await C.clearLog(page);
    for (const sel of [C.S.confirm, C.S.undo]) await T.tap(page, sel, { allowDisabled: true });
    await T.pump(page, 4);
    assert.deepEqual((await T.frames(page)).log, [], 'nenhum quadro, de nenhum tipo');
    assert.equal(await C.text(page, C.S.state), '—');
    assert.equal(await C.count(page, C.S.preview), 0);
    assert.match(await C.text(page, '.adv-hint'), /Conecte a ECU|Esperando a leitura/);
    await T.shot(page, 'avancado-360x740-10-desabilitado');
    assert.deepEqual(app.problems.page.concat(app.problems.console), []);
  } finally { await app.context.close(); }
});

test('rotação de tela (troca de viewport) no meio do fluxo: a prévia sobrevive, nada é gravado sozinho, o 2º toque grava uma vez', { skip: skip || false }, async () => {
  const app = await T.openApp(browser, { usb: true });
  const { page } = app;
  try {
    await C.abrirAvancado(app);
    await C.clearLog(page);
    await T.tap(page, C.S.confirm); await T.pump(page, 4);
    assert.equal(await C.count(page, C.S.preview), 1);
    await page.setViewportSize({ width: 740, height: 360 });          // gira
    await T.pump(page, 8, { data: true });
    assert.equal(await C.count(page, C.S.preview), 1, 'a prévia continua após girar');
    assert.deepEqual(await C.ecuWrites(page), [], 'girar não grava');
    await T.shot(page, 'avancado-rotacao-740x360-previa');
    await page.setViewportSize({ width: 360, height: 740 });          // volta
    await T.pump(page, 4);
    await T.tap(page, C.S.confirm); await T.pump(page, 4);
    assert.deepEqual(await C.ecuWrites(page), [C.LIGAR]);
    assert.deepEqual(app.problems.page.concat(app.problems.console), []);
  } finally { await app.context.close(); }
});

test('rotação durante a gravação (resposta ainda não chegou): conclui uma vez só e confirma', { skip: skip || false }, async () => {
  const app = await T.openApp(browser, { usb: true, bench: { hold: true } });
  const { page } = app;
  try {
    await C.abrirAvancado(app);
    await C.clearLog(page);
    await T.tap(page, C.S.confirm); await T.pump(page, 4);
    await T.tap(page, C.S.confirm); await T.pump(page, 2);
    assert.match(await C.text(page, C.S.confirm), /Gravando e conferindo/);
    assert.equal(await page.locator(C.S.confirm).isDisabled(), true, 'botão desabilitado enquanto grava');
    await page.setViewportSize({ width: 740, height: 360 });
    await T.pump(page, 4);
    await page.evaluate(() => window.__bench.releaseWrite());          // a resposta da ECU chega agora
    await T.pump(page, 6);
    await page.waitForSelector('.adv-result[data-level="ok"]');
    assert.deepEqual(await C.ecuWrites(page), [C.LIGAR]);
    assert.equal(await C.text(page, C.S.state), 'Ligado');
  } finally { await app.context.close(); }
});

// ---------------------------------------------------------------- cenário 5: alvos de toque >= 44 px
for (const [nome, vp] of Object.entries(VPS)) {
  test(`alvos de toque >= 44px nos botões do Avançado @${nome} (principal, preview/desfazer e abas de detalhes)`, { skip: skip || false }, async () => {
    const app = await T.openApp(browser, { viewport: vp, usb: true });
    const { page } = app;
    const medir = () => page.evaluate(() => [...document.querySelectorAll('#toolAdvancedToggle, #toolAdvanced button')]
      .filter(b => { const r = b.getBoundingClientRect(); const cs = getComputedStyle(b); return r.width > 0 && r.height > 0 && cs.display !== 'none' && cs.visibility !== 'hidden'; })
      .map(b => { const r = b.getBoundingClientRect(); return { t: b.textContent.trim().replace(/\s+/g, ' ').slice(0, 28), w: Math.round(r.width), h: Math.round(r.height) }; }));
    try {
      await C.abrirAvancado(app);
      const lotes = [await medir()];
      await T.tap(page, C.S.confirm); await T.pump(page, 4); lotes.push(await medir());            // com Cancelar
      await T.tap(page, C.S.cancel); await T.pump(page, 2);
      await T.tap(page, '[data-adv-details]'); await T.pump(page, 2); lotes.push(await medir());     // abas
      for (const tab of ['FLAG_CONF1', 'FLAG_CONF2', 'PARAMS']) { await T.tap(page, `[data-adv-tab="${tab}"]`); await T.pump(page, 1); lotes.push(await medir()); }
      const pequenos = lotes.flat().filter(b => b.w < 44 || b.h < 44);
      assert.deepEqual(pequenos, [], 'botões menores que 44x44 CSS px');
      assert.ok(lotes.flat().length >= 15);
    } finally { await app.context.close(); }
  });
}

// ---------------------------------------------------------------- mutação no DOM real: sem o toque duplo o cenário (a) falha
test('mutação no app carregado: confirmação automática (sem toque duplo) faz a prova "nenhuma escrita após o 1º toque" FALHAR', { skip: skip || false }, async () => {
  const dir = T.mutatedUi([['core/advanced-flags.js', "s.plan = plan;\n      s.token = status.token;\n      set('preview', '');", "s.plan = plan;\n      s.token = status.token;\n      set('preview', '');\n      confirm();"]]);
  const app = await T.openApp(browser, { ui: dir, usb: true });
  try {
    await C.abrirAvancado(app);
    await assert.rejects(() => C.cenarioGravar(app, null), assert.AssertionError);
    assert.deepEqual(await C.ecuWrites(app.page), [C.LIGAR], 'o mutante realmente gravou sozinho');
  } finally { await app.context.close(); require('node:fs').rmSync(dir, { recursive: true, force: true }); }
});
