'use strict';
// Ferramentas por GRUPO (dono, 2026-10-20: "muito vai e vem"): cada "Ver ›" / "Mudar valores" abre UMA página com tudo do assunto, no
// modelo da tela "Valores do anti-apagão": cartões lado a lado, − / + visíveis (≥ 58 px) nos editáveis, "Só leitura" sem − / + nos que o
// app ainda não grava, rodapé de botões à esquerda, rolagem só vertical, ≥ 8 cartões à vista em 1280x720. Central emulada (bancada),
// 3 tamanhos de multimídia e 3 estados da central. Sem Chromium PULA.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');

const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

const VIEWPORTS = { '1280x720': { width: 1280, height: 720 }, '1024x600': { width: 1024, height: 600 }, '800x480': { width: 800, height: 480 } };
// cartão da tela inicial -> grupo que abre e cartões que PRECISAM estar na mesma página
const GROUPS = [
  { open: 'valores', group: 'valores', must: ['anti', 'av-0', 'av-1', 'av-2', 'av-3', 'av-4'], edit: 5 },
  { open: 'volta', group: 'volta', must: ['volta-liga', 'volta-mbar', 'volta-espera'], edit: 2 },
  { open: 'troca', group: 'troca', must: ['FLAG_CONF1-1-1000', 'corte-inj', 'FLAG_CONF2-0-1', 'FLAG_CONF1-1-10'], edit: 0 },
  { open: 'corte', group: 'troca', must: ['corte-inj'], edit: 0 },
  { open: 'injetores', group: 'troca', must: ['FLAG_CONF1-1-10'], edit: 0 },
  { open: 'lenta', group: 'lenta', must: ['lenta-modo', 'lenta-giro'], edit: 0 },
  { open: 'outras', group: 'outras', must: [], edit: 0 },
];

async function openTools(vp, central) {
  const app = await T.openApp(browser, { viewport: vp, usb: central === 'com dados', scale: 1 });
  if (central === 'lendo') await app.page.evaluate(() => { window.__bench.plugUsb(); });
  await T.route(app, 'tools');
  return app;
}

/** Mede a página de grupo aberta. */
const measure = page => page.evaluate(() => {
  const view = document.querySelector('#toolAdvanced .grp-view');
  if (!view) return null;
  const grid = view.querySelector('.grp-grid');
  const gr = grid.getBoundingClientRect();
  const cards = [...grid.querySelectorAll('.grp-card')].map(c => {
    const r = c.getBoundingClientRect();
    const steps = [...c.querySelectorAll('.step-btn')].map(b => { const br = b.getBoundingClientRect(); const cs = getComputedStyle(b); return { x: br.x, w: br.width, h: br.height, visible: cs.visibility !== 'hidden' && cs.display !== 'none' && br.width > 0, call: b.dataset.advStep || b.dataset.voltaStep || '' }; });
    const val = c.querySelector('.grp-val, input');
    return {
      id: c.dataset.item, kind: c.dataset.kind, top: r.top, bottom: r.bottom, left: r.left, right: r.right,
      steps, pencil: !!c.querySelector('.pencil'), ro: /Só leitura/.test(c.innerText), text: c.innerText,
      valFont: val ? parseFloat(getComputedStyle(val).fontSize) : 0, clipped: c.scrollWidth > c.clientWidth + 1,
    };
  });
  const bar = view.querySelector('.grp-actions');
  const btns = bar ? [...bar.querySelectorAll('button')].filter(b => b.offsetParent).map(b => { const r = b.getBoundingClientRect(); return { x: r.x, w: r.width, h: r.height, y: r.y }; }) : [];
  return {
    group: view.dataset.group, title: view.querySelector('h3').innerText, views: document.querySelectorAll('#toolAdvanced .grp-view').length,
    back: !!view.querySelector('[data-adv-back]'), grid: { top: gr.top, bottom: gr.bottom, left: gr.left, right: gr.right, sw: grid.scrollWidth, cw: grid.clientWidth },
    oy: getComputedStyle(grid).overflowY, cards, bar: bar ? bar.getBoundingClientRect().x : null, btns,
    pageW: document.scrollingElement.scrollWidth, status: (view.querySelector('.grp-status') || {}).innerText || '',
  };
});

