'use strict';
// Fluxo completo por TOQUE (Chromium, touch emulado) das telas novas: Ferramentas (cartões por assunto, sem
// abas), anti-apagão (interruptor + 5 números com lápis), Desfazer e Sessões. Central emulada; registra todo quadro que sai.
// Gera docs/evidence/img/ux-<tamanho>-N-*.png (1280x720 canônico, 1024x600 e 800x480 em paisagem). Sem Chromium PULA.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const U = require('./helpers/ux-cenarios.cjs');

const skip = T.skipReason() || false;
const VPS = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };
const TITULOS_P1 = ['Anti-apagão', 'Valores do anti-apagão', 'Saída do corte', 'Troca de gasolina para gás', 'Injetores', 'Volta para gasolina', 'Outras configurações'];
const TITULOS_P2 = ['Cópia de segurança', 'Refino automático', 'Bateria', 'Balão sobre outros apps', 'Sessões', 'Registro do app', 'Multimídia (Car Info)'];
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

for (const [nome, vp] of Object.entries(VPS)) {
  test(`fluxo completo por toque @${nome}`, { skip }, async () => {
    const app = await T.openApp(browser, { viewport: vp });
    const { page } = app;
    const foto = n => U.foto(page, `${nome}-${n}`);
    try {
      // ---- navegação principal: 5 áreas; Sessões fica dentro de Ferramentas
      const rotulos = await page.locator('.side-nav button').allInnerTexts();
      assert.deepEqual(rotulos.map(t => t.trim()), ['AutoCal', 'Refino', 'Ajuste local', 'Ajuste global', 'Ferramentas']);
      for (const b of await page.locator('.side-nav button').all()) assert.ok((await b.boundingBox()).height >= 56, 'alvo da navegação ≥ 56 px');

      // ---- Ferramentas, sem central: tudo desligado, motivo escrito
      await U.abrirMotor(app);
      assert.deepEqual((await page.locator('#toolAdvanced .tool-tile h3').allInnerTexts()).map(t => t.trim()), TITULOS_P1);
      assert.equal(await page.locator('[data-adv-switch]').isDisabled(), true);
      await foto('0-ferramentas-sem-central');

      // ---- central conectada: estado escrito em todos os cartões
      await page.evaluate(() => { window.__bench.plugUsb(); window.__bench.deliver(); });
      await T.pump(page, 40);
      // Intuitive Software Design: mostrar a rotação real no próprio cartão,
      // sem transformar valores de fábrica em número presumido quando a leitura falha.
      const tiles = await page.locator('#toolAdvanced [data-card="valores"]').innerText();
      assert.match(tiles, /1\.600 giros/, 'a pessoa reconhece o limite sem abrir o editor');
      assert.match(tiles, /De fábrica/, 'a pessoa reconhece a origem do valor');
      const outrosEstados = await page.evaluate(() => {
        const model = window.OmegasUi.CartoesMotor;
        const row = values => model.build(values ? { ANTI_STALLO: { state: 'OK', values } } : {})
          .find(c => c.id === 'valores');
        return { alterado: row([1700,100,1500,2,20]), semLeitura: row(null) };
      });
      assert.match(outrosEstados.alterado.line, /1\.700 giros/);
      assert.equal(outrosEstados.alterado.stateText, 'Mudados');
      assert.equal(outrosEstados.semLeitura.stateText, '—');
      assert.doesNotMatch(outrosEstados.semLeitura.line, /1\.600|1\.700/);
      // Ferramentas usa o tema Instrumento; sinalização sem mudar o comportamento do motor.
      const instrumento = await page.evaluate(() => {
        const tile = document.querySelector('#toolAdvanced .tool-tile');
        const line = tile?.querySelector('.tile-line');
        if (!tile || !line) return { missing: true };
        const visual = getComputedStyle(tile);
        return {
          background: visual.backgroundImage,
          radius: parseFloat(visual.borderTopLeftRadius),
          captionColor: getComputedStyle(line).color
        };
      });
      assert.match(instrumento.background || '', /linear-gradient/, 'painel Instrumento usa acabamento técnico');
      assert.ok(instrumento.radius >= 14, JSON.stringify(instrumento));
      assert.equal(instrumento.captionColor, 'rgb(174, 191, 208)', 'cor de texto secundário do tema Instrumento');
      await foto('1-ferramentas-motor-e-combustivel');
      const sw = page.locator('[data-adv-switch]');
      assert.equal(await sw.getAttribute('role'), 'switch');
      assert.equal(await sw.getAttribute('aria-checked'), 'false');
      assert.match(await sw.innerText(), /Desligado/);
      const alvo = await sw.boundingBox();
      assert.ok(alvo.height >= 56 && alvo.width >= 56, `interruptor ≥ 56 px: ${alvo.width}x${alvo.height}`);
      for (const tile of await page.locator('#toolAdvanced .tool-tile').all()) {
        const t = (await tile.innerText()).replace(/\s+/g, ' ');
        assert.match(t, /Ligado|Desligado|De fábrica|Mudados|Só gás|Segurar com gasolina|Sempre gasolina|injeções|Só leitura|—/, 'cartão sem estado escrito: ' + t);
      }

      // ---- anti-apagão: SEM ATRITO, um toque liga (sem janela de pergunta)
      await U.clearLog(page);
      await T.tap(page, '[data-adv-switch]'); await T.pump(page, 10);
      assert.equal(await U.camada(page), 0, 'nenhuma janela de pergunta');
      assert.deepEqual(await U.ecuWrites(page), [U.LIGAR], 'quadro exato, uma vez');
      assert.deepEqual(await U.flag3(page), [0x20, 0, 0, 0]);
      assert.match(await sw.innerText(), /Ligado/);
      await foto('3-anti-apagao-pronto');
      // Desfazer (também um toque, no próprio cartão)
      await U.clearLog(page);
      await T.tap(page, '[data-adv-undo-flag]'); await T.pump(page, 10);
      assert.equal(await U.camada(page), 0, 'desfazer sem pergunta');
      assert.deepEqual(await U.ecuWrites(page), [U.DESLIGAR]);
      assert.deepEqual(await U.flag3(page), [0, 0, 0, 0], 'a central voltou ao original');
      assert.match(await sw.innerText(), /Desligado/);

      // ---- cartão só de leitura
      await T.tap(page, '[data-adv-open="troca"]'); await T.pump(page, 3);
      assert.match(await U.text(page, '.read-view'), /Troca entre gasolina e gás.*Sobreposição da troca Desligado/);
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
      await U.clearLog(page);
      await page.evaluate(() => document.activeElement && document.activeElement.blur()); await T.pump(page, 10);   // saiu do campo: grava na hora
      assert.equal(await U.camada(page), 0, 'gravar valores sem janela de pergunta');
      assert.deepEqual(await U.ecuWrites(page), U.quadroValores([1700, 100, 1500, 2, 20]));
      await foto('8-valores-pronto');
      await T.tap(page, '[data-adv-factory]'); await T.pump(page, 10);
      assert.deepEqual(await U.anti5(page), U.FABRICA, 'Restaurar fábrica em um toque');
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

      // ---- Sessões: acesso por Ferramentas e retorno à página que a abriu
      await T.tap(page, '[data-tool-open-sessions]'); await T.pump(page, 4, { data: true });
      assert.equal(await page.evaluate(() => document.querySelector('.screen.active')?.dataset.screen), 'sessions');
      assert.equal(await page.locator('.side-nav button[data-route="tools"]').getAttribute('aria-current'), 'page');
      await foto('11-sessoes');
      await T.tap(page, '.diagnostic-settings > summary'); await T.pump(page, 2);
      await foto('12-sessoes-tamanho-das-gravacoes');
      await T.tap(page, '[data-session-back]'); await T.pump(page, 3);
      assert.equal(await page.locator('.screen.active').getAttribute('data-screen'), 'tools');
      assert.equal(await page.locator('#toolsDrawer').getAttribute('data-page'), '2');
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
    await T.tap(page, '[data-adv-switch]'); await T.pump(page, 10);
    // Sem janela: o que houve e o que fazer aparecem na linha do próprio cartão.
    await page.waitForSelector('[data-card="anti"] .tile-line[data-level="warn"]');
    const msg = await U.text(page, '[data-card="anti"] .tile-line');
    assert.match(msg, /A central não aceitou\. Tente de novo\./);
    assert.doesNotMatch(msg, /0x|status|ACK|NAK|ECU/i, 'sem código cru');
    assert.equal(await U.camada(page), 0, 'erro sem janela');
    await U.foto(page, '1280x720-13-central-nao-aceitou');
    assert.match(await U.text(page, '[data-adv-switch]'), /Desligado/, 'o estado mostrado é o real');
  } finally { await app.context.close(); }
});

test('cabo solto: o interruptor fica desligado e nada é gravado', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await U.clearLog(page);
    await page.evaluate(() => window.__bench.unplugUsb());
    await T.pump(page, 12);
    assert.equal(await page.locator('[data-adv-switch]').isDisabled(), true);
    assert.deepEqual(await U.ecuWrites(page), []);
  } finally { await app.context.close(); }
});

