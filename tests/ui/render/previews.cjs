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
    if (process.env.DARK) await page.addStyleTag({ content: ':root{color-scheme:dark;--bg:#0b1118;--rail:#0f1722;--surface:#131d2a;--surface-2:#182434;--surface-3:#1f2e41;--line:#2b3b50;--line-soft:#223144;--text:#e8eef6;--muted:#a9b8ca;--dim:#8193a8;--accent:#4c8dff;--accent-strong:#8fb8ff;--accent-soft:#16294a;--accent-line:#2c4a80;--on-accent:#06101f;--ok:#3ccf91;--warn:#f0b64a;--danger:#ff6b6b;--ok-soft:#10301f;--warn-soft:#33270d;--danger-soft:#3a1517;--danger-line:#7a2b2e;--danger-strong:#ff9a9a;--field-bg:#101a27;--field-border:#34475f;--chart-bg:#0e1621;--petrol:#5b9bff;--cng:#2fd39c;--c-37-99-235-28:rgba(76,141,255,.28)}' });
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
