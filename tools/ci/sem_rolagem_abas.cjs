// Trava do dono (2026-10-11): nenhuma das abas rola nem tem sub-rolagem, nada fica cortado, a navegação inferior
// nunca some e não existe botão "Abas". Mede o app real (index.html) no Chromium a 1280x672 (WebView do carro) e
// 1280x720, também nos estados que mais crescem (Ações avançadas e ponto tocado do AutoCal). Ponte AutoCal simulada
// só para haver dados; nenhum dado vai para o app.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const assert = require('node:assert/strict');

const INDEX = path.resolve('app/src/main/assets/ui/index.html');
const OUT = 'build/ui-evidence/sem-rolagem';
fs.mkdirSync(OUT, { recursive: true });

const { fakeBridge } = require('./_ponte_falsa.cjs');

// Estados extras que mais crescem, por aba: [nome, toques, seletores que precisam ficar visíveis e inteiros].
// Toque { dispatch } entrega o clique direto ao elemento (bolinha do gráfico SVG, que o AGORA pode cobrir).
const EXTRAS = {
  // Ferramentas (cartões por assunto, sem abas): página 2, cartões abertos e o registro também não rolam nem cortam.
  tools: [
    ['página 2', ['#toolsPageNext']],
    ['registro do app', ['#toolsPageNext', '[data-tool-open-log]']],
    ['anti-apagão: valores', ['[data-adv-open="valores"]']],
    ['cartão de leitura', ['[data-adv-open="troca"]']],
  ],
  autocal: [
    ['Ações avançadas', ['.autocal-reset-menu > summary']],
    // Dono (2026-10-11): ao tocar num ponto, "Reaprender este ponto" aparece inteiro (antes ia para uma 2ª linha cortada).
    ['ponto tocado', [{ dispatch: '[data-autocal-acquired-fuel="GAS"][data-autocal-acquired-index="0"]' }],
      ['#autocalChartInspector [data-autocal-reacquire-point]', '#autocalChartInspector [data-autocal-toggle-point-selection]']],
  ],
};

/** Seletor obrigatório: existe, está visível e cabe inteiro na tela e em todo ancestral que corta (overflow). */
function requiredVisible(selector) {
  const e = document.querySelector(selector);
  if (!e) return 'ausente: ' + selector;
  const r = e.getBoundingClientRect();
  const cs = getComputedStyle(e);
  if (cs.display === 'none' || cs.visibility === 'hidden' || r.width < 40 || r.height < 40) return 'invisível: ' + selector;
  if (r.bottom > innerHeight || r.right > innerWidth || r.top < 0 || r.left < 0) return 'fora da tela: ' + selector;
  for (let a = e.parentElement; a; a = a.parentElement) {
    const acs = getComputedStyle(a);
    if (!/(hidden|clip)/.test(acs.overflowY + acs.overflowX) || acs.display === 'contents') continue;
    const ar = a.getBoundingClientRect();
    if (r.bottom > ar.bottom + 1 || r.right > ar.right + 1 || r.top < ar.top - 1 || r.left < ar.left - 1) return 'cortado: ' + selector;
  }
  return null;
}

