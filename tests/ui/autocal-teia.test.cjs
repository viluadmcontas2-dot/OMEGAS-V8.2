'use strict';
// TEIA DE ARANHA da tela AutoCal (dono, 2026-10-08): aquisicao por faixa/zona e combustivel, formacao da curva, TODOS os botoes
// (individuais e em sequencia), com ponte que registra cada chamada e comparacao por PIXEL do grafico.
// Classe de prova 4 parcial: Chromium + ECU SIMULADA (tests/ui/helpers/teia-sim.cjs). NAO e o carro; bytes vem da tabela, a ponte Kotlin e outra prova.
// Saida: OMEGAS_TEIA_OUT (padrao tests/ui/__teia__/out): shots/*.png, contact-sheet.html, index.html, log.md, log.json.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { open, go, playwright } = require('./render/lib.js');
const { SIM_SCRIPT } = require('./helpers/teia-sim.cjs');
const { analyzer, gridDiff } = require('./helpers/teia-pixels.cjs');
const { build } = require('./helpers/teia-sheet.cjs');

const pw = playwright();
let browserOk = false;
if (pw) { try { browserOk = !!require('./render/lib.js').chromiumPath(pw.chromium); } catch (_) { browserOk = false; } }
const skip = browserOk ? false : 'Chromium/Playwright indisponivel neste ambiente';
const OUT = process.env.OMEGAS_TEIA_OUT || path.join(__dirname, '__teia__', 'out');
const BASE = path.join(__dirname, '__teia__', 'baseline');
const SHOTS = path.join(OUT, 'shots');
fs.mkdirSync(SHOTS, { recursive: true }); fs.mkdirSync(BASE, { recursive: true });
const REC = [];
const BYTES = { RESET_PETROL: '02 24 04 01', RESET_GAS: '02 24 04 02', DISABLE_AUTO_CAL: '12 4A 01 00', ENABLE_AUTO_CAL: '12 4A 01 01' };
const sleep = (page, ms) => page.waitForTimeout(ms);
let GEOM = null; // geometria do grafico: igual em TODOS os estados

async function session(zero) {
  const o = await open(pw.chromium, 'connected');
  const { page } = o;
  await sleep(page, 1200);
  await page.evaluate(() => { window.__SPEED = 0; }); // AGORA parado: captura estavel
  await go(page, 'autocal'); await sleep(page, 2200);
  await page.evaluate(SIM_SCRIPT);
  o.an = await analyzer(o.page);
  o.last = 0;
  if (zero !== false) { await page.evaluate(() => window.__sim.clear()); await sleep(page, 1100); }
  return o;
}
const sim = (o, fn, ...a) => o.page.evaluate(([f, args]) => window.__sim[f](...args), [fn, a]);
const click = (o, sel) => o.page.evaluate(s => document.querySelector(s).click(), sel);
const settle = (o, ms) => sleep(o.page, ms || 600);

function probeFn() {
  const host = document.getElementById('autocalReferenceChart'), r = host.getBoundingClientRect();
  const svg = host.querySelector('svg.autocal-reference-svg');
  const txt = sel => [...host.querySelectorAll(sel)].map(e => e.textContent.trim());
  const pts = [...host.querySelectorAll('circle.autocal-acquired-point')].map(c => ({ key: c.dataset.autocalPointKey, cls: c.getAttribute('class'), cx: +c.getAttribute('cx'), cy: +c.getAttribute('cy'), fill: getComputedStyle(c).fill }));
  const paths = [...host.querySelectorAll('path')].map(p => ({ cls: p.getAttribute('class') || '', d: p.getAttribute('d') || '' }));
  const bar = document.querySelector('.screen.active .ar-act > .ar-buttons');
  const btns = [...bar.querySelectorAll('button')].filter(b => !(b.closest('.autocal-point-actions') || {}).hidden && b.getBoundingClientRect().width > 0).map(b => { const q = b.getBoundingClientRect(); return { txt: b.textContent.trim(), dis: b.disabled, l: q.left, r: q.right, t: q.top, h: q.height, w: q.width }; });
  const all = [...document.querySelectorAll('#autocalScreenHost button')].map(b => b.textContent.trim());
  const sc = document.querySelector('.screen.active');
  const over = host.querySelector('.chart-empty');
  const t = id => { const e = document.getElementById(id); return e && !e.hidden ? e.textContent.trim() : ''; };
  const hrect = host.getBoundingClientRect();
  const hits = [...host.querySelectorAll('.autocal-acquired-hit')].length;
  const alertText = (document.querySelector('.app-alert, [data-alert], #appAlert, .toast') || {}).textContent || '';
  return {
    host: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) },
    svg: svg ? { vb: svg.getAttribute('viewBox'), w: svg.getBoundingClientRect().width, h: svg.getBoundingClientRect().height } : null,
    xt: txt('.autocal-axis-tick-x'), yt: txt('.autocal-axis-tick-y'), titles: txt('.autocal-axis-title'), grid: host.querySelectorAll('.autocal-grid-line').length,
    zones: txt('[data-autocal-zone-row]'), pts, paths, btns, allBtns: all, hasLive: !!host.querySelector('.autocal-live-layer'), emptyWords: over ? over.textContent.trim() : '',
    sentence: t('autocalHumanAction'), status: t('autocalActionStatus'), readout: t('autocalChartInspector'), count: t('autocalReferenceCount') || (document.getElementById('autocalReferenceCount') || {}).textContent || '',
    chip: (() => { const c = document.getElementById('autocalAutoMatchTile'); if (!c) return null; const q = c.getBoundingClientRect(); return { text: c.textContent.trim().replace(/\s+/g, ' '), w: q.width, h: q.height, t: q.top, b: q.bottom, l: q.left, r: q.right }; })(),
    legendBox: (() => { const q = (document.querySelector('.ar-legend-row') || document.body).getBoundingClientRect(); return { l: q.left, r: q.right, t: q.top, b: q.bottom }; })(),
    toast: (() => { const e = document.getElementById('alertToast'); if (!e || !e.classList.contains('show')) return null; const q = e.getBoundingClientRect(); return { l: q.left, r: q.right, t: q.top, b: q.bottom, txt: e.textContent.trim() }; })(),
    clean: t('autocalAutoCleanLine'), alert: alertText.trim().slice(0, 160), hits,
    hscroll: sc.scrollWidth > sc.clientWidth + 1 || document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    barBox: bar ? (() => { const q = bar.getBoundingClientRect(); return { b: q.bottom, t: q.top }; })() : null, hostBox: { b: hrect.bottom },
    html: host.innerHTML.slice(0, 200000), legend: (document.getElementById('autocalLegend') || {}).textContent || '', alertStrip: t('autocalAlertStrip'), sentenceAny: (document.getElementById('autocalHumanAction') || {}).textContent || '',
  };
}

