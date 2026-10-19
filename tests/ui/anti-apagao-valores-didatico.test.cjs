'use strict';
// Regras do dono (2026-10-19): quem nunca mexeu entende, sabe para que lado mexer e CADA TOQUE em − / + grava na hora (sem botão Gravar,
// sem janela de pergunta, sem rolagem). Telas: principal (número + "como funciona"), "explicação completa" e "ajustes extras" (uma aba por vez).
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
  test(`cada número explica o efeito, diz para que lado mexer, tem − / + e a tela NÃO rola @${nome}`, { skip }, async () => {
    const app = await T.openApp(browser, { viewport: vp, usb: true });
    const { page } = app;
    try {
      await U.abrirValores(app);
      for (let i = 0; i < 5; i++) {
        await U.irParaCampo(app, i);
        const box = page.locator(`[data-field="${i}"]`);
        assert.equal(await box.locator('[data-adv-step]').count(), 2, `campo ${i}: botões − e +`);
        const tips = await box.locator('.num-tips').innerText();
        assert.match(tips, /Se precisar de mais/, `campo ${i}: diz quando aumentar`);
        assert.match(tips, /Se atrapalhar/, `campo ${i}: diz quando diminuir`);
        assert.match(await box.locator('.num-meta').innerText(), /Valor de fábrica/, `campo ${i}: mostra o valor de fábrica`);
        const rola = await page.evaluate(() => { const v = document.querySelector('.values-view'); return v.scrollHeight > v.clientHeight + 1 || v.scrollWidth > v.clientWidth + 1; });
        assert.equal(rola, false, `campo ${i}: a tela não pode rolar (nem para baixo nem para o lado)`);
        const bs = await box.locator('[data-adv-step]').first().boundingBox();
        assert.ok(bs.height >= 52 && bs.width >= 52, `botão − grande o bastante: ${bs.width}x${bs.height}`);
      }
      await U.foto(page, `valores-extras-${nome}`);
      await U.irParaCampo(app, 0);
      await U.foto(page, `valores-principal-${nome}`);
      assert.deepEqual(app.problems.page, []);
    } finally { await app.context.close(); }
  });
}

test('liga os pontos: explica QUANDO age, o caminho completo e que NÃO é o corte de injeção (com a evidência do log)', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'], usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    const quando = await page.locator('.vv-how').innerText();
    assert.match(quando, /cai de repente/); assert.match(quando, /abaixo de 1\.600 rpm/); assert.match(quando, /embreagem/);
    await T.tap(page, '[data-adv-open-view="info"]'); await T.pump(page, 2);
    const tudo = await page.locator('.vv-info-full').innerText();
    assert.match(tudo, /Não é o corte de injeção/);
    assert.match(tudo, /Saída do corte/, 'aponta para o cartão do corte');
    assert.match(tudo, /447 rpm/); assert.match(tudo, /680 rpm/);
    await T.tap(page, '[data-adv-back-valores]'); await T.pump(page, 2);
    await U.irParaCampo(app, 1);
    assert.match(await page.locator('[data-field="1"] .num-help').innerText(), /duas voltas/, 'explica a escala do original sem jargão');
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
    assert.deepEqual(await U.anti5(page), [1650, 100, 1500, 2, 20], 'a central tem o novo valor depois de UM toque');
    assert.deepEqual(await U.ecuWrites(page), [U.quadroValores([1650, 100, 1500, 2, 20])], 'um quadro, exato');
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
    await T.tap(page, '[data-field="0"] [data-adv-step="0:1"]');
    await T.tap(page, '[data-field="0"] [data-adv-step="0:1"]');
    await T.tap(page, '[data-field="0"] [data-adv-step="0:1"]');
    await T.pump(page, 40);
    assert.deepEqual(await U.anti5(page), [1750, 100, 1500, 2, 20], 'três toques de +50 = 1750 na central');
    assert.equal(await page.locator('#advField0').inputValue(), '1.750');
  } finally { await app.context.close(); }
});

test('o botão − e o limite: nunca passa da faixa do original e grava para baixo também', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'], usb: true });
  const { page } = app;
  try {
    await U.abrirValores(app);
    await T.tap(page, '[data-field="0"] [data-adv-step="0:-1"]'); await T.pump(page, 10);
    assert.deepEqual(await U.anti5(page), [1550, 100, 1500, 2, 20]);
    await T.tap(page, '[data-adv-undo-values]'); await T.pump(page, 10);
    assert.deepEqual(await U.anti5(page), [1600, 100, 1500, 2, 20], 'Desfazer volta ao anterior, também em um toque');
  } finally { await app.context.close(); }
});
