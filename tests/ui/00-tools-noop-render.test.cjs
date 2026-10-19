'use strict';
// TDD: Ferramentas não deve reescrever atributos iguais a cada quadro de contexto.
// Exercita app.js, Drawers e AdvancedPanel REAIS, no Chromium com central emulada.
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('./helpers/browser.cjs');
const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

test('RED: Ferramentas não reescreve atributos iguais ao receber contexto sem mudança', { skip }, async () => {
  const app = await T.openApp(browser, { viewport: { width: 1280, height: 720 }, usb: true });
  const { page } = app;
  try {
    await T.route(app, 'tools');
    await page.waitForSelector('#toolAdvanced [data-card="anti"]', { state: 'visible' });
    await T.pump(page, 4);
    const data = await page.evaluate(async () => {
      const bridge = window.OmegasAutoCal;
      const original = bridge.getAdvancedParams;
      let reads = 0;
      bridge.getAdvancedParams = (...args) => {
        reads += 1;
        return original.apply(bridge, args);
      };
      const watched = ['toolsDrawer', 'toolAdvanced', 'toolDiagnosticsWorkspace']
        .map(id => document.getElementById(id)).filter(Boolean);
      const equalWrites = [];
      const observer = new MutationObserver(mutations => {
        for (const mutation of mutations) {
          if (mutation.type !== 'attributes' || !watched.includes(mutation.target)) continue;
          if (mutation.oldValue === mutation.target.getAttribute(mutation.attributeName))
            equalWrites.push(mutation.target.id + ':' + mutation.attributeName);
        }
      });
      observer.observe(document.getElementById('toolsDrawer'), {
        attributes: true, attributeOldValue: true, subtree: true,
      });
      for (let n = 0; n < 3; n += 1) {
        window.dispatchEvent(new Event('omegas-refresh'));
        await new Promise(resolve => requestAnimationFrame(() => setTimeout(resolve, 35)));
      }
      observer.disconnect();
      bridge.getAdvancedParams = original;
      return { reads, equalWrites };
    });
    assert.ok(data.reads >= 2, 'o teste precisa realmente exercitar releituras do painel real');
    assert.deepEqual(data.equalWrites, [], 'DOM idêntico reescrito: ' + data.equalWrites.join(', '));
    assert.deepEqual(T.writesOf((await T.frames(page)).log), [], 'atualizar leitura não grava na central');
  } finally { await app.context.close(); }
});
