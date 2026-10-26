'use strict';
// Ajuda em 1 toque e primeiro uso (nova experiência): textos completos e sem jargão, cartão que não cobre as abas, Esc, foco, sem rolagem.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');

const skip = T.skipReason() || false;
const ROTAS = ['regular', 'carro', 'autocal', 'refino', 'map', 'curve', 'tools', 'sessions'];
const VISOES = ['autocal', 'refino', 'map', 'curve'];
const JARGAO = /\b(ECU|RPM|readback|snapshot|ciclos?|flags?|bits?|hash|payload|módulo)\b|0x[0-9a-f]+/i;
const VPS = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

test('textos: toda rota tem os 3 blocos; visões do Ajuste fino dizem quando usar; sem jargão', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  const dados = await app.page.evaluate(() => JSON.parse(JSON.stringify(window.OmegasUi.AjudaTextos)));
  for (const r of ROTAS) {
    const t = dados.telas[r];
    assert.ok(t, `sem texto para ${r}`);
    for (const k of ['eoque', 'fazer', 'acontece']) assert.ok(t[k] && t[k].trim().length > 10, `${r}.${k} vazio`);
    if (VISOES.includes(r)) assert.ok(t.quando && t.quando.trim().length > 10, `${r}.quando vazio`);
  }
  assert.equal(dados.primeiroUso.length, 4);
  dados.primeiroUso.forEach(c => assert.ok(c.titulo && c.texto));
  const todos = Object.values(dados.telas).flatMap(t => Object.values(t)).concat(dados.primeiroUso.flatMap(c => [c.titulo, c.texto]));
  for (const s of todos) assert.ok(!JARGAO.test(s), `jargão em: ${s}`);
  await app.context.close();
});

for (const [label, viewport] of Object.entries(VPS)) {
  test(`ajuda abre, não cobre as abas, não rola e fecha (${label})`, { skip }, async () => {
    const app = await T.openApp(browser, { viewport, usb: true, scale: 1 });
    const { page } = app;
    for (const r of ROTAS) {
      await page.evaluate(n => window.OmegasApp.router.navigate(n), r);
      await T.pump(page, 2, { data: true });
      await T.tap(page, '#ajudaBtn');
      const m = await page.evaluate(() => {
        const s = document.querySelector('.ajuda-sheet');
        if (!s) return null;
        const rail = document.querySelector('.side-rail').getBoundingClientRect();
        const b = s.getBoundingClientRect();
        const blocos = [...s.querySelectorAll('.ajuda-bloco p')].map(p => p.textContent.trim());
        const alvos = [...s.querySelectorAll('button')].map(x => x.getBoundingClientRect().height);
        return { role: s.getAttribute('role'), label: !!document.getElementById(s.getAttribute('aria-labelledby')), blocos, alvos, bottom: b.bottom, top: b.top, railTop: rail.top,
          corta: s.scrollHeight > s.clientHeight + 1, foco: s.contains(document.activeElement), expandido: document.getElementById('ajudaBtn').getAttribute('aria-expanded') };
      });
      assert.ok(m, `ajuda não abriu em ${r}`);
      assert.equal(m.role, 'dialog'); assert.ok(m.label); assert.equal(m.expandido, 'true');
      assert.ok(m.blocos.length >= 3 && m.blocos.every(x => x.length > 10), `blocos de ${r}`);
      assert.ok(m.bottom <= m.railTop + 0.5, `cartão cobre as abas em ${r}`);
      assert.ok(m.top >= 0, `cartão sai pelo topo em ${r}`);
      assert.ok(!m.corta, `texto cortado em ${r} ${label}`);
      assert.ok(m.alvos.every(h => h >= 56), `alvo pequeno em ${r}: ${m.alvos}`);
      assert.ok(m.foco, 'foco dentro do cartão');
      if (r === 'regular') await T.shot(page, `ajuda-${label}`).catch(() => {});
      // as abas continuam tocáveis: T.tap confere que nada cobre o botão
      await page.keyboard.press('Escape');
      assert.equal(await page.locator('.ajuda-sheet').count(), 0, 'Esc fecha');
      assert.equal(await page.evaluate(() => document.activeElement.id), 'ajudaBtn', 'foco volta ao ?');
    }
    // abre e fecha por 'Entendi' e por toque fora
    await T.tap(page, '#ajudaBtn');
    await T.tap(page, '.ajuda-sheet [data-ajuda="fechar"]');
    assert.equal(await page.locator('.ajuda-sheet').count(), 0);
    await T.tap(page, '#ajudaBtn');
    await page.touchscreen.tap(viewport.width / 2, 8);
    assert.equal(await page.locator('.ajuda-sheet').count(), 0, 'toque fora fecha');
    await T.tap(page, '#ajudaBtn'); await T.tap(page, '#ajudaBtn');
    assert.equal(await page.locator('.ajuda-sheet').count(), 0, 'segundo toque no ? fecha');
    // aba continua tocável com o cartão aberto
    await T.tap(page, '#ajudaBtn');
    await T.tap(page, '.side-nav button[data-route="carro"]');
    assert.equal(await page.evaluate(() => window.OmegasApp.store.get().route), 'carro');
    assert.equal(await page.locator('.ajuda-sheet').count(), 0, 'trocar de aba fecha o cartão');
    assert.deepEqual(app.problems.page, []);
    await app.context.close();
  });
}

