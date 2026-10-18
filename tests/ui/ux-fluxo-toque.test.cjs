'use strict';
// Fluxo completo por TOQUE (Chromium, touch emulado) das telas novas: navegação com a aba Sessões, Ferramentas (cartões por assunto, sem
// abas), anti-apagão (interruptor + 5 números com lápis), Desfazer e Sessões. Central emulada; registra todo quadro que sai.
// Gera docs/evidence/img/ux-<tamanho>-N-*.png (1280x720 canônico, 1024x600 e 800x480 em paisagem). Sem Chromium PULA.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const U = require('./helpers/ux-cenarios.cjs');

const skip = T.skipReason() || false;
const VPS = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };
const TITULOS_P1 = ['Anti-apagão', 'Valores do anti-apagão', 'Saída do corte', 'Marcha lenta', 'Troca de gasolina para gás', 'Injetores', 'Volta para gasolina'];
const TITULOS_P2 = ['Cópia de segurança', 'Aprendizado', 'Aprendizado de outro arquivo', 'Refino automático', 'Teste de gravação', 'Bateria', 'Balão sobre outros apps', 'Registro do app'];
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

for (const [nome, vp] of Object.entries(VPS)) {
  test(`fluxo completo por toque @${nome}`, { skip }, async () => {
    const app = await T.openApp(browser, { viewport: vp });
    const { page } = app;
    const foto = n => U.foto(page, `${nome}-${n}`);
    try {
      // ---- navegação: 6 abas, Sessões é aba própria
      const rotulos = await page.locator('.side-nav button').allInnerTexts();
      assert.deepEqual(rotulos.map(t => t.trim()), ['AutoCal', 'Refino', 'Ajuste local', 'Ajuste global', 'Sessões', 'Ferramentas']);
      for (const b of await page.locator('.side-nav button').all()) assert.ok((await b.boundingBox()).height >= 56, 'alvo da navegação ≥ 56 px');

      // ---- Ferramentas, sem central: tudo desligado, motivo escrito
      await U.abrirMotor(app);
      assert.deepEqual((await page.locator('#toolAdvanced .tool-tile h3').allInnerTexts()).map(t => t.trim()), TITULOS_P1);
      assert.equal(await page.locator('[data-adv-switch]').isDisabled(), true);
      await foto('0-ferramentas-sem-central');

      // ---- central conectada: estado escrito em todos os cartões
      await page.evaluate(() => { window.__bench.plugUsb(); window.__bench.deliver(); });
      await T.pump(page, 40);
      await foto('1-ferramentas-motor-e-combustivel');
      const sw = page.locator('[data-adv-switch]');
      assert.equal(await sw.getAttribute('role'), 'switch');
      assert.equal(await sw.getAttribute('aria-checked'), 'false');
      assert.match(await sw.innerText(), /Desligado/);
      const alvo = await sw.boundingBox();
      assert.ok(alvo.height >= 56 && alvo.width >= 56, `interruptor ≥ 56 px: ${alvo.width}x${alvo.height}`);
      for (const tile of await page.locator('#toolAdvanced .tool-tile').all()) {
        const t = (await tile.innerText()).replace(/\s+/g, ' ');
        assert.match(t, /Ligado|Desligado|De fábrica|Mudados|Só gás|Segurar com gasolina|Sempre gasolina/, 'cartão sem estado escrito: ' + t);
      }

      // ---- anti-apagão: 1º toque só mostra; nada grava
      await U.clearLog(page);
      await T.tap(page, '[data-adv-switch]'); await T.pump(page, 4);
      assert.equal(await U.camada(page), 1);
      assert.match(await U.text(page, '#toolsLayer'), /Ligar anti-apagão\?\s*Desligado\s*→\s*Ligado/);
      assert.deepEqual(await U.ecuWrites(page), [], 'nenhuma escrita depois do 1º toque');
      assert.equal(await page.locator('#toolsLayer [data-adv-confirm]').innerText(), 'Ligar anti-apagão');
      assert.equal(await page.locator('#toolsLayer [data-adv-cancel]').innerText(), 'Agora não');
      await foto('2-anti-apagao-confirmar');
      await T.tap(page, '#toolsLayer [data-adv-cancel]'); await T.pump(page, 2);
      assert.equal(await U.camada(page), 0, 'Agora não descarta');
      assert.deepEqual(await U.ecuWrites(page), []);
      await T.tap(page, '[data-adv-switch]'); await T.pump(page, 4);
      await T.tap(page, '#toolsLayer .tool-layer-card h3'); await T.pump(page, 2);       // 2º toque cai no cartão: não cancela
      assert.equal(await U.camada(page), 1, 'tocar no cartão não cancela');
      await T.tap(page, '#toolsLayer [data-adv-confirm]'); await T.pump(page, 6);
      await page.waitForSelector('#toolsLayer .adv-result[data-level="ok"]', { timeout: 8000 });
      assert.deepEqual(await U.ecuWrites(page), [U.LIGAR], 'quadro exato, uma vez');
      assert.deepEqual(await U.flag3(page), [0x20, 0, 0, 0]);
      assert.match(await U.text(page, '#toolsLayer'), /Pronto\. Anti-apagão ligado e conferido na central\./);
      await foto('3-anti-apagao-pronto');
      // Desfazer
      await U.clearLog(page);
      await T.tap(page, '#toolsLayer [data-adv-undo-flag]'); await T.pump(page, 4);
      assert.match(await U.text(page, '#toolsLayer'), /Desligar anti-apagão\?/);
      assert.deepEqual(await U.ecuWrites(page), [], 'o 1º toque do Desfazer não grava');
      await T.tap(page, '#toolsLayer [data-adv-confirm]'); await T.pump(page, 6);
      await page.waitForSelector('#toolsLayer .adv-result[data-level="ok"]');
      assert.deepEqual(await U.ecuWrites(page), [U.DESLIGAR]);
      assert.deepEqual(await U.flag3(page), [0, 0, 0, 0], 'a central voltou ao original');
      await T.tap(page, '#toolsLayer [data-adv-close]'); await T.pump(page, 3);
      assert.match(await sw.innerText(), /Desligado/);

      // ---- cartão só de leitura
      await T.tap(page, '[data-adv-open="troca"]'); await T.pump(page, 3);
      assert.match(await U.text(page, '.read-view'), /Troca de gasolina para gás.*Um cilindro por vez Ligado/);
      assert.equal(await page.locator('.read-view details.tool-tech').count(), 1, 'um único "Ver detalhes técnicos"');
      assert.equal(await page.locator('.read-view details.tool-tech').evaluate(e => e.open), false, 'recolhido');
      await foto('4-cartao-de-leitura');
      await T.tap(page, '[data-adv-back]'); await T.pump(page, 2);

      // ---- os 5 números
      await T.tap(page, '[data-adv-open="valores"]'); await T.pump(page, 3);
      await foto('5-valores-do-anti-apagao');
      await U.digitar(app, 0, '7000');
      assert.match(await U.text(page, '[data-field="0"] .num-error'), /Use um valor de 0 a 6\.000/);
      await foto('6-valor-fora-da-faixa');
      await U.digitar(app, 0, '1700');
      await T.tap(page, '[data-adv-save]'); await T.pump(page, 4);
      assert.match(await U.text(page, '#toolsLayer'), /1\.600 → 1\.700 rotação/);
      await foto('7-valores-confirmar');
      await U.clearLog(page);
      await T.tap(page, '#toolsLayer [data-adv-confirm]'); await T.pump(page, 6);
      await page.waitForSelector('#toolsLayer .adv-result[data-level="ok"]');
      assert.deepEqual(await U.ecuWrites(page), [U.quadroValores([1700, 100, 1500, 2, 20])]);
      await foto('8-valores-pronto');
      await T.tap(page, '#toolsLayer [data-adv-close]'); await T.pump(page, 3);
      await T.tap(page, '[data-adv-factory]'); await T.pump(page, 4);
      await T.tap(page, '#toolsLayer [data-adv-confirm]'); await T.pump(page, 6);
      await page.waitForSelector('#toolsLayer .adv-result[data-level="ok"]');
      assert.deepEqual(await U.anti5(page), U.FABRICA, 'Restaurar fábrica');
      await T.tap(page, '#toolsLayer [data-adv-close]'); await T.pump(page, 3);
      await T.tap(page, '[data-adv-back]'); await T.pump(page, 2);

      // ---- página 2 de Ferramentas (um cartão por função)
      await T.tap(page, '#toolsPageNext'); await T.pump(page, 3);
      assert.deepEqual((await page.locator('#toolDiagnosticsWorkspace .tool-tile h3').allInnerTexts()).map(t => t.trim()), TITULOS_P2);
      await foto('9-ferramentas-app-e-arquivos');
      const botoes = await page.locator('#toolDiagnosticsWorkspace button').evaluateAll(bs => bs.map(b => { const r = b.getBoundingClientRect(); return { t: b.textContent.trim().replace(/\s+/g, ' ').slice(0, 30), h: Math.round(r.height), w: Math.round(r.width) }; }));
      assert.deepEqual(botoes.filter(b => b.h < 56 || b.w < 56), [], 'botões menores que 56 px');
      await T.tap(page, '[data-tool-open-log]'); await T.pump(page, 3);
      await foto('10-registro-do-app');
      await T.tap(page, '[data-tool-back]'); await T.pump(page, 2);

      // ---- Sessões: tela própria
      await T.route(app, 'sessions'); await T.pump(page, 4, { data: true });
      assert.equal(await page.evaluate(() => document.querySelector('.screen.active')?.dataset.screen), 'sessions');
      assert.equal(await page.locator('.side-nav button[data-route="sessions"]').getAttribute('aria-current'), 'page');
      assert.equal(await page.locator('#toolsDrawer.active').count(), 0, 'Sessões saiu de Ferramentas');
      await foto('11-sessoes');
      await T.tap(page, '.diagnostic-settings > summary'); await T.pump(page, 2);
      await foto('12-sessoes-tamanho-das-gravacoes');
      assert.deepEqual(app.problems.page.concat(app.problems.console), [], 'sem erro de página');
    } finally { await app.context.close(); }
  });
}

