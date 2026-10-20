'use strict';
// Prova de coerência ponta a ponta da aba Ferramentas (dono, 2026-10-20: "a aba Ferramentas não funciona, leitura e escrita").
// Carrega a UI como o WebView (index.html por file://, scripts na ordem dele, contexto novo = sem cache) e, para CADA função
// visível, em 3 telas da multimídia e 3 estados da central (desconectada, conectada lendo, conectada com dados), afirma:
// cartão existe com área; estado escrito e claro; botões visíveis e tocáveis; o toque chega na ponte esperada e termina em
// estado legível (nunca vazio, nunca preso em "Gravando"). Qualquer console.error / pageerror / arquivo não carregado reprova.
//
// Ponte ESTRITA como a do Android: no WebView o método Java só responde quando chamado no próprio objeto injetado
// (`OmegasAutoCal.metodo()`); com outro `this` o Chromium do aparelho lança "Java bridge method can't be invoked on a
// non-injected object". A bancada comum aceitava qualquer `this` e por isso os testes passavam enquanto o aparelho não gravava.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');

const VIEWPORTS = {
  '1280x720': { width: 1280, height: 720 },
  '1024x600': { width: 1024, height: 600 },
  '800x480': { width: 800, height: 480 },
};
const STATES = ['desconectada', 'lendo', 'com dados'];
const MOTOR = ['anti', 'valores', 'corte', 'lenta', 'troca', 'injetores', 'volta'];
const LEITURA = ['corte', 'lenta', 'troca', 'injetores', 'volta'];
const APP = ['copia', 'refino-automatico', 'teste-gravacao', 'bateria', 'balao', 'registro'];

/** Troca cada método das pontes por um que exige o `this` certo (como o WebView) e registra a chamada. */
async function strictBridges(page) {
  await page.evaluate(() => {
    const json = v => JSON.stringify(v);
    window.__calls = [];
    const ac = window.OmegasAutoCal;
    let refine = 'SHADOW';
    Object.assign(ac, {
      getAutoRefineMode: () => refine,
      setAutoRefineMode: mode => { refine = String(mode); return json({ ok: true, mode: refine }); },
      startBufferWriteProbe: () => json({ ok: true }),
      getBufferWriteProbeStatus: () => json({ state: 'DONE', outcome: 'REFUSED' }),
    });
    Object.assign(window.OmegasNative, { exportData: () => true, exportLogs: () => true, runEngineSelfTests: () => json({ ok: true }) });
    const power = {
      getBatteryOptimizationStatus: () => json({ supported: true, ignoringOptimizations: false }),
      requestBatteryOptimizationExemption: () => json({ ok: true }),
      getOverlayStatus: () => json({ ok: true, supported: true, permissionGranted: false, requestedEnabled: false, visible: false }),
      requestOverlayPermissionAndEnable: () => json({ ok: true }),
      setOverlayEnabled: () => json({ ok: true }),
    };
    const strict = (obj, label) => {
      for (const name of Object.keys(obj)) {
        const fn = obj[name];
        if (typeof fn !== 'function') continue;
        obj[name] = function (...args) {
          if (this !== obj) throw new Error("Java bridge method can't be invoked on a non-injected object");
          window.__calls.push(`${label}.${name}`);
          return fn.apply(obj, args);
        };
      }
    };
    strict(ac, 'OmegasAutoCal');
    strict(window.OmegasNative, 'OmegasNative');
    strict(power, 'OmegasPower');
    window.OmegasApp.api.power = power;
  });
}

const calls = page => page.evaluate(() => window.__calls.slice());
const clearCalls = page => page.evaluate(() => { window.__calls.length = 0; });
const text = (page, sel) => page.locator(sel).first().innerText();

async function inside(page, selector, viewport, what) {
  const box = await page.locator(selector).first().boundingBox();
  assert.ok(box && box.width > 0 && box.height > 0, `${what}: sem área (${selector})`);
  assert.ok(box.x >= -1 && box.y >= -1 && box.x + box.width <= viewport.width + 1 && box.y + box.height <= viewport.height + 1,
    `${what}: fora da tela sem rolar (${selector}) ${JSON.stringify(box)}`);
}

/** Toca e confere que a ponte esperada foi chamada. */
async function tapCalls(page, selector, expected, what) {
  await clearCalls(page);
  await T.tap(page, selector);
  await T.pump(page, 4);
  const got = await calls(page);
  assert.ok(got.includes(expected), `${what}: o toque não chegou em ${expected} (chamadas: ${got.join(', ') || 'nenhuma'})`);
}

async function noVisibleLayer(page, what) {
  assert.equal(await page.locator('#toolsLayer:not([hidden]) .tool-layer-card').count(), 0, `${what}: abriu janela (2º toque)`);
}

