// Fotos reais (Chromium) das PÁGINAS DE GRUPO de Ferramentas com a central emulada (bancada tests/ui/helpers/ecu-bench.js, estado do
// LOGNOVO): tela inicial + Anti-apagão, Volta para gasolina, Troca, Marcha lenta e Outras. Também mede cartões à vista e altura dos − / +.
// Uso: node tools/ci/capturar_grupos_ferramentas.cjs [pasta-saida] [largura] [altura]   (padrão: docs/evidence/img, 1280x720)
const path = require('node:path');
const fs = require('node:fs');
const T = require('../../tests/ui/helpers/browser.cjs');

const OUT = path.resolve(process.argv[2] || path.join(T.ROOT, 'docs/evidence/img'));
const viewport = { width: Number(process.argv[3]) || 1280, height: Number(process.argv[4]) || 720 };
const GROUPS = ['valores', 'volta', 'troca', 'lenta', 'outras'];

(async () => {
  if (T.skipReason()) { console.error('Sem navegador:', T.skipReason()); process.exit(2); }
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await T.launch();
  const app = await T.openApp(browser, { viewport, usb: true, scale: 1 });
  const { page } = app;
  const tag = `${viewport.width}x${viewport.height}`;
  await T.route(app, 'tools');
  await page.screenshot({ path: path.join(OUT, `ferramentas-inicio-${tag}.png`) });
  const report = [];
  for (const g of GROUPS) {
    await T.tap(page, `#toolAdvanced [data-adv-open="${g}"]`); await T.pump(page, 4);
    const file = path.join(OUT, `ferramentas-grupo-${g}-${tag}.png`);
    await page.screenshot({ path: file });
    const m = await page.evaluate(() => {
      const grid = document.querySelector('#toolAdvanced .grp-grid'); const gr = grid.getBoundingClientRect();
      const cards = [...grid.querySelectorAll('.grp-card')];
      const inView = cards.filter(c => { const r = c.getBoundingClientRect(); return r.top >= gr.top - 1 && r.bottom <= gr.bottom + 1; }).length;
      const steps = [...grid.querySelectorAll('.step-btn')].map(b => Math.round(b.getBoundingClientRect().height));
      return { cards: cards.length, edit: cards.filter(c => c.dataset.kind === 'edit').length, read: cards.filter(c => c.dataset.kind === 'read').length, inView, stepMin: steps.length ? Math.min(...steps) : null };
    });
    report.push({ grupo: g, ...m, foto: file });
    await T.tap(page, '#toolAdvanced [data-adv-back]'); await T.pump(page, 2);
  }
  console.table(report);
  if (app.problems.page.length || app.problems.console.length) { console.error(app.problems); process.exitCode = 1; }
  await browser.close();
})().catch(error => { console.error(error); process.exit(1); });