test('falha honesta: a central não aceita o anti-apagão e o texto diz o que houve e o que fazer', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await page.evaluate(() => window.__bench.setFault('nak'));
    await T.tap(page, '[data-adv-switch]'); await T.pump(page, 4);
    await T.tap(page, '#toolsLayer [data-adv-confirm]'); await T.pump(page, 6);
    await page.waitForSelector('#toolsLayer .adv-result[data-level="warning"]');
    const msg = await U.text(page, '#toolsLayer');
    assert.match(msg, /A central não aceitou\. Tente de novo\./);
    assert.doesNotMatch(msg, /0x|status|ACK|NAK|ECU/i, 'sem código cru');
    await U.foto(page, '1280x720-13-central-nao-aceitou');
    await T.tap(page, '#toolsLayer [data-adv-close]'); await T.pump(page, 2);
    assert.match(await U.text(page, '[data-adv-switch]'), /Desligado/, 'o estado mostrado é o real');
  } finally { await app.context.close(); }
});

test('o cabo soltar na janela de confirmação descarta e nada é gravado', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await U.clearLog(page);
    await T.tap(page, '[data-adv-switch]'); await T.pump(page, 4);
    assert.equal(await U.camada(page), 1);
    await page.evaluate(() => window.__bench.unplugUsb());
    await T.pump(page, 12);
    assert.equal(await U.camada(page), 0);
    assert.deepEqual(await U.ecuWrites(page), []);
  } finally { await app.context.close(); }
});