test('mutação: trocar a chamada única por "só preparar" faz a prova "um toque grava" FALHAR no app carregado', { skip }, async () => {
  const dir = T.mutatedUi([['core/advanced-flags.js', 'bridge.applyAntiStalloFlag(enable)', 'bridge.prepareAntiStalloFlag(enable)']]);
  const app = await T.openApp(browser, { ui: dir, usb: true, viewport: VPS['1280x720'] });
  try {
    await U.abrirMotor(app);
    await U.clearLog(app.page);
    await T.tap(app.page, '[data-adv-switch]'); await T.pump(app.page, 10);
    assert.deepEqual(await U.ecuWrites(app.page), [], 'o mutante não grava em um toque: a prova real (UM toque = 1 quadro) falharia');
  } finally { await app.context.close(); require('node:fs').rmSync(dir, { recursive: true, force: true }); }
});

// ---- portados do antigo e2e-avancado-toque (a UI antiga do Avançado saiu): mesmas garantias na nova tela
test('toque duplo RÁPIDO e rajada: gravam UMA vez só', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await page.evaluate(() => window.OmegasApp.scheduler.stop()); // só o teste gira os ciclos: os dois toques caem antes de qualquer ciclo (o que o nome diz)
    await U.clearLog(page);
    await T.doubleTap(page, '[data-adv-switch]'); await T.pump(page, 10);
    assert.deepEqual(await U.ecuWrites(page), [U.LIGAR], 'toque duplo = exatamente 1 quadro');
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
      await T.tap(page, '[data-adv-switch]'); await T.pump(page, 10);
      await page.waitForSelector('[data-card="anti"] .tile-line[data-level="warn"]');
      const msg = await U.text(page, '[data-card="anti"] .tile-line');
      assert.match(msg, re, falha);
      assert.doesNotMatch(msg, /Pronto\./);
      assert.equal(await U.camada(page), 0, 'erro sem janela');
      assert.equal(await page.locator('[data-adv-undo-flag]').count(), 0, 'sem Desfazer sem confirmação');
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


// OP-2 — inspeção por toque de controles delegados sem promover navegação a writer da central.
test('OP-2: abrir valores e explicação sem USB não dispara escrita nem inventa leitura', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await U.clearLog(page);
    await T.tap(page, '[data-adv-open="valores"]'); await T.pump(page, 2);
    assert.equal(await page.locator('[data-adv-step="0:1"]').isDisabled(), true, 'sem leitura e sem cabo não habilita ajuste');
    await T.tap(page, '[data-adv-open-view="info"]'); await T.pump(page, 2);
    assert.ok(await page.locator('[data-adv-info-tab]').count() > 0, 'guia explicativa acessível');
    await T.tap(page, '[data-adv-back-valores]'); await T.pump(page, 2);
    assert.ok(await page.locator('[data-adv-step="0:1"]').count() > 0, 'voltar preserva contexto');
    await T.tap(page, '[data-adv-back]'); await T.pump(page, 2);
    assert.ok(await page.locator('[data-adv-open="valores"]').count() > 0, 'retorna ao grupo de ferramentas');
    assert.deepEqual(await U.ecuWrites(page), [], 'ler/explicar/voltar não pode escrever');
    assert.deepEqual(app.problems.page, [], 'sem erro no fluxo');
  } finally { await app.context.close(); }
});

