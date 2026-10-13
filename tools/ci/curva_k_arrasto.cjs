// Arrasto de nó na Curva K (dono, 2026-10-13): mede o app real (index.html, modo demonstração) no Chromium.
// Prova: arrastar um nó muda o valor e prepara a prévia (nada é escrito), e durante o arrasto o gráfico NÃO é
// redesenhado (zero mutações de filhos em #curveChart) — a fluidez vem de mover só o nó e a linha tracejada.
const { chromium } = require('playwright');
const path = require('path');
const assert = require('node:assert/strict');
const INDEX = path.resolve('app/src/main/assets/ui/index.html');

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errors = [];
    page.on('pageerror', e => errors.push(String(e.message || e)));
    await page.goto('file://' + INDEX, { waitUntil: 'load' });
    await page.waitForTimeout(1200);
    await page.locator('.side-nav button[data-route="curve"]').click();
    await page.waitForSelector('#curveChart [data-curve-dot="5"]', { timeout: 8000 });
    const dot = page.locator('#curveChart [data-curve-dot="5"]');
    const box = await dot.boundingBox();
    const before = await page.evaluate(() => document.getElementById('curveTargetFactor')?.value || '');
    await page.evaluate(() => {
      window.__mut = 0;
      new MutationObserver(list => { window.__mut += list.filter(m => m.type === 'childList').length; })
        .observe(document.getElementById('curveChart'), { childList: true, subtree: true });
    });
    const cx = box.x + box.width / 2; const cy = box.y + box.height / 2;
    await page.mouse.move(cx, cy);
    await page.mouse.down();
    await page.waitForTimeout(60);
    const mutAfterDown = await page.evaluate(() => window.__mut); // a seleção do ponto redesenha uma vez
    let last = cy;
    for (let i = 1; i <= 20; i += 1) { last = cy - i * 3; await page.mouse.move(cx, last); await page.waitForTimeout(8); }
    const mutDuring = await page.evaluate(() => window.__mut);
    assert.equal(mutDuring - mutAfterDown, 0, 'durante o arrasto o gráfico não pode ser redesenhado (mutações: ' + (mutDuring - mutAfterDown) + ')');
    const dragging = await page.evaluate(() => ({
      input: document.getElementById('curveTargetFactor').value,
      cls: document.getElementById('curveChart').className,
      cy: document.querySelector('#curveChart [data-curve-dot="5"]').getAttribute('cy'),
    }));
    assert.match(dragging.cls, /dragging/);
    assert.notEqual(dragging.input, before, 'o valor acompanha o arrasto');
    await page.mouse.up();
    await page.waitForTimeout(150);
    const done = await page.evaluate(() => ({
      input: document.getElementById('curveTargetFactor').value,
      normalized: document.getElementById('curveTargetNormalized')?.textContent || '',
      proposals: document.querySelectorAll('#curveProposalList > *').length,
      cls: document.getElementById('curveChart').className,
    }));
    assert.doesNotMatch(done.cls, /dragging/);
    assert.match(done.normalized, /→/, 'a prévia do ponto foi preparada: ' + done.normalized);
    assert.deepEqual(errors, [], 'sem erros de página');
    console.log('CURVA_K_ARRASTO=PASS', JSON.stringify({ before, dragging: dragging.input, done: done.input, normalized: done.normalized, mutationsDuringDrag: mutDuring - mutAfterDown }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
