'use strict';
// Lote F · render real em 1280×720 (Chromium + Playwright). Pula sozinho quando o Chromium não está disponível.
// Classe de prova 4 (parcial): ponte falsa com snapshot e telemetria REAIS (fixtures/autocal/real); nada de ECU.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { open, go, playwright } = require('./render/lib.js');

const pw = playwright();
let browserOk = false;
if (pw) {
  try { browserOk = fs.existsSync(pw.chromium.executablePath()); } catch (_) { browserOk = false; }
}
const skip = browserOk ? false : 'Chromium/Playwright indisponível neste ambiente';
const TABS = ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools', 'diagnostico'];

async function audit(page, route) {
  return page.evaluate(r => {
    const W = 1280, H = 720;
    const vis = e => { const cs = getComputedStyle(e); const b = e.getBoundingClientRect(); return cs.visibility !== 'hidden' && cs.display !== 'none' && +cs.opacity > 0 && b.width > 0 && b.height > 0; };
    const sel = e => e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (e.className && typeof e.className === 'string' ? '.' + e.className.split(' ')[0] : '');
    const sc = document.querySelector(`[data-screen="${r}"]`);
    const out = { overflowX: [], smallTargets: [], smallText: [], hitCircles: [] };
    sc.querySelectorAll('*').forEach(e => { if (!vis(e) || (e.closest('svg') && e.tagName !== 'svg')) return; const b = e.getBoundingClientRect(); if (b.right > W + 0.5 || b.left < -0.5) out.overflowX.push(sel(e)); });
    sc.querySelectorAll('button,input:not([type=hidden]),select,textarea,summary,[role=button]').forEach(e => {
      if (!vis(e) || e.tagName === 'circle') return;
      const b = e.getBoundingClientRect(); if (b.bottom < 0 || b.top > H) return;
      const grid = e.classList.contains('map-k-cell') || e.classList.contains('map-axis-header');
      const min = grid ? 44 : (r === "autocal" ? 52 : 58);
      if (b.height < min - 0.5 || b.width < min - 0.5) out.smallTargets.push(`${sel(e)} ${Math.round(b.width)}x${Math.round(b.height)}`);
    });
    sc.querySelectorAll('circle[class*="hit"]').forEach(e => { const b = e.getBoundingClientRect(); if (b.width > 0) out.hitCircles.push(Math.round(b.width)); });
    const walker = document.createTreeWalker(sc, NodeFilter.SHOW_TEXT);
    let n; while ((n = walker.nextNode())) {
      const t = n.textContent.trim(); if (!t) continue; const e = n.parentElement; if (!vis(e)) continue;
      const b = e.getBoundingClientRect(); if (b.bottom < 0 || b.top > H) continue;
      const f = parseFloat(getComputedStyle(e).fontSize);
      if (f < 16 - 0.1) out.smallText.push(`${sel(e)} ${f}px "${t.slice(0, 16)}"`);
    }
    const head = document.querySelector('.workspace-head').getBoundingClientRect();
    out.head = { top: Math.round(head.top), height: Math.round(head.height), hidden: head.height === 0 };
    const host = sc.querySelector('.autocal-cockpit-view') || sc;
    out.scrollsY = sc.scrollHeight > sc.clientHeight + 1 || host.scrollHeight > host.clientHeight + 1;
    return out;
  }, route);
}

test('render: 7 abas sem corte lateral, alvos >= 58 px (AutoCal 52) (grade do Mapa K >= 44), texto >= 16 px e cabeçalho inteiro', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected');
  try {
    await page.waitForTimeout(1500);
    for (const route of TABS) {
      await go(page, route);
      await page.waitForTimeout(route === 'map' || route === 'curve' ? 3500 : 2500);
      const result = await audit(page, route);
      assert.deepEqual(result.overflowX, [], `${route}: corte lateral`);
      assert.deepEqual(result.smallTargets, [], `${route}: alvo pequeno`);
      assert.deepEqual(result.smallText, [], `${route}: texto < 16 px`);
      for (const w of result.hitCircles) assert.ok(w >= 44, `${route}: área de toque do gráfico ${w}px < 44`);
      if (!result.head.hidden) {
        assert.ok(result.head.height >= 40, `${route}: faixa de condição com altura própria (${result.head.height})`);
        assert.ok(result.head.top >= 0 && result.head.height <= 80, `${route}: faixa de condição cabe (${result.head.height})`);
      }
      if (['dashboard', 'map', 'curve', 'refino'].includes(route)) assert.equal(result.scrollsY, false, `${route}: sem rolagem vertical em 1280×720`);
    }
  } finally { await browser.close(); }
});