// OP-3 — resultado de serviço é diferente de clique aceito e não perde informações válidas.
test('OP-3: sessão e ajustes sem resposta não viram sucesso; recibo parcial preserva ajustes', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await T.tap(page, '#toolsPageNext'); await T.pump(page, 2);
    await T.tap(page, '[data-tool-open-sessions]'); await T.pump(page, 3, { data: true });
    await page.evaluate(() => {
      const api = window.OmegasApp.api;
      window.OmegasApp.store.patch({ sessionStatus: {
        recording: false, events: 9, settings: { autoStartOnUsb: true, telemetryEveryMs: 500, maxSessionMb: 256, keepSessions: 24 }
      } });
      // O agendador pode reler status durante o teste; mantenha a
      // resposta de consulta consistente, sem sobrescrever o fixture.
      api.sessionStatus = () => ({ recording: false, events: 9,
        settings: { autoStartOnUsb: true, telemetryEveryMs: 500, maxSessionMb: 256, keepSessions: 24 } });
      api.startSession = () => undefined; // retorno nativo ausente, não êxito
      api.setSessionSettings = () => undefined;
    });
    await T.tap(page, '[data-session-start]');
    let outcome = await page.evaluate(() => ({ alert: window.OmegasApp.store.get().alert, status: window.OmegasApp.store.get().sessionStatus }));
    assert.equal(outcome.alert.level, 'warning', 'resposta ausente não confirma gravação da sessão');
    assert.match(outcome.alert.message, /Não foi possível confirmar/);
    assert.equal(outcome.status.recording, false, 'sem resposta a tela não presume sessão iniciada');

    await T.tap(page, '.diagnostic-settings > summary');
    await T.tap(page, '[data-session-settings]');
    outcome = await page.evaluate(() => ({ alert: window.OmegasApp.store.get().alert, status: window.OmegasApp.store.get().sessionStatus }));
    assert.equal(outcome.alert.level, 'warning', 'ajustes sem resultado não viram sucesso');
    assert.equal(outcome.status.settings.maxSessionMb, 256, 'ajuste sem confirmação não sobrescreve o salvo');
    assert.match(await U.text(page, '.settings-feedback'), /Não foi possível confirmar/, 'recuperação aparece junto à edição');

    // RED #2051: a edição aberta usa uma superfície de detalhes que cobre a ação
    // principal. Primeiro concluir/fechar os ajustes; não forçar clique por JS nem
    // remover a proteção visual. O usuário volta sem perder escolhas válidas.
    assert.equal(await page.locator('.diagnostic-settings').evaluate(el => el.open), true,
      'ajustes continuam visíveis após falha e permitem recuperação');
    await T.tap(page, '.diagnostic-settings > summary');
    assert.equal(await page.locator('.diagnostic-settings').evaluate(el => el.open), false,
      'fechar ajustes libera a ação principal sem intervenção oculta');

    await page.evaluate(() => { window.OmegasApp.api.startSession = () => ({ ok: true, recording: true }); });
    await T.tap(page, '[data-session-start]');
    outcome = await page.evaluate(() => ({ alert: window.OmegasApp.store.get().alert, status: window.OmegasApp.store.get().sessionStatus }));
    assert.equal(outcome.alert.level, 'ok', 'êxito exige ok explícito');
    assert.equal(outcome.status.recording, true);
    assert.equal(outcome.status.events, 9, 'recibo parcial não apaga contadores anteriores');
    assert.equal(outcome.status.settings.keepSessions, 24, 'recibo parcial não apaga configuração anterior');
    assert.deepEqual(await U.ecuWrites(page), [], 'sessões e ajustes são fluxo do app, sem writer ECU');
  } finally { await app.context.close(); }
});