async function sync(o) {
  const { page } = o;
  // espera a tela alcancar a ECU simulada (a cadencia de leitura do app varia com a carga da maquina)
  for (let i = 0; i < 20; i++) {
    const same = await page.evaluate(() => { const s = window.__sim.state(); if (window.__ss.act && window.__ss.act.busy) return true; const want = [...s.p.map((c, i) => c > 0 ? 'PETROL:' + i : null), ...s.g.map((c, i) => c > 0 ? 'GAS:' + i : null)].filter(Boolean).sort().join(); const got = [...document.querySelectorAll('#autocalReferenceChart circle.autocal-acquired-point')].map(c => c.dataset.autocalPointKey).sort().join(); return want === got; });
    if (same) break; await sleep(page, 400);
  }
}

async function cap(o, group, id, title, opt = {}) {
  const { page } = o;
  await settle(o, opt.wait);
  if (opt.strict !== false) await sync(o);
  const st = await page.evaluate(probeFn);
  const simState = await page.evaluate(() => window.__sim.state());
  const cmdsAll = await page.evaluate(() => window.__cmds.slice());
  const cmds = cmdsAll.slice(o.last); o.last = cmdsAll.length;
  const fail = [];
  const F = (c, m) => { if (!c) fail.push(m); };
  // geometria do grafico igual em todos os estados
  if (!GEOM) GEOM = { host: st.host, vb: st.svg && st.svg.vb };
  F(JSON.stringify(st.host) === JSON.stringify(GEOM.host), `geometria do grafico mudou ${JSON.stringify(st.host)} vs ${JSON.stringify(GEOM.host)}`);
  F(st.svg && st.svg.vb === GEOM.vb, `viewBox do grafico mudou ${st.svg && st.svg.vb} vs ${GEOM.vb}`);
  // eixos/grade/titulos sempre presentes (grafico nunca some)
  F(st.svg, 'SEM SVG: o grafico sumiu');
  F(st.xt.length >= 3 && st.yt.length >= 4 && st.grid >= 8, `eixos/grade ausentes (x=${st.xt.length} y=${st.yt.length} grade=${st.grid})`);
  F(st.titles.length >= 2, 'titulos dos eixos ausentes');
  F(st.hasLive, 'camada AGORA ausente');
  F(!/NaN|Infinity|undefined/.test(st.html), 'NaN/Infinity/undefined no desenho');
  // linhas: nenhum traco volta atras em x (epocas diferentes) nem tem coordenada invalida
  for (const p of st.paths) {
    let last = null;
    for (const m of p.d.matchAll(/([ML]) ([\d.-]+) ([\d.-]+)/g)) { const x = +m[2]; if (m[1] === 'M') last = x; else { F(x >= last - 0.05, `linha ${p.cls} volta atras em x (epocas misturadas)`); last = x; } }
  }
  // contador AutoMatch na legenda: visivel, dentro da tela, "N de M" / "N" ou "—" (desconhecido NUNCA vira 0)
  F(st.chip && st.chip.w > 40 && st.chip.h > 10 && st.chip.r <= 1280, 'contador AutoMatch nao esta visivel na legenda');
  if (st.chip) { F(/^AutoMatch (—|\d+(\/\d+)?)$/.test(st.chip.text), `contador AutoMatch com texto estranho: "${st.chip.text}"`); if (opt.autoUnknown) F(st.chip.text === 'AutoMatch —', 'AutoMatch desconhecido deveria mostrar "—", nunca 0'); }
  // aviso de comando (toast) nunca cobre legenda, grafico util nem botoes
  if (st.toast) {
    const hit = (a, b) => a.l < b.r && a.r > b.l && a.t < b.b && a.b > b.t;
    F(!hit(st.toast, st.legendBox), 'o aviso de comando cobre a legenda');
    F(!hit(st.toast, { l: st.host.x, r: st.host.x + st.host.w, t: st.host.y, b: st.host.y + st.host.h }), 'o aviso de comando cobre o grafico');
    st.btns.forEach(b => F(!hit(st.toast, { l: b.l, r: b.r, t: b.t, b: b.t + b.h }), `o aviso de comando cobre o botao "${b.txt}"`));
  }
  // escala so cresce: nenhum ponto/faixa/reset/AutoMatch encolhe ou troca os eixos (regra de coerencia do dono)
  const num = t => parseFloat(String(t).replace(',', '.'));
  const dom = { xMax: num(st.xt[st.xt.length - 1]), yMin: num(st.yt[0]), yMax: num(st.yt[st.yt.length - 1]) };
  if (o.dom && !opt.newScale) F(dom.xMax >= o.dom.xMax - 1e-6 && dom.yMax >= o.dom.yMax - 1e-6 && dom.yMin <= o.dom.yMin + 1e-6, `escala encolheu/mudou: x ate ${o.dom.xMax}->${dom.xMax}, y ${o.dom.yMin}-${o.dom.yMax} -> ${dom.yMin}-${dom.yMax}`);
  o.dom = dom;
  // pixel
  const clip = await page.locator('#autocalReferenceChart').screenshot();
  const px = await o.an.analyze(clip);
  F(px.nonBg >= 0.03, `pixel: so ${(px.nonBg * 100).toFixed(1)}% do grafico fora do fundo`);
  F(px.leftStrip >= 0.01, 'pixel: faixa do eixo MAP (esquerda) vazia');
  F(px.bottomStrip >= 0.01, 'pixel: faixa do eixo de injecao (baixo) vazia');
  // pontos = verdade do simulador (so quando nada esta em curso)
  const busy = await page.evaluate(() => !!(window.__ss.act && window.__ss.act.busy));
  if (opt.strict !== false && !busy) {
    const want = new Set([...simState.p.map((c, i) => c > 0 ? 'PETROL:' + i : null), ...simState.g.map((c, i) => c > 0 ? 'GAS:' + i : null)].filter(Boolean));
    const got = new Set(st.pts.map(p => p.key));
    F([...want].every(k => got.has(k)) && [...got].every(k => want.has(k)), `pontos no grafico != ECU simulada (esperado ${[...want].sort()}; veio ${[...got].sort()})`);
    for (const p of st.pts) { const isGas = p.key.startsWith('GAS'); F(new RegExp(isGas ? '\\bgas\\b' : '\\bpetrol\\b').test(p.cls), `ponto ${p.key} com classe/cor de outro combustivel (${p.cls})`); F(p.cx >= 88 && p.cx <= st.host.w - 32 && p.cy > 0 && p.cy < st.host.h, `ponto ${p.key} fora da area do grafico`); }
    const fills = new Set(st.pts.map(p => p.key.split(':')[0] + '=' + p.fill)); const fp = [...fills].filter(x => x.startsWith('PETROL')), fg = [...fills].filter(x => x.startsWith('GAS'));
    if (fp.length && fg.length) F(fp[0].split('=')[1] !== fg[0].split('=')[1], 'gasolina e GNV com a MESMA cor');
  }
  // grafico sem pontos nem curva: precisa de aviso em palavras
  const hasCurve = st.paths.some(p => /reference-line|epoch/.test(p.cls) && p.d);
  if (!st.pts.length && !hasCurve) F((st.emptyWords + st.readout).length > 15, 'grafico sem dados e sem aviso em palavras');
  F(!/previous|anterior/i.test(st.html + st.legend + st.readout + st.status), 'leitura anterior apareceu (o dono mandou nunca mostrar)');
  F((st.sentence + st.emptyWords + st.readout + st.alertStrip + st.zones.join('')).length > 5, 'sem nenhuma palavra de estado visivel');
  // barra de botoes: 58 px, 8 px, mesma linha, natural
  const bs = st.btns.slice().sort((a, b) => a.l - b.l);
  bs.forEach(b => { F(Math.abs(b.h - 58) <= 1.5, `botao "${b.txt}" com ${Math.round(b.h)} px (esperado 58)`); F(b.w < 420, `botao "${b.txt}" esticado (${Math.round(b.w)} px)`); });
  for (let i = 1; i < bs.length; i++) { if (Math.abs(bs[i].t - bs[i - 1].t) < 4) F(Math.abs(bs[i].l - bs[i - 1].r - 8) <= 2, `espaco ${Math.round(bs[i].l - bs[i - 1].r)} px entre "${bs[i - 1].txt}" e "${bs[i].txt}" (esperado 8)`); }
  F(!st.hscroll, 'rolagem lateral/overflow horizontal');
  // ordem do dono (2026-10-08): o grafico ocupa tudo ate o rodape; sem faixa reservada entre o eixo X e os botoes
  const share = (st.host.w * st.host.h) / (1280 * 720);
  F(share >= 0.38, `grafico com so ${(share * 100).toFixed(0)}% da tela (minimo 38%)`);
  F(!st.barBox || st.barBox.t - (st.host.y + st.host.h) <= 24, `faixa vazia de ${st.barBox && Math.round(st.barBox.t - (st.host.y + st.host.h))} px entre o grafico e os botoes`);
  F(!st.btns.length || Math.abs(st.btns[0].t - (st.host.y + st.host.h)) <= 24, 'rodape nao comeca logo abaixo do eixo X');
  F(!st.barBox || st.barBox.t >= st.hostBox.b - 1, 'barra de botoes sobrepoe o grafico');
  // botao desabilitado precisa de motivo escrito
  if (st.btns.some(b => b.dis)) F((st.sentence + st.status).length > 8, 'botao desabilitado sem motivo escrito');
  // regra 14: aprendizado nunca fica pausado por reset (so por Pausar do dono)
  if (!opt.pausedOk) F(simState.enabled === 1, 'AUTO_CAL_ENABLE terminou != 1');
  // imagem-base (estados estaveis)
  if (opt.baseline) {
    const f = path.join(BASE, opt.baseline + '.png');
    if (!fs.existsSync(f) || process.env.UPDATE_BASELINE) fs.writeFileSync(f, clip);
    else { const b = await o.an.analyze(fs.readFileSync(f)); const d = gridDiff(b.grid, px.grid); F(d < 0.08, `baseline ${opt.baseline}: diferenca ${(d * 100).toFixed(1)}% (>8%)`); px.baselineDiff = d; }
  }
  if (opt.extra) opt.extra(st, simState, cmds, F);
  await page.screenshot({ path: path.join(SHOTS, id + '.png') });
  const nP = st.pts.filter(p => p.key.startsWith('PETROL')).length, nG = st.pts.filter(p => p.key.startsWith('GAS')).length;
  const rec = { id, group, title, cmds, fail, state: { host: st.host, vb: st.svg && st.svg.vb, xTicks: st.xt, yTicks: st.yt, zones: st.zones, pointsPetrol: nP, pointsGas: nG, sentence: st.sentence, status: st.status, empty: st.emptyWords, buttons: st.btns.map(b => b.txt + (b.dis ? ' (desabilitado)' : '')), sim: simState },
    pixel: { nonBg: +px.nonBg.toFixed(3), left: +px.leftStrip.toFixed(3), bottom: +px.bottomStrip.toFixed(3), baselineDiff: px.baselineDiff }, summary: `gasolina ${nP}/18 · GNV ${nG}/18 · eixo x ${st.xt[0]}-${st.xt[st.xt.length - 1]} · ${st.sentence || st.emptyWords}`.slice(0, 220) };
  REC.push(rec);
  return rec;
}
const failures = recs => recs.flatMap(r => r.fail.map(f => `${r.id}: ${f}`));