async function notStuck(page, what) {
  const all = await text(page, '#toolAdvanced');
  assert.ok(all.trim().length > 0, `${what}: aba vazia`);
  assert.doesNotMatch(all, /Gravando/, `${what}: ficou preso em "Gravando"`);
}

async function openAppIn(browser, viewport, state, ui) {
  const app = await T.openApp(browser, { viewport, usb: state === 'com dados', ui });
  if (state === 'lendo') { await app.page.evaluate(() => window.__bench.plugUsb()); await T.pump(app.page, 3); }
  await strictBridges(app.page);
  await T.route(app, 'tools');
  await app.page.waitForSelector('#toolAdvanced [data-card="anti"]', { state: 'visible', timeout: 8000 });
  return app;
}

async function motorPage(page, viewport, state, { depois = false } = {}) {
  const where = `${viewport.width}x${viewport.height} ${state}${depois ? ' (depois dos toques)' : ''}`;
  assert.equal(await text(page, '#globalEcu'), state === 'desconectada' ? 'Sem central' : 'Central conectada', `${where}: estado da central`);
  for (const id of MOTOR) {
    const sel = `#toolAdvanced [data-card="${id}"]`;
    assert.equal(await page.locator(sel).count(), 1, `${where}: cartão ${id} sumiu`);
    await inside(page, sel, viewport, `${where} cartão ${id}`);
    const stateText = (await text(page, `${sel} ${id === 'anti' ? '.sw-caption b' : '.tile-state'}`)).trim();
    assert.ok(stateText.length > 0, `${where}: ${id} sem estado escrito`);
    if (depois) continue;
    if (state !== 'com dados') assert.equal(stateText, '—', `${where}: ${id} sem leitura precisa mostrar "—", nunca inventar`);
    else assert.notEqual(stateText, '—', `${where}: ${id} com dados deveria mostrar o valor lido`);
  }
  const line = await text(page, '#toolAdvanced [data-card="anti"] .tile-line');
  assert.ok(line.trim(), `${where}: linha do anti-apagão vazia`);
  if (depois) return;
  const expected = { desconectada: /Conecte a central/, lendo: /Ainda não lido/, 'com dados': /embreagem/ }[state];
  assert.match(line, expected, `${where}: linha do anti-apagão não diz o estado`);
}

async function antiSwitch(page, viewport, state) {
  const where = `${viewport.width}x${viewport.height} ${state}`;
  const sw = page.locator('#toolAdvanced [data-adv-switch]');
  await inside(page, '#toolAdvanced [data-adv-switch]', viewport, `${where} interruptor`);
  assert.equal(await sw.isDisabled(), state === 'desconectada', `${where}: interruptor só some sem a central`);
  if (state === 'desconectada') return;
  await tapCalls(page, '#toolAdvanced [data-adv-switch]', 'OmegasAutoCal.applyAntiStalloFlag', `${where} ligar anti-apagão`);
  await T.pump(page, 4);
  await noVisibleLayer(page, `${where} ligar`);
  await notStuck(page, `${where} ligar`);
  assert.equal((await text(page, '#toolAdvanced [data-card="anti"] .sw-caption b')).trim(), 'Ligado', `${where}: não ficou Ligado após a leitura de volta`);
  assert.match(await text(page, '#toolAdvanced [data-card="anti"] .tile-line'), /Pronto.*conferido/, `${where}: sucesso sem dizer que conferiu`);
  await tapCalls(page, '#toolAdvanced [data-adv-undo-flag]', 'OmegasAutoCal.applyAntiStalloFlag', `${where} Desfazer`);
  await T.pump(page, 4);
  await notStuck(page, `${where} desfazer`);
  assert.equal((await text(page, '#toolAdvanced [data-card="anti"] .sw-caption b')).trim(), 'Desligado', `${where}: Desfazer não voltou`);
}

async function valuesView(page, viewport, state) {
  const where = `${viewport.width}x${viewport.height} ${state} valores`;
  await T.tap(page, '[data-adv-open="valores"]');
  await T.pump(page, 4);
  await inside(page, '#toolAdvanced .values-view', viewport, where);
  const status = await text(page, '#toolAdvanced .vv-status');
  assert.ok(status.trim(), `${where}: linha de estado vazia`);
  if (state === 'desconectada') {
    assert.match(status, /Conecte a central/, where);
    assert.equal(await page.locator('[data-adv-step="0:1"]').isDisabled(), true, `${where}: sem central não grava`);
  } else {
    await inside(page, '[data-adv-step="0:1"]', viewport, `${where} botão +`);
    await tapCalls(page, '[data-adv-step="0:1"]', 'OmegasAutoCal.applyAntiStalloValues', `${where} +50`);
    await T.pump(page, 4);
    await noVisibleLayer(page, where);
    assert.equal((await text(page, '#toolAdvanced .vv-status')).trim(), 'Gravado e conferido ✓', `${where}: não terminou em estado legível`);
    await notStuck(page, where);
  }
  await T.tap(page, '[data-adv-back]');
  await T.pump(page, 2);
}

