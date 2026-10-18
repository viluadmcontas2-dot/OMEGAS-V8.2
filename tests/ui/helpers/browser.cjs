'use strict';
// Ajudante dos testes e2e de toque (Playwright + Chromium). Sem Chromium/Playwright o teste PULA (skip), nunca falha por isso.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

const ROOT = path.resolve(__dirname, '../../..');
const UI_DIR = path.join(ROOT, 'app/src/main/assets/ui');
const FIXTURE = path.join(ROOT, 'tests/ui/fixtures/ecu-estado-lognovo.json');
const BENCH_SRC = path.join(__dirname, 'ecu-bench.js');
const IMG_DIR = path.join(ROOT, 'docs/evidence/img');
const VIEWPORTS = { pequeno: { width: 360, height: 740 }, grande: { width: 412, height: 915 } };

function loadPlaywright() {
  const candidates = ['playwright', 'playwright-core', '/opt/node-tools/node_modules/playwright', '/opt/node22/lib/node_modules/playwright',
    '/usr/local/lib/node_modules_global/playwright'];
  for (const name of candidates) { try { return require(name); } catch (_) { /* próximo */ } }
  return null;
}
function chromiumPath() {
  const env = process.env.OMEGAS_CHROMIUM || process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
  const list = [env, '/opt/pw-browsers/chromium', '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/google-chrome'];
  return list.find(p => p && fs.existsSync(p)) || null; // null = deixa o Playwright achar o dele
}
/** Motivo para pular (string) ou '' se dá para rodar. */
function skipReason() {
  const pw = loadPlaywright();
  if (!pw) return 'Playwright não instalado';
  const exe = chromiumPath();
  if (!exe) {
    try { if (!fs.existsSync(pw.chromium.executablePath())) return 'Chromium não encontrado'; } catch (_) { return 'Chromium não encontrado'; }
  }
  return '';
}

async function launch() {
  const pw = loadPlaywright();
  const exe = chromiumPath();
  return pw.chromium.launch({ headless: true, executablePath: exe || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
}

/** Cópia da pasta ui com mutações de texto (para provar que o teste falha quando a regra é estragada). */
function mutatedUi(mutations) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'omegas-ui-mut-'));
  fs.cpSync(UI_DIR, dir, { recursive: true });
  for (const [file, from, to] of mutations) {
    const full = path.join(dir, file);
    const src = fs.readFileSync(full, 'utf8');
    if (!src.includes(from)) throw new Error(`mutação não encontrou o trecho em ${file}: ${from.slice(0, 50)}`);
    fs.writeFileSync(full, src.replace(from, to));
  }
  return dir;
}

function initScript(extra) {
  const { fakeBridge } = require('../../../tools/ci/_ponte_falsa.cjs');
  const benchSrc = fs.readFileSync(BENCH_SRC, 'utf8');
  const fixture = fs.readFileSync(FIXTURE, 'utf8');
  return `
    (${fakeBridge.toString()})();
    ${benchSrc}
    (function () {
      const bench = window.OmegasE2eBench.createBench(${fixture}, ${JSON.stringify(extra || {})});
      window.__bench = bench;
      window.OmegasNative = bench.native;
      window.OmegasV7 = bench.v7;
      Object.assign(window.OmegasAutoCal, bench.autocalExtra);
      ${extra && extra.quota ? `
      // Cota de AutoMatch da ECU atingida (3 de 3): é o que libera o botão manual. O contador vem do estado da ECU emulada.
      bench.seed('09 74 01 7E', [3]);
      const fix = sn => { if (sn && Array.isArray(sn.fields)) sn.fields.forEach(f => { if (f.key === 'NUM_AUTOMATCH_EXECUTED') { f.physicalValues = [3]; f.rawValues = [3]; } }); };
      for (const n of ['getSnapshot', 'getNativeMonitorSnapshot', 'getUiProjection']) {
        const f = window.OmegasAutoCal[n];
        window.OmegasAutoCal[n] = () => { const o = JSON.parse(f()); fix(o); fix(o.snapshot); fix(o.nativeSnapshot); return JSON.stringify(o); };
      }` : ''}
    })();
  `;
}

/**
 * Abre o app (index.html) com a ponte nativa trocada pela bancada. touch emulado (hasTouch + isMobile).
 * Devolve um objeto com page, bench helpers e os erros de console coletados.
 */