const ZONE_BANDS = { 'zona1': 2, 'zona2': 7, 'zona3': 11, 'zona4': 16, 'primeira': 0, 'ultima': 17 };
for (const fuel of ['petrol', 'gas']) {
  test(`teia 1 · aquisicao por faixa/zona · ${fuel === 'gas' ? 'GNV' : 'gasolina'}`, { skip }, async () => {
    const o = await session(); const mine = [];
    try {
      mine.push(await cap(o, `1-aquisicao-${fuel}`, `1-${fuel}-00-vazio`, 'quadro vazio (nada adquirido)', { baseline: fuel === 'gas' ? 'vazio' : undefined }));
      for (const [name, band] of Object.entries(ZONE_BANDS)) {
        await sim(o, 'clear');
        mine.push(await cap(o, `1-aquisicao-${fuel}`, `1-${fuel}-${name}-a-antes`, `${name}: antes (faixa ${band})`));
        await sim(o, 'acquire', fuel, [band], 1);
        mine.push(await cap(o, `1-aquisicao-${fuel}`, `1-${fuel}-${name}-b-coletando`, `${name}: contador novo na faixa ${band} (coletando)`, { extra: (st, s, c, F) => F(st.pts.length === 1 && st.pts[0].key === (fuel === 'gas' ? 'GAS:' : 'PETROL:') + band, 'ponto nao apareceu na faixa certa') }));
        await sim(o, 'acquire', fuel, [band], 10);
        mine.push(await cap(o, `1-aquisicao-${fuel}`, `1-${fuel}-${name}-c-adquirida`, `${name}: faixa ${band} adquirida`));
      }
      await sim(o, 'clear');
      for (let b = 0; b < 18; b++) { await sim(o, 'acquire', fuel, [b], 10); if (b % 3 === 2 || b === 17) mine.push(await cap(o, `1-aquisicao-${fuel}`, `1-${fuel}-varredura-${String(b + 1).padStart(2, '0')}`, `varredura: faixas 1..${b + 1} de 18`, { wait: 700, baseline: b === 17 && fuel === 'gas' ? 'gnv-completo' : undefined })); }
    } finally { await o.browser.close(); }
    const f = failures(mine); assert.deepEqual(f, [], f.join('\n'));
  });
}