for (const [name, vp] of Object.entries(VIEWPORTS)) {
  for (const central of ['desconectada', 'lendo', 'com dados']) {
    test(`grupos de Ferramentas @${name}, central ${central}: uma página por assunto, − / + grandes, só leitura sem − / +, nada à direita sozinho`, { skip }, async () => {
      const app = await openTools(vp, central);
      const { page } = app;
      try {
        for (const g of GROUPS) {
          const where = `${g.open} @${name} ${central}`;
          await T.tap(page, `#toolAdvanced [data-adv-open="${g.open}"]`); await T.pump(page, 3);
          const m = await measure(page);
          assert.ok(m, `${where}: abriu a página de grupo`);
          assert.equal(m.views, 1, `${where}: uma página só`);
          assert.equal(m.group, g.group, where);
          assert.ok(m.back, `${where}: tem "‹ Voltar"`);
          const ids = m.cards.map(c => c.id);
          for (const id of g.must) assert.ok(ids.includes(id), `${where}: falta o cartão ${id} (tudo do grupo na mesma página): ${ids.join(',')}`);
          assert.ok(m.cards.length >= 1);
          assert.equal(m.cards.filter(c => c.kind === 'edit').length, g.edit, `${where}: cartões editáveis`);
          // sem rolagem horizontal; só vertical
          assert.ok(m.pageW <= vp.width, `${where}: a página não rola na horizontal (${m.pageW})`);
          assert.ok(m.grid.sw <= m.grid.cw + 1, `${where}: a grade não rola na horizontal`);
          assert.ok(m.cards.every(c => c.left >= m.grid.left - 1 && c.right <= m.grid.right + 1 && !c.clipped), `${where}: nenhum cartão cortado na lateral`);
          // ≥ 8 cartões à vista sem rolar (ou todos, se o grupo tiver menos)
          const visible = m.cards.filter(c => c.top >= m.grid.top - 1 && c.bottom <= m.grid.bottom + 1).length;
          if (name === '1280x720') assert.ok(visible >= Math.min(8, m.cards.length), `${where}: ${visible} de ${m.cards.length} cartões à vista`);
          for (const c of m.cards) {
            if (c.kind === 'edit') {
              assert.equal(c.steps.length, 2, `${where} ${c.id}: − e +`);
              for (const s of c.steps) assert.ok(s.visible && s.h >= 58 && s.w >= 58, `${where} ${c.id}: botão ${s.call} ${s.w}x${s.h} (≥ 58 px)`);
              assert.ok(Math.abs(c.steps[0].x - c.left) <= 16, `${where} ${c.id}: o − começa à esquerda do cartão`);
              assert.ok(c.valFont >= 22, `${where} ${c.id}: valor ≥ 22 px (${c.valFont})`);
            } else if (c.kind === 'read') {
              assert.equal(c.steps.length, 0, `${where} ${c.id}: só leitura não tem − / +`);
              assert.equal(c.pencil, false, `${where} ${c.id}: só leitura não tem lápis`);
              assert.ok(c.ro, `${where} ${c.id}: diz "Só leitura"`);
              assert.ok(c.valFont >= 22, `${where} ${c.id}: valor ≥ 22 px (${c.valFont})`);
              if (central !== 'com dados') assert.match(c.text, /—/, `${where} ${c.id}: sem leitura mostra "—", nunca 0`);
            }
            assert.doesNotMatch(c.text, /\bECU\b/, `${where}: "central", nunca "ECU"`);
          }
          // rodapé: botões juntos à esquerda, 58 px, 8 px entre
          if (m.btns.length) {
            assert.ok(Math.abs(m.btns[0].x - m.bar) <= 2, `${where}: o 1º botão do rodapé começa à esquerda`);
            for (let i = 0; i < m.btns.length; i += 1) {
              assert.ok(m.btns[i].h >= 58, `${where}: botão do rodapé ≥ 58 px`);
              if (i && Math.abs(m.btns[i].y - m.btns[i - 1].y) < 2) {
                const gap = m.btns[i].x - (m.btns[i - 1].x + m.btns[i - 1].w);
                assert.ok(gap >= 6 && gap <= 10, `${where}: 8 px entre botões (${gap})`);
              }
            }
          }
          await T.tap(page, '#toolAdvanced [data-adv-back]'); await T.pump(page, 2);
          assert.equal(await page.locator('#toolAdvanced .grp-view').count(), 0, `${where}: Voltar devolve aos cartões`);
        }
        assert.deepEqual(app.problems.page, []);
        assert.deepEqual(app.problems.console, []);
      } finally { await app.context.close(); }
    });
  }
}

