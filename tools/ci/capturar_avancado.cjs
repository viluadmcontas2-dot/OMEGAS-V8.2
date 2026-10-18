// Imagens reais (Chromium 1280x720) de Ferramentas > Avançado com UMA ponte de mentira (nenhuma ECU): desligado, prévia, ligado
// e (opcional) detalhes. Uso: node tools/ci/capturar_avancado.cjs [pasta-saida]   (padrão docs/evidence/img)
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const { fakeBridge } = require('./_ponte_falsa.cjs');
const OUT = path.resolve(process.argv[2] || 'docs/evidence/img');
fs.mkdirSync(OUT, { recursive: true });

// Valores reais lidos no LOGNOVO (12/08/2026): FLAG_CONF1 2486 1051, FLAG_CONF2 0001 0000, FLAG_CONF3 0000 0000,
// ANTI_STALLO 1600 100 1500 2 20, GIRI_PER_BENZINA 0 8500, NUMERO_INJ_BENZINA_CUTOFF 0, PARAM_PROGRESSIONI 4 linhas.
function ponteAvancado() {
  const ok = values => ({ state: 'OK', values });
  const params = {
    FLAG_CONF1: ok([0x2486, 0x1051]), FLAG_CONF2: ok([1, 0]), FLAG_CONF3: ok([0, 0]), ANTI_STALLO: ok([1600, 100, 1500, 2, 20]),
    GIRI_PER_BENZINA: ok([0, 8500]), NUMERO_INJ_BENZINA_CUTOFF: ok([0]),
    PARAM_PROGRESSIONI_L0: ok([0, 4, 500]), PARAM_PROGRESSIONI_L1: ok([20, 4, 500]), PARAM_PROGRESSIONI_L2: ok([20, 4, 500]), PARAM_PROGRESSIONI_L3: ok([25, 4, 500]),
  };
  const A = window.OmegasUi.AdvancedFlags;
  let status = { state: 'IDLE' }, pending = null, undo = null;
  const base = window.OmegasAutoCal;
  window.__ecuLog = [];
  window.OmegasAutoCal = {
    ...base,
    getAdvancedParams: () => JSON.stringify({ ok: true, read: true, trigger: 'AUTO_START', params }),
    prepareAntiStalloFlag: enable => {
      const plan = A.planAntiStallo(params.FLAG_CONF3.values, enable);
      pending = { plan, token: 't' + Math.random() };
      status = { state: 'READY', token: pending.token, enable, before: plan.before, after: plan.after, frameHex: plan.frameHex, noop: plan.noop };
      window.__ecuLog.push('prepare');
      return JSON.stringify({ ok: true, started: true });
    },
    confirmAntiStalloFlag: token => {
      if (!pending || pending.token !== token) return JSON.stringify({ ok: false, error: 'token' });
      const before = params.FLAG_CONF3.values.slice();
      params.FLAG_CONF3 = ok(pending.plan.after.slice());
      undo = { available: true, enable: (before[0] & 0x20) !== 0 };
      status = { state: 'DONE', outcome: 'CONFIRMED', readback: params.FLAG_CONF3.values, before, after: pending.plan.after, undo };
      window.__ecuLog.push('write ' + pending.plan.frameHex);
      return JSON.stringify({ ok: true, started: true });
    },
    cancelAntiStalloFlag: () => { status = { state: 'IDLE' }; return '{"ok":true}'; },
    getAntiStalloFlagStatus: () => JSON.stringify({ ...status, undo: undo || { available: false } }),
  };
}

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e.message || e)));
  await page.addInitScript(fakeBridge);
  await page.goto('file://' + path.resolve('app/src/main/assets/ui/index.html'), { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await page.evaluate(ponteAvancado);
  await page.evaluate(() => { document.querySelectorAll('.alert-toast').forEach(x => x.remove()); });
  await page.locator('.side-nav button[data-route="tools"]').click();
  await page.waitForTimeout(500);
  await page.locator('#toolAdvancedToggle').click();
  await page.waitForTimeout(700);
  const shot = async name => { await page.waitForTimeout(500); await page.evaluate(() => document.querySelectorAll('.alert-toast').forEach(x => x.remove())); await page.screenshot({ path: path.join(OUT, `avancado-${name}.png`) }); };
  await shot('1-desligado');
  await page.locator('[data-adv-confirm]').click();           // 1º toque: prévia
  await page.waitForSelector('[data-testid="adv-preview"]', { timeout: 4000 });
  await shot('2-previa');
  await page.locator('[data-adv-confirm]').click();           // 2º toque: grava
  await page.waitForSelector('.adv-result[data-level="ok"]', { timeout: 4000 });
  await shot('3-ligado');
  await page.locator('[data-adv-details]').click();
  await page.waitForTimeout(300);
  await shot('4-detalhes-ajustes3');
  await page.locator('[data-adv-tab="FLAG_CONF2"]').click();
  await shot('5-detalhes-ajustes2');
  await page.locator('[data-adv-tab="PARAMS"]').click();
  await shot('6-detalhes-numeros');
  console.log('ECU falsa recebeu:', JSON.stringify(await page.evaluate(() => window.__ecuLog)), 'erros de página:', errors.length ? errors : 'nenhum');
  await browser.close();
  if (errors.length) process.exit(1);
})().catch(e => { console.error(e); process.exit(1); });
