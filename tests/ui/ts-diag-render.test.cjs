'use strict';
// UI C · Ferramentas, Sessões e Diagnóstico (08): render real (Chromium) com a ponte falsa. Classe de prova 4 (parcial).
// Contratos novos simulados: stalls.regions[] e fluidity; ausência tratada com elegância.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { open, go, playwright } = require('./render/lib.js');

const pw = playwright();
let ok = false;
if (pw) { try { ok = !!require("./render/lib.js").chromiumPath(pw.chromium); } catch (_) { ok = false; } }
const skip = ok ? false : 'Chromium/Playwright indisponível neste ambiente';

async function audit(page, route) {
  return page.evaluate(r => {
    const sc = document.querySelector(`[data-screen="${r}"]`);
    const small = [], wide = [];
    const w = document.createTreeWalker(sc, NodeFilter.SHOW_TEXT); let n;
    while ((n = w.nextNode())) {
      const t = n.textContent.trim(); if (!t) continue; const e = n.parentElement;
      const b = e.getBoundingClientRect(); if (!b.width) continue;
      if (e.closest('.page-intro small')) continue;
      const f = parseFloat(getComputedStyle(e).fontSize);
      if (f < 18) small.push(`${f}px ${t.slice(0, 20)}`);
    }
    sc.querySelectorAll('*').forEach(e => { const b = e.getBoundingClientRect(); if (b.width && b.right > 1280.5) wide.push(e.className); });
    return { small, wide, scrollsX: sc.scrollWidth > sc.clientWidth + 1, scrollsY: sc.scrollHeight > sc.clientHeight + 1 };
  }, route);
}

test('Diagnóstico com engasgos: gráfico primeiro, frase + atalho ao Refino, toque numa região mostra UMA linha', { skip }, async () => {
  const { browser, page, errors } = await open(pw.chromium, 'connected', { scn: { stalls: 'some' } });
  try {
    await page.waitForTimeout(1500); await go(page, 'diagnostico'); await page.waitForTimeout(2500);
    const order = await page.$$eval('#diagnosticoHost > *', els => els.map(e => e.className.split(' ')[0]));
    assert.deepEqual(order.slice(0, 3), ['dg-card', 'dg-state', 'dg-card']);
    assert.equal(await page.$$eval('.dg-region', e => e.length), 3);
    assert.match(await page.textContent('.dg-state'), /5 engasgos em marcha lenta \(870–1\.350 rpm\)/);
    assert.ok(await page.$('[data-diag-go-refino].btn-primary'), 'atalho para o Refino quando há proposta');
    await page.click('[data-region="0"] .dg-hit', { force: true });
    assert.match(await page.textContent('.dg-region-line'), /Marcha lenta: 3 engasgos perto de 1\.040 rpm/);
    const a = await audit(page, 'diagnostico');
    assert.deepEqual(a.small, []); assert.deepEqual(a.wide, []); assert.equal(a.scrollsX, false);
    assert.deepEqual(errors, []);
    await page.click('[data-diag-go-refino]');
    await page.waitForTimeout(300);
    assert.equal(await page.evaluate(() => window.OmegasApp.store.get().route), 'refino');
  } finally { await browser.close(); }
});

test('Diagnóstico sem engasgos / sem dados / contrato ausente: estado desenhado, sem exceção e sem botão falso', { skip }, async () => {
  for (const [stalls, re, nobtn] of [['none', /Nenhum engasgo registrado/, true], ['nodata', /Ainda sem dados de engasgo/, true], ['absent', /Ainda sem dados de engasgo/, true], ['noproposal', /ainda não tem proposta/, true]]) {
    const { browser, page, errors } = await open(pw.chromium, 'connected', { scn: { stalls } });
    try {
      await page.waitForTimeout(1200); await go(page, 'diagnostico'); await page.waitForTimeout(2200);
      assert.match(await page.textContent('.dg-state'), re, stalls);
      if (nobtn) assert.equal(await page.$('.dg-state [data-diag-go-refino]'), null, stalls);
      assert.doesNotMatch(await page.textContent('#diagnosticoHost'), /NaN|undefined|Infinity/, stalls);
      assert.deepEqual(errors, [], stalls);
    } finally { await browser.close(); }
  }
});

test('Sessões: sem Exportar ZIP, com pasta, estado e resumo; Ferramentas em blocos; sem corte lateral', { skip }, async () => {
  const { browser, page, errors } = await open(pw.chromium, 'connected');
  try {
    await page.waitForTimeout(1200); await go(page, 'sessions'); await page.waitForTimeout(2200);
    assert.equal(await page.$('[data-export-session]'), null);
    assert.equal(await page.$$eval('.ss-item', e => e.length), 5);
    assert.match(await page.textContent('[data-screen="sessions"]'), /Gravando/);
    let a = await audit(page, 'sessions');
    assert.deepEqual(a.small, []); assert.deepEqual(a.wide, []); assert.equal(a.scrollsX, false);
    await go(page, 'tools'); await page.waitForTimeout(2200);
    assert.ok((await page.$$('.ts-card')).length >= 5);
    a = await audit(page, 'tools');
    assert.deepEqual(a.small, []); assert.deepEqual(a.wide, []); assert.equal(a.scrollsX, false);
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
});
