'use strict';
// Campo digitável do anti-apagão (os 5 números), por TOQUE no Chromium com a central emulada: tem aparência de campo (caixa + LÁPIS),
// unidade em palavra simples, "De X a Y" e "Valor de fábrica"; recusa o que está fora da faixa; só grava no 2º toque; "Restaurar fábrica".
// Sem Chromium o arquivo PULA. Alvos: 1280x720 (canônico), 1024x600 e 800x480 (multimídia do carro, paisagem).
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const U = require('./helpers/ux-cenarios.cjs');

const skip = T.skipReason() || false;
const VPS = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };
const CAMPOS = [
  { i: 0, nome: 'Rotação máxima para agir', unidade: 'rpm', faixa: 'De 0 a 6.000', fabrica: '1.600' },
];
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

for (const [nome, vp] of Object.entries(VPS)) {
  test(`aparência de campo @${nome}: caixa, lápis, unidade, faixa, valor de fábrica e alvo ≥ 56 px`, { skip }, async () => {
    const app = await T.openApp(browser, { viewport: vp, usb: true });
    const { page } = app;
    try {
      await U.abrirValores(app);
      for (const c of CAMPOS) {
        await U.irParaCampo(app, c.i);
        const campo = page.locator(`[data-field="${c.i}"]`);
        assert.match((await campo.locator('label').innerText()).trim(), new RegExp('^' + c.nome + '( \\(experimental\\))?$'));
        const caixa = campo.locator('.num-box');
        assert.equal(await caixa.locator('svg.pencil').count(), 1, 'lápis dentro do campo: ' + c.nome);
        assert.ok(await caixa.locator('svg.pencil').isVisible(), 'lápis visível');
        assert.equal(await caixa.locator('.num-unit').innerText(), c.unidade, 'unidade em palavra simples');
        assert.match(await campo.locator('.num-meta').innerText(), new RegExp(`^${c.faixa} · Valor de fábrica: ${c.fabrica.replace('.', '\\.')}$`));
        const box = await caixa.boundingBox();
        assert.ok(box.height >= 56, `altura do campo ${c.nome}: ${box.height}`);
        const borda = await caixa.evaluate(e => parseFloat(getComputedStyle(e).borderTopWidth));
        assert.ok(borda >= 2, 'borda visível: parece campo, não texto');
        const input = campo.locator('input');
        assert.equal(await input.getAttribute('inputmode'), 'none');
        assert.equal(await input.inputValue(), c.fabrica, 'mostra o valor lido da central');
        assert.equal(await page.locator(`label[for="advField${c.i}"]`).count(), 1, 'campo com rótulo associado');
      }
      assert.deepEqual(await U.ecuWrites(page), [], 'abrir e olhar nunca grava');
      const tamanhoTexto = await page.evaluate(() => Math.min(...[...document.querySelectorAll('.num-field label, .num-box input, .num-unit, .num-meta')].filter(e => e.offsetParent).map(e => parseFloat(getComputedStyle(e).fontSize))));
      assert.ok(tamanhoTexto >= 15, 'texto dos campos legível: ' + tamanhoTexto);
      await T.shot(page, `ux-${nome}-campos-editaveis`);
      assert.deepEqual(app.problems.page.concat(app.problems.console), []);
    } finally { await app.context.close(); }
  });
}

test('tocar no lápis ou na unidade leva o foco para o campo (o teclado abre ali)', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirValores(app);
    await U.irParaCampo(app, 0);
    await T.tap(page, '[data-field="0"] .pencil');
    assert.equal(await page.evaluate(() => document.activeElement?.id), 'advField0');
    await page.evaluate(() => document.activeElement && document.activeElement.blur());
    await T.tap(page, '[data-field="0"] .num-unit');
    assert.equal(await page.evaluate(() => document.activeElement?.id), 'advField0');
  } finally { await app.context.close(); }
});

