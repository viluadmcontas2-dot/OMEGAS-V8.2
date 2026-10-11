// Trava do dono (2026-10-11): nenhuma das 8 abas rola nem tem sub-rolagem, nada fica cortado, a navegação inferior
// nunca some e não existe botão "Abas". Mede o app real (index.html) no Chromium a 1280x672 (WebView do carro) e
// 1280x720, também nos estados que mais crescem (Ações avançadas do AutoCal, evidência da Curva K). Ponte AutoCal simulada
// só para haver dados; nenhum dado vai para o app.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const assert = require('node:assert/strict');

const INDEX = path.resolve('app/src/main/assets/ui/index.html');
const OUT = 'build/ui-evidence/sem-rolagem';
fs.mkdirSync(OUT, { recursive: true });

function fakeBridge() {
  const n = 30;
  const tbp = Array.from({ length: n }, (_, i) => +(1.5 + i * (10.1 / (n - 1))).toFixed(3));
  const petrolRv = tbp.map((_, i) => +(0.18 + i * 0.028).toFixed(3));
  const gasRv = petrolRv.map((m, i) => +(m * (1.03 + 0.01 * Math.sin(i / 4))).toFixed(3));
  const buf = Array.from({ length: 18 }, (_, i) => +(2 + i * 0.5).toFixed(2));
  const bufMap = buf.map((_, i) => +(0.22 + i * 0.045).toFixed(3));
  const f = (key, v) => ({ key, status: 'VALID', physicalValues: v, rawValues: v });
  const fields = [
    f('PETR_INJ_TBP', tbp), f('PETR_MNFLD_PRESS_RV', petrolRv), f('GAS_MNFLD_PRESS_RV', gasRv),
    f('PETR_INJ_TBUF', buf), f('MNFLD_PRESS_BUF', bufMap), f('NUM_BUF_UPD_PETR', buf.map((_, i) => (i < 15 ? 12 : 4))),
    f('PETR_INJ_TBUF_GAS', buf.map(t => +(t * 1.02).toFixed(2))), f('MNFLD_PRESS_BUF_GAS', bufMap.map(m => +(m * 1.04).toFixed(3))),
    f('NUM_BUF_UPD_GAS', buf.map((_, i) => (i < 11 ? 12 : i < 14 ? 5 : 0))),
    f('CALIBRATION_VAL_1', [0, 0, 10, 0, 0, 8, 0, 0, 10]), f('VECT_AUTOCAL_U8_1', [8]),
    f('MNFLD_PRESS_THD', Array.from({ length: 18 }, (_, i) => +(0.15 + i * 0.055).toFixed(3))),
    f('ACQUIRED_ZONES_GAS', [1, 1, 1, 0]), f('ACQUIRED_ZONES_PETROL', [1, 1, 1, 1]), f('AUTO_CAL_ENABLE', [1]),
  ];
  const snapshot = { available: true, snapshotHash: 'ci-1', fields };
  const projection = { ok: true, source: 'NATIVE', sessionId: 1, referenceUsable: true, snapshot, analysis: {}, nativeStatus: { ok: true, state: 'RUNNING', autoCalEnabled: 1 }, nativeSnapshot: snapshot };
  const dense = k => Array.from({ length: 14 }, (_, i) => ({ tpetMs: +(2.2 + i * 0.55).toFixed(2), mapBar: +((0.24 + i * 0.05) * k).toFixed(3), samples: 20 + i }));
  const equivalence = {
    ok: true, ratio: 1.021, samples: 840, denseBands: { petrol: dense(1), gas: dense(1.035) }, stalls: { count: 0 },
    autopilot: {
      phase: 'PROPOSTA_PRONTA', headline: 'Curva refinada pronta: 12 pontos para ajustar.', next: 'Toque em Revisar e gravar.',
      petrolValid: 18, gasValid: 14, action: { kind: 'review', label: 'Revisar e gravar 12 pontos', enabled: true },
      secondary: [{ kind: 'undo', label: 'Desfazer última gravação', enabled: true }],
      proposal: { known: true, count: 12, signature: 'ci', points: [] }, autoMatchCount: 3, maxAutomatch: 3,
    },
    refinement: { result: null }, restorePoints: [],
  };
  const sessions = Array.from({ length: 8 }, (_, i) => ({ id: 's' + i, createdAt: Date.now() - i * 864e5, durationMs: (20 + i) * 6e4, active: i === 0, reason: 'Sessão MP48', semanticSummary: { autocal: { gasZones: 3, correlatedRegions: [1, 2] } } }));
  const json = v => JSON.stringify(v);
  window.OmegasAutoCal = {
    getIdentity: () => json({ ok: true }), getStatus: () => json({ ok: true, state: 'READY' }),
    getSnapshot: () => json(snapshot), getNativeMonitorStatus: () => json({ ok: true, state: 'RUNNING', autoCalEnabled: 1 }),
    getNativeMonitorSnapshot: () => json(snapshot), getUiProjection: () => json(projection),
    getSessionLedgerStatus: () => json({ ok: true, sessionCount: 8 }), listAutoCalSessions: () => json(sessions),
    getNativeActionStatus: () => json({}), getRefinedAnalysis: () => json({ ok: true, available: true, points: [] }),
    getEquivalence: () => json(equivalence), getRefinementPhase: () => json({ ok: true, phase: 'PROPOSTA_PRONTA' }),
  };
}

