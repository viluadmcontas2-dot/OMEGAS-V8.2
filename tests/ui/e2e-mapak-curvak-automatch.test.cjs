'use strict';
// E2E de TOQUE: AutoMatch manual, sugestão do Mapa K, Curva K -> Mapa K e Suavizar. Regra do dono (2026-10-18): o app NUNCA grava
// sozinho. A bancada registra todo quadro; aqui se prova que escrita/ação só sai por toque do operador, nos quadros certos, e que a
// ECU emulada realmente muda e a leitura de volta bate. Sem Chromium o arquivo é pulado.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');

const skip = T.skipReason() || false;
const VPS = { '360x740': T.VIEWPORTS.pequeno, '412x915': T.VIEWPORTS.grande, '1280x720': { width: 1280, height: 720 } };
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

const writes = page => T.frames(page).then(f => T.writesOf(f.log).map(x => x.hex));
const clear = page => page.evaluate(() => window.__bench.ecu.clearLog());
const ciclos = (page, n) => page.evaluate(async n => { for (let i = 0; i < n; i += 1) { window.__bench.deliver(); window.OmegasApp.scheduler.run(); } }, n);
const txt = (page, sel) => page.locator(sel).first().innerText().then(t => t.replace(/\s+/g, ' ').trim());
const ECU_MAPA = page => page.evaluate(() => window.__bench.ecu.get('2A 54 00 02 80'));
const ECU_CURVA = page => page.evaluate(() => window.__bench.ecu.get('29 61 01 8B'));
const CURVA_SERRILHADA = Array.from({ length: 30 }, (_, i) => (i < 5 || i > 27 ? 1.0 : 1.0 + (i % 2 ? 0.07 : -0.05)));

// ---------------------------------------------------------------- AutoMatch manual
async function cenarioAutoMatch(app, shot) {
  const { page } = app;
  await T.route(app, 'autocal');
  await T.pump(page, 12, { data: true });
  const sel = '#autocalManualAutoMatch';
  assert.equal(await page.locator(sel).isVisible(), true, 'cota atingida: botão manual aparece');
  await clear(page);
  await ciclos(page, 500);
  await T.pump(page, 3);
  assert.deepEqual(await writes(page), [], 'nenhum 02 24 04 08 (nem outro quadro de escrita/ação) em 500 ciclos sem toque');
  assert.deepEqual((await T.frames(page)).acoes, []);
  if (shot) await shot('automatch-1-antes-do-toque');
  await T.tap(page, sel);
  await T.pump(page, 4);
  assert.deepEqual(await writes(page), ['02 24 04 08 32'], 'um toque = um quadro, exatamente AutoMatch manual');
  assert.deepEqual((await T.frames(page)).acoes, [8]);
  await ciclos(page, 100);
  assert.deepEqual(await writes(page), ['02 24 04 08 32'], 'depois do toque, nada mais sai sozinho');
  if (shot) await shot('automatch-2-depois-do-toque');
}

for (const [nome, vp] of Object.entries(VPS)) {
  test(`AutoMatch manual só por toque @${nome}`, { skip }, async () => {
    const app = await T.openApp(browser, { viewport: vp, usb: true, bench: { quota: true } });
    try {
      await cenarioAutoMatch(app, n => T.shot(app.page, `${nome}-${n}`));
      assert.deepEqual(app.problems.page.concat(app.problems.console), []);
    } finally { await app.context.close(); }
  });
}

test('AutoMatch manual sem USB: toque não envia nada', { skip }, async () => {
  const app = await T.openApp(browser, { bench: { quota: true } });
  try {
    await T.route(app, 'autocal'); await T.pump(app.page, 12);
    await app.page.evaluate(() => window.__bench.seed('09 74 01 7E', [3]));
    if (await app.page.locator('#autocalManualAutoMatch').isVisible()) await T.tap(app.page, '#autocalManualAutoMatch');
    await T.pump(app.page, 4);
    assert.deepEqual(await writes(app.page), []);
  } finally { await app.context.close(); }
});

