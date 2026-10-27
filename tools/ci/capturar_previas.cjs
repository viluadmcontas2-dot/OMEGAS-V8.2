// Gera as prévias (PNG 1280x720) das telas mexidas, no app real em modo demonstração, para o dono aprovar antes do APK.
// Uso: node tools/ci/capturar_previas.cjs <pasta-de-saida>
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const INDEX = path.resolve('app/src/main/assets/ui/index.html');
const OUT = path.resolve(process.argv[2] || 'build/ui-evidence/previas');
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errors = [];
    page.on('pageerror', e => errors.push(String(e.message || e)));
    await page.goto('file://' + INDEX, { waitUntil: 'load' });
    await page.waitForTimeout(1500);
    const shot = async name => { await page.screenshot({ path: path.join(OUT, name) }); console.log('ok', name); };
    // Curva K: antes e durante/depois do arrasto
    await page.locator('.side-nav button[data-route="curve"]').click();
    await page.waitForSelector('#curveChart [data-curve-dot="5"]', { timeout: 8000 });
    await shot('04-curva-k-antes.png');
    const box = await page.locator('#curveChart [data-curve-dot="5"]').boundingBox();
    const cx = box.x + box.width / 2, cy = box.y + box.height / 2;
    await page.mouse.move(cx, cy); await page.mouse.down();
    for (let i = 1; i <= 24; i += 1) { await page.mouse.move(cx, cy - i * 3); await page.waitForTimeout(8); }
    await shot('05-curva-k-arrastando.png');
    await page.mouse.up(); await page.waitForTimeout(250);
    await shot('06-curva-k-previa-apos-soltar.png');
    // Mapa K: grade lida e proposta Curva K -> Mapa K
    await page.locator('.side-nav button[data-route="map"]').click();
    await page.waitForSelector('#mapGrid .map-k-cell', { timeout: 8000 });
    await shot('07-mapa-k-lido.png');
    await page.locator('#mapFromCurve').click();
    await page.waitForFunction(() => /Gravar \d+ células/.test(document.getElementById('mapReviewButton')?.textContent || ''), null, { timeout: 8000 });
    await page.waitForTimeout(200);
    await shot('08-mapa-k-proposta-curva-para-mapa.png');
    await page.locator('.side-nav button[data-route="autocal"]').click().catch(() => {}); await page.waitForTimeout(600); await shot('09-autocal-referencia.png');
    // Refino e Refino local com dados de exemplo (o demo não tem aquisição da ECU): snapshot real capturado + nossos pontos + engasgos.
    const snapshot = JSON.parse(fs.readFileSync(path.resolve('tests/ui/fixtures/autocal-captura-ac-t1000.json'), 'utf8')).snapshot;
    await page.evaluate(snap => {
      const api = window.OmegasUi.AutoCalApi;
      const stalls = {
        count: 2, nearCount: 4,
        events: [{ kind: 'STALL', petrolMs: 2.4, mapBar: 0.34 }, { kind: 'STALL', petrolMs: 2.2, mapBar: 0.3 }],
        nearEvents: [{ kind: 'NEAR_STALL', petrolMs: 2.1, mapBar: 0.28 }, { kind: 'NEAR_STALL', petrolMs: 2.6, mapBar: 0.36 }, { kind: 'NEAR_STALL', petrolMs: 3.2, mapBar: 0.42 }],
        regions: [{ fromMs: 2.0, toMs: 2.5, count: 2, mapBar: 0.32, rpmBefore: 1500, rpmLow: 1400, rpmHigh: 1600 }],
        nearRegions: [{ fromMs: 2.0, toMs: 2.5, count: 4, mapBar: 0.3, rpmBefore: 1450, rpmLow: 1350, rpmHigh: 1700 }],
      };
      const dense = { petrol: [], gas: [] };
      for (let i = 0; i < 14; i += 1) {
        const ms = 1.6 + i * 0.55;
        dense.petrol.push({ tpetMs: ms, mapBar: 0.2 + ms * 0.095, samples: 40 });
        if (i < 11) dense.gas.push({ tpetMs: ms + 0.05, mapBar: 0.2 + ms * 0.095 + (i % 3 === 0 ? 0.008 : -0.004), samples: 30 });
      }
      api.available = () => true;
      api.projection = () => ({ ok: true, snapshot: snap });
      api.equivalence = () => ({ autopilot: { phase: 'COLETANDO_NOSSOS', headline: 'Coletando os nossos pontos', next: 'Dirija normalmente: o refino mede e propõe a curva sozinho.', action: { kind: 'waiting', label: 'Aguardando', enabled: false } }, denseBands: dense, stalls });
      api.stalls = () => ({ ok: true, stalls });
    }, snapshot);
    await page.evaluate(() => document.querySelectorAll('.alert-toast').forEach(n => n.remove()));
    await page.locator('.side-nav button[data-route="refino"]').click();
    await page.waitForTimeout(500);
    await page.evaluate(() => window.OmegasApp.refino.refresh(true));
    await page.waitForTimeout(400);
    await shot('15-refino-mesmo-grafico-do-autocal.png');
    await page.locator('.side-nav button[data-route="local"]').click();
    await page.waitForTimeout(500);
    await page.evaluate(() => window.OmegasApp.localRefino.refresh(true));
    await page.waitForTimeout(300);
    await shot('16-refino-local-um-botao.png');
    await page.locator('.local-main').click();
    await page.waitForSelector('[data-local-action="write"]', { timeout: 15000 });
    await shot('17-refino-local-pronto-para-gravar.png');
    if (errors.length) { console.error('erros de página:', errors); process.exit(1); }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
