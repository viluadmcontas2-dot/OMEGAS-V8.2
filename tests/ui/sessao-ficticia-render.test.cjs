'use strict';
// Sessão fictícia em render real (Chromium): as telas AutoCal e Ajuste GNV alimentadas com os snapshots que o MOTOR
// real produziu (SessaoFicticiaTest.kt exporta o formato da ponte: snapshot nativo + EquivalenceView.build) em 5 momentos:
// antes de aprender, aprendendo, proposta pronta, depois do RESET gasolina, repovoando.
// Também é teste VISUAL por pixel: gráfico com eixos/grade/curva, mesma geometria nos 5 momentos, nunca vazio nem encolhido.
// Regra do dono: reset só limpa a curva/pontos; gráfico, eixos e escala ficam. Classe de prova 4 (parcial: não é o carro).
// Capturas em OMEGAS_SHOTS (padrão: tests/ui/__screens__).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { open, go, playwright } = require('./render/lib.js');

const pw = playwright();
let ok = false;
if (pw) { try { ok = !!require('./render/lib.js').chromiumPath(pw.chromium); } catch (_) { ok = false; } }
const skip = ok ? false : 'Chromium/Playwright indisponível neste ambiente';
const SHOTS = process.env.OMEGAS_SHOTS || path.join(__dirname, '__screens__');
const DIR = process.env.OMEGAS_SESSAO_DIR || path.join(__dirname, 'render/sessao-ficticia');
fs.mkdirSync(SHOTS, { recursive: true });
const MOMENTS = ['1-antes-de-aprender', '2-aprendendo', '3-proposta-pronta', '4-depois-do-reset-gasolina', '5-repovoando'];
const load = name => JSON.parse(fs.readFileSync(path.join(DIR, `${name}.json`), 'utf8'));

async function feed(page, m) {
  await page.evaluate(({ m }) => {
    const SC = { PETR_INJ_TBP: 512, MNFLD_PRESS_THD: 1024, PETR_INJ_TBUF: 512, PETR_INJ_TBUF_GAS: 512, MNFLD_PRESS_BUF: 1024, MNFLD_PRESS_BUF_GAS: 1024, MUL_ACT: 16384 };
    const J = o => JSON.stringify(o);
    const A = window.OmegasAutoCal;
    const base = JSON.parse(A.getUiProjection());
    const fields = base.snapshot.fields.slice();
    for (const f of m.snapshot.fields) {
      const i = fields.findIndex(x => x.key === f.key);
      const nf = { ...(i >= 0 ? fields[i] : {}), key: f.key, status: 'VALID', rawValues: f.rawValues, physicalValues: SC[f.key] ? f.rawValues.map(v => v / SC[f.key]) : f.rawValues.slice(), capturedAtMs: Date.now() };
      if (i >= 0) fields[i] = nf; else fields.push(nf);
    }
    const snap = { ...base.snapshot, fields };
    const zone = k => (fields.find(x => x.key === k) || { rawValues: [] }).rawValues.map(v => v > 0);
    const proj = { ...base, acquisitionZones: { petrol: zone('ACQUIRED_ZONES_PETROL'), gas: zone('ACQUIRED_ZONES_GAS') }, snapshot: snap, nativeSnapshot: snap, nativeStatus: { ...base.nativeStatus, latestSnapshot: snap } };
    A.getUiProjection = () => J(proj);
    A.getNativeMonitorSnapshot = () => J(snap);
    const eq = { ...m.bridge, ok: true, available: true };
    A.getEquivalence = () => J(eq); A.getEquivalenceFresh = () => J(eq); A.getEquivalenceResult = () => J(eq);
    A.getRefinementPhase = () => J({ ok: true, autopilot: eq.autopilot });
    const refined = JSON.parse(A.getRefinedAnalysis());
    const na = eq.nextAction;
    if (na && na.currentRaw) refined.points = na.currentRaw.map((v, i) => ({ index: i, referenceTimeMs: (refined.points[i] || {}).referenceTimeMs, currentRaw: v, calculatedRaw: na.refinedRaw[i], origin: na.refinedRaw[i] !== v ? 'MEASURED' : 'HELD' }));
    else refined.points = (refined.points || []).map(p => ({ ...p, calculatedRaw: p.currentRaw, origin: 'HELD' }));
    A.getRefinedAnalysis = () => J(refined);
  }, { m });
}

// Pixels de tinta do gráfico (fora do fundo) e das cores de curva, lidos da captura real do elemento.
async function ink(page, selector) {
  const el = await page.$(selector);
  const box = await el.boundingBox();
  const buf = await el.screenshot();
  const stats = await page.evaluate(async b64 => {
    const img = new Image(); img.src = 'data:image/png;base64,' + b64; await img.decode();
    const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
    const g = c.getContext('2d'); g.drawImage(img, 0, 0);
    const d = g.getImageData(0, 0, c.width, c.height).data;
    const bg = [d[0], d[1], d[2]]; let non = 0, colored = 0; const rows = new Set(); const cols = new Set();
    for (let i = 0; i < d.length; i += 4) {
      const dist = Math.abs(d[i] - bg[0]) + Math.abs(d[i + 1] - bg[1]) + Math.abs(d[i + 2] - bg[2]);
      if (dist > 30) { non++; const p = i / 4; rows.add(Math.floor(p / c.width)); cols.add(p % c.width); }
      const mx = Math.max(d[i], d[i + 1], d[i + 2]), mn = Math.min(d[i], d[i + 1], d[i + 2]);
      if (mx - mn > 60) colored++;
    }
    return { w: c.width, h: c.height, non, colored, rows: rows.size, cols: cols.size };
  }, buf.toString('base64'));
  return { box, buf, ...stats };
}