// ---------------------------------------------------------------- sugestão do Mapa K: só sugere
async function cenarioSugestaoMapa(app, shot) {
  const { page } = app;
  await page.evaluate(() => { window.__bench.suggestionCells = [{ row: 2, column: 3, deltaPercent: 3 }, { row: 2, column: 4, deltaPercent: 3 }]; });
  await T.route(app, 'map');
  await T.pump(page, 12, { data: true });
  await page.waitForSelector('#mapGrid .map-k-cell');
  const antes = await ECU_MAPA(page);
  await clear(page);
  assert.match(await txt(page, '#mapSuggestionStatus'), /Sugestão pendente: 2 células/);
  await ciclos(page, 500); await T.pump(page, 3);
  assert.deepEqual(await writes(page), [], 'a sugestão existe há 500 ciclos e nada foi gravado');
  await T.tap(page, '#mapPrepareSuggestion'); await T.pump(page, 4);
  assert.deepEqual(await writes(page), [], 'Preparar sugestão só prepara');
  assert.deepEqual(await ECU_MAPA(page), antes, 'a ECU não mudou');
  assert.equal(await page.locator('.map-k-cell[data-row="2"][data-column="3"] span').innerText().then(t => t.length > 0), true, 'a célula mostra a prévia (alvo)');
  assert.equal(await page.locator('#mapReviewButton').isDisabled(), false, 'Gravar liberado para o toque do operador');
  if (shot) await shot('mapa-1-sugestao-preparada');
  await ciclos(page, 200);
  assert.deepEqual(await writes(page), [], 'preparada e esperando o dono: nada sai');
  await T.tap(page, '#mapReviewButton'); await T.pump(page, 6);
  const w = await writes(page);
  assert.equal(w.length, 2, 'só agora: uma escrita por célula (2)');
  assert.ok(w.every(h => /^14 54 00 02 0[34] [0-9A-F]{2} [0-9A-F]{2}$/.test(h)), w.join(' | '));
  const depois = await ECU_MAPA(page);
  assert.notDeepEqual(depois, antes);
  assert.ok(depois[3] > antes[3] && depois[4] > antes[4], 'a ECU emulada mudou de verdade: ' + depois.join(','));
  if (shot) await shot('mapa-2-gravado-por-toque');
}
for (const [nome, vp] of Object.entries(VPS)) {
  test(`sugestão do Mapa K só sugere; grava só no toque em Gravar @${nome}`, { skip, todo: nome === '1280x720' ? false : 'o painel de edição (Gravar na ECU) fica fora da tela em retrato' }, async () => {
    const app = await T.openApp(browser, { viewport: vp, usb: true });
    try { await cenarioSugestaoMapa(app, n => T.shot(app.page, `${nome}-${n}`)); assert.deepEqual(app.problems.page.concat(app.problems.console), []); }
    finally { await app.context.close(); }
  });
}

// ---------------------------------------------------------------- Curva K: Suavizar só prévia
async function cenarioSuavizar(app, shot) {
  const { page } = app;
  await page.evaluate(f => window.__bench.seedCurve(f), CURVA_SERRILHADA);
  await T.route(app, 'curve');
  await T.pump(page, 12, { data: true });
  await page.waitForSelector('#curveChart .curve-point');
  const antes = await ECU_CURVA(page);
  await clear(page);
  await T.tap(page, '#curveSmoothButton'); await T.pump(page, 4);
  assert.deepEqual(await writes(page), [], 'Suavizar só prepara a prévia');
  assert.deepEqual(await ECU_CURVA(page), antes, 'a Curva K da ECU não mudou');
  assert.match(await txt(page, '#curveReviewButton'), /Gravar \d+ pontos? na ECU/);
  assert.equal(await page.locator('#curveReviewButton').isDisabled(), false);
  if (shot) await shot('curva-1-suavizar-previa');
  await ciclos(page, 500); await T.pump(page, 3);
  assert.deepEqual(await writes(page), [], 'prévia parada por 500 ciclos: nada sai');
  await T.tap(page, '#curveReviewButton'); await T.pump(page, 8);
  const w = await writes(page);
  assert.ok(w.length >= 1 && w.every(h => /^14 61 01 [0-9A-F]{2} [0-9A-F]{2} [0-9A-F]{2} [0-9A-F]{2}$/.test(h)), w.join(' | '));
  const depois = await ECU_CURVA(page);
  assert.notDeepEqual(depois, antes, 'a ECU emulada recebeu a curva suavizada');
  const fatores = depois.reduce((a, _, i) => (i % 2 ? a : [...a, (depois[i] | (depois[i + 1] << 8)) / 16384]), []);
  assert.ok(fatores.every(f => f <= 1.8 && f >= 0.6), 'dentro do teto 1,8');
  if (shot) await shot('curva-2-gravada-por-toque');
}
for (const [nome, vp] of Object.entries(VPS)) {
  test(`Suavizar na Curva K só prepara; grava só no toque em Gravar @${nome}`, { skip, todo: nome === '1280x720' ? false : 'o painel de edição (Gravar na ECU) fica fora da tela em retrato' }, async () => {
    const app = await T.openApp(browser, { viewport: vp, usb: true });
    try { await cenarioSuavizar(app, n => T.shot(app.page, `${nome}-${n}`)); assert.deepEqual(app.problems.page.concat(app.problems.console), []); }
    finally { await app.context.close(); }
  });
}

