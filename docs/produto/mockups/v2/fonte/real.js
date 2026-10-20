// Telas 15-18: o app REAL (assets/ui/index.html, ponte falsa da CI) com só as duas barras trocadas na hora da foto:
// navegação de 5 botões (sem Sessões, sem marca, sem status) e bolinhas do nível + lâmpada na barra do topo.
// Nada do app é alterado em disco. Uso (da raiz do repo): NODE_PATH=$(npm root -g) node docs/produto/mockups/v2/fonte/real.js
const { chromium } = require('playwright');
const path = require('path');
const ROOT = path.resolve(__dirname, '../../../../..');
const { fakeBridge } = require(path.join(ROOT, 'tools/ci/_ponte_falsa.cjs'));
const INDEX = path.join(ROOT, 'app/src/main/assets/ui/index.html');
const SHOTS = [['autocal', '15-autocal'], ['refino', '16-refino'], ['map', '17-ajuste-local'], ['curve', '18-ajuste-global']];
const PATCH = `
.side-nav button[data-route="sessions"],.brand-block,.rail-status{display:none!important}
.side-nav{gap:8px!important}.side-nav button span{font-size:22px!important;font-weight:800!important;white-space:normal!important}
.v2lvl{display:flex;align-items:center;gap:7px;padding:6px 12px;border:1px solid rgba(80,170,220,.18);border-radius:10px;background:rgba(9,21,33,.72)}
.v2lvl small{color:#8fa3b6;font-size:11px;font-weight:700}.v2lvl i{width:16px;height:16px;border-radius:50%;background:#16202b;border:1px solid #2c3a4a}
.v2lvl i.on{background:#5fe38a;border-color:#9bf5b6;box-shadow:0 0 8px #5fe38aaa}
.v2lamp{display:flex;align-items:center;gap:8px;height:34px;padding:0 14px;border-radius:10px;font-size:20px;font-weight:850;background:#0f2a1c;color:#5fe38a;white-space:nowrap}
.v2lamp b{width:14px;height:14px;border-radius:50%;background:#5fe38a}
@media (max-width:900px){.v2lvl{gap:4px;padding:4px 6px}.v2lvl i{width:12px;height:12px}.v2lamp{font-size:18px;padding:0 8px}}`;
(async () => {
  const b = await chromium.launch({ args: ['--no-sandbox'] });
  for (const [w, h] of [[1280, 720], [800, 480]]) {
    const page = await b.newPage({ viewport: { width: w, height: h } });
    await page.addInitScript(fakeBridge);
    await page.goto('file://' + INDEX, { waitUntil: 'load' });
    await page.waitForTimeout(1500);
    await page.evaluate(() => {
      const frame = () => ({ valid: true, ageMs: 40, telemetryAgeMs: 40, sequence: Date.now(), live: { rpm: 860, petrol_ms: 3.44, load_bar: 0.39, fuel: 'GNV', unknown_raw_19: 221 } });
      window.OmegasApp.api.presentSnapshot = () => ({ ok: true, revision: Date.now(), data: frame() });
    });
    await page.addStyleTag({ content: PATCH });
    await page.evaluate(() => {
      const bar = document.getElementById('liveBar');
      const lvl = document.createElement('div'); lvl.className = 'v2lvl';
      lvl.innerHTML = '<small>Nível</small><i></i><i class="on"></i><i></i><i></i><i></i>';
      const lamp = document.createElement('div'); lamp.className = 'v2lamp'; lamp.innerHTML = '<b></b>' + (innerWidth < 900 ? 'Gás' : 'No gás');
      bar.append(lvl, lamp);
    });
    for (const [route, name] of SHOTS) {
      await page.locator(`.side-nav button[data-route="${route}"]`).click();
      await page.waitForTimeout(900);
      await page.evaluate(() => document.querySelectorAll('.alert-toast').forEach(x => x.remove()));
      await page.screenshot({ path: path.join(__dirname, '..', `${name}-${w}x${h}.png`) });
      console.log('ok', name, w);
    }
    await page.close();
  }
  await b.close();
})().catch((e) => { console.error(e); process.exit(1); });