async function readCards(page, viewport, state) {
  for (const id of LEITURA) {
    const where = `${viewport.width}x${viewport.height} ${state} ${id}`;
    await T.tap(page, `[data-adv-open="${id}"]`);
    await T.pump(page, 2);
    await inside(page, '#toolAdvanced .read-view', viewport, where);
    const rows = await page.locator('#toolAdvanced .read-list li b').allInnerTexts();
    assert.ok(rows.length > 0 && rows.every(r => r.trim()), `${where}: leitura sem linhas ou com valor vazio`);
    await noVisibleLayer(page, where);
    await T.tap(page, '[data-adv-back]');
    await T.pump(page, 2);
  }
}

async function appPage(page, viewport, state) {
  const where = `${viewport.width}x${viewport.height} ${state} página 2`;
  await T.tap(page, '#toolsPageNext');
  await T.pump(page, 2);
  for (const id of APP) await inside(page, `#toolDiagnosticsWorkspace [data-card="${id}"]`, viewport, `${where} ${id}`);
  await tapCalls(page, '#toolExportData', 'OmegasNative.exportData', `${where} Salvar cópia`);
  await tapCalls(page, '#toolAutoRefine', 'OmegasAutoCal.setAutoRefineMode', `${where} Refino automático`);
  assert.match(await text(page, '#toolAutoRefine'), /Desligado/, `${where}: interruptor não mudou`);
  await tapCalls(page, '#toolAutoRefine', 'OmegasAutoCal.setAutoRefineMode', `${where} Refino automático de volta`);
  assert.match(await text(page, '#toolAutoRefine'), /Ligado/, where);
  // Teste de gravação: ainda arma no 1º toque e roda no 2º (pendente decisão do dono: escreve na central). Só confere que chega e termina legível.
  await T.tap(page, '#toolBufferProbe');
  await tapCalls(page, '#toolBufferProbe', 'OmegasAutoCal.startBufferWriteProbe', `${where} Teste de gravação`);
  assert.match(await text(page, '#toolBufferProbe'), /recusou|aceitou|Nenhuma faixa|Sonda/, `${where}: teste sem resultado legível`);
  await tapCalls(page, '[data-tool-battery-request]', 'OmegasPower.requestBatteryOptimizationExemption', `${where} Bateria`);
  await tapCalls(page, '[data-tool-overlay-request]', 'OmegasPower.requestOverlayPermissionAndEnable', `${where} Balão`);
  await T.tap(page, '[data-tool-open-log]');
  await T.pump(page, 2);
  await inside(page, '#toolDiagnosticsWorkspace .log-view', viewport, `${where} registro`);
  await tapCalls(page, '[data-tool-export-logs]', 'OmegasNative.exportLogs', `${where} Salvar registro`);
  await tapCalls(page, '[data-tool-selftest]', 'OmegasNative.runEngineSelfTests', `${where} Testar o app`);
  await T.tap(page, '[data-tool-back]');
  await T.pump(page, 2);
  await T.tap(page, '#toolsPagePrev');
  await T.pump(page, 2);
  await page.waitForSelector('#toolAdvanced [data-card="anti"]', { state: 'visible' });
}

async function wholeTab(browser, viewport, state, ui) {
  const app = await openAppIn(browser, viewport, state, ui);
  const { page } = app;
  try {
    if (state === 'desconectada') await page.evaluate(() => window.__bench.ecu.clearLog());
    await motorPage(page, viewport, state);
    await antiSwitch(page, viewport, state);
    await valuesView(page, viewport, state);
    await readCards(page, viewport, state);
    await appPage(page, viewport, state);
    await motorPage(page, viewport, state, { depois: true }); // a página voltou inteira depois de todos os toques
    if (state === 'desconectada') assert.deepEqual(T.writesOf((await T.frames(page)).log), [], 'sem central nada sai pelo fio');
    assert.deepEqual(app.problems, { console: [], page: [], requests: [] }, 'erro de console, de página ou arquivo não carregado');
  } finally { await app.context.close(); }
}

const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

for (const [name, viewport] of Object.entries(VIEWPORTS)) {
  for (const state of STATES) {
    test(`Ferramentas ${name}, central ${state}: cada função aparece, diz o estado, chega na ponte e termina legível`, { skip }, async () => {
      await wholeTab(browser, viewport, state);
    });
  }
}