test('render: Agora tem 4 valores de peso parecido que preenchem a tela (razão de área <= 1,5; cobertura >= 85%)', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected');
  try {
    await page.waitForTimeout(2500);
    const m = await page.evaluate(() => {
      const tiles = [...document.querySelectorAll('.now-tile')].map(e => { const b = e.getBoundingClientRect(); return { area: b.width * b.height, left: b.left, top: b.top, right: b.right, bottom: b.bottom, font: parseFloat(getComputedStyle(e.querySelector('b')).fontSize), label: parseFloat(getComputedStyle(e.querySelector('small')).fontSize) }; });
      const host = document.querySelector('.screen-host').getBoundingClientRect();
      const box = { l: Math.min(...tiles.map(t => t.left)), t: Math.min(...tiles.map(t => t.top)), r: Math.max(...tiles.map(t => t.right)), b: Math.max(...tiles.map(t => t.bottom)) };
      const quiet = document.querySelector('.now-quiet-row').getBoundingClientRect();
      return { tiles, hostArea: host.width * host.height, boxArea: (box.r - box.l) * (box.b - box.t), quietArea: quiet.width * quiet.height };
    });
    assert.equal(m.tiles.length, 4);
    const areas = m.tiles.map(t => t.area);
    assert.ok(Math.max(...areas) / Math.min(...areas) <= 1.5, `razão de área ${Math.max(...areas) / Math.min(...areas)}`);
    assert.ok((m.boxArea + m.quietArea) / m.hostArea >= 0.85, `cobertura ${(m.boxArea + m.quietArea) / m.hostArea}`);
    for (const t of m.tiles) { assert.ok(t.font >= 88, `valor ${t.font}px < 88 (combustível usa clamp 48–96 px para a palavra caber)`); assert.ok(t.label >= 28, `rótulo ${t.label}px < 28`); }
    assert.equal(await page.$('#dashEquivalence'), null, 'o cartão de equivalência saiu do Agora');
    const texts = await page.$$eval('[data-screen="dashboard"] *', nodes => nodes.map(n => n.textContent).join(' '));
    assert.doesNotMatch(texts, /Ir para Refino|PRÓXIMA AÇÃO|provisório/);
  } finally { await browser.close(); }
});

test('render: Refino sem rolagem; título, legenda e desenho não se sobrepõem; faixa de equivalência pequena', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected', { scn: { phase: 'PROPOSTA_PRONTA' } });
  try {
    await page.waitForTimeout(1500);
    await go(page, 'refino');
    await page.waitForTimeout(3500);
    const m = await page.evaluate(() => {
      const R = e => { const b = e.getBoundingClientRect(); return { l: b.left, t: b.top, r: b.right, b: b.bottom, h: b.height }; };
      const title = R(document.querySelector('.refino-chart-head .autocal-plot-title'));
      const legend = R(document.getElementById('refinoLegend'));
      const plot = R(document.querySelector('#refinoChart svg'));
      const strip = R(document.getElementById('refinoEq'));
      const metric = R(document.querySelector('.refino-cockpit .autocal-focus-metric'));
      const cockpit = document.querySelector('.refino-cockpit');
      const intersects = (a, b) => !(a.r <= b.l || b.r <= a.l || a.b <= b.t || b.b <= a.t);
      return {
        titleLegend: intersects(title, legend), titlePlot: intersects(title, plot), legendPlot: intersects(legend, plot),
        scrolls: cockpit.scrollHeight > cockpit.clientHeight + 1, stripH: strip.h, metricH: metric.h,
        shared: window.OmegasUi.CurveChart.shared.renders, mode: window.OmegasUi.CurveChart.shared.mode,
        legendText: document.getElementById('refinoLegend').textContent,
        headline: document.getElementById('refinoHeadline').textContent, strip: document.getElementById('refinoEq').textContent,
      };
    });
    assert.equal(m.titleLegend, false, 'título e legenda sobrepostos');
    assert.equal(m.titlePlot, false);
    assert.equal(m.legendPlot, false);
    assert.equal(m.scrolls, false, 'Refino rola na vertical');
    assert.equal(m.mode, 'between');
    for (const label of ['Curva da gasolina', 'Curva do GNV hoje', 'O que medimos', 'Proposta', 'Agora', 'Faixas da ECU']) assert.ok(m.legendText.includes(label), label);
    assert.ok(m.stripH <= 100, `faixa de equivalência discreta (${m.stripH}px)`);
    // Uma fala só: o texto do cérebro aparece uma vez dentro da faixa
    assert.ok(m.strip.includes(m.headline));
    // O gráfico não é redesenhado a cada leitura: depois de assentar, o contador de desenhos não anda em 4 s.
    const before = await page.evaluate(() => window.OmegasUi.CurveChart.shared.renders);
    await page.waitForTimeout(4000);
    const after = await page.evaluate(() => window.OmegasUi.CurveChart.shared.renders);
    assert.equal(after - before, 0, 'evidência igual: zero redesenhos em 4 s');
  } finally { await browser.close(); }
});

test('render: Conectando… (permissão USB) e Sem cabo são estados diferentes, cada um com a sua próxima ação', { skip }, async () => {
  const seen = {};
  for (const mode of ['offline', 'connecting']) {
    const { browser, page } = await open(pw.chromium, mode);
    try {
      await page.waitForTimeout(2200);
      seen[mode] = { rail: await page.$eval('#globalEcu', e => e.textContent), health: await page.$eval('#dashHealth', e => e.innerText.replace(/\n/g, ' | ')) };
    } finally { await browser.close(); }
  }
  assert.equal(seen.offline.rail, 'Sem cabo');
  assert.match(seen.offline.health, /Conecte o cabo USB/);
  assert.equal(seen.connecting.rail, 'Conectando…');
  assert.match(seen.connecting.health, /Permitir/);
});
