'use strict';
// Mockups v2 das 4 abas (AutoCal, Refino, Ajuste local, Ajuste global) a partir do app REAL em Chromium com a bancada dos testes.
// Não altera o app: só injeta CSS/DOM na página de captura (v2-injetar.js). Interage no layout real, aplica o v2, fotografa, desfaz.
// Uso (da raiz do repositório): NODE_PATH=/opt/node22/lib/node_modules node docs/produto/mockups/v2/abas/fonte/capturar.cjs
const fs = require('node:fs');
const path = require('node:path');
const T = require(path.resolve('tests/ui/helpers/browser.cjs'));
const OUT = path.resolve('docs/produto/mockups/v2/abas');
const INJ = fs.readFileSync(path.join(__dirname, 'v2-injetar.js'), 'utf8');
const VPS = [[1280, 720], [800, 480]];
const SERRILHADA = Array.from({ length: 30 }, (_, i) => (i < 5 || i > 27 ? 1.0 : 1.0 + (i % 2 ? 0.07 : -0.05)));
const SUAVE = Array.from({ length: 30 }, (_, i) => 1.0 + Math.min(0.14, Math.max(0, (i - 6) * 0.006)));
const medicoes = {};
let n = 0;

async function abrir(browser, w, h, { usb = false } = {}) {
  const app = await T.openApp(browser, { viewport: { width: w, height: h }, usb, scale: 1 });
  await app.page.evaluate(INJ);
  return app;
}
async function foto(app, rota, nome, w, h) {
  const { page } = app;
  await page.evaluate(() => document.querySelectorAll('.alert-toast').forEach(x => x.remove()));
  const info = await page.evaluate(r => window.__v2.on(r), rota);
  await page.waitForTimeout(250);
  const arquivo = `${String(++n).padStart(2, '0')}-${nome}-${w}x${h}.png`;
  await page.screenshot({ path: path.join(OUT, arquivo) });
  // medida dos botões no layout v2 (getBoundingClientRect)
  medicoes[arquivo] = await page.evaluate(() => {
    const vis = e => { const b = e.getBoundingClientRect(); return b.width > 0 && b.height > 0 && getComputedStyle(e).visibility !== 'hidden'; };
    return [...document.querySelectorAll('button:not(.map-k-cell):not(.map-axis-header), summary, select')].filter(vis).filter(e => !e.closest('.operation-layer'))
      .map(e => { const b = e.getBoundingClientRect(); return [(e.innerText || e.getAttribute('aria-label') || e.id || '').trim().replace(/\s+/g, ' ').slice(0, 24), Math.round(b.x), Math.round(b.y), Math.round(b.width), Math.round(b.height)]; });
  });
  await page.evaluate(() => window.__v2.off());
  console.log('ok', arquivo, JSON.stringify(info));
}
const ciclos = (page, k) => page.evaluate(async k => { for (let i = 0; i < k; i += 1) { window.__bench.deliver(); window.OmegasApp.scheduler.run(); } }, k);
// Toque de verdade; se algo cobre o botão no layout REAL (ex.: 800x480), registra e usa click() só para chegar ao estado.
async function tocar(a, sel) {
  try { await T.tap(a.page, sel); } catch (e) { console.warn('AVISO toque bloqueado em ' + sel + ' (' + a.viewport.width + 'x' + a.viewport.height + '): ' + String(e.message).split(':').slice(-1)[0].trim()); await a.page.evaluate(s => document.querySelector(s).click(), sel); }
}
async function tentar(nome, fn) { try { await fn(); } catch (e) { console.error('FALHOU ' + nome + ': ' + String(e.message).split('\n')[0]); } }

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await T.launch();
  for (const [w, h] of VPS) {
    n = 0;
    // a numeração segue a ordem do fluxo por aba: 1 autocal.., depois refino.., etc. Cada aba abre uma sessão própria.
    const abas = {
      autocal: async () => {
        let a = await abrir(browser, w, h);
        await tentar('autocal sem central', async () => { await T.route(a, 'autocal'); await T.pump(a.page, 5); await foto(a, 'autocal', 'autocal-sem-central', w, h); });
        await a.context.close();
        a = await abrir(browser, w, h);
        await a.page.evaluate(() => window.__bench.plugUsb()); await T.pump(a.page, 2);
        await tentar('autocal lendo', async () => { await T.route(a, 'autocal'); await T.pump(a.page, 2); await foto(a, 'autocal', 'autocal-lendo', w, h); });
        await a.context.close();
        a = await abrir(browser, w, h, { usb: true });
        await T.route(a, 'autocal'); await T.pump(a.page, 12, { data: true });
        await tentar('autocal com dados', async () => foto(a, 'autocal', 'autocal-com-dados', w, h));
        await tentar('autocal acoes', async () => {
          await T.tap(a.page, '.autocal-reset-menu > summary'); await T.pump(a.page, 2, { data: true });
          await foto(a, 'autocal', 'autocal-acoes-avancadas', w, h);
          await T.tap(a.page, '.autocal-reset-menu > summary'); await T.pump(a.page, 2, { data: true });
        });
        await a.context.close();
      },
      refino: async () => {
        let a = await abrir(browser, w, h);
        await tentar('refino sem central', async () => { await T.route(a, 'refino'); await T.pump(a.page, 5); await foto(a, 'refino', 'refino-sem-central', w, h); });
        await a.context.close();
        a = await abrir(browser, w, h);
        await a.page.evaluate(() => window.__bench.plugUsb()); await T.pump(a.page, 2);
        await tentar('refino lendo', async () => { await T.route(a, 'refino'); await T.pump(a.page, 2); await foto(a, 'refino', 'refino-lendo', w, h); });
        await a.context.close();
        a = await abrir(browser, w, h, { usb: true });
        await T.route(a, 'refino'); await T.pump(a.page, 12, { data: true });
        await tentar('refino com dados', async () => foto(a, 'refino', 'refino-com-dados', w, h));
        // proposta de exemplo do piloto (a bancada não tem o piloto do Kotlin): 12 pontos
        await a.page.evaluate(() => {
          const refino = window.OmegasApp.refino; const original = refino.api.equivalence.bind(refino.api);
          const points = Array.from({ length: 12 }, (_, i) => ({ index: 8 + i, currentRaw: 16384, targetRaw: 16384 + 300 + i * 40, referenceTimeMs: 3 + i * 0.5 }));
          refino.api = Object.assign(Object.create(refino.api), { equivalence: () => { const o = original() || {};
            if (window.__rmDone) return { ...o, ok: true, autopilot: { ...(o.autopilot || {}), phase: 'VERIFICANDO', ecuDone: true, ecuOnline: true, headline: 'Gravado e conferido pela central', next: 'Dirija um pouco: o app mede se melhorou. Se piorar, desfaça.',
              action: { kind: 'none', enabled: false }, secondary: [{ kind: 'undo', label: 'Desfazer última gravação', enabled: true }], proposal: { known: true, count: 0, points: [], held: [], signature: '' } },
              refinement: { ...(o.refinement || {}), latest: { undoable: true, undoPoints: points.map(p => ({ index: p.index, currentRaw: p.targetRaw, targetRaw: p.currentRaw })) } } };
            return { ...o, ok: true, autopilot: { ...(o.autopilot || {}), phase: 'PROPOSTA_PRONTA', ecuDone: true, ecuOnline: true,
            action: { kind: 'review', label: 'Revisar e gravar 12 pontos', enabled: true }, proposal: { known: true, count: 12, points, held: [], signature: 'v2-mock' } } }; } });
        });
        await a.page.evaluate(() => window.OmegasApp.refino.refresh(true));
        await T.pump(a.page, 3, { data: true });
        await tentar('refino previa', async () => {
          await T.tap(a.page, '.refino-action-bar button'); await T.pump(a.page, 6, { data: true });
          await foto(a, 'refino', 'refino-previa-de-gravacao', w, h);
        });
        await tentar('refino gravado', async () => {
          // A bancada devolve readbackValid=false para a Curva K (o Kotlin real devolve true); só na captura, completa o desfecho.
          await a.page.evaluate(() => { const n = window.OmegasApp.refino.native; const o = n.curveOperation.bind(n); n.curveOperation = () => { const r = o(); return r && r.state === 'BATCH_CONFIRMED' ? { ...r, readbackValid: true } : r; }; });
          const g = a.page.locator('button', { hasText: /Gravar na Curva K/ }).locator('visible=true').first();
          const b = await g.boundingBox(); await a.page.touchscreen.tap(b.x + b.width / 2, b.y + b.height / 2);
          await T.pump(a.page, 14, { data: true }); await a.page.waitForTimeout(400);
          console.log('refino operação:', await a.page.evaluate(() => window.OmegasApp.refino.operation.phase));
          await a.page.evaluate(() => { window.__rmDone = true; window.OmegasApp.refino.refresh(true); }); await T.pump(a.page, 3, { data: true });
          await foto(a, 'refino', 'refino-gravado', w, h);
        });
        await a.context.close();
      },
      map: async () => {
        let a = await abrir(browser, w, h);
        await tentar('map sem central', async () => { await T.route(a, 'map'); await T.pump(a.page, 5); await foto(a, 'map', 'ajuste-local-sem-central', w, h); });
        await a.context.close();
        a = await abrir(browser, w, h);
        await a.page.evaluate(() => window.__bench.plugUsb()); await T.pump(a.page, 2);
        await tentar('map lendo', async () => { await T.route(a, 'map'); await T.pump(a.page, 2); await foto(a, 'map', 'ajuste-local-lendo', w, h); });
        await a.context.close();
        a = await abrir(browser, w, h, { usb: true });
        await a.page.evaluate(f => window.__bench.seedCurve(f), SUAVE);
        await T.route(a, 'map'); await T.pump(a.page, 12, { data: true });
        await a.page.waitForSelector('#mapGrid .map-k-cell');
        await tentar('map com dados', async () => foto(a, 'map', 'ajuste-local-com-dados', w, h));
        await tentar('map celula', async () => {
          await T.tap(a.page, '.map-k-cell[data-row="2"][data-column="3"]'); await T.pump(a.page, 3, { data: true });
          await foto(a, 'map', 'ajuste-local-celula-selecionada', w, h);
        });
        await tentar('map previa', async () => {
          await T.tap(a.page, '#mapFromCurve');
          await a.page.waitForFunction(() => /Gravar \d+ alterações/.test(document.getElementById('mapReviewButton')?.textContent || ''), null, { timeout: 8000 });
          await T.pump(a.page, 3, { data: true });
          await foto(a, 'map', 'ajuste-local-previa-de-gravacao', w, h);
        });
        await tentar('map gravado', async () => {
          await T.tap(a.page, '#mapReviewButton'); await T.pump(a.page, 14, { data: true }); await a.page.waitForTimeout(600);
          await foto(a, 'map', 'ajuste-local-gravado', w, h);
        });
        await a.context.close();
      },
      curve: async () => {
        let a = await abrir(browser, w, h);
        await tentar('curve sem central', async () => { await T.route(a, 'curve'); await T.pump(a.page, 5); await foto(a, 'curve', 'ajuste-global-sem-central', w, h); });
        await a.context.close();
        a = await abrir(browser, w, h);
        await a.page.evaluate(() => window.__bench.plugUsb()); await T.pump(a.page, 2);
        await tentar('curve lendo', async () => { await T.route(a, 'curve'); await T.pump(a.page, 2); await foto(a, 'curve', 'ajuste-global-lendo', w, h); });
        await a.context.close();
        a = await abrir(browser, w, h, { usb: true });
        await a.page.evaluate(f => window.__bench.seedCurve(f), SERRILHADA);
        await T.route(a, 'curve'); await T.pump(a.page, 12, { data: true });
        await a.page.waitForSelector('#curveChart .curve-point');
        await tentar('curve com dados', async () => foto(a, 'curve', 'ajuste-global-com-dados', w, h));
        await tentar('curve ponto', async () => {
          await T.tap(a.page, '#curveChart [data-curve-dot="12"]'); await T.pump(a.page, 3, { data: true });
          await foto(a, 'curve', 'ajuste-global-ponto-ativo', w, h);
        });
        await tentar('curve previa', async () => {
          await tocar(a, '#curveSmoothButton'); await T.pump(a.page, 4, { data: true });
          await foto(a, 'curve', 'ajuste-global-previa-de-gravacao', w, h);
        });
        await tentar('curve gravado', async () => {
          await tocar(a, '#curveReviewButton'); await T.pump(a.page, 14, { data: true }); await a.page.waitForTimeout(600);
          await foto(a, 'curve', 'ajuste-global-gravado', w, h);
        });
        await a.context.close();
      },
    };
    for (const k of Object.keys(abas)) await abas[k]();
  }
  fs.writeFileSync(path.join(__dirname, 'medicao-v2.json'), JSON.stringify(medicoes, null, 1));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
