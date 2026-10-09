'use strict';
// curva-salvamento-so-manual (regra 15), parte visual: no rodapé da Curva K, Salvar existe e funciona; nenhum aviso de
// "foto salva/backup" aparece sem o toque (nem ao entrar, nem ao Resetar, nem ao esperar). Classe de prova 4 (render real, ponte falsa).
const test = require('node:test');
const assert = require('node:assert/strict');
const { open, go, playwright, chromiumPath } = require('./render/lib.js');

const pw = playwright();
let ok = false;
if (pw) { try { ok = !!chromiumPath(pw.chromium); } catch (_) { ok = false; } }
const skip = ok ? false : 'Chromium/Playwright indisponível neste ambiente';
const AVISO = /foto salva|backup salvo|Download\/Omegas|salva manualmente|Salvando/i;

async function curva(fn) {
  const { browser, page } = await open(pw.chromium, 'connected', { viewport: { width: 1280, height: 720 }, scn: { noStalls: true } });
  try { await go(page, 'curve'); await page.waitForTimeout(2200); return await fn(page); } finally { await browser.close(); }
}
const salvarCalls = page => page.evaluate(() => window.__mockCalls['C.startCurveBackup'] || 0);
const avisos = page => page.evaluate(() => {
  const s = document.querySelector('.screen.active');
  return [...document.querySelectorAll('#alertToast, #curveBackupStatus, #curveOperationTitle, #curveOperationMessage')].filter(e => e.getBoundingClientRect().width > 0 || e.id === 'alertToast').map(e => e.textContent).join(' | ') + ' || ' + s.querySelector('.curve-foot').innerText;
});

test('Curva K: sem tocar em Salvar, nenhum arquivo e nenhum aviso de foto/backup (entrada, espera)', { skip }, async () => {
  await curva(async page => {
    await page.waitForTimeout(3000);
    assert.equal(await salvarCalls(page), 0, 'a Curva K criou arquivo sem o toque em Salvar');
    const texto = await avisos(page);
    assert.doesNotMatch(texto.replace(/Salvar\b/g, ''), AVISO, `aviso de foto/backup sem toque: ${texto}`);
  });
});

test('Curva K: Resetar não salva arquivo nem avisa foto salva (a foto do Desfazer é privada)', { skip }, async () => {
  await curva(async page => {
    await page.evaluate(() => document.querySelector('.curve-photos').setAttribute('open', ''));
    await page.click('#curveResetButton');
    await page.waitForTimeout(600);
    const durante = await page.evaluate(() => (document.querySelector('#curveOperationTitle') || {}).textContent || '');
    assert.doesNotMatch(durante, /Foto antes · salvando|salvando a Curva K atual/i, 'etapa de foto visível ao Resetar');
    await page.waitForTimeout(3500);
    assert.equal(await salvarCalls(page), 0, 'Resetar criou arquivo da curva');
    const texto = await avisos(page);
    assert.doesNotMatch(texto.replace(/Salvar\b/g, ''), /foto salva|backup salvo|Download\/Omegas|salva manualmente/i, texto);
  });
});

test('Curva K: o botão Salvar do rodapé existe, é visível e UM toque cria UM arquivo em Downloads/Omegas/Curva', { skip }, async () => {
  await curva(async page => {
    const box = await page.evaluate(() => { const b = document.querySelector('.screen.active .curve-foot #curveSaveButton'); const r = b && b.getBoundingClientRect(); return r ? { w: r.width, h: r.height, text: b.textContent.trim(), disabled: b.disabled } : null; });
    assert.ok(box && box.w > 0 && box.h > 0, 'Salvar sumiu do rodapé da Curva K');
    assert.equal(box.text, 'Salvar');
    assert.equal(box.disabled, false);
    await page.click('#curveSaveButton');
    await page.waitForTimeout(1500);
    assert.equal(await salvarCalls(page), 1, 'um toque em Salvar = um arquivo');
    const status = await page.evaluate(() => document.getElementById('curveBackupStatus').textContent);
    assert.match(status, /Download\/Omegas\/Curva\/Curva K - \d{2}-\d{2}-\d{4} \d{2}h\d{2}m\d{2}s - 30 pontos - salva manualmente\.json/, `o dono não vê onde salvou: ${status}`);
  });
});
