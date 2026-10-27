// "Refinar por minha conta" (contrato de fluxo): o botão existe SEMPRE e no mesmo lugar; sem oferta fica cinza (aria-disabled) com o motivo.
// Com a central ainda no automático um toque assume (o Kotlin passa a dizer ecuDone) e o botão vira "Esperar a central terminar";
// com a central concluída de verdade ele continua lá, cinza, "A central já terminou".
const { chromium } = require('playwright');
const path = require('path');
const assert = require('node:assert/strict');
const { fakeBridge } = require('./_ponte_falsa.cjs');
const INDEX = path.resolve('app/src/main/assets/ui/index.html');

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errors = [];
    page.on('pageerror', e => errors.push(String(e.message || e)));
    await page.addInitScript(fakeBridge);
    await page.goto('file://' + INDEX, { waitUntil: 'load' });
    await page.waitForTimeout(1200);
    await page.locator('.side-nav button[data-route="refino"]').click();
    await page.waitForSelector('#refinoChart .ao-svg', { timeout: 8000 });
    const visible = async sel => page.evaluate(s => { const n = document.querySelector(s); return !!n && !n.hidden && getComputedStyle(n).display !== 'none'; }, sel);
    const SEL = '[data-refino-manual]';
    const foto = () => page.evaluate(s => { const n = document.querySelector(s); const r = n.getBoundingClientRect(); const cs = getComputedStyle(n);
      return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), shown: !n.hidden && cs.display !== 'none' && cs.visibility !== 'hidden',
        aria: n.getAttribute('aria-disabled'), disabled: n.disabled === true, text: n.textContent.trim(), el: n.id || n.className }; }, SEL);
    const lugar = f => [f.x, f.y, f.w, f.h];
    // Contrato: o botão existe SEMPRE; sem o que oferecer fica cinza (aria-disabled) com o motivo dentro, nunca hidden nem disabled.
    const antes = await foto();
    assert.ok(antes.shown && !antes.disabled, 'sem o piloto, o botão continua visível (sem hidden/disabled)');
    assert.equal(antes.aria, 'true', 'sem o piloto dizer "ainda no automático": cinza');
    assert.match(antes.text, /^Refinar por minha conta · /, 'cinza com o motivo dentro');
    await page.evaluate(() => {
      const refino = window.OmegasApp.refino;
      const eq = JSON.parse(JSON.stringify(refino.eq || {}));
      eq.autopilot = { ...(eq.autopilot || {}), phase: 'ECU_TRABALHANDO', ecuDone: false, ecuOnline: true, manual: false, autoCalEnabled: 1, autoMatchCount: 1, maxAutomatch: 3,
        action: { kind: 'waiting', label: 'Aguardando a ECU', enabled: false }, proposal: { known: true, count: 0, points: [] }, secondary: [] };
      window.__manual = [];
      const base = refino.api;
      refino.api = Object.assign(Object.create(base), { equivalence: () => eq, setRefinementManual: on => { window.__manual.push(on); eq.autopilot.manual = on; eq.autopilot.ecuDone = on; return { ok: true }; } });
      refino.refresh(true);
    });
    await page.waitForTimeout(300);
    const oferta = await foto();
    assert.equal(oferta.aria, 'false', 'ECU trabalhando: botão ativo');
    assert.equal(oferta.text, 'Refinar por minha conta');
    assert.deepEqual(lugar(oferta), lugar(antes), 'ativo ou cinza, o botão fica no mesmo lugar e tamanho');
    assert.ok(oferta.x + oferta.w <= 1280 && oferta.y + oferta.h <= 720, 'botão dentro da tela: ' + JSON.stringify(oferta));
    // Enquanto a central ainda aprende, o principal fica cinza com o motivo (e também fixo).
    const principal = await page.evaluate(() => { const n = document.querySelector('#refino-principal'); return { aria: n.getAttribute('aria-disabled'), text: n.textContent.trim(), disabled: n.disabled }; });
    assert.equal(principal.aria, 'true'); assert.equal(principal.disabled, false);
    assert.match(principal.text, /^Gravar correção · /);
    await page.click(SEL);
    assert.deepEqual(await page.evaluate(() => window.__manual), [true], 'um toque = assumir');
    await page.waitForTimeout(1200);
    const assumido = await foto();
    assert.equal(assumido.text, 'Esperar a central terminar', 'depois de assumir, o botão volta ao automático');
    assert.deepEqual(lugar(assumido), lugar(antes), 'mesmo lugar depois de assumir');
    await page.click(SEL);
    assert.deepEqual(await page.evaluate(() => window.__manual), [true, false]);
    // ECU concluída de verdade: o botão NÃO some; fica cinza dizendo "A central já terminou".
    await page.evaluate(() => {
      const refino = window.OmegasApp.refino; const eq = JSON.parse(JSON.stringify(refino.api.equivalence()));
      eq.autopilot.ecuDone = true; eq.autopilot.manual = false; eq.autopilot.ecuDoneReason = 'MAX_AUTOMATCH'; eq.autopilot.phase = 'ESTAVEL';
      refino.api = Object.assign(Object.create(refino.api), { equivalence: () => eq }); refino.refresh(true);
    });
    await page.waitForTimeout(300);
    const fim = await foto();
    assert.ok(fim.shown && !fim.disabled, 'ECU concluída: o botão continua visível');
    assert.equal(fim.aria, 'true');
    assert.equal(fim.text, 'Refinar por minha conta · A central já terminou');
    assert.deepEqual(lugar(fim), lugar(antes), 'mesmo lugar com a central concluída');
    const ordem = await page.evaluate(() => [...document.querySelectorAll('#refinoActions > [data-fluxo]')].map(n => n.getAttribute('data-fluxo')));
    assert.deepEqual(ordem, ['conta', 'ultima', 'principal']);
    assert.deepEqual(errors, [], 'sem erro de página');
    console.log('REFINO_MANUAL=PASS');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exit(1); });