// ---------------------------------------------------------------- Curva K -> Mapa K só propõe
test('Curva K → Mapa K só propõe: nenhum quadro de escrita até o toque em Gravar', { skip }, async () => {
  const app = await T.openApp(browser, { usb: true, viewport: { width: 1280, height: 720 } });
  const { page } = app;
  try {
    await page.evaluate(f => window.__bench.seedCurve(f), Array.from({ length: 30 }, (_, i) => 1.0 + Math.min(0.5, i * 0.02)));
    await T.route(app, 'map'); await T.pump(page, 12, { data: true });
    await page.waitForSelector('#mapGrid .map-k-cell');
    await clear(page);
    await T.tap(page, '#mapFromCurve');
    await T.pump(page, 12);
    assert.deepEqual(await writes(page), [], 'a proposta não grava');
    await ciclos(page, 200);
    assert.deepEqual(await writes(page), []);
    await T.shot(page, '1280x720-curva-para-mapa-proposta');
    assert.deepEqual(app.problems.page.concat(app.problems.console), []);
  } finally { await app.context.close(); }
});

// ---------------------------------------------------------------- mutações (app carregado com o código estragado)
test('mutação: AutoMatch enviado sozinho quando a cota atinge -> o cenário de 500 ciclos FALHA', { skip }, async () => {
  const dir = T.mutatedUi([['screens/autocal-cockpit.js', 'if (!manualAutoMatch.hidden) this.renderManualAutoMatchAdvice(manualAutoMatch, snapshot);', "if (!manualAutoMatch.hidden) { this.renderManualAutoMatchAdvice(manualAutoMatch, snapshot); if (window.__bench && window.__bench.usb && !this.__auto) { this.__auto = true; this.prepare('MANUAL_AUTOMATCH'); } }"]]);
  const app = await T.openApp(browser, { ui: dir, usb: true, bench: { quota: true } });
  try {
    await assert.rejects(() => cenarioAutoMatch(app, null));
    assert.deepEqual((await T.frames(app.page)).acoes, [8], 'o mutante realmente mandou 02 24 04 08 sem toque');
  } finally { await app.context.close(); require('node:fs').rmSync(dir, { recursive: true, force: true }); }
});

test('mutação: Suavizar gravando direto -> o cenário "só prévia" FALHA', { skip }, async () => {
  const dir = T.mutatedUi([['screens/curve.js', "this.alert('Confira os pontos preparados; nada foi gravado. Para aplicar, toque em Gravar na ECU.');", 'this.writePrepared();']]);
  const app = await T.openApp(browser, { ui: dir, usb: true, viewport: { width: 1280, height: 720 } });
  try {
    await assert.rejects(() => cenarioSuavizar(app, null), assert.AssertionError);
    assert.ok((await writes(app.page)).length >= 1, 'o mutante gravou na ECU sem o toque em Gravar');
  } finally { await app.context.close(); require('node:fs').rmSync(dir, { recursive: true, force: true }); }
});