function measure() {
  const screen = document.querySelector('.screen.active');
  const visible = e => { const cs = getComputedStyle(e); if (cs.display === 'none' || cs.visibility === 'hidden') return false; const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const name = e => e.tagName + (e.id ? '#' + e.id : '') + '.' + String(e.className?.baseVal ?? e.className).slice(0, 36);
  const problems = [];
  if (!screen) return { problems: ['nenhuma tela ativa'] };
  for (const e of [screen, ...screen.querySelectorAll('*')]) {
    if (!visible(e) || e.closest('details:not([open]) > :not(summary)')) continue;
    const cs = getComputedStyle(e);
    // Exceção do dono (2026-10-20): a grade da página de grupo de Ferramentas rola na VERTICAL (nunca na horizontal).
    if (/(auto|scroll)/.test(cs.overflowY) && e.scrollHeight - e.clientHeight > 1 && !e.matches('.grp-grid')) problems.push('rola: ' + name(e) + ' +' + (e.scrollHeight - e.clientHeight) + 'px');
    if (/(auto|scroll)/.test(cs.overflowX) && e.scrollWidth - e.clientWidth > 1) problems.push('rola na horizontal: ' + name(e));
  }
  const sb = screen.getBoundingClientRect();
  for (const e of screen.querySelectorAll('button, select, input, h2, h3, h4, p, b, span, small, li, dd, dt')) {
    if (!visible(e) || e.closest('.operation-layer, [hidden], details:not([open]) > :not(summary), option')) continue;
    if (!(e.textContent || '').trim() && !/^(BUTTON|SELECT|INPUT)$/.test(e.tagName)) continue; // decoração sem texto
    const r = e.getBoundingClientRect();
    let cut = (!e.closest('.grp-grid') && r.bottom > sb.bottom + 1) || r.right > sb.right + 1;
    for (let a = e.parentElement; !cut && a && a !== screen.parentElement; a = a.parentElement) {
      const acs = getComputedStyle(a);
      if (/(hidden|clip)/.test(acs.overflowY + acs.overflowX) && acs.display !== 'contents') {
        const ar = a.getBoundingClientRect();
        const rolaNaGrade = !!e.closest('.grp-grid') && (a.matches('.grp-grid') || a.contains(e.closest('.grp-grid')));
        cut = r.right > ar.right + 1 || (!rolaNaGrade && (r.bottom > ar.bottom + 1 || r.top < ar.top - 1));
      }
    }
    if (cut) problems.push('cortado: ' + name(e) + ' "' + (e.textContent || '').trim().slice(0, 30) + '"');
  }
  const doc = document.documentElement;
  if (doc.scrollHeight > innerHeight + 1 || doc.scrollWidth > innerWidth + 1) problems.push('documento rola');
  const rail = document.querySelector('.side-rail');
  const rr = rail?.getBoundingClientRect();
  if (!rail || !visible(rail) || rr.bottom > innerHeight + 1 || rr.height < 60) problems.push('navegação inferior não visível');
  const navButtons = [...document.querySelectorAll('.side-nav button')].filter(visible).length;
  if (navButtons !== 6) problems.push('navegação com ' + navButtons + ' botões');
  // Barra do topo persistente: visível em TODAS as abas, com as 4 leituras inteiras e centralizadas (dono, 2026-10-13).
  const bar = document.getElementById('liveBar');
  const br = bar?.getBoundingClientRect();
  if (!bar || !visible(bar) || br.top < 0 || br.height < 30) problems.push('barra do topo ausente');
  else {
    const cells = [...bar.querySelectorAll('.live-cell')];
    if (cells.length !== 4 || cells.some(c => !visible(c) || c.getBoundingClientRect().right > innerWidth)) problems.push('barra do topo incompleta');
    else {
      const first = cells[0].getBoundingClientRect();
      const last = cells[cells.length - 1].getBoundingClientRect();
      if (Math.abs(first.left - (innerWidth - last.right)) > 4) problems.push('barra do topo descentralizada');
    }
  }
  if ([...document.querySelectorAll('button, summary')].some(e => visible(e) && /\bAbas\b/.test(e.textContent || ''))) problems.push('botão "Abas"');
  return { problems: problems.slice(0, 10), total: problems.length };
}

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const failures = [];
  try {
    for (const [width, height] of [[1280, 672], [1280, 720], [1024, 600], [800, 480]]) {
      const page = await browser.newPage({ viewport: { width, height } });
      const errors = [];
      page.on('pageerror', e => errors.push(String(e.message || e)));
      await page.addInitScript(fakeBridge);
      await page.goto('file://' + INDEX, { waitUntil: 'load' });
      await page.waitForTimeout(1500);
      const routes = await page.$$eval('.side-nav button[data-route]', bs => bs.map(b => b.dataset.route));
      assert.equal(routes.length, 5, 'a navegação principal tem cinco áreas');
      for (const route of routes) {
        for (const [label, clicks, required = []] of [['', []], ...(EXTRAS[route] || [])]) {
          await page.locator(`.side-nav button[data-route="${route}"]`).click();
          await page.waitForTimeout(900);
          for (const selector of clicks) {
            if (selector.dispatch) await page.locator(selector.dispatch).first().dispatchEvent('click', {}, { timeout: 2000 });
            else await page.locator(selector).locator('visible=true').first().click({ timeout: 2000 });
            await page.waitForTimeout(300);
          }
          const result = await page.evaluate(measure);
          for (const selector of required) {
            const problem = await page.evaluate(requiredVisible, selector);
            if (problem) result.problems.push(problem);
          }
          const id = `${route}${label ? ' (' + label + ')' : ''}@${width}x${height}`;
          // Todas as áreas são avaliadas nas três resoluções de multimídia.
          const dura = true;
          console.log(id, result.problems.length ? (dura ? 'PROBLEMA ' : 'diagnóstico (não redesenhada) ') + result.problems.join(' | ') : 'OK');
          if (result.problems.length && dura) failures.push(id);
          await page.screenshot({ path: path.join(OUT, `${route}${label ? '-' + label.replace(/\W+/g, '-') : ''}-${width}x${height}.png`) });
          // Volta ao estado normal (fecha o que abriu).
          if (clicks.length) await page.reload({ waitUntil: 'load' }).then(() => page.waitForTimeout(1200));
        }
      }
      await page.locator('.side-nav button[data-route="tools"]').click(); await page.waitForTimeout(500);
      await page.locator('#toolsPageNext').click(); await page.waitForTimeout(300);
      await page.locator('[data-tool-open-sessions]').click(); await page.waitForTimeout(900);
      const sessionResult = await page.evaluate(measure);
      const sessionId = `sessions@${width}x${height}`;
      if (sessionResult.problems.length) failures.push(sessionId);
      console.log(sessionId, sessionResult.problems.length ? 'PROBLEMA ' + sessionResult.problems.join(' | ') : 'OK');
      await page.screenshot({ path: path.join(OUT, `sessions-${width}x${height}.png`) });
      await page.locator('.diagnostic-settings > summary').click(); await page.waitForTimeout(300);
      const sessionSettings = await page.evaluate(measure);
      const sessionSettingsId = `sessions (tamanho das gravações)@${width}x${height}`;
      if (sessionSettings.problems.length) failures.push(sessionSettingsId);
      console.log(sessionSettingsId, sessionSettings.problems.length ? 'PROBLEMA ' + sessionSettings.problems.join(' | ') : 'OK');
      await page.screenshot({ path: path.join(OUT, `sessions-settings-${width}x${height}.png`) });
      await page.locator('[data-session-back]').click(); await page.waitForTimeout(300);
      assert.equal(await page.locator('#toolsDrawer').getAttribute('data-page'), '2', 'retorno mantém a página de Ferramentas');
      if (errors.length) failures.push(`erros de página @${width}x${height}: ${errors.slice(0, 3).join(' | ')}`);
      await page.close();
    }
  } finally {
    await browser.close();
  }
  assert.deepEqual(failures, [], 'nenhuma aba pode rolar, cortar conteúdo, esconder a navegação ou ter "Abas"');
  console.log('SEM_ROLAGEM_ABAS=PASS');
})().catch(error => { console.error(error); process.exitCode = 1; });
