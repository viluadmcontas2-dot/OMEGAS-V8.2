'use strict';
// E2E de navegação por TOQUE (Chromium, touch emulado): abre o app com a ECU emulada e visita todas as abas.
// Duro em 1280x720 (viewport canônico do dono): sem erro de console, sem rolagem, sem texto cortado.
// 360x740 e 412x915: navegação e ausência de erros são duras; o LAYOUT é medido e fica como `todo` (o app é desenhado para
// 1280x720 e não tem layout de celular em retrato:).
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');

const skip = T.skipReason() || false;
const ROTAS = [['autocal', 'AutoCal'], ['refino', 'Refino'], ['map', 'Mapa K'], ['curve', 'Curva K'], ['tools', 'Ferramentas']];
// Alvo: multimídia do carro em PAISAGEM (dono, 2026-10-18): 1280x720 canônico, 1024x600 e 800x480. Retrato de celular não é alvo.
const VPS = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

/** Problemas de layout da aba ativa: rolagem do documento, rolagem interna, conteúdo fora da tela ou cortado por ancestral. */
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

for (const [nome, vp] of Object.entries(VPS)) {
  test(`navegação por toque @${nome}: todas as abas abrem, sem erro de console`, { skip }, async t => {
    const app = await T.openApp(browser, { viewport: vp, usb: true });
    const { page } = app;
    const layout = {};
    try {
      for (const [rota, rotulo] of ROTAS) {
        await T.route(app, rota);
        await T.pump(page, 12, { data: true });
        assert.equal(await page.evaluate(() => document.querySelector('.screen.active')?.dataset.screen), rota, `aba ${rotulo} ativa`);
        assert.equal(await page.locator(`.side-nav button[data-route="${rota}"]`).getAttribute('aria-current'), 'page');
        assert.match(await page.locator('#liveBar [data-live="rpm"] b').innerText(), /^8\d\d$/, 'barra do topo com o dado da central emulada');
        layout[rota] = await page.evaluate(medir);
        await T.shot(page, `navegacao-${nome}-${rota}`);
      }
      // Sessões: entra por Ferramentas e volta ao mesmo contexto.
      await T.route(app, 'tools');
      await T.tap(page, '#toolsPageNext'); await T.pump(page, 3);
      await T.tap(page, '[data-tool-open-sessions]'); await T.pump(page, 4, { data: true });
      assert.equal(await page.locator('.screen.active').getAttribute('data-screen'), 'sessions');
      assert.equal(await page.locator('.side-nav button[data-route="tools"]').getAttribute('aria-current'), 'page');
      layout.sessions = await page.evaluate(medir);
      await T.shot(page, `navegacao-${nome}-sessions`);
      await T.tap(page, '[data-session-back]'); await T.pump(page, 3);
      assert.equal(await page.locator('.screen.active').getAttribute('data-screen'), 'tools');
      assert.equal(await page.locator('#toolsDrawer').getAttribute('data-page'), '2', 'volta para a página que abriu Sessões');
      layout['tools>pagina2'] = await page.evaluate(medir);
      await T.tap(page, '#toolsPagePrev'); await T.pump(page, 2);
      assert.deepEqual(app.problems.console, [], 'erros de console');
      assert.deepEqual(app.problems.page, [], 'erros de página');
      assert.deepEqual(app.problems.requests, [], 'recursos que falharam');
      const total = Object.values(layout).flat();
      t.diagnostic(`problemas de layout @${nome}: ${total.length} ` + JSON.stringify(Object.fromEntries(Object.entries(layout).map(([k, v]) => [k, v.length]))));
      // As cinco áreas principais, Sessões e Ferramentas cabem nas três resoluções de multimídia.
      const duras = Object.keys(layout);
      assert.deepEqual(Object.fromEntries(duras.map(k => [k, layout[k]])), Object.fromEntries(duras.map(k => [k, []])), 'sem rolagem nem corte nas telas duras');
      app.layoutTotal = total.length;
    } finally { await app.context.close(); }
  });
}