test('RED do aparelho: com a ponte solta (Proxy devolvendo o método sem o objeto), o toque não grava', { skip }, async () => {
  const dir = T.mutatedUi([['components/advanced.js', 'return (...args) => target[name](...args);', 'return target[name];']]);
  try {
    await assert.rejects(wholeTab(browser, VIEWPORTS['1280x720'], 'com dados', dir), /applyAntiStalloFlag/);
  } finally { require('node:fs').rmSync(dir, { recursive: true, force: true }); }
});

test('"já está ligado" aparece na linha do cartão, sem janela nem 2º toque', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VIEWPORTS['1280x720'], usb: true });
  const { page } = app;
  try {
    await strictBridges(page);
    await T.route(app, 'tools');
    await page.evaluate(() => { window.__bench.seed('29 FA 00 23', [0x20, 0, 0, 0]); window.__bench.params = {}; window.__bench.advRead = false; });
    await T.pump(page, 3);
    await tapCalls(page, '#toolAdvanced [data-adv-switch]', 'OmegasAutoCal.applyAntiStalloFlag', 'conferir e ligar');
    await T.pump(page, 4);
    await noVisibleLayer(page, '"já está"');
    assert.match(await text(page, '#toolAdvanced [data-card="anti"] .tile-line'), /já está ligado/i);
    assert.deepEqual(T.writesOf((await T.frames(page)).log), [], 'já estava ligado: nada gravado');
    assert.equal(await page.locator('#toolAdvanced [data-adv-switch]').isDisabled(), false, 'o interruptor continua respondendo');
  } finally { await app.context.close(); }
});

test('barreira: um cartão que lança erro ao desenhar some sozinho; os outros seguem e ele volta no próximo ciclo', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VIEWPORTS['1280x720'], usb: true });
  const { page } = app;
  try {
    await T.route(app, 'tools');
    await page.evaluate(() => {
      const original = window.OmegasUi.CartoesMotor.build;
      window.__quebrado = true;
      window.OmegasUi.CartoesMotor.build = (...args) => original(...args).map(c => (c.id === 'corte' && window.__quebrado
        ? Object.defineProperty(c, 'title', { get() { throw Error('cartão falhou'); } }) : c));
    });
    await T.tap(page, '[data-adv-open="lenta"]'); await T.tap(page, '[data-adv-back]'); await T.pump(page, 2);
    assert.match(await text(page, '#toolAdvanced [data-card="corte"]'), /Leitura indisponível/);
    for (const id of MOTOR.filter(x => x !== 'corte')) await inside(page, `#toolAdvanced [data-card="${id}"]`, VIEWPORTS['1280x720'], id);
    await page.evaluate(() => { window.__quebrado = false; });
    await T.pump(page, 6);
    assert.doesNotMatch(await text(page, '#toolAdvanced [data-card="corte"]'), /Leitura indisponível/, 'o cartão não voltou sozinho');
    assert.match(await text(page, '#toolAdvanced [data-card="corte"]'), /Saída do corte/);
  } finally { await app.context.close(); }
});

test('barreira: tela interna que lança erro devolve aos cartões em vez de prender o dono', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VIEWPORTS['1280x720'], usb: true });
  const { page } = app;
  try {
    await T.route(app, 'tools');
    await page.evaluate(() => {
      const proto = window.OmegasUi.AdvancedPanel.prototype;
      proto.renderReadOnly = () => { throw Error('tela falhou'); };
    });
    await T.tap(page, '[data-adv-open="troca"]');
    await T.pump(page, 2);
    for (const id of MOTOR) await inside(page, `#toolAdvanced [data-card="${id}"]`, VIEWPORTS['1280x720'], id);
    await inside(page, '#toolsPageNext', VIEWPORTS['1280x720'], 'rodapé');
  } finally { await app.context.close(); }
});

test('barreira: o fluxo do anti-apagão quebrado derruba só o cartão dele', { skip }, async () => {
  const dir = T.mutatedUi([['core/advanced-flags.js', '      view() {\n        const preview', '      view() {\n        if (window.__flagQuebrado) throw Error(\'fluxo falhou\');\n        const preview']]);
  const app = await T.openApp(browser, { viewport: VIEWPORTS['1280x720'], usb: true, ui: dir });
  const { page } = app;
  try {
    await T.route(app, 'tools');
    await page.evaluate(() => { window.__flagQuebrado = true; window.__bench.deliver(); });
    await T.tap(page, '[data-adv-open="lenta"]'); await T.tap(page, '[data-adv-back]').catch(() => {}); await T.pump(page, 2);
    assert.match(await text(page, '#toolAdvanced [data-card="anti"]'), /Leitura indisponível/);
    for (const id of MOTOR.filter(x => x !== 'anti')) await inside(page, `#toolAdvanced [data-card="${id}"]`, VIEWPORTS['1280x720'], id);
  } finally {
    await app.context.close();
    require('node:fs').rmSync(dir, { recursive: true, force: true });
  }
});