const SCREENS = [
  { route: 'autocal', chart: '#autocalReferenceChart', tag: 'autocal' },
  { route: 'refino', chart: '#refinoChart', tag: 'ajuste-gnv' },
];

for (const screen of SCREENS) {
  test(`sessão fictícia · ${screen.tag}: 5 momentos, gráfico com eixos/grade/curva e mesma geometria`, { skip }, async () => {
    const geo = [];
    const lines = [];
    for (const name of MOMENTS) {
      const { browser, page, errors } = await open(pw.chromium, 'connected', { viewport: { width: 1280, height: 720 }, scn: { noStalls: true } });
      try {
        await page.waitForTimeout(1200);
        await feed(page, load(name));
        await go(page, screen.route); await page.waitForTimeout(2600);
        const info = await page.evaluate(({ chart }) => {
          const s = document.querySelector('.screen.active');
          const host = s.querySelector(chart);
          const svg = host && host.querySelector('svg');
          const bar = s.querySelector('.ar-act > .ar-buttons');
          const btns = bar ? [...bar.querySelectorAll('button, summary')].filter(e => e.getBoundingClientRect().width > 0 && !e.closest('[hidden]') && !e.closest('.instrument-detail-content')).map(e => ({ t: e.textContent.trim().replace(/\s+/g, ' '), top: Math.round(e.getBoundingClientRect().top), h: Math.round(e.getBoundingClientRect().height) })) : [];
          const text = [...s.querySelectorAll('#refinoHeadline, .ar-sentence, .ar-status-line, [data-refino-reason], .refino-sentence')].map(e => e.textContent.trim()).filter(Boolean).join(' / ');
          return {
            hasSvg: !!svg, empty: !!(host && host.querySelector('.chart-empty')), gridLines: svg ? svg.querySelectorAll('line').length : 0,
            paths: svg ? [...svg.querySelectorAll('path')].filter(p => (p.getAttribute('d') || '').length > 20).length : 0,
            texts: svg ? svg.querySelectorAll('text').length : 0, btns, text, overflowX: s.scrollWidth > s.clientWidth + 1,
          };
        }, { chart: screen.chart });
        const px = await ink(page, screen.chart);
        await page.screenshot({ path: path.join(SHOTS, `sessao-${screen.tag}-${name}.png`) });
        geo.push({ name, w: Math.round(px.box.width), h: Math.round(px.box.height), x: Math.round(px.box.x), y: Math.round(px.box.y) });
        lines.push(`${screen.tag} ${name}: svg=${info.hasSvg} vazio=${info.empty} linhasGrade=${info.gridLines} curvas=${info.paths} rotulos=${info.texts} tinta=${px.non} colorido=${px.colored} botoes=[${info.btns.map(b => b.t).join(' | ')}] frase="${info.text.slice(0, 160)}"`);
        assert.ok(info.hasSvg && !info.empty, `${name}: gráfico vazio ou ausente`);
        assert.ok(info.gridLines >= 6 && info.texts >= 6, `${name}: faltam eixos/grade (linhas=${info.gridLines}, rótulos=${info.texts})`);
        assert.ok(info.paths >= 1, `${name}: sem nenhuma curva desenhada`);
        assert.ok(px.rows > px.h * 0.5 && px.cols > px.w * 0.5, `${name}: gráfico quase vazio na captura (linhas ${px.rows}/${px.h}, colunas ${px.cols}/${px.w})`);
        assert.ok(px.non > 3000, `${name}: poucos pixels de tinta (${px.non})`);
        assert.ok(px.colored > 300, `${name}: sem curva colorida na captura (${px.colored})`);
        assert.ok(px.box.height >= 330, `${name}: gráfico encolheu (${Math.round(px.box.height)} px)`);
        assert.equal(info.overflowX, false, `${name}: rolagem lateral`);
        const tops = new Set(info.btns.map(b => b.top)), hs = new Set(info.btns.map(b => b.h));
        assert.ok(info.btns.length >= 2, `${name}: rodapé sem botões`);
        assert.equal(tops.size, 1, `${name}: botões fora de uma linha única ${[...tops]}`);
        assert.deepEqual([...hs], [58], `${name}: altura dos botões ${[...hs]}`);
        assert.deepEqual(errors.filter(e => e.startsWith('pageerror')), [], `${name}: erro de página`);
      } finally { await browser.close(); }
    }
    console.log('SESSAO_RENDER\n' + lines.join('\n'));
    // reset só limpa a curva/pontos: gráfico, eixos e escala ficam (mesma caixa nos 5 momentos)
    for (const g of geo) {
      assert.ok(Math.abs(g.w - geo[0].w) <= 1 && Math.abs(g.h - geo[0].h) <= 1 && Math.abs(g.x - geo[0].x) <= 1 && Math.abs(g.y - geo[0].y) <= 1,
        `geometria do gráfico mudou em ${g.name}: ${JSON.stringify(g)} vs ${JSON.stringify(geo[0])}`);
    }
  });
}