// RED controlado: a versão anterior do tratamento de resultado reintroduz o falso êxito.
test('OP-3 mutante: regressão de confirmação ausente seria detectada', { skip }, async () => {
  const dir = T.mutatedUi([['components/drawers.js',
    "notifyResult(result, successMessage) {\n      // Uma resposta ausente não é confirmação: o seletor/bridge pode ter falhado\n      // depois de iniciar a operação. Não criar recibo verde sem ok explícito.\n      if (result?.ok !== true) {",
    "notifyResult(result, successMessage) {\n      // Mutante intencional da antiga checagem permissiva.\n      if (result?.ok === false) {"
  ]]);
  const app = await T.openApp(browser, { ui: dir, viewport: VPS['1280x720'] });
  try {
    await U.abrirMotor(app);
    await T.tap(app.page, '#toolsPageNext'); await T.pump(app.page, 2);
    await T.tap(app.page, '[data-tool-open-sessions]'); await T.pump(app.page, 2);
    await app.page.evaluate(() => { window.OmegasApp.api.startSession = () => undefined; });
    await T.tap(app.page, '[data-session-start]');
    const level = await app.page.evaluate(() => window.OmegasApp.store.get().alert.level);
    assert.equal(level, 'ok', 'mutante reproduz falso êxito; a verificação OP-3 anterior falharia');
  } finally { await app.context.close(); require('node:fs').rmSync(dir, { recursive: true, force: true }); }
});