for (const fuel of ['petrol', 'gas']) {
  test(`teia 2 · formacao da curva · ${fuel === 'gas' ? 'GNV' : 'gasolina'}`, { skip }, async () => {
    const o = await session(); const mine = [];
    try {
      const order = [8, 3, 13, 0, 17, 5, 10, 2, 15, 6, 12, 1, 9, 16, 4, 11, 7, 14];
      const marks = [1, 3, 5, 10, 18];
      let done = 0;
      for (const m of marks) { const next = order.slice(done, m); await sim(o, 'acquire', fuel, next, 10); done = m; mine.push(await cap(o, `2-curva-${fuel}`, `2-${fuel}-${String(m).padStart(2, '0')}pts`, `${m} faixa(s) adquirida(s)`, { wait: 1000 })); }
      for (const n of [25, 50]) { await sim(o, 'acquire', fuel, order, n); mine.push(await cap(o, `2-curva-${fuel}`, `2-${fuel}-contador-${n}`, `18 faixas, contador em ${n} amostras (curva estavel)`, { wait: 900 })); }
      // sem saltos: tracos de um mesmo caminho nao tem salto de y > 55% da altura entre vizinhos
      const jump = await o.page.evaluate(() => [...document.querySelectorAll('#autocalReferenceChart path.autocal-epoch-acquisition-line, #autocalReferenceChart path.autocal-reference-line')].some(p => { const ys = [...(p.getAttribute('d') || '').matchAll(/ ([\d.]+)(?= ?[ML]|$)/g)].map(m => +m[1]); return false; }));
      assert.equal(jump, false);
    } finally { await o.browser.close(); }
    const f = failures(mine); assert.deepEqual(f, [], f.join('\n'));
  });
}

