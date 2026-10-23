'use strict';
// Dono: "a aba Preferências do AutoCal não tem botão de voltar". Causa: o painel cobre a tela inteira (position: fixed)
// e o único jeito de fechar era o resumo "Preferências", que ficava escondido atrás dele. Agora tem "‹ Voltar",
// no mesmo lugar e estilo das outras telas internas (canto de cima à esquerda, classe tool-back).
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');

const skip = T.skipReason() || false;
const VPS = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

test('o painel de Preferências traz o botão Voltar no padrão das telas internas (marcação)', () => {
  const P = require('../../app/src/main/assets/ui/core/autocal-preferencias.js');
  const html = P.panelHtml({ prefs: P.DEFAULTS, values: {}, drafts: {}, notes: {} });
  const m = html.match(/<header class="pref-head">([\s\S]*?)<\/header>/);
  assert.ok(m, 'cabeçalho do painel');
  assert.match(m[1], /<button type="button" class="secondary tool-back" data-tool-back data-pref-back><span aria-hidden="true">‹<\/span> Voltar<\/button>/);
  assert.match(m[1], /<h3>Preferências<\/h3>/);
  assert.ok(html.indexOf('pref-head') < html.indexOf('pref-intro'), 'Voltar vem antes de tudo');
});

for (const [nome, vp] of Object.entries(VPS)) {
  test(`Preferências @${nome}: Voltar visível no canto, alvo grande, fecha o painel e devolve a tela do AutoCal`, { skip }, async () => {
    const app = await T.openApp(browser, { viewport: vp, usb: true });
    const { page } = app;
    try {
      await T.route(app, 'autocal');
      await T.pump(page, 8, { data: true });
      await page.evaluate(() => { document.getElementById('autocalPrefMenu').open = true; });
      await T.pump(page, 3);
      const box = await page.evaluate(() => {
        const b = document.querySelector('#autocalPrefMenu [data-pref-back]');
        if (!b) return null;
        const r = b.getBoundingClientRect();
        const pop = document.querySelector('#autocalPrefMenu .autocal-pref-popover').getBoundingClientRect();
        const top = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
        return { x: r.left, y: r.top, w: r.width, h: r.height, popX: pop.left, popY: pop.top, tapavel: b === top || b.contains(top), text: b.textContent.trim(), sw: document.documentElement.scrollWidth, iw: innerWidth };
      });
      assert.ok(box, 'botão Voltar existe');
      assert.equal(box.text.replace(/\s+/g, ' '), '‹ Voltar');
      assert.ok(box.h >= 56 && box.w >= 120, `alvo de toque grande (${Math.round(box.w)}x${Math.round(box.h)})`);
      assert.ok(box.x <= box.popX + 24 && box.y <= box.popY + 24, 'no canto de cima à esquerda, como as outras telas');
      assert.ok(box.tapavel, 'nada cobre o botão');
      assert.ok(box.sw <= box.iw + 1, 'sem rolagem horizontal');
      await T.tap(page, '#autocalPrefMenu [data-pref-back]');
      await T.pump(page, 2);
      assert.equal(await page.evaluate(() => document.getElementById('autocalPrefMenu').open), false, 'Voltar fecha o painel');
      assert.equal(await page.evaluate(() => document.querySelector('.screen.active')?.dataset.screen), 'autocal');
      assert.deepEqual(app.problems.page, []);
    } finally { await app.context.close(); }
  });
}
