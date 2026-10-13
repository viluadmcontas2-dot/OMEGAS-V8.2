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
    await page.waitForFunction(() => /Gravar \d+ alterações/.test(document.getElementById('mapReviewButton')?.textContent || ''), null, { timeout: 8000 });
    await page.waitForTimeout(200);
    await shot('08-mapa-k-proposta-curva-para-mapa.png');
    if (errors.length) { console.error('erros de página:', errors); process.exit(1); }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
