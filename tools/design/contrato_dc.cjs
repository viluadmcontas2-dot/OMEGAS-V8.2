#!/usr/bin/env node
'use strict';
// Uso: node tools/design/contrato_dc.cjs tela1.dc.html tela2.dc.html [...]
// Renderiza no viewport canônico; compara todos os data-fluxo (tolerância 2px).
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require('playwright');
async function medir(page) {
  return page.evaluate(() => Array.from(document.querySelectorAll('[data-fluxo]')).map(el => {
    const r = el.getBoundingClientRect();
    let visivel = r.width > 0 && r.height > 0 && r.right > 0 && r.bottom > 0 && r.left < innerWidth && r.top < innerHeight;
    for (let p = el; p; p = p.parentElement) {
      const s = getComputedStyle(p);
      if (s.display === 'none' || s.visibility === 'hidden' || Number(s.opacity) === 0) visivel = false;
    }
    return { id: el.dataset.fluxo, visivel, x: r.x, y: r.y, width: r.width, height: r.height };
  }));
}
function comparar(base, atual, nome) {
  const violacoes = [];
  for (const id of new Set(['principal', 'ultima', 'desfazer', ...base.map(e => e.id), ...atual.map(e => e.id)])) {
    const aa = base.filter(e => e.id === id), bb = atual.filter(e => e.id === id);
    if (aa.length !== 1 || bb.length !== 1) { violacoes.push(`${nome}: ${id} ausente, apareceu ou duplicado`); continue; }
    const a = aa[0], b = bb[0];
    if (!a.visivel || !b.visivel) violacoes.push(`${nome}: ${id} invisível`);
    for (const k of ['x', 'y', 'width', 'height']) if (Math.abs(a[k] - b[k]) > 2) violacoes.push(`${nome}: ${id}.${k} mudou ${Math.abs(a[k] - b[k])}px`);
  }
  return violacoes;
}
async function abrir(page, file) {
  const source = fs.readFileSync(file, 'utf8');
  await page.route('**/support.js', route => route.fulfill({ path: path.join(__dirname, 'dc-runtime.js'), contentType: 'text/javascript' }));
  await page.goto(pathToFileURL(path.resolve(file)).href);
  if (source.includes('<x-dc>')) {
    await page.waitForFunction(() => window.__dcReady || window.__dcError);
    const error = await page.evaluate(() => window.__dcError);
    if (error) throw new Error(String(error));
  }
  await page.evaluate(() => document.fonts.ready);
}
async function verificar(files, steps = {}) {
  if (!files.length) throw new Error('Informe pelo menos uma tela');
  const browser = await chromium.launch();
  const medidas = [], violacoes = [];
  try {
    for (const file of files) {
      const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
      const errors = [];
      page.on('pageerror', e => errors.push(e.message));
      await abrir(page, file);
      for (const step of [null, ...(steps[path.basename(file)] || [])]) {
        if (step) await page.locator(step).click();
        const atual = await medir(page);
        violacoes.push(...comparar(medidas[0]?.elementos || atual, atual, file));
        medidas.push({ tela: file, passo: step, elementos: atual });
      }
      violacoes.push(...errors.map(e => `${file}: ${e}`));
      await page.close();
    }
  } finally { await browser.close(); }
  return { medidas, violacoes };
}
module.exports = { verificar, medir, comparar, abrir };
if (require.main === module) verificar(process.argv.slice(2)).then(r => {
  console.log(JSON.stringify(r, null, 2));
  process.exitCode = r.violacoes.length ? 1 : 0;
}).catch(e => { console.error(e); process.exitCode = 1; });
