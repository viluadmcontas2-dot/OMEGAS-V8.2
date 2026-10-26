'use strict';
// Tela Regular (início) da nova experiência: abre por padrão, estado vazio convida a agir, anel + 4 passos com dados,
// UM botão principal que nunca grava, e nada rola nem é cortado em 1280x720, 1024x600 e 800x480.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');

const skip = T.skipReason() || false;
const VPS = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

function medir() {
  const screen = document.querySelector('.screen.active');
  const visible = e => { const cs = getComputedStyle(e); if (cs.display === 'none' || cs.visibility === 'hidden') return false; const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const nome = e => e.tagName + (e.id ? '#' + e.id : '') + '.' + String(e.className?.baseVal ?? e.className).slice(0, 30);
  const out = [];
  const doc = document.documentElement;
  if (doc.scrollHeight > innerHeight + 1) out.push('documento rola na vertical');
  if (doc.scrollWidth > innerWidth + 1) out.push('documento rola na horizontal');
  if (!screen) return ['nenhuma aba ativa'];
  for (const e of [screen, ...screen.querySelectorAll('*')]) {
    if (!visible(e) || e.closest('.operation-layer, [hidden], details:not([open]) > :not(summary)')) continue;
    const cs = getComputedStyle(e);
    if (/(auto|scroll)/.test(cs.overflowY) && e.scrollHeight - e.clientHeight > 1) out.push('rola: ' + nome(e));
    if (/(auto|scroll)/.test(cs.overflowX) && e.scrollWidth - e.clientWidth > 1) out.push('rola na horizontal: ' + nome(e));
  }
  for (const e of screen.querySelectorAll('button, select, input, h2, h3, h4, p, b, span, small, li')) {
    if (!visible(e) || e.closest('.operation-layer, [hidden], details:not([open]) > :not(summary), option, svg')) continue;
    if (!(e.textContent || '').trim() && !/^(BUTTON|SELECT|INPUT)$/.test(e.tagName)) continue;
    const r = e.getBoundingClientRect();
    let cortado = r.right > innerWidth + 1 || r.left < -1 || r.bottom > innerHeight + 1;
    for (let a = e.parentElement; !cortado && a && a !== document.body; a = a.parentElement) {
      const cs = getComputedStyle(a);
      if (/(hidden|clip)/.test(cs.overflowY + cs.overflowX) && cs.display !== 'contents') {
        const ar = a.getBoundingClientRect();
        cortado = r.bottom > ar.bottom + 1 || r.right > ar.right + 1 || r.top < ar.top - 1 || r.left < ar.left - 1;
      }
    }
    if (cortado) out.push(`cortado: ${nome(e)} "${(e.textContent || '').trim().slice(0, 24)}"`);
  }
  const rail = document.querySelector('.side-rail'); const rr = rail?.getBoundingClientRect();
  if (!rail || rr.bottom > innerHeight + 1 || rr.right > innerWidth + 1) out.push('navegação fora da tela');
  return out;
}


const texto = page => page.evaluate(() => document.querySelector('#regularHost').innerText);

test('abre em "regular" por padrão, com a aba marcada', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  try {
    await T.pump(app.page, 3);
    assert.equal(await app.page.evaluate(() => document.querySelector('.screen.active')?.dataset.screen), 'regular');
    assert.equal(await app.page.locator('.side-nav button[data-route="regular"]').getAttribute('aria-current'), 'page');
    assert.deepEqual(app.problems.page, []);
  } finally { await app.context.close(); }
});

test('sem central: diz "Sem central", o próximo passo e não inventa valores', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  try {
    await T.pump(app.page, 4);
    const t = await texto(app.page);
    assert.match(t, /Sem central/);
    assert.match(t, /Conecte o cabo USB e ligue a ignição/);
    for (const nome of ['Preparar', 'Coletar', 'Ajustar', 'Conferir']) assert.match(t, new RegExp(nome));
    assert.equal(await app.page.locator('[data-rg="pct"]').innerText(), '—', 'progresso desconhecido é "—", nunca 0%');
    assert.doesNotMatch(t, /\b0\s?%/);
    assert.equal(await app.page.locator('[data-rg-primary]').count(), 1);
    assert.deepEqual(app.problems.page, []);
  } finally { await app.context.close(); }
});