for (const [name, vp] of Object.entries({ '1280x720': VIEWPORTS['1280x720'], '800x480': VIEWPORTS['800x480'] })) {
  test(`um toque em − / + chama a ponte certa e termina em estado legível @${name}`, { skip }, async () => {
    const app = await openTools(vp, 'com dados');
    const { page } = app;
    try {
      await page.evaluate(() => {
        window.__calls = [];
        const b = window.OmegasAutoCal;
        for (const n of ['applyAntiStalloValues', 'applyVoltaGasolina']) { const f = b[n]; b[n] = (...a) => { window.__calls.push([n, ...a.map(String)]); return f(...a); }; }
      });
      // anti-apagão: + na rotação máxima grava na hora e diz "Gravado e conferido"
      await T.tap(page, '#toolAdvanced [data-adv-open="valores"]'); await T.pump(page, 3);
      await page.evaluate(() => window.__bench.ecu.clearLog());
      await T.tap(page, '#toolAdvanced [data-adv-step="0:1"]'); await T.pump(page, 4);
      let calls = await page.evaluate(() => window.__calls.splice(0));
      assert.deepEqual(calls.map(c => c[0]), ['applyAntiStalloValues']);
      assert.match(calls[0][1], /"0":1650/);
      assert.equal(T.writesOf((await T.frames(page)).log).length, 1, 'um quadro de escrita');
      assert.match(await page.locator('#toolAdvanced .grp-status').innerText(), /Gravado e conferido/);
      assert.equal(await page.locator('#toolAdvanced [data-adv-field="0"]').inputValue(), '1.650');
      assert.equal(await page.locator('#toolAdvanced [data-adv-undo-values]').count(), 1, 'Desfazer à vista');
      await T.tap(page, '#toolAdvanced [data-adv-back]'); await T.pump(page, 2);

      // volta: + na espera grava na hora (quadro exato do original) e confere
      await T.tap(page, '#toolAdvanced [data-adv-open="volta"]'); await T.pump(page, 3);
      await page.evaluate(() => window.__bench.ecu.clearLog());
      await T.tap(page, '#toolAdvanced [data-volta-step="segundos:1"]'); await T.pump(page, 4);
      calls = await page.evaluate(() => window.__calls.splice(0));
      assert.deepEqual(calls, [['applyVoltaGasolina', '{"acao":"salvar","segundos":1}']]);
      assert.match(JSON.stringify(T.writesOf((await T.frames(page)).log)), /12 3E 00 01 51/);
      assert.match(await page.locator('#toolAdvanced .grp-status').innerText(), /Salvo e conferido na central/);
      assert.match(await page.locator('#toolAdvanced [data-item="volta-espera"] .grp-val').innerText(), /1 s/);
      assert.equal(await page.locator('#toolAdvanced [data-volta-undo]').count(), 1, 'Desfazer à vista');
      // volta: + na pressão vai à ponte com o novo limite; a bancada recusa a tabela e a recusa aparece escrita
      await T.tap(page, '#toolAdvanced [data-volta-step="mbar:1"]'); await T.pump(page, 4);
      calls = await page.evaluate(() => window.__calls.splice(0));
      assert.deepEqual(calls, [['applyVoltaGasolina', '{"acao":"salvar","mbar":1300}']]);
      assert.match(await page.locator('#toolAdvanced .grp-status').innerText(), /\S/, 'o resultado aparece escrito');
      assert.match(await page.locator('#toolAdvanced [data-item="volta-mbar"] .grp-val').innerText(), /1,25/, 'sem confirmação, mostra o valor da central');
      // ligar/desligar num toque
      await T.tap(page, '#toolAdvanced [data-volta-toggle]'); await T.pump(page, 4);
      calls = await page.evaluate(() => window.__calls.splice(0));
      assert.deepEqual(calls, [['applyVoltaGasolina', '{"acao":"desligar"}']]);
      assert.match(await page.locator('#toolAdvanced [data-item="volta-liga"]').innerText(), /Desligado/);
      assert.deepEqual(app.problems.page, []);
      assert.deepEqual(app.problems.console, []);
    } finally { await app.context.close(); }
  });
}

test('tela inicial: continua a grade de cartões com o interruptor do anti-apagão direto (1 toque) e ganha o acesso a "Outras configurações"', { skip }, async () => {
  const app = await openTools(VIEWPORTS['1280x720'], 'com dados');
  try {
    const { page } = app;
    assert.equal(await page.locator('#toolAdvanced [data-card="anti"] [data-adv-switch]').count(), 1);
    for (const id of ['valores', 'corte', 'lenta', 'troca', 'injetores', 'volta', 'outras']) assert.equal(await page.locator(`#toolAdvanced [data-adv-open="${id}"]`).count(), 1, id);
    const over = await page.evaluate(() => { const g = document.getElementById('toolAdvanced'); return g.scrollHeight > g.clientHeight + 1 || g.scrollWidth > g.clientWidth + 1; });
    assert.equal(over, false, 'a tela inicial não rola');
  } finally { await app.context.close(); }
});
