'use strict';
// Render real (Chromium + Playwright, sem emulador) da UI com a ponte falsa. Classe de prova 4 (parcial).
// PLAYWRIGHT_BROWSERS_PATH aponta para o Chromium já instalado; nunca rode "playwright install" aqui.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const cp = require('node:child_process');

const ROOT = path.resolve(__dirname, '../../..');
const UI = 'file://' + path.join(ROOT, 'app/src/main/assets/ui/index.html');
const MOCK = fs.readFileSync(path.join(__dirname, 'mock-bridge.js'), 'utf8');

function dataFile() {
  const out = path.join(os.tmpdir(), 'omegas-render-data.json');
  if (!fs.existsSync(out)) cp.execFileSync('python3', [path.join(__dirname, 'prep.py'), out], { stdio: 'ignore' });
  return JSON.parse(fs.readFileSync(out, 'utf8'));
}

function playwright() {
  try { return require('playwright'); } catch (_) { return null; }
}

async function open(chromium, mode, opts = {}) {
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
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

module.exports = { open, go, playwright, ROOT };
