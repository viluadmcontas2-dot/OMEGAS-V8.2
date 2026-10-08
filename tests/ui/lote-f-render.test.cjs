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
  try { browserOk = !!require("./render/lib.js").chromiumPath(pw.chromium); } catch (_) { browserOk = false; }
}
const skip = browserOk ? false : 'Chromium/Playwright indisponível neste ambiente';
const TABS = ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools', 'diagnostico'];

async function audit(page, route) {
  return page.evaluate(r => {
    const W = 1280, H = 720;
    // Atualizado de propósito (07/10): o conteúdo de um <details> fechado (menu Fotos da Curva K etc.) não está na tela; só o <summary> conta.
    const inClosed = e => { const d = e.closest('details:not([open])'); return !!d && !e.closest('summary'); };
    const vis = e => { if (inClosed(e)) return false; const cs = getComputedStyle(e); const b = e.getBoundingClientRect(); return cs.visibility !== 'hidden' && cs.display !== 'none' && +cs.opacity > 0 && b.width > 0 && b.height > 0; };
    const sel = e => e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (e.className && typeof e.className === 'string' ? '.' + e.className.split(' ')[0] : '');
    const sc = document.querySelector(`[data-screen="${r}"]`);
    const out = { overflowX: [], smallTargets: [], smallText: [], hitCircles: [] };
    sc.querySelectorAll('*').forEach(e => { if (!vis(e) || (e.closest('svg') && e.tagName !== 'svg')) return; const b = e.getBoundingClientRect(); if (b.right > W + 0.5 || b.left < -0.5) out.overflowX.push(sel(e)); });
    sc.querySelectorAll('button,input:not([type=hidden]),select,textarea,summary,[role=button]').forEach(e => {
      if (!vis(e) || e.tagName === 'circle') return;
      const b = e.getBoundingClientRect(); if (b.bottom < 0 || b.top > H) return;
      const grid = e.classList.contains('map-k-cell') || e.classList.contains('map-axis-header');
      // Atualizado de propósito (07/10, rodada 2): no rodapé único do Mapa K os botões de passo (%, −5, −1, +1, +5) têm 58 px de altura
      // e 48–52 px de largura para a barra caber numa linha (layout travado pelo dono); a altura de 58 px continua obrigatória.
      const step = r === 'map' && !!e.closest('.map-bar');
      // Atualizado de propósito: bolhas de região do gráfico do Diagnóstico (<g role=button> dentro do SVG) seguem a regra de área de toque do gráfico (>= 44 px), como os círculos de acerto.
      const min = (grid || !!e.closest('svg')) ? 44 : (r === "autocal" ? 52 : 58);
      const minW = step ? 44 : min, minH = step ? 58 : min;
      if (b.height < minH - 0.5 || b.width < minW - 0.5) out.smallTargets.push(`${sel(e)} ${Math.round(b.width)}x${Math.round(b.height)}`);
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

test('render: 8 abas sem corte lateral, alvos >= 58 px (AutoCal 52) (grade do Mapa K >= 44), texto >= 16 px e cabeçalho inteiro', { skip }, async () => {
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
      if (['dashboard', 'refino'].includes(route)) assert.equal(result.scrollsY, false, `${route}: sem rolagem vertical em 1280×720`);
    }
  } finally { await browser.close(); }
});

// Atualizado de propósito (07/10, rodada 2): a Agora já não tem .now-overview; tem a intenção (.now-intention) no topo, a cobertura
// (.now-coverage) e a linha quieta. Os 7 dados vivos seguem na faixa única do topo.
test('render: menu Fotos da Curva K, aberto, fica inteiro dentro da tela e acima do rodapé (bug real corrigido em 07/10: abria cortado à direita)', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected');
  try {
    await page.waitForTimeout(1500); await go(page, 'curve'); await page.waitForTimeout(3500);
    await page.click('.curve-photos > summary');
    await page.waitForTimeout(300);
    const m = await page.evaluate(() => { const b = document.querySelector('.curve-photos-menu').getBoundingClientRect(); const f = document.querySelector('.curve-photos > summary').getBoundingClientRect(); return { l: b.left, r: b.right, t: b.top, b: b.bottom, footTop: f.top }; });
    assert.ok(m.l >= 0 && m.r <= 1280, `menu Fotos fora da tela: ${Math.round(m.l)}–${Math.round(m.r)}`);
    assert.ok(m.t >= 0 && m.b <= m.footTop, 'menu Fotos não cobre o rodapé');
  } finally { await browser.close(); }
});

test('render: Agora apresenta resultado e intenção, com dados vivos únicos no cabeçalho', { skip }, async () => {
  const { browser,page }=await open(pw.chromium,'connected');
  try {
    await page.waitForTimeout(2500);
    const m=await page.evaluate(()=>{
      const sc=document.querySelector('[data-screen="dashboard"]'), header=document.querySelector('.workspace-head');
      const rect=e=>{const b=e.getBoundingClientRect();return {h:b.height,b:b.bottom,w:b.width};};
      return {duplicates:sc.querySelectorAll('.now-tile').length,facts:header.querySelectorAll('[data-vehicle-fact]').length,
       intention:rect(sc.querySelector('.now-intention')),coverage:rect(sc.querySelector('.now-coverage')),next:rect(sc.querySelector('[data-dash-refino]')),eq:!!sc.querySelector('#dashEquivalence')};
    });
    assert.equal(m.duplicates,0); assert.equal(m.facts,7); assert.ok(m.eq);
    assert.ok(m.intention.h>=58&&m.intention.b<=m.coverage.b); assert.ok(m.coverage.h>=120); assert.ok(m.next.h>=58&&m.next.b<=644);
  } finally {await browser.close();}
});

test('render: Refino na anatomia única — intenção primeiro e gráfico ≥ 50% da altura, legenda/gráfico/frase sem sobreposição, uma ação', { skip }, async () => {
  const { browser, page } = await open(pw.chromium, 'connected', { scn: { phase: 'PROPOSTA_PRONTA' } });
  try {
    await page.waitForTimeout(1500);
    await go(page, 'refino');
    await page.waitForTimeout(3500);
    const m = await page.evaluate(() => {
      const R = e => { const b = e.getBoundingClientRect(); return { l: b.left, t: b.top, r: b.right, b: b.bottom, h: b.height, w: b.width }; };
      const hasHeader = !!document.querySelector('.refino-cockpit .ar-status');
      const legend = R(document.getElementById('refinoLegend'));
      const plot = R(document.querySelector('#refinoChart svg'));
      const sentence = R(document.getElementById('refinoHeadline'));
      const primary = R(document.querySelector('[data-refino-primary]'));
      const intersects = (a, b) => !(a.r <= b.l || b.r <= a.l || a.b <= b.t || b.b <= a.t);
      return {
        legendPlot: intersects(legend, plot), plotSentence: intersects(plot, sentence), sentencePrimary: intersects(sentence, primary),
        order: !hasHeader && sentence.t >= plot.b && sentence.b <= primary.t,
        plotH: plot.h, view: window.innerHeight, primaryBottom: primary.b,
        shared: window.OmegasUi.CurveChart.shared.renders, mode: window.OmegasUi.CurveChart.shared.mode,
        legendText: document.getElementById('refinoLegend').textContent,
        oldCards: document.querySelectorAll('#refinoEq, #refinoSteps, #refinoJournal, #refinoTech, [data-refino-dismiss]').length,
        ours: document.querySelectorAll('#refinoChart .chart-between').length, triangles: document.querySelectorAll('#refinoChart .chart-ours').length,
        hScroll: document.querySelector('[data-screen="refino"]').scrollWidth > document.querySelector('[data-screen="refino"]').clientWidth + 1,
      };
    });
    assert.equal(m.legendPlot, false, 'legenda e desenho sobrepostos');
    assert.equal(m.plotSentence, false);
    assert.equal(m.sentencePrimary, false);
    assert.equal(m.order, true, 'sem cabeçalho: gráfico → frase de estado abaixo do gráfico → rodapé único (atualizado de propósito, 07/10)');
    assert.ok(m.plotH >= m.view * 0.5, `gráfico ocupa ${m.plotH}px de ${m.view}px (≥ 50%)`);
    assert.ok(m.primaryBottom <= m.view, 'a ação primária cabe na primeira tela, sem rolar');
    assert.equal(m.hScroll, false, 'nunca rolagem horizontal');
    assert.equal(m.mode, 'between');
    for (const label of ['Curva da gasolina', 'Curva do GNV hoje', 'Agora', 'Pontos da ECU', 'Pontos do OMEGAS']) assert.ok(m.legendText.includes(label), label);
    assert.equal(m.oldCards, 0, 'sem faixa de %, passos, último resultado, detalhes técnicos nem Entendi');
    assert.ok(m.ours > 0, 'nossos pontos aparecem como bolinhas'); assert.equal(m.triangles, 0);
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