// Estados extras que mais crescem, por aba.
const EXTRAS = {
  curve: [['evidência aberta', ['.evidence-disclosure > summary']]],
  autocal: [['Ações avançadas', ['.autocal-reset-menu > summary']]],
};

function measure() {
  const screen = document.querySelector('.screen.active');
  const visible = e => { const cs = getComputedStyle(e); if (cs.display === 'none' || cs.visibility === 'hidden') return false; const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const name = e => e.tagName + (e.id ? '#' + e.id : '') + '.' + String(e.className?.baseVal ?? e.className).slice(0, 36);
  const problems = [];
  if (!screen) return { problems: ['nenhuma tela ativa'] };
  for (const e of [screen, ...screen.querySelectorAll('*')]) {
    if (!visible(e) || e.closest('details:not([open]) > :not(summary)')) continue;
    const cs = getComputedStyle(e);
    if (/(auto|scroll)/.test(cs.overflowY) && e.scrollHeight - e.clientHeight > 1) problems.push('rola: ' + name(e) + ' +' + (e.scrollHeight - e.clientHeight) + 'px');
    if (/(auto|scroll)/.test(cs.overflowX) && e.scrollWidth - e.clientWidth > 1) problems.push('rola na horizontal: ' + name(e));
  }
  const sb = screen.getBoundingClientRect();
  for (const e of screen.querySelectorAll('button, select, input, h2, h3, h4, p, b, span, small, li, dd, dt')) {
    if (!visible(e) || e.closest('.operation-layer, [hidden], details:not([open]) > :not(summary), option')) continue;
    if (!(e.textContent || '').trim() && !/^(BUTTON|SELECT|INPUT)$/.test(e.tagName)) continue; // decoração sem texto
    const r = e.getBoundingClientRect();
    let cut = r.bottom > sb.bottom + 1 || r.right > sb.right + 1;
    for (let a = e.parentElement; !cut && a && a !== screen.parentElement; a = a.parentElement) {
      const acs = getComputedStyle(a);
      if (/(hidden|clip)/.test(acs.overflowY + acs.overflowX) && acs.display !== 'contents') {
        const ar = a.getBoundingClientRect();
        cut = r.bottom > ar.bottom + 1 || r.right > ar.right + 1 || r.top < ar.top - 1;
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
  if (navButtons !== 8) problems.push('navegação com ' + navButtons + ' botões');
  if ([...document.querySelectorAll('button, summary')].some(e => visible(e) && /\bAbas\b/.test(e.textContent || ''))) problems.push('botão "Abas"');
  return { problems: problems.slice(0, 10), total: problems.length };
}

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const failures = [];
  try {
    for (const height of [672, 720]) {
      const page = await browser.newPage({ viewport: { width: 1280, height } });
      const errors = [];
      page.on('pageerror', e => errors.push(String(e.message || e)));
      await page.addInitScript(fakeBridge);
      await page.goto('file://' + INDEX, { waitUntil: 'load' });
      await page.waitForTimeout(1500);
      const routes = await page.$$eval('.side-nav button[data-route]', bs => bs.map(b => b.dataset.route));
      assert.equal(routes.length, 8, 'a navegação tem as 8 abas');
      for (const route of routes) {
        for (const [label, clicks] of [['', []], ...(EXTRAS[route] || [])]) {
          await page.locator(`.side-nav button[data-route="${route}"]`).click();
          await page.waitForTimeout(900);
          for (const selector of clicks) {
            await page.locator(selector).locator('visible=true').first().click({ timeout: 2000 });
            await page.waitForTimeout(300);
          }
          const result = await page.evaluate(measure);
          const id = `${route}${label ? ' (' + label + ')' : ''}@${height}`;
          console.log(id, result.problems.length ? 'PROBLEMA ' + result.problems.join(' | ') : 'OK');
          if (result.problems.length) failures.push(id);
          await page.screenshot({ path: path.join(OUT, `${route}${label ? '-' + label.replace(/\W+/g, '-') : ''}-${height}.png`) });
          // Volta ao estado normal (fecha o que abriu).
          if (clicks.length) await page.reload({ waitUntil: 'load' }).then(() => page.waitForTimeout(1200));
        }
      }
      if (errors.length) failures.push(`erros de página @${height}: ${errors.slice(0, 3).join(' | ')}`);
      await page.close();
    }
  } finally {
    await browser.close();
  }
  assert.deepEqual(failures, [], 'nenhuma aba pode rolar, cortar conteúdo, esconder a navegação ou ter "Abas"');
  console.log('SEM_ROLAGEM_8_ABAS=PASS');
})().catch(error => { console.error(error); process.exitCode = 1; });