// Task 14 / OP-3: sem ponte de energia NÃO significa isenção Android.
test('Ferramentas: bateria indisponível é desconhecida, não "sem limite"; ação só com suporte', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await T.tap(page, '#toolsPageNext'); await T.pump(page, 3);
    const state = async response => {
      await page.evaluate(value => {
        window.OmegasApp.api.batteryOptimizationStatus = () => value;
        // Esta é a notificação real usada pelo Android para atualizar a
        // projeção após o serviço mudar: um pump sem evento pode não repintar.
        window.dispatchEvent(new Event('omegas-refresh'));
      }, response);
      await T.pump(page, 2);
      const expected = response.ignoringOptimizations === true ? 'on'
        : response.supported === true ? 'off' : 'unknown';
      await page.waitForFunction(value =>
        document.querySelector('[data-card="bateria"]')?.getAttribute('data-state') === value,
      expected, { timeout: 2500 });
      return {
        text: await U.text(page, '[data-card="bateria"]'),
        request: await page.locator('[data-tool-battery-request]').count(),
        kind: await page.locator('[data-card="bateria"]').getAttribute('data-state')
      };
    };
    let actual = await state({ supported: false, ignoringOptimizations: false });
    assert.equal(actual.kind, 'unknown', 'ponte indisponível não significa permissão concedida');
    assert.equal(actual.request, 0, 'não oferecer autorização sem ponte pronta');
    assert.match(actual.text, /Sem leitura|Não foi possível consultar/, 'orientação visível sem jargão');

    actual = await state({ supported: true, ignoringOptimizations: false });
    assert.equal(actual.kind, 'off', 'permissão realmente necessária');
    assert.equal(actual.request, 1, 'oferecer ação apenas com suporte comprovado');

    actual = await state({ supported: false, ignoringOptimizations: true }); // Android antigo, sem essa restrição
    assert.equal(actual.kind, 'on');
    assert.equal(actual.request, 0, 'não pedir autorização impossível no Android antigo');
    assert.match(actual.text, /Sem restrição/);
    assert.deepEqual(await U.ecuWrites(page), [], 'status de bateria não pode acionar writer ECU');
  } finally { await app.context.close(); }
});


// OP-3 / Task 14: preferências locais não devem parecer salvas sem confirmação.
test('Ferramentas: refino sombra não muda de estado com recibo ausente ou incompatível', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await T.tap(page, '#toolsPageNext'); await T.pump(page, 2);
    await page.evaluate(() => {
      window.OmegasAutoCal.getAutoRefineMode = () => 'SHADOW';
      window.OmegasAutoCal.setAutoRefineMode = () => '{}'; // JSON parseável, sem confirmação
    });
    await T.tap(page, '#toolAutoRefine');
    let status = await page.evaluate(() => ({
      alert: window.OmegasApp.store.get().alert,
      checked: document.getElementById('toolAutoRefine')?.getAttribute('aria-checked')
    }));
    assert.equal(status.alert.level, 'warning', 'recibo sem ok não pode confirmar preferência');
    assert.equal(status.checked, 'true', 'manter modo SHADOW lido da bridge');

    await page.evaluate(() => { window.OmegasAutoCal.setAutoRefineMode = () => '{"ok":true,"mode":"SHADOW"}'; });
    await T.tap(page, '#toolAutoRefine');
    status = await page.evaluate(() => ({
      alert: window.OmegasApp.store.get().alert,
      checked: document.getElementById('toolAutoRefine')?.getAttribute('aria-checked')
    }));
    assert.equal(status.alert.level, 'warning', 'modo diferente não comprova a mudança pedida');
    assert.equal(status.checked, 'true');

    await page.evaluate(() => {
      window.__testAutoRefineMode = 'SHADOW';
      window.OmegasAutoCal.getAutoRefineMode = () => window.__testAutoRefineMode;
      window.OmegasAutoCal.setAutoRefineMode = mode => {
        window.__testAutoRefineMode = mode;
        return JSON.stringify({ ok: true, mode });
      };
    });
    await T.tap(page, '#toolAutoRefine');
    assert.equal(await page.locator('#toolAutoRefine').getAttribute('aria-checked'), 'false',
      'mudar somente após modo confirmado');
    assert.deepEqual(await U.ecuWrites(page), [], 'preferência de refino SHADOW/OFF não grava K automaticamente');
  } finally { await app.context.close(); }
});