async function openApp(browser, { viewport = VIEWPORTS.pequeno, ui = UI_DIR, bench = {}, usb = false, scale = 2 } = {}) {
  const context = await browser.newContext({ viewport, hasTouch: true, isMobile: true, deviceScaleFactor: scale, locale: 'pt-BR' });
  const page = await context.newPage();
  const problems = { console: [], page: [], requests: [] };
  page.on('console', m => { if (m.type() === 'error') problems.console.push(m.text()); });
  page.on('pageerror', e => problems.page.push(String(e.message || e)));
  page.on('requestfailed', r => problems.requests.push(r.url()));
  await page.addInitScript(initScript(bench));
  await page.goto('file://' + path.join(ui, 'index.html'), { waitUntil: 'load' });
  await page.waitForFunction(() => !!window.OmegasApp && document.querySelectorAll(".side-nav button").length === 6);
  const app = { browser, context, page, problems, viewport };
  if (usb) await plugAndDeliver(app);
  return app;
}

/** Liga o USB (evento) e entrega o primeiro quadro válido da sessão (evento): os parâmetros passam a ser lidos. */
async function plugAndDeliver(app) {
  await app.page.evaluate(() => { window.__bench.plugUsb(); window.__bench.deliver(); });
  await pump(app.page, 45);
}

/** Roda ciclos do agendador do app (cada ciclo = o que o relógio dele faria), com ou sem dado novo. */
async function pump(page, cycles = 2, { data = false } = {}) {
  await page.evaluate(async ({ cycles, data }) => {
    for (let i = 0; i < cycles; i += 1) {
      if (data) window.__bench.deliver();
      window.OmegasApp.scheduler.run();
      await new Promise(r => requestAnimationFrame(() => setTimeout(r, 0)));
    }
  }, { cycles, data });
}

async function route(app, name) {
  await tap(app.page, `.side-nav button[data-route="${name}"]`);
  await pump(app.page, 3);
}

/** Toque de verdade (touchscreen) no centro do elemento; confere que é o elemento (ou filho) que está no ponto: nada cobre o botão. */
async function tap(page, selector, { index = 0, allowDisabled = false } = {}) {
  const loc = page.locator(selector).locator('visible=true').nth(index);
  await loc.scrollIntoViewIfNeeded().catch(() => {});
  const box = await loc.boundingBox();
  if (!box) throw new Error('sem caixa visível: ' + selector);
  const x = box.x + box.width / 2; const y = box.y + box.height / 2;
  const hit = await loc.evaluate((el, [px, py]) => {
    const top = document.elementFromPoint(px, py);
    return { ok: !!top && (el === top || el.contains(top) || top.contains(el)), top: top ? (top.tagName + '#' + top.id + '.' + String(top.className).slice(0, 40)) : null, disabled: !!el.disabled };
  }, [x, y]);
  if (!hit.ok) throw new Error(`toque em ${selector} cairia em outro elemento: ${hit.top}`);
  if (hit.disabled && !allowDisabled) throw new Error(`botão desabilitado: ${selector}`);
  await page.touchscreen.tap(x, y);
  return { x, y, disabled: hit.disabled };
}
/** Dois toques seguidos no mesmo ponto, sem esperar nada entre eles (toque duplo rápido). */
async function doubleTap(page, selector) {
  const loc = page.locator(selector).locator('visible=true').first();
  await loc.scrollIntoViewIfNeeded().catch(() => {});
  const box = await loc.boundingBox();
  const x = box.x + box.width / 2; const y = box.y + box.height / 2;
  await page.touchscreen.tap(x, y);
  await page.touchscreen.tap(x, y);
}

async function shot(page, name) {
  fs.mkdirSync(IMG_DIR, { recursive: true });
  await page.evaluate(() => document.querySelectorAll('.alert-toast.show').forEach(x => x.classList.remove('show')));
  const file = path.join(IMG_DIR, `e2e-${name}.png`);
  await page.screenshot({ path: file });
  return file;
}

const frames = page => page.evaluate(() => ({ log: window.__bench.ecu.log.map(f => ({ ...f })), acoes: window.__bench.ecu.acoes.slice() }));
const writesOf = log => log.filter(f => f.kind !== 'READ');

module.exports = { ROOT, UI_DIR, FIXTURE, IMG_DIR, VIEWPORTS, skipReason, launch, mutatedUi, openApp, plugAndDeliver, pump, route, tap, doubleTap, shot, frames, writesOf };