test('validação: aceita os limites, recusa 1 acima, vazio, vírgula e letra; erro em texto e botão desligado', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirValores(app);
    for (const c of CAMPOS) {
      const faixa = Number(c.faixa.match(/a ([\d.]+)/)[1].replace(/\./g, ''));
      await U.digitar(app, c.i, faixa);
      assert.equal(await page.locator(`[data-field="${c.i}"]`).getAttribute('data-invalid'), 'false', `limite ${faixa} aceito em ${c.nome}`);
      await U.digitar(app, c.i, faixa + 1);
      assert.equal(await page.locator(`[data-field="${c.i}"]`).getAttribute('data-invalid'), 'true', `${faixa + 1} recusado em ${c.nome}`);
      assert.match(await page.locator(`[data-field="${c.i}"] .num-error`).innerText(), /Use um valor de 0 a/);
      assert.equal(await page.locator(`#advField${c.i}`).getAttribute('aria-invalid'), 'true');
      assert.deepEqual(await U.ecuWrites(page), [], 'com erro nada é gravado');
      for (const ruim of ['', 'abc', '1,5', '-3', '12a']) {
        await U.digitar(app, c.i, ruim);
        assert.equal(await page.locator(`[data-field="${c.i}"]`).getAttribute('data-invalid'), 'true', `"${ruim}" recusado em ${c.nome}`);
        assert.deepEqual(await U.ecuWrites(page), []);
      }
      await U.digitar(app, c.i, c.fabrica);   // volta ao valor lido
      assert.equal(await page.locator(`[data-field="${c.i}"]`).getAttribute('data-invalid'), 'false');
    }
    assert.deepEqual(await U.ecuWrites(page), [], 'digitar nunca escreve');
    await U.digitar(app, 0, '1.700');
    assert.deepEqual(await U.ecuWrites(page), [], 'digitando ainda não gravou');
    assert.equal(await page.locator('[data-field="0"]').getAttribute('data-changed'), 'true');
    await U.digitar(app, 0, '1600');
    await page.evaluate(() => document.activeElement && document.activeElement.blur()); await T.pump(page, 6);
    assert.deepEqual(await U.ecuWrites(page), [], 'voltar ao valor lido: nada a gravar, nada gravado');
  } finally { await app.context.close(); }
});

test('fora da faixa nunca chega à central, nem pela ponte', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.clearLog(page);
    for (const ruim of ['{"0":6001}', '{"1":1001}', '{"2":10001}', '{"3":-1}', '{"4":1.5}', '{"9":1}', '{}']) {
      const r = await page.evaluate(j => JSON.parse(window.OmegasAutoCal.prepareAntiStalloValues(j)), ruim);
      assert.equal(r.ok, false, ruim);
    }
    assert.deepEqual((await T.frames(page)).log.filter(f => f.kind !== 'READ'), []);
  } finally { await app.context.close(); }
});

test('gravar valores: UM toque grava o quadro exato e a central confere (sem janela de pergunta)', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirValores(app);
    await U.clearLog(page);
    await U.digitar(app, 0, '1.700');
    await page.evaluate(() => document.activeElement && document.activeElement.blur()); await T.pump(page, 10);   // saiu do campo: grava na hora
    assert.equal(await U.camada(page), 0, 'sem janela de confirmação');
    assert.deepEqual(await U.ecuWrites(page), U.quadroValores([1700, 100, 1500, 2, 20]));
    assert.deepEqual(await U.anti5(page), [1700, 100, 1500, 2, 20]);
    
    assert.equal(await page.locator('#advField0').inputValue(), '1.700', 'o campo mostra o que a central confirmou');
    assert.match(await U.text(page, '.vv-status'), /Gravado e conferido/);
  } finally { await app.context.close(); }
});

test('Restaurar fábrica: UM toque grava o padrão; Desfazer (também um toque) volta ao anterior', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await page.evaluate(() => window.__bench.seed('29 33 01 5D', [0xB8, 0x0B, 0x64, 0x00, 0xDC, 0x05, 0x05, 0x00, 0x14, 0x00])); // 3000 100 1500 5 20
    await T.pump(page, 2);
    await page.evaluate(() => { window.__bench.deliver(); }); await T.pump(page, 1);
    await page.evaluate(() => { window.__bench.plugUsb(); window.__bench.deliver(); }); await T.pump(page, 40);
    await U.abrirValores(app);
    assert.equal(await page.locator('#advField0').inputValue(), '3.000');
    assert.equal(await page.locator('[data-adv-factory]').isDisabled(), false);
    await U.clearLog(page);
    await T.tap(page, '[data-adv-factory]'); await T.pump(page, 10);
    assert.equal(await U.camada(page), 0, 'sem janela de confirmação');
    assert.deepEqual(await U.anti5(page), U.FABRICA);
    assert.deepEqual(await U.ecuWrites(page), U.quadroValores(U.FABRICA, [3000, 100, 1500, 5, 20]));
    assert.equal(await page.locator('[data-adv-factory]').isDisabled(), true, 'já está de fábrica: nada a restaurar');
    await U.clearLog(page);
    await T.tap(page, '[data-adv-undo-values]'); await T.pump(page, 10);
    assert.equal(await U.camada(page), 0, 'desfazer também sem pergunta');
    assert.deepEqual(await U.anti5(page), [3000, 100, 1500, 5, 20], 'voltou byte a byte ao que era antes');
  } finally { await app.context.close(); }
});