// OP-3 / Task 14: permissão solicitada ≠ permissão concedida; erros precisam
// de recuperação visível sem acionamento de serial/ECU.
test('Ferramentas: permissão de bateria e balão com erro, pendente e sem ponte', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await T.tap(page, '#toolsPageNext'); await T.pump(page, 3);
    await page.evaluate(() => {
      const api = window.OmegasApp.api;
      api.batteryOptimizationStatus = () => ({ supported: true, ignoringOptimizations: false });
      api.overlayStatus = () => ({ ok: false, supported: false, permissionGranted: false, visible: false });
      api.requestBatteryOptimizationExemption = () => ({ ok: false, error: 'Bateria indisponível' });
      api.requestOverlayPermissionAndEnable = () => ({ ok: false, error: 'Balão indisponível' });
      window.dispatchEvent(new Event('omegas-refresh'));
    });
    await T.pump(page, 3);
    await page.waitForFunction(() =>
      document.querySelector('[data-card="balao"]')?.getAttribute('data-state') === 'unknown');
    assert.match(await U.text(page, '[data-card="balao"]'), /Sem leitura/);
    assert.equal(await page.locator('[data-tool-overlay-request]').count(), 0, 'sem bridge não apresentar CTA falso');
    await T.tap(page, '[data-tool-battery-request]');
    let alert = await page.evaluate(() => window.OmegasApp.store.get().alert);
    assert.equal(alert.level, 'warning');
    assert.match(alert.message, /Bateria indisponível/);

    await page.evaluate(() => {
      window.OmegasApp.api.overlayStatus = () =>
        ({ ok: true, supported: true, permissionGranted: false, requestedEnabled: false, visible: false });
      window.dispatchEvent(new Event('omegas-refresh'));
    });
    await T.pump(page, 2);
    await page.waitForFunction(() => !!document.querySelector('[data-tool-overlay-request]'));
    await T.tap(page, '[data-tool-overlay-request]');
    alert = await page.evaluate(() => window.OmegasApp.store.get().alert);
    assert.equal(alert.level, 'warning');
    assert.match(alert.message, /Balão indisponível/);

    await page.evaluate(() => {
      window.OmegasApp.api.requestOverlayPermissionAndEnable = () =>
        ({ ok: true, launched: true, permissionRequired: true, permissionGranted: false });
    });
    await T.tap(page, '[data-tool-overlay-request]');
    alert = await page.evaluate(() => window.OmegasApp.store.get().alert);
    assert.equal(alert.level, 'warning', 'abrir Android não comprova permissão');
    assert.match(alert.message, /Aguardando permissão/);
    assert.equal(await page.locator('[data-card="balao"]').getAttribute('data-state'), 'off');

    await page.evaluate(() => {
      window.OmegasApp.api.overlayStatus = () =>
        ({ ok: true, supported: true, permissionGranted: true, visible: false });
      window.OmegasApp.api.setTelemetryOverlayEnabled = () => ({ ok: false, error: 'Falha ao ligar balão' });
      window.dispatchEvent(new Event('omegas-refresh'));
    });
    await T.pump(page, 2);
    await page.waitForFunction(() => !!document.querySelector('[data-tool-overlay-enable]'));
    await T.tap(page, '[data-tool-overlay-enable]');
    alert = await page.evaluate(() => window.OmegasApp.store.get().alert);
    assert.equal(alert.level, 'warning');
    assert.match(alert.message, /Falha ao ligar balão/);
    assert.deepEqual(await U.ecuWrites(page), [], 'permissões não emitem quadros de escrita ECU');
  } finally { await app.context.close(); }
});