test('com central e dados: anel com %, 4 passos com estado e a água "não medida"', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'], usb: true });
  try {
    await T.pump(app.page, 12, { data: true });
    const pct = await app.page.locator('[data-rg="pct"]').innerText();
    assert.match(pct, /^\d{1,3}%$/);
    const passos = await app.page.locator('.rg-step').count();
    assert.equal(passos, 4);
    const estados = await app.page.$$eval('.rg-step', els => els.map(e => e.dataset.state));
    assert.equal(estados.filter(s => s === 'now').length, 1, 'exatamente um passo "agora"');
    assert.ok(estados.every(s => ['done', 'now', 'later'].includes(s)));
    // Preparar vem feito (central pronta, tensão normal) e o passo agora mostra o que o app já sabe.
    assert.equal(estados[0], 'done');
    const t = await texto(app.page);
    assert.match(t, /Central conectada/);
    assert.ok(await app.page.locator('.rg-ring-fg').count() === 1);
    await T.tap(app.page, '[data-rg-step="0"]');
    const preparar = await texto(app.page);
    assert.match(preparar, /Tensão/);
    assert.match(preparar, /Nível do gás/);
    assert.match(preparar, /Água quente/);
    assert.match(preparar, /Não medida/);
    await T.tap(app.page, '[data-rg-step="1"]');
    assert.equal(await app.page.locator('.rg-zone').count(), 8, 'GNV e gasolina x 4 faixas');
    assert.deepEqual(app.problems.page, []);
  } finally { await app.context.close(); }
});

test('botão principal: um só, grande, e tocar nele não grava nada na central', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'], usb: true });
  try {
    await T.pump(app.page, 12, { data: true });
    assert.equal(await app.page.locator('[data-rg-primary]').count(), 1);
    assert.equal(await app.page.locator('#regularHost .nx-btn.primary').count(), 1, 'uma única ação dominante');
    const box = await app.page.locator('[data-rg-primary]').boundingBox();
    assert.ok(box.height >= 56, `alvo de toque ${box.height}px`);
    const antes = await T.frames(app.page);
    await T.tap(app.page, '[data-rg-primary]');
    await T.pump(app.page, 6, { data: true });
    const depois = await T.frames(app.page);
    assert.deepEqual(T.writesOf(depois.log.slice(antes.log.length)), [], 'nenhum quadro de escrita ao tocar no botão principal');
    assert.deepEqual(T.writesOf(depois.log), [], 'nenhuma escrita no registro da central');
    // A proposta pronta (ponte falsa) leva ao Refino, com o cartão "O que vai mudar" aberto para o dono confirmar.
    assert.equal(await app.page.evaluate(() => window.OmegasApp.store.get().route), 'refino');
    assert.equal(await app.page.locator('#refinoReview .refino-card').count(), 1, 'a prévia do Refino abriu');
    assert.deepEqual(app.problems.page, []);
  } finally { await app.context.close(); }
});

test('sem central, o botão principal só leva a conferir a conexão (nunca escreve)', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: VPS['1280x720'] });
  try {
    await T.pump(app.page, 4);
    assert.equal(await app.page.locator('[data-rg-primary]').innerText(), 'Conferir a conexão');
    await T.tap(app.page, '[data-rg-primary]');
    await T.pump(app.page, 3);
    assert.equal(await app.page.evaluate(() => window.OmegasApp.store.get().route), 'carro');
    assert.deepEqual(T.writesOf((await T.frames(app.page)).log), []);
  } finally { await app.context.close(); }
});

for (const [nome, vp] of Object.entries(VPS)) {
  test(`sem rolagem nem corte @${nome}, com cada passo aberto`, { skip }, async () => {
    for (const usb of [false, true]) {
      const app = await T.openApp(browser, { viewport: vp, usb });
      try {
        await T.pump(app.page, 12, { data: true });
        const problemas = {};
        for (const passo of [null, 0, 1, 2, 3]) {
          if (passo !== null) { await T.tap(app.page, `[data-rg-step="${passo}"]`); await T.pump(app.page, 2); }
          const lista = await app.page.evaluate(medir);
          const interno = await app.page.evaluate(() => [...document.querySelectorAll('.rg, .rg-hero, .rg-steps, .rg-step, .rg-body')]
            .filter(e => e.scrollHeight - e.clientHeight > 1 || e.scrollWidth - e.clientWidth > 1).map(e => e.className));
          if (lista.length || interno.length) problemas[`passo ${passo}`] = [...lista, ...interno.map(c => 'transborda: ' + c)];
        }
        assert.deepEqual(problemas, {}, `usb=${usb}`);
        const botao = await app.page.locator('[data-rg-primary]').boundingBox();
        assert.ok(botao.height >= 56, `botão com ${botao.height}px`);
        assert.deepEqual(app.problems.page, []);
        assert.deepEqual(app.problems.console, []);
      } finally { await app.context.close(); }
    }
  });
}
