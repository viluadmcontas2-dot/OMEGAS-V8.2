'use strict';
// Cenários de TOQUE da nova Ferramentas (cartões por assunto) e do anti-apagão (interruptor + 5 números). Chromium + touchscreen,
// central emulada (helpers/ecu-bench.js). Reutilizado por campos-editaveis, ux-fluxo-toque e as provas de mutação.
const assert = require('node:assert/strict');
const T = require('./browser.cjs');

const LIGAR = '35 FA 00 20 00 00 00 4F';
const DESLIGAR = '35 FA 00 00 00 00 00 2F';
const hexSum = bytes => (bytes.reduce((a, b) => a + b, 0) & 255).toString(16).toUpperCase().padStart(2, '0');
/** Quadro esperado dos 5 números (conta independente: 37, end. baixo, n+1, end. alto, dados, soma). */
function quadroValores(valores) {
  const dados = valores.flatMap(v => [v & 255, v >> 8]);
  const corpo = [0x37, 0x33, dados.length + 2, 0x01, ...dados];
  return [...corpo, corpo.reduce((a, b) => a + b, 0) & 255].map(b => b.toString(16).toUpperCase().padStart(2, '0')).join(' ');
}
const FABRICA = [1600, 100, 1500, 2, 20];

const text = (page, sel) => page.locator(sel).first().innerText().then(t => t.replace(/\s+/g, ' ').trim());
const count = (page, sel) => page.locator(sel).locator('visible=true').count();
const ecuWrites = page => T.frames(page).then(f => T.writesOf(f.log).map(x => x.hex));
const clearLog = page => page.evaluate(() => window.__bench.ecu.clearLog());
const flag3 = page => page.evaluate(() => window.__bench.ecu.get('29 FA 00 23'));
const anti5 = page => page.evaluate(() => { const d = window.__bench.ecu.get('29 33 01 5D'); const out = []; for (let i = 0; i < d.length; i += 2) out.push(d[i] | (d[i + 1] << 8)); return out; });
const camada = page => page.locator('#toolsLayer:not([hidden]) .tool-layer-card').count();

async function abrirMotor(app) {
  await T.route(app, 'tools');
  await T.pump(app.page, 3);
}
async function abrirValores(app) {
  await abrirMotor(app);
  await T.tap(app.page, '[data-adv-open="valores"]');
  await T.pump(app.page, 3);
}
/** Todos os 5 ajustes ficam expostos na mesma tela (cada um no seu card): não há mais navegação até o campo. */
async function irParaCampo() {}
/** Digita no campo por toque (foca pelo toque no campo) e dispara a validação, como o teclado do Android faria. */
async function digitar(app, indice, valor) {
  const { page } = app;
  await irParaCampo(app, indice);
  await T.tap(page, `#advField${indice}`);
  await page.locator(`#advField${indice}`).fill(String(valor));
  await T.pump(page, 1);
}

/** Imagem para o dono: docs/evidence/img/ux-<nome>.png (sem avisos soltos por cima). */
async function foto(page, nome) {
  const fs = require('node:fs');
  const path = require('node:path');
  fs.mkdirSync(T.IMG_DIR, { recursive: true });
  await page.evaluate(() => document.querySelectorAll('.alert-toast.show').forEach(x => x.classList.remove('show')));
  const file = path.join(T.IMG_DIR, `ux-${nome}.png`);
  await page.screenshot({ path: file });
  return file;
}

module.exports = { foto, LIGAR, DESLIGAR, FABRICA, quadroValores, hexSum, text, count, ecuWrites, clearLog, flag3, anti5, camada, abrirMotor, abrirValores, irParaCampo, digitar };