// OBS-COD Android: visible=false com o app em primeiro plano é normal.
// A preferência solicitada é separada da janela flutuante desenhada.
test('Ferramentas: balão pedido e autorizado permanece ligado mesmo oculto dentro do OMEGAS', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await T.tap(page, '#toolsPageNext'); await T.pump(page, 2);
    await page.evaluate(() => {
      window.OmegasApp.api.overlayStatus = () =>
        ({ ok: true, supported: true, permissionGranted: true, requestedEnabled: true, visible: false });
      window.dispatchEvent(new Event('omegas-refresh'));
    });
    await T.pump(page, 2);
    await page.waitForFunction(() =>
      document.querySelector('[data-card="balao"]')?.getAttribute('data-state') === 'on',
    null, { timeout: 2500 });
    assert.equal(await page.locator('[data-tool-overlay-disable]').count(), 1,
      'se o balão está configurado deve oferecer desligar, mesmo oculto no próprio app');
    assert.equal(await page.locator('[data-tool-overlay-disable]').getAttribute('aria-checked'), 'true');
    assert.match(await U.text(page, '[data-card="balao"]'), /Aparece fora do OMEGAS/);

    await page.evaluate(() => {
      window.OmegasApp.api.overlayStatus = () =>
        ({ ok: true, supported: true, permissionGranted: true, requestedEnabled: false, visible: false });
      window.dispatchEvent(new Event('omegas-refresh'));
    });
    await T.pump(page, 2);
    await page.waitForFunction(() =>
      document.querySelector('[data-card="balao"]')?.getAttribute('data-state') === 'off',
    null, { timeout: 2500 });
    assert.equal(await page.locator('[data-tool-overlay-enable]').count(), 1,
      'sem pedido ativo e com permissão, oferecer ligar');
    assert.deepEqual(await U.ecuWrites(page), [], 'ler preferência do balão não grava central');
  } finally { await app.context.close(); }
});


// Contrato do retorno Android: atualizar o botão tocado NÃO autoriza apagar
// um filtro/entrada que a pessoa ainda está preenchendo em Ferramentas.
test('Ferramentas: atualização nativa preserva foco e rascunho de filtro em edição', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  const { page } = app;
  try {
    await U.abrirMotor(app);
    await T.tap(page, '#toolsPageNext'); await T.pump(page, 2);
    await T.tap(page, '[data-tool-open-log]'); await T.pump(page, 2);
    await page.locator('[data-log-level]').focus();
    const before = await page.evaluate(() => {
      window.__focusedLogFilter = document.querySelector('[data-log-level]');
      return {focused: document.activeElement === window.__focusedLogFilter,
        value: window.__focusedLogFilter?.value};
    });
    assert.equal(before.focused, true, 'filtro de registro realmente em edição');
    await page.evaluate(() => {
      // Mudança de outra configuração torna a assinatura de render diferente,
      // mas NÃO autoriza reconstruir o seletor ainda focado.
      window.OmegasApp.api.overlayStatus = () =>
        ({ ok: true, supported: true, permissionGranted: true,
          requestedEnabled: true, visible: false });
      window.dispatchEvent(new Event('omegas-refresh'));
    });
    await T.pump(page, 3);
    const after = await page.evaluate(() => ({
      sameNode: document.querySelector('[data-log-level]') === window.__focusedLogFilter,
      focused: document.activeElement === window.__focusedLogFilter,
      value: window.__focusedLogFilter?.value
    }));
    assert.equal(after.sameNode, true, 'evento do Android não destrói seletor/rascunho em edição');
    assert.equal(after.focused, true, 'foco preservado para continuar a decisão');
    assert.equal(after.value, before.value, 'seleção não deve voltar ao padrão');
    assert.deepEqual(await U.ecuWrites(page), []);
  } finally { await app.context.close(); }
});


