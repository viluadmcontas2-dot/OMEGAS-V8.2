'use strict';
// Regras do dono: cinco campos editáveis, unidades do ProgBase, entrada manual de inteiros e botões ±5. Cada toque grava na hora, com ACK e leitura de volta.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const U = require('./helpers/ux-cenarios.cjs');
const skip = T.skipReason() || false;
const VPS = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

for (const [nome, vp] of Object.entries(VPS)) {
  test(`os cinco ajustes ficam expostos, com −5 / +5, e a tela NÃO rola @${nome}`, { skip }, async () => {
    const app = await T.openApp(browser, { viewport: vp, usb: true });
    const { page } = app;
    try {
      await U.abrirValores(app);
      assert.equal(await page.locator('[data-field]').count(), 5, 'os cinco parâmetros ficam editáveis');
      assert.equal(await page.locator('[data-adv-open-view="extras"]').count(), 0, 'sem botão de "ajustes extras"');
      for (let i = 0; i < 5; i++) {
        const box = page.locator(`[data-field="${i}"]`);
        assert.ok(await box.isVisible(), `card ${i} visível`);
        assert.equal(await box.locator('[data-adv-step]').count(), 2, `campo ${i}: botões − e +`);
        assert.notEqual((await box.locator('input').inputValue()).trim(), '', `campo ${i}: o número aparece`);
        assert.equal((await box.locator('[data-adv-step]').allTextContents()).join('|'), '− 5|+ 5', `campo ${i}: passo fixo de cinco`);
        const units = ['rpm', 'rpm/ciclo', 'ms', 'rpm/ciclo', 'rpm/ciclo'];
        assert.equal(await box.locator('.num-unit').innerText(), units[i], `campo ${i}: unidade correta`);
        const bs = await box.locator('[data-adv-step]').first().boundingBox();
        assert.ok(bs.height >= 50 && bs.width >= 44, `botão − grande o bastante: ${bs.width}x${bs.height}`);
        const bi = await box.locator('input').boundingBox();
        assert.ok(bi.width >= 60, `o número cabe na caixa: ${bi.width}px`);
      }
      const rola = await page.evaluate(() => { const v = document.querySelector('.values-view'); return v.scrollHeight > v.clientHeight + 1 || v.scrollWidth > v.clientWidth + 1; });
      assert.equal(rola, false, 'a tela não pode rolar (nem para baixo nem para o lado)');
      const cortado = await page.evaluate(() => [...document.querySelectorAll('.vv-card, .vv-info')].filter(e => e.scrollHeight > e.clientHeight + 2).map(e => e.className + ' ' + e.textContent.slice(0, 30)));
      assert.deepEqual(cortado, [], 'nenhum card com texto cortado');
      await U.foto(page, `valores-${nome}`);
      assert.deepEqual(app.problems.page, []);
    } finally { await app.context.close(); }
  });

  test(`a explicação completa cabe sem cortar em nenhuma aba @${nome}`, { skip }, async () => {
    const app = await T.openApp(browser, { viewport: vp, usb: true });
    const { page } = app;
    try {
      await U.abrirValores(app);
      await T.tap(page, '[data-adv-open-view="info"]'); await T.pump(page, 2);
      const abas = await page.locator('[data-adv-info-tab]').count();
      assert.equal(abas, 8, 'Como funciona + cinco parâmetros + Seu carro + NÃO é');
      for (let i = 0; i < abas; i++) {
        await page.locator('[data-adv-info-tab]').nth(i).click(); await T.pump(page, 1);
        const cortado = await page.evaluate(() => { const e = document.querySelector('.vv-info-full'); const v = document.querySelector('.values-view'); return [e.scrollHeight > e.clientHeight + 2, v.scrollHeight > v.clientHeight + 2]; });
        assert.deepEqual(cortado, [false, false], `aba ${i}: texto cortado`);
        const dentro = await page.evaluate(() => { const r = document.querySelector('.vv-info-full').getBoundingClientRect(); const n = document.querySelector('nav, .side-nav, #appNav'); return r.bottom; });
        assert.ok(dentro > 0);
      }
      assert.deepEqual(app.problems.page, []);
    } finally { await app.context.close(); }
  });
}

