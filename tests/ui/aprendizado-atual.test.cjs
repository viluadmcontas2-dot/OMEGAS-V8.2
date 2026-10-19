'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const T = require('./helpers/browser.cjs');
const root = path.resolve(__dirname, '../..');

test('arquivo antigo não tem entrada na interface, ponte, atividade ou serviço', () => {
  for (const file of ['assets/ui/core/native-api.js', 'assets/ui/components/drawers.js',
    'java/com/omegas/prohub/web/HubJavascriptBridge.kt', 'java/com/omegas/prohub/MainActivity.kt',
    'java/com/omegas/prohub/service/TelemetryForegroundService.kt',
    'java/com/omegas/prohub/learning/LearningArchiveManager.kt']) {
    const text = fs.readFileSync(path.join(root, 'app/src/main', file), 'utf8');
    assert.equal(/importLearning|toolImportLearning|Aprendizado de outro arquivo|fun import\(resolver/.test(text), false, file);
  }
});

test('Ferramentas usa cópia única e não pede salvar aprendizado separado', {skip: T.skipReason() || false}, async () => {
  const browser = await T.launch();
  try {
    for (const [width,height] of [[1280,720],[1024,600],[800,480]]) {
      const app = await T.openApp(browser, {viewport:{width,height}, usb:true});
      try {
        await T.route(app, 'tools'); await T.pump(app.page, 3);
        await T.tap(app.page, '[data-tools-page="2"]'); await T.pump(app.page, 3);
        assert.equal(await app.page.locator('#toolExportLearning, #toolImportLearning').count(), 0);
        assert.match(await app.page.locator('[data-card="copia"]').innerText(), /Inclui o aprendizado atual/);
        const clipped = await app.page.locator('#toolDiagnosticsWorkspace .tile-line').evaluateAll(nodes => nodes.filter(e => e.scrollHeight > e.clientHeight + 1).map(e => e.textContent));
        assert.deepEqual(clipped, [], 'texto deve caber inteiro');
        const overflow = await app.page.evaluate(() => document.documentElement.scrollHeight > innerHeight);
        assert.equal(overflow, false);
        await app.page.screenshot({path:path.join(root, `docs/evidence/img/aprendizado-atual-${width}x${height}.png`)});
      } finally { await app.context.close(); }
    }
  } finally { await browser.close(); }
});
