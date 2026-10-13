// Curva K → Mapa K (dono, 2026-10-13) no app real (index.html, modo demonstração): o botão lê a curva, deixa a PROPOSTA
// na grade e libera "Gravar"; nada é gravado (a demonstração recusa escrita) e as células mudam como valor × fator da linha.
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
    await page.locator('.side-nav button[data-route="map"]').click();
    await page.waitForSelector('#mapGrid .map-k-cell', { timeout: 8000 });
    const before = await page.$$eval('#mapGrid .map-k-cell b', nodes => nodes.map(n => Number(n.textContent)));
    assert.equal(before.length, 144);
    await page.locator('#mapFromCurve').click();
    await page.waitForFunction(() => /Gravar \d+ alterações/.test(document.getElementById('mapReviewButton')?.textContent || ''), null, { timeout: 8000 });
    const state = await page.evaluate(() => ({
      button: document.getElementById('mapReviewButton').textContent,
      disabled: document.getElementById('mapReviewButton').disabled,
      selection: document.getElementById('mapSelectionCount').textContent,
      grid: [...document.querySelectorAll('#mapGrid .map-k-cell')].map(c => ({ shown: Number(c.querySelector('b').textContent), prop: c.querySelector('span').textContent })),
      fromCurveEnabled: !document.getElementById('mapFromCurve').disabled,
    }));
    assert.equal(state.disabled, false);
    assert.equal(state.fromCurveEnabled, true, 'o botão volta a ficar disponível');
    const changed = state.grid.filter(c => c.prop.trim() !== '');
    assert.ok(changed.length > 0, 'há células propostas: ' + state.button);
    // Cada célula proposta mostra "atual→novo": o atual é o da ECU (nada foi gravado) e o novo é atual × fator da linha.
    state.grid.forEach((cell, index) => {
      if (!cell.prop.trim()) return;
      const [current, target] = cell.prop.split('→').map(Number);
      assert.equal(current, before[index], 'célula ' + index + ': o valor atual da ECU não mudou');
      assert.equal(cell.shown, target);
      assert.ok(target >= 100 && target <= 255, 'alvo dentro de 100–255');
    });
    assert.deepEqual(errors, [], 'sem erros de página');
    console.log('CURVA_PARA_MAPA=PASS', JSON.stringify({ button: state.button, selection: state.selection, proposedCells: changed.length }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