test('mutação: confirmação automática faz a prova "nada grava no 1º toque" FALHAR no app carregado', { skip }, async () => {
  const dir = T.mutatedUi([['core/advanced-flags.js', "s.plan = plan;\n      s.token = status.token;\n      set('preview', '');", "s.plan = plan;\n      s.token = status.token;\n      set('preview', '');\n      confirm();"]]);
  const app = await T.openApp(browser, { ui: dir, usb: true, viewport: VPS['1280x720'] });
  try {
    await U.abrirMotor(app);
    await U.clearLog(app.page);
    await T.tap(app.page, '[data-adv-switch]'); await T.pump(app.page, 6);
    assert.deepEqual(await U.ecuWrites(app.page), [U.LIGAR], 'o mutante grava sozinho: exatamente o que a prova real proíbe');
  } finally { await app.context.close(); require('node:fs').rmSync(dir, { recursive: true, force: true }); }
});

// ---- portados do antigo e2e-avancado-toque (a UI antiga do Avançado saiu): mesmas garantias na nova tela
test('toque duplo RÁPIDO: do repouso só prepara; na confirmação, rajada de toques grava UMA vez', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await U.clearLog(page);
    await T.doubleTap(page, '[data-adv-switch]'); await T.pump(page, 4);
    assert.deepEqual(await U.ecuWrites(page), [], 'o 2º toque caiu em "lendo": nada escrito');
    assert.equal(await U.camada(page), 1);
    const b = await page.locator('#toolsLayer [data-adv-confirm]').boundingBox();
    for (let i = 0; i < 3; i += 1) await page.touchscreen.tap(b.x + b.width / 2, b.y + b.height / 2);
    await T.pump(page, 6);
    assert.deepEqual(await U.ecuWrites(page), [U.LIGAR], 'rajada de 3 toques = exatamente 1 quadro');
  } finally { await app.context.close(); }
});