// RED antes do ajuste: o antigo handler aceitava {ok:true} como sessão iniciada
// e mostrava um recibo verde mesmo quando recording estava ausente/divergente.
test('OP-3: iniciar/parar gravação exige estado confirmado, sem mudar o anterior', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  try {
    const { page } = app;
    await U.abrirMotor(app);
    await T.tap(page, '#toolsPageNext'); await T.pump(page, 2);
    await T.tap(page, '[data-tool-open-sessions]'); await T.pump(page, 2, { data: true });
    await page.evaluate(() => {
      const api = window.OmegasApp.api;
      api.sessionStatus = () => ({ recording: false, events: 7, settings: { maxSessionMb: 256, keepSessions: 20 } });
      window.OmegasApp.store.patch({ sessionStatus: api.sessionStatus() });
      api.startSession = () => ({ ok: true }); // sem confirmação de gravação
    });
    await T.tap(page, '[data-session-start]');
    let state = await page.evaluate(() => window.OmegasApp.store.get());
    assert.equal(state.alert.level, 'warning', 'ok sem recording não prova início');
    assert.equal(state.sessionStatus.recording, false);
    await page.evaluate(() => {
      window.OmegasApp.api.startSession = () => ({ ok: true, recording: false });
    });
    await T.tap(page, '[data-session-start]');
    state = await page.evaluate(() => window.OmegasApp.store.get());
    assert.equal(state.alert.level, 'warning', 'estado oposto não prova início');
    assert.equal(state.sessionStatus.recording, false);
    await page.evaluate(() => {
      window.OmegasApp.api.startSession = () => ({ ok: true, recording: true });
    });
    await T.tap(page, '[data-session-start]');
    state = await page.evaluate(() => window.OmegasApp.store.get());
    assert.equal(state.alert.level, 'ok');
    assert.equal(state.sessionStatus.recording, true);
    await page.evaluate(() => {
      window.OmegasApp.api.stopSession = () => ({ ok: true, recording: true });
    });
    await T.tap(page, '[data-session-stop]');
    state = await page.evaluate(() => window.OmegasApp.store.get());
    assert.equal(state.alert.level, 'warning', 'gravação ainda ativa não é encerramento');
    assert.equal(state.sessionStatus.recording, true);
    await page.evaluate(() => {
      window.OmegasApp.api.stopSession = () => ({ ok: true, recording: false });
    });
    await T.tap(page, '[data-session-stop]');
    state = await page.evaluate(() => window.OmegasApp.store.get());
    assert.equal(state.alert.level, 'ok');
    assert.equal(state.sessionStatus.recording, false);
    assert.deepEqual(await U.ecuWrites(page), [], 'gravação local não gera comando ECU');
  } finally { await app.context.close(); }
});

test('OP-3: aplicar configuração de sessão rejeita ok incompleto ou valor divergente', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  try {
    const { page } = app;
    await U.abrirMotor(app);
    await T.tap(page, '#toolsPageNext'); await T.pump(page, 2);
    await T.tap(page, '[data-tool-open-sessions]'); await T.pump(page, 2, { data: true });
    await T.tap(page, '.diagnostic-settings > summary');
    await page.evaluate(() => {
      const api = window.OmegasApp.api;
      api.setSessionSettings = s => ({ ok: true, settings: { ...s, maxSessionMb: 64 } });
    });
    const before = await page.locator('[data-session-maxmb]').inputValue();
    await T.tap(page, '[data-session-settings]');
    assert.equal(await page.evaluate(() => window.OmegasApp.store.get().alert.level), 'warning');
    assert.match(await U.text(page, '.settings-feedback'), /sem confirmação completa/i);
    assert.equal(await page.locator('[data-session-maxmb]').inputValue(), before,
      'não sobrescrever rascunho com resposta incompatível');
    await page.evaluate(() => {
      window.OmegasApp.api.setSessionSettings = () => ({ ok: true });
    });
    await T.tap(page, '[data-session-settings]');
    assert.equal(await page.evaluate(() => window.OmegasApp.store.get().alert.level), 'warning',
      'ok sem settings não é confirmação completa');
    await page.evaluate(() => {
      window.OmegasApp.api.setSessionSettings = settings => ({ ok: true, settings });
    });
    await T.tap(page, '[data-session-settings]');
    assert.equal(await page.evaluate(() => window.OmegasApp.store.get().alert.level), 'ok',
      'resposta completa e igual confirma os ajustes');
    assert.deepEqual(await U.ecuWrites(page), [], 'aplicar ajustes de arquivo não escreve na central');
  } finally { await app.context.close(); }
});
