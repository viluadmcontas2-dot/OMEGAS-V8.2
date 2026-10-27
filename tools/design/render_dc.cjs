#!/usr/bin/env node
/* Renderiza telas .dc.html (canvas de design OMEGAS) com Chromium + Playwright, tira captura e confere:
 * texto cortado/vazando, elementos fora do quadro, alvos de toque pequenos, fonte pequena demais, erros de script.
 *
 * Uso:  node tools/design/render_dc.cjs <pasta-das-telas> [--out <pasta>] [--only 03,05] [--steps <arquivo.json>] [--quiet]
 *   <pasta-das-telas>  contém os *.dc.html e (opcional) canvas.json (tamanho de cada tela).
 *   --steps            JSON { "05-Refino.dc.html": [ {"nome":"depois","click":"text=Gravar 2 mudanças"} ] }
 *                      cada passo clica (seletor Playwright) e tira outra captura + confere de novo.
 * Saída: <out>/<tela>[__passo].png e <out>/RELATORIO.md. Código de saída 1 se houver erro de script ou texto cortado.
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

function parseArgs(argv) {
  const a = { dir: null, out: null, only: null, steps: null, quiet: false };
  for (let i = 2; i < argv.length; i++) {
    const v = argv[i];
    if (v === '--out') a.out = argv[++i];
    else if (v === '--only') a.only = argv[++i].split(',');
    else if (v === '--steps') a.steps = argv[++i];
    else if (v === '--quiet') a.quiet = true;
    else if (!a.dir) a.dir = v;
  }
  if (!a.dir) { console.error('uso: render_dc.cjs <pasta-das-telas> [--out dir] [--only 03,05] [--steps arq.json]'); process.exit(2); }
  a.dir = path.resolve(a.dir);
  a.out = path.resolve(a.out || path.join(a.dir, '..', 'render'));
  return a;
}

function sizeOf(file, canvas) {
  const name = path.basename(file);
  const b = canvas && canvas.boards && canvas.boards[name];
  if (b) return { w: b.w, h: b.h };
  const src = fs.readFileSync(file, 'utf8');
  const m = /"\$preview"\s*:\s*\{\s*"width"\s*:\s*(\d+)\s*,\s*"height"\s*:\s*(\d+)/.exec(src);
  return m ? { w: +m[1], h: +m[2] } : { w: 1280, h: 720 };
}

// Roda dentro da página: devolve problemas de layout.
function pageChecks(minTouch) {
  const out = { cortado: [], fora: [], toque: [], fonte: [], sobrepoe: [], colisao: [] };
  const host = document.getElementById('dc-host');
  if (!host) return out;
  const root = host.firstElementChild || host;
  const R = root.getBoundingClientRect();
  const vis = (el) => {
    const cs = getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden' || +cs.opacity === 0) return false;
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };
  const label = (el) => (el.getAttribute('aria-label') || el.textContent || el.tagName).replace(/\s+/g, ' ').trim().slice(0, 48);
  const clipAncestor = (el) => {
    for (let p = el; p && p !== document.body; p = p.parentElement) {
      const cs = getComputedStyle(p);
      if (cs.overflow !== 'visible' || cs.overflowX !== 'visible' || cs.overflowY !== 'visible') return p;
    }
    return null;
  };
  // Cartão = caixa com borda ou fundo visível: texto que passa da borda dele "vaza" mesmo sem overflow:hidden.
  const pintada = (p) => {
    const cs = getComputedStyle(p);
    const borda = ['Top', 'Right', 'Bottom', 'Left'].some((s) => parseFloat(cs['border' + s + 'Width']) > 0 && cs['border' + s + 'Style'] !== 'none' && !/rgba\([^)]*,\s*0\)/.test(cs['border' + s + 'Color']));
    const m = /rgba?\(([^)]*)\)/.exec(cs.backgroundColor);
    const alfa = m ? (m[1].split(',').length > 3 ? parseFloat(m[1].split(',')[3]) : 1) : 0;
    return borda || alfa > 0.04;
  };
  const cartao = (el) => {
    for (let p = el; p && p !== host && p !== root; p = p.parentElement) if (pintada(p)) return p;
    return null;
  };
  // Texto: compara o retângulo de cada nó de texto com o ancestral que corta e com o quadro.
  const walker = document.createTreeWalker(host, NodeFilter.SHOW_TEXT);
  const seenFont = new Set();
  const textBoxes = [];
  let node;
  while ((node = walker.nextNode())) {
    const t = node.nodeValue.replace(/\s+/g, ' ').trim();
    if (!t) continue;
    const el = node.parentElement;
    if (!el || el.closest('script,style') || !vis(el)) continue;
    const range = document.createRange();
    range.selectNodeContents(node);
    const rects = Array.from(range.getClientRects()).filter((r) => r.width > 0 && r.height > 0);
    if (!rects.length) continue;
    const box = rects.reduce((m, r) => ({ l: Math.min(m.l, r.left), t: Math.min(m.t, r.top), r: Math.max(m.r, r.right), b: Math.max(m.b, r.bottom) }), { l: 1e9, t: 1e9, r: -1e9, b: -1e9 });
    textBoxes.push({ t, el, l: box.l, tp: box.t + 2, r: box.r, b: box.b - 2, rects: rects.map((q) => ({ l: q.left, t: q.top + 2, r: q.right, b: q.bottom - 2 })) });
    const clip = clipAncestor(el);
    if (clip) {
      const c = clip.getBoundingClientRect();
      if (box.r > c.right + 1.5 || box.b > c.bottom + 1.5 || box.l < c.left - 1.5 || box.t < c.top - 1.5) out.cortado.push(`"${t.slice(0, 50)}" passa da caixa que corta (${Math.round(box.r - c.right)}px dir, ${Math.round(box.b - c.bottom)}px baixo)`);
    }
    const card = cartao(el);
    if (card && card !== clip) {
      const c = card.getBoundingClientRect();
      if (box.r > c.right + 1.5 || box.b > c.bottom + 1.5 || box.l < c.left - 1.5 || box.t < c.top - 1.5) out.cortado.push(`"${t.slice(0, 50)}" vaza do cartão (${Math.round(box.r - c.right)}px dir, ${Math.round(box.b - c.bottom)}px baixo)`);
    }
    // texto dentro de caixa de largura fixa que estoura (overflow visível)
    const own = el.getBoundingClientRect();
    if (el.scrollWidth > el.clientWidth + 2 && getComputedStyle(el).display !== 'inline') {
      const cs = getComputedStyle(el);
      if (cs.overflowX === 'visible' && el.clientWidth > 0) out.cortado.push(`"${t.slice(0, 50)}" estoura a própria caixa (${el.scrollWidth}px em ${el.clientWidth}px)`);
    }
    if (box.r > R.right + 1.5 || box.b > R.bottom + 1.5 || box.l < R.left - 1.5 || box.t < R.top - 1.5) out.fora.push(`texto "${t.slice(0, 50)}" fora do quadro`);
    const fs = parseFloat(getComputedStyle(el).fontSize);
    if (fs < 12 && !seenFont.has(t)) { seenFont.add(t); out.fonte.push(`${fs}px: "${t.slice(0, 40)}"`); }
  }
  // Texto contra texto: quase sempre é erro (rótulo por cima de rótulo, quebra de linha que invade o vizinho).
  for (let i = 0; i < textBoxes.length; i++) {
    for (let j = i + 1; j < textBoxes.length; j++) {
      const a = textBoxes[i]; const b = textBoxes[j];
      if (a.el === b.el || a.el.contains(b.el) && a.el.children.length === 0) continue;
      // menu/janela flutuante cobre o conteúdo por desenho: não é erro de layout
      if (a.el.closest('[role="menu"],[role="dialog"]') !== b.el.closest('[role="menu"],[role="dialog"]')) continue;
      if (Math.min(a.r, b.r) - Math.max(a.l, b.l) <= 3 || Math.min(a.b, b.b) - Math.max(a.tp, b.tp) <= 3) continue;
      let hit = null;
      for (const ra of a.rects) for (const rb of b.rects) {
        const w = Math.min(ra.r, rb.r) - Math.max(ra.l, rb.l);
        const h = Math.min(ra.b, rb.b) - Math.max(ra.t, rb.t);
        if (w > 3 && h > 3 && !hit) hit = `${Math.round(w)}×${Math.round(h)}`;
      }
      if (hit) out.colisao.push(`"${a.t.slice(0, 28)}" × "${b.t.slice(0, 28)}" (${hit})`);
    }
  }
  // Alvos de toque
  const targets = Array.from(host.querySelectorAll('button, a[href], [role="button"], input, select')).filter(vis);
  const boxes = [];
  for (const el of targets) {
    const r = el.getBoundingClientRect();
    boxes.push({ el, r });
    if (Math.min(r.width, r.height) < minTouch) out.toque.push(`${Math.round(r.width)}×${Math.round(r.height)} "${label(el)}"`);
    if (r.right > R.right + 1.5 || r.bottom > R.bottom + 1.5 || r.left < R.left - 1.5 || r.top < R.top - 1.5) out.fora.push(`alvo "${label(el)}" fora do quadro`);
  }
  for (let i = 0; i < boxes.length; i++) {
    for (let j = i + 1; j < boxes.length; j++) {
      const a = boxes[i]; const b = boxes[j];
      if (a.el.contains(b.el) || b.el.contains(a.el)) continue;
      const w = Math.min(a.r.right, b.r.right) - Math.max(a.r.left, b.r.left);
      const h = Math.min(a.r.bottom, b.r.bottom) - Math.max(a.r.top, b.r.top);
      if (w > 3 && h > 3) out.sobrepoe.push(`"${label(a.el)}" × "${label(b.el)}" (${Math.round(w)}×${Math.round(h)})`);
    }
  }
  return out;
}

async function main() {
  const args = parseArgs(process.argv);
  fs.mkdirSync(args.out, { recursive: true });
  const site = path.join(args.out, 'site');
  fs.rmSync(site, { recursive: true, force: true });
  fs.mkdirSync(site, { recursive: true });
  for (const f of fs.readdirSync(args.dir)) {
    if (f.endsWith('.dc.html') || f === 'canvas.json') fs.copyFileSync(path.join(args.dir, f), path.join(site, f));
  }
  fs.copyFileSync(path.join(__dirname, 'dc-runtime.js'), path.join(site, 'support.js'));
  let canvas = null;
  try { canvas = JSON.parse(fs.readFileSync(path.join(args.dir, 'canvas.json'), 'utf8')); } catch (e) { /* sem canvas.json */ }
  const steps = args.steps ? JSON.parse(fs.readFileSync(path.resolve(args.steps), 'utf8')) : {};

  let files = fs.readdirSync(site).filter((f) => f.endsWith('.dc.html')).sort();
  if (args.only) files = files.filter((f) => args.only.some((o) => f.startsWith(o)));

  const browser = await chromium.launch();
  const report = [];
  let bad = 0;
  for (const f of files) {
    const { w, h } = sizeOf(path.join(site, f), canvas);
    const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
    const page = await ctx.newPage();
    const errs = [];
    page.on('pageerror', (e) => errs.push('erro: ' + e.message));
    page.on('console', (m) => { if (m.type() === 'error' && !/fonts\.g|ERR_|Failed to load resource|\{\{/.test(m.text())) errs.push('console: ' + m.text()); });
    await page.goto('file://' + path.join(site, f));
    await page.waitForFunction(() => window.__dcReady || window.__dcError, null, { timeout: 8000 }).catch(() => errs.push('não ficou pronta'));
    await page.evaluate(() => document.fonts && document.fonts.ready);
    const interactive = !!(canvas && canvas.boards && canvas.boards[f] && canvas.boards[f].is_interactive);
    const minTouch = 44;
    const run = async (tag) => {
      const png = path.join(args.out, f.replace('.dc.html', '') + (tag ? '__' + tag : '') + '.png');
      await page.screenshot({ path: png, clip: { x: 0, y: 0, width: w, height: h } });
      const res = await page.evaluate(pageChecks, minTouch);
      const line = { tela: f + (tag ? ' › ' + tag : ''), png: path.relative(args.out, png), w, h, interactive, erros: errs.slice(), ...res };
      report.push(line);
      if (errs.length || res.cortado.length || res.fora.length || res.colisao.length) bad++;
      errs.length = 0;
    };
    await run('');
    for (const st of (steps[f] || [])) {
      try {
        if (st.click) await page.click(st.click, { timeout: 4000 });
        if (st.press) await page.keyboard.press(st.press);
        if (st.drag) {
          const [x1, y1, x2, y2] = st.drag;
          await page.mouse.move(x1, y1); await page.mouse.down();
          await page.mouse.move((x1 + x2) / 2, (y1 + y2) / 2, { steps: 4 }); await page.mouse.move(x2, y2, { steps: 4 }); await page.mouse.up();
        }
        await page.waitForTimeout(60);
        await run(st.nome || 'passo');
      } catch (e) { errs.push(`passo "${st.nome || st.click}": ${e.message.split('\n')[0]}`); await run('falha'); }
    }
    await ctx.close();
  }
  await browser.close();

  const L = [];
  L.push('# Relatório de renderização\n');
  for (const r of report) {
    const prob = r.erros.length + r.cortado.length + r.fora.length + r.colisao.length;
    L.push(`## ${prob ? '✗' : '✓'} ${r.tela} (${r.w}×${r.h}${r.interactive ? ', interativa' : ''}) → ${r.png}`);
    const sec = (nome, arr, max = 12) => { if (arr.length) { L.push(`- **${nome}** (${arr.length}):`); arr.slice(0, max).forEach((x) => L.push(`  - ${x}`)); if (arr.length > max) L.push(`  - … +${arr.length - max}`); } };
    sec('erros de script', r.erros); sec('texto cortado/vazando', r.cortado); sec('texto em cima de texto', r.colisao); sec('fora do quadro', r.fora);
    sec(`alvos de toque < ${44}px`, r.toque); sec('fonte < 12px', r.fonte); sec('alvos se sobrepõem', r.sobrepoe);
    L.push('');
  }
  fs.writeFileSync(path.join(args.out, 'RELATORIO.md'), L.join('\n'));
  if (!args.quiet) console.log(L.join('\n'));
  console.log(`\n${report.length} capturas em ${args.out}; ${bad} com erro/corte/fora do quadro.`);
  process.exit(bad ? 1 : 0);
}
main().catch((e) => { console.error(e); process.exit(2); });