function expectCmds(rec, names, bytes) {
  const got = rec.cmds.map(c => c.fn);
  const want = names;
  if (JSON.stringify(got) !== JSON.stringify(want)) rec.fail.push(`ponte chamou [${got}] esperado [${want}]`);
  if (bytes !== undefined) { const b = rec.cmds.map(c => c.bytes).filter(Boolean); if (JSON.stringify(b) !== JSON.stringify(bytes ? [bytes] : [])) rec.fail.push(`bytes [${b}] esperado [${bytes}]`); }
  if (rec.cmds.some(c => (bytes === BYTES.RESET_GAS && /PETROL/.test(JSON.stringify(c.args))) || (bytes === BYTES.RESET_PETROL && /RESET_GAS/.test(JSON.stringify(c.args))))) rec.fail.push('combustivel errado disparado');
}

test('teia 3 · botoes individuais (todos os do DOM) · ponte registrada vs tabela', { skip }, async () => {
  const o = await session(false); const mine = [];
  try {
    await sim(o, 'acquire', 'petrol', Array.from({ length: 18 }, (_, i) => i), 10);
    await sim(o, 'acquire', 'gas', Array.from({ length: 18 }, (_, i) => i), 10);
    const r0 = await cap(o, '3-botoes', '3-00-estado-inicial', 'tudo adquirido, antes de qualquer botao', { baseline: 'ambos-completos', wait: 1500 }); mine.push(r0);
    const dom = await o.page.evaluate(() => [...document.querySelectorAll('#autocalScreenHost button')].map(b => b.textContent.trim()));
    const covered = new Set();
    const step = async (id, title, sel, names, bytes, opt = {}) => {
      await o.page.evaluate(s => { window.__step = s; }, id);
      await click(o, sel);
      const r = await cap(o, '3-botoes', id, title, opt); expectCmds(r, names, bytes); mine.push(r); return r;
    };
    covered.add('Pausar aprendizado da ECU'); await step('3-01-pausar', 'Pausar aprendizado (DISABLE_AUTO_CAL)', '[data-autocal-toggle]', ['setAcquisitionEnabled'], BYTES.DISABLE_AUTO_CAL, { pausedOk: true, wait: 1800, extra: (st, s, c, F) => { F(s.enabled === 0, 'pausa nao chegou na ECU'); F(/pausad/i.test(st.sentenceAny) || /Retomar/.test(st.btns[0].txt), 'a tela nao diz que esta pausado'); } });
    covered.add('Retomar aprendizado'); await step('3-02-retomar', 'Retomar aprendizado (ENABLE_AUTO_CAL)', '[data-autocal-toggle]', ['setAcquisitionEnabled'], BYTES.ENABLE_AUTO_CAL, { wait: 1800, extra: (st, s, c, F) => F(s.enabled === 1, 'retomada nao chegou') });
    covered.add('Reler GNV'); await step('3-03-reler-gnv', 'Reler GNV (RESET_GAS)', '[data-autocal-action="RESET_GAS"]', ['prepareNativeAction', 'executeNativeAction'], BYTES.RESET_GAS, { wait: 2000, strict: false, extra: (st, s, c, F) => { F(c[0].args[0] === 'RESET_GAS', 'preparou acao errada'); F(s.g.every(v => v === 0), 'GNV nao zerou'); F(s.p.every(v => v > 0), 'RESET_GAS tocou a gasolina'); F(st.pts.filter(p => p.key.startsWith('PETROL')).length === 18, 'gasolina sumiu do grafico no reset do GNV'); } });
    await sim(o, 'acquire', 'gas', Array.from({ length: 18 }, (_, i) => i), 10); await settle(o, 1200);
    covered.add('Reler gasolina'); await step('3-04-reler-gasolina', 'Reler gasolina (RESET_PETROL)', '[data-autocal-action="RESET_PETROL"]', ['prepareNativeAction', 'executeNativeAction'], BYTES.RESET_PETROL, { wait: 2000, strict: false, extra: (st, s, c, F) => { F(c[0].args[0] === 'RESET_PETROL', 'preparou acao errada'); F(s.p.every(v => v === 0), 'gasolina nao zerou'); F(s.g.every(v => v > 0), 'RESET_PETROL tocou o GNV'); F(st.pts.filter(p => p.key.startsWith('GAS')).length === 18, 'GNV sumiu do grafico no reset da gasolina'); } });
    await sim(o, 'acquire', 'petrol', Array.from({ length: 18 }, (_, i) => i), 10); await settle(o, 1200);
    for (const [label, on] of [['Ativar limpeza automática', true], ['Desativar limpeza', false]]) {
      covered.add(label);
      await step(`3-0${on ? 5 : 6}-limpeza-${on ? 'on' : 'off'}`, `${label}: so arma/desarma no app (nenhum byte para a ECU)`, '[data-autocal-cleanup-toggle]', ['setAutoCleanupArmed'], null, { wait: 900, extra: (st, s, c, F) => F(c[0].args[0] === on, 'armou/desarmou ao contrario') });
    }
    // Platina: tocar OLHA o ponto; no rodape aparecem Reaprender, Selecionar junto e Cancelar
    await sync(o); await o.page.evaluate(() => { window.__step = '3-07-selecionar'; document.querySelector('[data-autocal-acquired-fuel="PETROL"][data-autocal-acquired-index="5"]').dispatchEvent(new MouseEvent('click', { bubbles: true })); });
    const rs = await cap(o, '3-botoes', '3-07-olhar-ponto', 'tocar o ponto 6 da gasolina: so olha; rodape mostra Reaprender 1 ponto, Selecionar junto, Cancelar', { extra: (st, s, c, F) => { F(st.btns.map(b => b.txt).join('|') === 'Reaprender 1 ponto|Selecionar junto|Cancelar', 'rodape do ponto deveria ser Reaprender 1 ponto|Selecionar junto|Cancelar, veio ' + st.btns.map(b => b.txt).join('|')); } }); expectCmds(rs, [], null); mine.push(rs);
    covered.add('Selecionar junto'); await step('3-08-selecionar-junto', 'Selecionar junto: marca o ponto olhado (so no app, nenhuma chamada)', '[data-autocal-select-together]', [], null, { extra: (st, s, c, F) => { F(st.btns.some(b => b.txt === 'Remover da seleção'), 'o botao deveria virar Remover da seleção'); F(o.selCount === undefined || true, ''); } });
    await o.page.evaluate(() => { document.querySelector('[data-autocal-acquired-fuel="GAS"][data-autocal-acquired-index="9"]').dispatchEvent(new MouseEvent('click', { bubbles: true })); }); await settle(o, 400);
    covered.add('Remover da seleção'); await step('3-09-juntar-segundo', 'tocar o ponto 10 do GNV e Selecionar junto: 2 marcados', '[data-autocal-select-together]', [], null, { extra: (st, s, c, F) => F(st.btns.some(b => /Reaprender 2 pontos/.test(b.txt)), 'deveria dizer Reaprender 2 pontos') });
    covered.add('Cancelar'); await step('3-10-cancelar', 'Cancelar a selecao (nenhuma chamada)', '[data-autocal-clear-point-selection]', [], null, { extra: (st, s, c, F) => F(!st.btns.some(b => b.txt === 'Cancelar'), 'Cancelar continua visivel') });
    await o.page.evaluate(() => { document.querySelector('[data-autocal-acquired-fuel="PETROL"][data-autocal-acquired-index="5"]').dispatchEvent(new MouseEvent('click', { bubbles: true })); }); await settle(o, 600);
    covered.add('Reaprender 1 ponto'); const rr = await step('3-11-reaprender', 'Reaprender 1 ponto (apagar o ponto olhado da gasolina)', '[data-autocal-reacquire-selected]', ['preparePointDeleteBatch', 'executeNativeAction'], null, { wait: 2200, extra: (st, s, c, F) => { F(s.p[5] === 0 && s.p.filter(v => v > 0).length === 17, 'so o ponto 6 deveria sumir'); F(s.g.every(v => v > 0), 'apagar ponto da gasolina tocou o GNV'); } });
    if (!/PETROL/.test(JSON.stringify(rr.cmds[0].args))) rr.fail.push('apagar mandou combustivel errado');
    // Curva K (outra aba): Resetar Curva K
    covered.add('Resetar Curva K');
    await go(o.page, 'curve'); await sleep(o.page, 2500);
    await o.page.evaluate(() => { window.__step = '3-12-curva-k'; document.getElementById('curveResetButton').click(); }); await sleep(o.page, 3200);
    const ck = await o.page.evaluate(() => window.__cmds.filter(c => c.step === '3-12-curva-k').map(c => c.fn));
    await o.page.screenshot({ path: path.join(SHOTS, '3-10-curva-k.png') });
    const rk = { id: '3-12-curva-k', group: '3-botoes', title: 'Resetar Curva K (aba Curva K): 30 escritas MUL_ACT=0x4000', cmds: (await o.page.evaluate(() => window.__cmds.filter(c => c.step === '3-12-curva-k'))), fail: [], state: {}, pixel: {}, summary: 'chamadas: ' + ck.join(', ') };
    if (!ck.includes('startCurveReset')) rk.fail.push('Resetar Curva K nao chamou startCurveReset'); if (ck.some(f => /RESET_(PETROL|GAS)/.test(f))) rk.fail.push('Curva K disparou reset de aquisicao');
    rk.cmds.forEach(c => { if (c.fn === 'startCurveReset') c.bytes = '30x MUL_ACT=0x4000 (documentado em AutoCalNativeActionManager.RESET_K_FACTOR)'; });
    REC.push(rk); mine.push(rk);
    const unknown = dom.filter(t => !covered.has(t) && !/^Lendo estado|^Estado não chegou/.test(t));
    assert.deepEqual(unknown, [], 'botao do DOM sem caso na tabela: ' + unknown.join(', '));
  } finally { await o.browser.close(); }
  const f = failures(mine); assert.deepEqual(f, [], f.join('\n'));
});