test('rever o passo a passo, foco preso e primeiro uso grava a flag', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1024x600'], scale: 1 });
  const { page } = app;
  // sob automação o primeiro uso não abre sozinho
  assert.equal(await page.locator('.ajuda-tour').count(), 0);
  assert.equal(await page.evaluate(() => localStorage.getItem('omegas-v9-primeiro-uso')), null);
  await T.tap(page, '#ajudaBtn');
  await page.keyboard.press('Tab'); await page.keyboard.press('Tab'); await page.keyboard.press('Tab');
  assert.ok(await page.evaluate(() => document.querySelector('.ajuda-sheet').contains(document.activeElement)), 'foco preso no cartão');
  await T.tap(page, '.ajuda-sheet [data-ajuda="tour"]');
  assert.equal(await page.locator('.ajuda-tour').count(), 1);
  assert.equal(await page.evaluate(() => localStorage.getItem('omegas-v9-primeiro-uso')), '1');
  for (let i = 0; i < 4; i += 1) {
    const m = await page.evaluate(() => {
      const t = document.querySelector('.ajuda-tour'); const box = t.querySelector('.ajuda-tour-caixa');
      return { role: t.getAttribute('role'), titulo: t.querySelector('.ajuda-tour-titulo').textContent, pontos: t.querySelectorAll('.ajuda-ponto').length,
        on: [...t.querySelectorAll('.ajuda-ponto')].findIndex(p => p.dataset.on === '1'), corta: box.scrollHeight > box.clientHeight + 1 || box.getBoundingClientRect().bottom > innerHeight,
        alvos: [...t.querySelectorAll('button')].map(b => b.getBoundingClientRect().height) };
    });
    assert.equal(m.role, 'dialog'); assert.equal(m.pontos, 4); assert.equal(m.on, i); assert.ok(!m.corta); assert.ok(m.alvos.every(h => h >= 56));
    if (i === 0) await T.shot(page, 'primeiro-uso-1024').catch(() => {});
    await T.tap(page, i < 3 ? '.ajuda-tour [data-tour="prox"]' : '.ajuda-tour [data-tour="fim"]');
  }
  assert.equal(await page.locator('.ajuda-tour').count(), 0);
  // Esc e Pular fecham
  await page.evaluate(() => window.OmegasUi.Ajuda.openTour());
  await page.keyboard.press('Escape');
  assert.equal(await page.locator('.ajuda-tour').count(), 0);
  await page.evaluate(() => window.OmegasUi.Ajuda.openTour());
  await T.tap(page, '.ajuda-tour [data-tour="pular"]');
  assert.equal(await page.locator('.ajuda-tour').count(), 0);
  assert.deepEqual(app.problems.page, []);
  await app.context.close();
});

test('primeiro boot sem automação abre sozinho uma vez', { skip }, async () => {
  const ctx = await browser.newContext({ viewport: VPS['1280x720'], locale: 'pt-BR' });
  const page = await ctx.newPage();
  const erros = []; page.on('pageerror', e => erros.push(String(e)));
  await page.addInitScript(() => { Object.defineProperty(navigator, 'webdriver', { get: () => false }); });
  await page.goto('file://' + require('node:path').join(T.UI_DIR, 'index.html'));
  await page.waitForFunction(() => !!window.OmegasApp);
  await page.waitForSelector('.ajuda-tour', { timeout: 3000 });
  assert.equal(await page.evaluate(() => localStorage.getItem('omegas-v9-primeiro-uso')), '1');
  await page.reload();
  await page.waitForFunction(() => !!window.OmegasApp);
  await page.waitForTimeout(400);
  assert.equal(await page.locator('.ajuda-tour').count(), 0, 'só uma vez');
  assert.deepEqual(erros, []);
  await ctx.close();
});