test('sem central e sem leitura: o interruptor está desligado, nada sai e o motivo está escrito', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    assert.equal(await page.locator('[data-adv-switch]').isDisabled(), true);
    await T.tap(page, '[data-adv-switch]', { allowDisabled: true }); await T.pump(page, 3);
    assert.deepEqual((await T.frames(page)).log, [], 'nenhum quadro, de nenhum tipo');
    assert.match(await U.text(page, '[data-card="anti"] .tile-line'), /Conecte a central|Aguardando a central|Só funciona/);
    assert.equal(await U.camada(page), 0);
  } finally { await app.context.close(); }
});

test('falhas honestas: silêncio, ACK sem aplicar e leitura diferente nunca viram "Pronto"', { skip }, async () => {
  for (const [falha, re] of [['noReply', /não aceitou/], ['ignoreWrite', /Não sei como ficou/], ['corruptWrite', /Não sei como ficou/]]) {
    const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
    const { page } = app;
    try {
      await U.abrirMotor(app);
      await page.evaluate(f => window.__bench.setFault(f), falha);
      await T.tap(page, '[data-adv-switch]'); await T.pump(page, 4);
      await T.tap(page, '#toolsLayer [data-adv-confirm]'); await T.pump(page, 6);
      await page.waitForSelector('#toolsLayer .adv-result[data-level="warning"]');
      const msg = await U.text(page, '#toolsLayer');
      assert.match(msg, re, falha);
      assert.doesNotMatch(msg, /Pronto\./);
      assert.equal(await page.locator('#toolsLayer [data-adv-undo-flag]').count(), 0, 'sem Desfazer sem confirmação');
    } finally { await app.context.close(); }
  }
});

test('500 ciclos de dados não escrevem nada na central', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await U.clearLog(page);
    await page.evaluate(() => { for (let i = 0; i < 500; i += 1) { window.__bench.deliver(); window.OmegasApp.scheduler.run(); } });
    await T.pump(page, 2);
    assert.deepEqual(T.writesOf((await T.frames(page)).log), []);
  } finally { await app.context.close(); }
});