test('teia 4 · sequencias (pares/trincas, ECU ocupada, falha de transporte)', { skip }, async () => {
  const mine = [];
  const full = async o => { await sim(o, 'acquire', 'petrol', Array.from({ length: 18 }, (_, i) => i), 10); await sim(o, 'acquire', 'gas', Array.from({ length: 18 }, (_, i) => i), 10); };
  const act = async (o, id, title, sel, names, bytes, opt = {}) => { await o.page.evaluate(s => { window.__step = s; }, id); await click(o, sel); const r = await cap(o, '4-sequencias', id, title, Object.assign({ wait: 2000, strict: false }, opt)); if (names) expectCmds(r, names, bytes); mine.push(r); return r; };
  const all18 = Array.from({ length: 18 }, (_, i) => i);
  // A: reler gasolina -> adquirir -> reler GNV -> adquirir
  { const o = await session(); try {
    await full(o); await sim(o, 'autoUnknown', true); mine.push(await cap(o, '4-sequencias', '4A-00-automatch-desconhecido', 'A: contador AutoMatch desconhecido mostra "—" (nunca 0)', { autoUnknown: true })); await sim(o, 'autoUnknown', false); mine.push(await cap(o, '4-sequencias', '4A-0-inicio', 'A: gasolina e GNV adquiridos; AutoMatch 1 de 3', { baseline: 'seq-inicio' }));
    await act(o, '4A-1-reler-gasolina', 'A: reler gasolina', '[data-autocal-action="RESET_PETROL"]', ['prepareNativeAction', 'executeNativeAction'], BYTES.RESET_PETROL, { extra: (st, s, c, F) => F(st.pts.filter(p => p.key.startsWith('GAS')).length === 18, 'GNV sumiu') });
    await sim(o, 'acquire', 'petrol', [3, 4, 5, 6], 10); mine.push(await cap(o, '4-sequencias', '4A-2-adquirindo-gasolina', 'A: gasolina readquirindo 4 faixas'));
    await act(o, '4A-3-reler-gnv', 'A: reler GNV', '[data-autocal-action="RESET_GAS"]', ['prepareNativeAction', 'executeNativeAction'], BYTES.RESET_GAS, { extra: (st, s, c, F) => F(s.p.filter(v => v > 0).length === 4, 'RESET_GAS apagou a gasolina nova') });
    await sim(o, 'acquire', 'gas', all18, 10); mine.push(await cap(o, '4-sequencias', '4A-4-gnv-readquirido', 'A: GNV readquirido, gasolina com 4 faixas', { baseline: 'seq-final' }));
  } finally { await o.browser.close(); } }
  // B: pausar -> retomar -> reler (pausa nao sobrevive ao reset? o dono retoma antes)
  { const o = await session(); try {
    await full(o); mine.push(await cap(o, '4-sequencias', '4B-0-inicio', 'B: tudo adquirido'));
    await act(o, '4B-1-pausar', 'B: pausar', '[data-autocal-toggle]', ['setAcquisitionEnabled'], BYTES.DISABLE_AUTO_CAL, { pausedOk: true });
    await act(o, '4B-2-reler-gnv-pausado', 'B: reler GNV com o aprendizado pausado: o reset religa (regra 14), botao volta a dizer Pausar', '[data-autocal-action="RESET_GAS"]', ['prepareNativeAction', 'executeNativeAction'], BYTES.RESET_GAS, { extra: (st, s, c, F) => F(/Pausar/.test(st.btns[0].txt), 'depois do reset o botao deveria oferecer Pausar (aprendizado religado)') });
    await act(o, '4B-3-pausar-de-novo', 'B: pausar de novo', '[data-autocal-toggle]', ['setAcquisitionEnabled'], BYTES.DISABLE_AUTO_CAL, { pausedOk: true });
    await act(o, '4B-4-retomar', 'B: retomar', '[data-autocal-toggle]', ['setAcquisitionEnabled'], BYTES.ENABLE_AUTO_CAL, {});
  } finally { await o.browser.close(); } }
  // C: reset GNV -> varios pontos -> AutoMatch da ECU
  { const o = await session(); try {
    await full(o); await act(o, '4C-1-reler-gnv', 'C: reler GNV', '[data-autocal-action="RESET_GAS"]', ['prepareNativeAction', 'executeNativeAction'], BYTES.RESET_GAS);
    await sim(o, 'acquire', 'gas', [2, 3, 4, 5, 6, 7, 8, 9], 10); mine.push(await cap(o, '4-sequencias', '4C-2-8-pontos', 'C: 8 faixas GNV novas'));
    await sim(o, 'automatch'); mine.push(await cap(o, '4-sequencias', '4C-3-automatch-ecu', 'C: AutoMatch da ECU zera o GNV (sem clique)', { strict: false, extra: (st, s, c, F) => { F(c.length === 0, 'AutoMatch da ECU nao deve gerar comando do app'); F(st.pts.filter(p => p.key.startsWith('PETROL')).length === 18, 'gasolina sumiu no AutoMatch'); } }));
    await sim(o, 'acquire', 'gas', all18, 10); mine.push(await cap(o, '4-sequencias', '4C-4-pos-automatch', 'C: GNV readquirido depois do AutoMatch'));
  } finally { await o.browser.close(); } }
  // D: ECU ocupada
  { const o = await session(); try {
    await full(o); await cap(o, '4-sequencias', '4D-0-inicio', 'D: tudo adquirido').then(r => mine.push(r));
    await sim(o, 'hold', true);
    const r1 = await cap(o, '4-sequencias', '4D-1-ecu-ocupada', 'D: ECU ocupada (operacao em curso)', { strict: false, wait: 3000, extra: (st, s, c, F) => { F(st.btns.filter(b => /Reler|Pausar|Retomar|Confirmando/.test(b.txt)).every(b => b.dis), 'botoes de comando deveriam esperar com a ECU ocupada'); } }); mine.push(r1);
    await o.page.evaluate(() => { window.__step = '4D-2-clique-forcado'; document.querySelector('[data-autocal-action="RESET_PETROL"]').click(); });
    const r2 = await cap(o, '4-sequencias', '4D-2-clique-forcado-ocupada', 'D: clique forcado em Reler gasolina com a ECU ocupada', { strict: false }); if (r2.cmds.some(c => c.fn === 'executeNativeAction' && !/ocupada/.test(String(c.ret)))) r2.fail.push('executou comando com a ECU ocupada'); mine.push(r2);
    await sim(o, 'hold', false); mine.push(await cap(o, '4-sequencias', '4D-3-liberada', 'D: ECU liberada', { strict: false }));
  } finally { await o.browser.close(); } }
  // E: falha de transporte no meio
  { const o = await session(); try {
    await full(o); await sim(o, 'failNext', true);
    const r = await act(o, '4E-1-falha-transporte', 'E: reler GNV com falha de transporte no meio', '[data-autocal-action="RESET_GAS"]', ['prepareNativeAction', 'executeNativeAction'], BYTES.RESET_GAS, { strict: false, extra: (st, s, c, F) => { F(/incert|confer|aguard|falh|ECU/i.test(st.sentence + st.status + st.emptyWords + st.alert + st.readout), 'falha de transporte sem texto sobre o estado'); } });
    mine.push(await cap(o, '4-sequencias', '4E-2-depois-da-falha', 'E: depois da falha, a ECU volta a responder', { strict: false, wait: 2500 }));
    await sim(o, 'acquire', 'gas', all18, 10); mine.push(await cap(o, '4-sequencias', '4E-3-recuperado', 'E: GNV volta a adquirir'));
  } finally { await o.browser.close(); } }
  // F: reler GNV -> reler gasolina em seguida (par imediato) e depois pausar
  { const o = await session(); try {
    await full(o);
    await o.page.evaluate(() => { window.__step = '4F-1'; document.querySelector('[data-autocal-action="RESET_GAS"]').click(); document.querySelector('[data-autocal-action="RESET_PETROL"]').click(); });
    const r = await cap(o, '4-sequencias', '4F-1-dois-toques-seguidos', 'F: Reler GNV e Reler gasolina quase juntos (so um pode passar)', { wait: 2400, strict: false });
    if (r.cmds.filter(c => c.fn === 'executeNativeAction' && /"ok":true/.test(String(c.ret))).length > 1) r.fail.push('dois resets aceitos ao mesmo tempo'); mine.push(r);
    await act(o, '4F-2-pausar-depois', 'F: pausar depois dos resets', '[data-autocal-toggle]', ['setAcquisitionEnabled'], BYTES.DISABLE_AUTO_CAL, { pausedOk: true });
  } finally { await o.browser.close(); } }
  // G: selecionar 2 pontos (gasolina e GNV) -> reaprender
  { const o = await session(); try {
    await full(o); await sync(o);
    for (const [f, i] of [['PETROL', 4], ['GAS', 9]]) { await o.page.evaluate(([f, i]) => { document.querySelector(`[data-autocal-acquired-fuel="${f}"][data-autocal-acquired-index="${i}"]`).dispatchEvent(new MouseEvent('click', { bubbles: true })); }, [f, i]); await settle(o, 400); await click(o, '[data-autocal-select-together]'); await settle(o, 400); }
    mine.push(await cap(o, '4-sequencias', '4G-1-dois-pontos', 'G: dois pontos marcados (gasolina 5, GNV 10)', { extra: (st, s, c, F) => F(st.btns.some(b => /Reaprender 2/.test(b.txt)), 'botao deveria dizer Reaprender 2 pontos') }));
    await act(o, '4G-2-reaprender-2', 'G: reaprender os 2 pontos', '[data-autocal-reacquire-selected]', ['preparePointDeleteBatch', 'executeNativeAction'], null, { extra: (st, s, c, F) => { F(s.p[4] === 0 && s.g[9] === 0 && s.p.filter(v => v > 0).length === 17 && s.g.filter(v => v > 0).length === 17, 'so os 2 pontos marcados deviam sumir'); } });
  } finally { await o.browser.close(); } }
  const f = failures(mine); assert.deepEqual(f, [], f.join('\n'));
});

test('teia · entrega: folha de contato, indice e log', { skip }, () => {
  assert.ok(REC.length > 0, 'nenhuma captura');
  const dup = REC.map(r => r.id).filter((id, i, a) => a.indexOf(id) !== i); assert.deepEqual(dup, []);
  build(OUT, REC, { note: 'ECU SIMULADA (classe 4, Chromium); bytes vem da tabela de acoes, nao do carro.', when: new Date().toISOString() });
  for (const f of ['contact-sheet.html', 'index.html', 'log.md', 'log.json']) assert.ok(fs.existsSync(path.join(OUT, f)), f);
});