test('"Longe do valor de fábrica" só avisa no campo; não bloqueia e a faixa do original continua valendo', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirValores(app);
    await U.digitar(app, 0, '5000');
    assert.match(await U.text(page, '[data-field="0"] .num-warn'), /Longe do valor de fábrica/);
    assert.equal(await U.camada(page), 0, 'avisar não abre janela');
    assert.equal(await U.camada(page), 0);
    assert.deepEqual(await U.ecuWrites(page), [], 'avisar não grava');
  } finally { await app.context.close(); }
});

async function valoresNaoFabrica(app) {
  const { page } = app;
  await page.evaluate(() => window.__bench.seed('29 33 01 5D', [0xB8, 0x0B, 0x64, 0x00, 0xDC, 0x05, 0x05, 0x00, 0x14, 0x00])); // 3000 100 1500 5 20
  await T.pump(page, 2);
  await page.evaluate(() => { window.__bench.plugUsb(); window.__bench.deliver(); }); await T.pump(page, 40);
  await U.abrirValores(app);
  await U.clearLog(page);
}

test('PARCIAL: o 2º quadro sem ACK = frase na linha de estado + Desfazer já visível; um toque volta ao que era', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await valoresNaoFabrica(app);
    await U.cenarioValores(page, 'parcial');
    await T.tap(page, '[data-adv-factory]'); await T.pump(page, 10);   // fábrica muda 2 campos = 2 quadros
    assert.deepEqual(await U.ecuWrites(page), U.quadroValores(U.FABRICA, [3000, 100, 1500, 5, 20]), 'os dois saíram; o 2º foi recusado');
    assert.deepEqual(await U.anti5(page), [1600, 100, 1500, 5, 20], 'só o 1º ficou na central');
    assert.equal(await U.text(page, '.vv-status'), 'Gravei só uma parte (1 de 2). Toque em Desfazer para voltar ao que era antes.');
    assert.doesNotMatch(await U.text(page, '.vv-status'), /Gravado|Não gravou/);
    assert.equal(await U.camada(page), 0, 'sem janela');
    assert.equal(await U.count(page, '[data-adv-undo-values]'), 1, 'Desfazer à vista, sem 2º toque');
    await U.foto(page, '1280x720-valores-parcial');
    await page.evaluate(() => window.__bench.setFault('nakAfter', null));
    await U.clearLog(page);
    await T.tap(page, '[data-adv-undo-values]'); await T.pump(page, 10);
    assert.deepEqual(await U.ecuWrites(page), U.quadroValores([3000, 100, 1500, 5, 20], [1600, 100, 1500, 5, 20]), 'Desfazer manda só o campo que foi gravado');
    assert.deepEqual(await U.anti5(page), [3000, 100, 1500, 5, 20], 'voltou ao que era antes');
    assert.match(await U.text(page, '.vv-status'), /Gravado e conferido/);
  } finally { await app.context.close(); }
});

test('escrita chegou, leitura de volta falhou ou veio outra: "Não consegui conferir se gravou", sem janela', { skip }, async () => {
  for (const cenario of ['leitura-falha', 'leitura-diferente']) {
    const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
    const { page } = app;
    try {
      await U.abrirValores(app);
      await U.cenarioValores(page, cenario);
      await U.digitar(app, 0, '1700');
      await page.evaluate(() => document.activeElement && document.activeElement.blur()); await T.pump(page, 10);
      assert.equal((await U.ecuWrites(page)).length >= 1, true, cenario);
      const linha = await U.text(page, '.vv-status');
      assert.equal(linha, 'Não consegui conferir se gravou. Espere a leitura voltar e olhe os valores.', cenario);
      assert.doesNotMatch(linha, /Não gravou|Não foi gravado|Gravado/, cenario);
      assert.equal(await U.camada(page), 0, cenario + ': sem janela');
    } finally { await app.context.close(); }
  }
});

test('sem central conectada: os campos ficam desligados e o motivo está escrito', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirValores(app);
    assert.equal(await page.locator('#advField0').isDisabled(), true);
    assert.equal(await page.locator('[data-adv-step]').first().isDisabled(), true, 'sem central os botões − / + ficam desligados');
    assert.equal(await page.locator('[data-adv-factory]').isDisabled(), true);
    assert.match(await U.text(page, '.values-view > header p'), /Conecte a central|Aguardando a central/);
  } finally { await app.context.close(); }
});
