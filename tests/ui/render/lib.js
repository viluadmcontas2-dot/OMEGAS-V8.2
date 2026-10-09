'use strict';
// Render real (Chromium + Playwright, sem emulador) da UI com a ponte falsa. Classe de prova 4 (parcial).
// PLAYWRIGHT_BROWSERS_PATH aponta para o Chromium já instalado; nunca rode "playwright install" aqui.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const cp = require('node:child_process');

const ROOT = path.resolve(__dirname, '../../..');
// OMEGAS_UI_URL: ambientes em que o Chromium bloqueia file:// (política) servem a pasta ui por HTTP local.
const UI = process.env.OMEGAS_UI_URL || ('file://' + path.join(ROOT, 'app/src/main/assets/ui/index.html'));
const MOCK = fs.readFileSync(path.join(__dirname, 'mock-bridge.js'), 'utf8');

function dataFile() {
  const out = path.join(os.tmpdir(), 'omegas-render-data.json');
  if (!fs.existsSync(out)) cp.execFileSync('python3', [path.join(__dirname, 'prep.py'), out], { stdio: 'ignore' });
  const base = JSON.parse(fs.readFileSync(out, 'utf8'));
  const realPath = process.env.OMEGAS_LOGNOVO_REPLAY_JSON;
  if (!realPath) return base;
  const real = JSON.parse(fs.readFileSync(realPath, 'utf8'));
  if (real.schema !== 'omegas.lognovo.real-live.v1' ||
      real.source?.rawSha256 !== '43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64' ||
      real.timebase?.kind !== 'SYNTHETIC_FIXED_CADENCE' ||
      !Array.isArray(real.frames) || real.frames.length < 2) {
    throw new Error('LOGNOVO: replay sem proveniência, carimbo sintético ou quadros válidos');
  }
  // Snapshot é da fixture AutoCal separada: híbrido para testar UI, NÃO
  // um pareamento nativo temporal com o LOGNOVO.
  return { ...base, frames: real.frames,
    label_tel: 'LOGNOVO original (relógio sintético; snapshot de outra sessão)',
    lognovoReplay: { source: real.source, frames: real.frames.length, timebase: real.timebase } };
}

function playwright() {
  try { return require('playwright'); } catch (_) { return null; }
}

// OMEGAS_CHROMIUM: Chromium já instalado (ex.: /usr/bin/chromium) quando o do Playwright não foi baixado.
function chromiumPath(chromium) {
  const env = process.env.OMEGAS_CHROMIUM;
  if (env && fs.existsSync(env)) return env;
  try { const p = chromium.executablePath(); return fs.existsSync(p) ? p : null; } catch (_) { return null; }
}

async function open(chromium, mode, opts = {}) {
  const executablePath = chromiumPath(chromium) || undefined;
  const browser = await chromium.launch({ args: ['--no-sandbox'], executablePath });
  const ctx = await browser.newContext({ viewport: opts.viewport || { width: 1280, height: 720 }, deviceScaleFactor: 1 });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) errors.push(m.type() + ': ' + m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  await page.addInitScript(`window.__DATA=${JSON.stringify(dataFile())};window.__MODE=${JSON.stringify(mode)};window.__SPEED=${opts.speed || 1};window.__SCN=${JSON.stringify(opts.scn || {})};` + MOCK);
  await page.goto(UI, { waitUntil: 'load' });
  return { browser, ctx, page, errors };
}

async function go(page, route) {
  await page.evaluate(r => { document.querySelector(`[data-route="${r}"]`).click(); }, route);
}

module.exports = { open, go, playwright, chromiumPath, ROOT };