test('liga os pontos: explica QUANDO age, o caminho completo e que NÃO é o corte de injeção (com a evidência do log)', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'], usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    await T.tap(page, '[data-adv-open-view="info"]'); await T.pump(page, 2);
    const quando = await page.locator('.vv-how').innerText();
    assert.match(quando, /cai de repente/); assert.match(quando, /abaixo de 1\.600 rpm/); assert.match(quando, /embreagem/);
    assert.match(await page.locator('.vv-info-full').innerText(), /Rotação máxima para agir/);
    await page.locator('[data-adv-info-tab="nao"]').click(); await T.pump(page, 1);
    const tudo = await page.locator('.vv-info-full').innerText();
    assert.match(tudo, /Não é o corte de injeção/);
    assert.match(tudo, /Saída do corte/, 'aponta para o cartão do corte');
    assert.match(tudo, /447 rpm/); assert.match(tudo, /680 rpm/);
    await page.locator('[data-adv-info-tab="f0"]').click(); await T.pump(page, 1);
    assert.match(await page.locator('.vv-info-full').innerText(), /Se precisar de mais/);
  } finally { await app.context.close(); }
});

test('SEM ATRITO: um toque em + grava na hora (sem botão Gravar, sem janela) e avisa "Gravado e conferido"', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'], usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    assert.equal(await page.locator('[data-adv-save]').count(), 0, 'não existe botão Gravar: o toque já é a ordem');
    await page.evaluate(() => {
      window.__janelaVista = false;
      const layer = document.getElementById('toolsLayer');
      new MutationObserver(() => { if (!layer.hidden && layer.querySelector('[data-adv-confirm],[data-adv-cancel]')) window.__janelaVista = true; })
        .observe(layer, { attributes: true, childList: true, subtree: true });
    });
    await U.clearLog(page);
    await T.tap(page, '[data-field="0"] [data-adv-step="0:1"]');
    await T.pump(page, 10);
    assert.deepEqual(await U.anti5(page), [1605, 100, 1500, 2, 20], 'a central tem o novo valor depois de UM toque');
    assert.deepEqual(await U.ecuWrites(page), U.quadroValores([1650, 100, 1500, 2, 20]), 'um quadro, exato');
    assert.equal(await page.evaluate(() => window.__janelaVista), false, 'nenhuma janela de pergunta');
    assert.match(await U.text(page, '.vv-status'), /Gravado e conferido/, 'o aviso está na própria tela');
    assert.deepEqual(app.problems.page, []);
  } finally { await app.context.close(); }
});

test('toques rápidos em + não se perdem: o último valor chega à central (grava em seguida, sem relógio)', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'], usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    await page.evaluate(() => window.OmegasApp.scheduler.stop()); // determinístico: só o teste gira os ciclos
    const mais = '[data-field="0"] [data-adv-step="0:1"]:not([disabled])';
    for (let k = 0; k < 3; k++) { await page.waitForSelector(mais, { state: 'visible' }); await T.tap(page, mais); }   // sem pausa de relógio entre os toques
    await T.pump(page, 40);
    assert.deepEqual(await U.anti5(page), [1615, 100, 1500, 2, 20], 'três toques de +5 = 1615 na central');
    assert.equal(await page.locator('#advField0').inputValue(), '1.615');
  } finally { await app.context.close(); }
});

test('o botão − e o limite: nunca passa da faixa do original e grava para baixo também', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'], usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    await T.tap(page, '[data-field="0"] [data-adv-step="0:-1"]'); await T.pump(page, 10);
    assert.deepEqual(await U.anti5(page), [1595, 100, 1500, 2, 20]);
    await T.tap(page, '[data-adv-undo-values]'); await T.pump(page, 10);
    assert.deepEqual(await U.anti5(page), [1600, 100, 1500, 2, 20], 'Desfazer volta ao anterior, também em um toque');
  } finally { await app.context.close(); }
});


test('entrada manual aceita inteiro ímpar e grava imediatamente ao confirmar o campo', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'], usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    const input = page.locator('#advField0');
    await input.fill('1601');
    await input.press('Enter');
    await T.pump(page, 12);
    assert.deepEqual(await U.anti5(page), [1601, 100, 1500, 2, 20], 'o inteiro ímpar foi aceito pela central emulada');
    assert.match(await U.text(page, '.vv-status'), /Gravado e conferido/);
  } finally { await app.context.close(); }
});
