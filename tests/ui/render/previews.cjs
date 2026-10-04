'use strict';
// Previews 1280x720 de AutoCal e Refino para aprovação do dono (uso: node previews.cjs <pasta>).
// Dados REAIS de fixtures/autocal/real (snapshot + telemetria); Refino/betweenPoints/refinoState são SINTÉTICOS no mock-bridge.
const fs = require('node:fs');
const { open, go, playwright } = require('./lib.js');
const out = process.argv[2];
fs.mkdirSync(out, { recursive: true });
const ALL = [1, 1, 1, 1];
const SHOTS = [
  { name: 'autocal-1-vivo', route: 'autocal', scn: {} },
  { name: 'autocal-2-ponto-selecionado', route: 'autocal', scn: {}, tap: '#autocalReferenceChart [data-autocal-acquired-fuel="GAS"]' },
  { name: 'autocal-3-falta-zona-real', route: 'autocal', scn: { zonesGas: [1, 1, 0, 1] }, scroll: false },
  { name: 'autocal-4-ecu-tudo-adquirido', route: 'autocal', scn: { zonesGas: ALL, zonesPetrol: ALL, autoMatch: 3 } },
  { name: 'autocal-5-secundario-rolado', route: 'autocal', scn: {}, scrollTo: 9999 },
  { name: 'refino-1-coletando', route: 'refino', scn: { phase: 'COLETANDO_NOSSOS' } },
  { name: 'refino-2-ponto-tocado', route: 'refino', scn: { phase: 'COLETANDO_NOSSOS' }, tap: '#refinoChart [data-chart-our]' },
  { name: 'refino-3-pronto-para-gravar', route: 'refino', scn: { phase: 'PROPOSTA_PRONTA' } },
  { name: 'refino-4-verificando', route: 'refino', scn: { phase: 'VERIFICANDO' } },
  { name: 'refino-5-estavel', route: 'refino', scn: { phase: 'ESTAVEL', noUndo: true, noStalls: true } },
  { name: 'refino-6-sem-ecu', route: 'refino', scn: { phase: 'SEM_ECU', noUndo: true, noStalls: true }, mode: 'disconnected' },
  { name: 'refino-7-sem-contrato-novo', route: 'refino', scn: { phase: 'COLETANDO_NOSSOS', noBetween: true, noRefinoState: true } },
  { name: 'refino-8-gravado', route: 'refino', scn: { phase: 'PROPOSTA_PRONTA' }, press: '[data-refino-primary]', wait: 4500 },
];
(async () => {
  const pw = playwright();
  const only = process.argv[3];
  for (const shot of SHOTS) {
    if (only && !shot.name.includes(only)) continue;
    const { browser, page, errors } = await open(pw.chromium, shot.mode || 'connected', { scn: shot.scn });
    await page.waitForTimeout(1500);
    await go(page, shot.route);
    await page.waitForTimeout(2500);
    if (shot.tap) { await page.evaluate(sel => { const nodes = [...document.querySelectorAll(sel)]; const e = nodes[Math.min(nodes.length - 1, 3)]; e.dispatchEvent(new MouseEvent('click', { bubbles: true })); }, shot.tap); await page.waitForTimeout(400); }
    if (shot.press) { await page.evaluate(sel => document.querySelector(sel).click(), shot.press); await page.waitForTimeout(shot.wait || 1000); }
    if (shot.scrollTo) { await page.evaluate(() => { const sc = document.querySelector('.screen.active'); sc.scrollTop = 99999; }); await page.waitForTimeout(300); }
    await page.screenshot({ path: `${out}/${shot.name}.png` });
    if (errors.length) console.log(shot.name, errors.slice(0, 3).join(' | '));
    await browser.close();
  }
})();
