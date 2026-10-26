'use strict';
// Tela Carro (spec 4.2): só leitura, valores formatados, faixa escrita, aviso de tensão, sem rolagem.
const test = require('node:test');
const assert = require('node:assert/strict');
const B = require('./helpers/browser.cjs');

const why = B.skipReason();
const SIZES = [{ width: 1280, height: 720 }, { width: 1024, height: 600 }, { width: 800, height: 480 }];
const text = (page, sel) => page.locator(sel).first().innerText();
const gauge = (page, k) => page.evaluate(key => {
  const n = document.querySelector(`#carroHost [data-g="${key}"]`);
  return { v: n.querySelector('[data-v]').textContent, s: n.querySelector('[data-s]').textContent, r: n.querySelector('.carro-g-range').textContent, z: n.dataset.zone };
}, k);

test('Carro: sem central, estado único e medidores em —', { skip: why || false }, async () => {
  const browser = await B.launch();
  try {
    const app = await B.openApp(browser, { viewport: SIZES[0], usb: false });
    await B.route(app, 'carro');
    assert.match(await text(app.page, '#carroHost .carro-sit'), /Sem central/);
    for (const k of ['rpm', 'map', 'petrol', 'voltage']) {
      const g = await gauge(app.page, k);
      assert.equal(g.v, '—', k);
      // Só a tensão tem limite comprovado; os outros medidores dizem isso em vez de inventar uma faixa "boa".
      assert.match(g.r, k === 'voltage' ? /14,1 V/ : /Sem faixa de referência comprovada/, 'referência escrita ' + k);
    }
    assert.match(await text(app.page, '#carroHost .carro-diag'), /Conecte o cabo USB/);
    assert.deepEqual(app.problems.page, []);
  } finally { await browser.close(); }
});

test('Carro: com central e quadro, 4 valores formatados e nenhuma gravação', { skip: why || false }, async () => {
  const browser = await B.launch();
  try {
    const app = await B.openApp(browser, { viewport: SIZES[0], usb: true });
    await app.page.evaluate(() => {
      window.__calls = 0;
      const f = window.OmegasAutoCal.getStalls;
      window.OmegasAutoCal.getStalls = () => { window.__calls += 1; return JSON.stringify({ ok: true, stalls: { count: 2 } }); };
    });
    const before = await B.frames(app.page);
    await B.route(app, 'carro');
    await app.page.evaluate(() => { window.__calls = 0; });
    await B.pump(app.page, 12, { data: true });
    await app.page.evaluate(() => { window.__bench.live.load_bar = 0.42; window.__bench.live.unknown_raw_19 = 220; window.__bench.seq += 1; }); // o quadro de bancada não traz pressão
    await B.pump(app.page, 3);
    const vals = {};
    for (const k of ['rpm', 'map', 'petrol', 'voltage']) vals[k] = await gauge(app.page, k);
    assert.match(vals.rpm.v, /^\d{1,3}(\.\d{3})*$/);
    assert.match(vals.map.v, /^\d,\d{2}$/);
    assert.match(vals.petrol.v, /^\d+,\d{2}$/);
    assert.equal(vals.map.v, '0,42');
    assert.equal(vals.voltage.v, '13,8');
    assert.match(vals.voltage.s, /^Normal/);
    for (const k of Object.keys(vals)) assert.match(vals[k].s, /Normal|Ao vivo|Atenção/, 'estado em palavra ' + k);
    assert.match(await text(app.page, '#carroHost .carro-sit'), /Motor apagou 2 vezes/);
    const calls = await app.page.evaluate(() => window.__calls);
    assert.ok(calls >= 1 && calls <= 3, 'stalls() só no ciclo de status: ' + calls); // 12 ciclos de 200 ms = ~2 ciclos de status
    await B.route(app, 'tools');
    await app.page.evaluate(() => { window.__calls = 0; });
    await B.pump(app.page, 12, { data: true });
    assert.equal(await app.page.evaluate(() => window.__calls), 0, 'fora da rota carro não chama stalls()');
    const after = await B.frames(app.page);
    assert.deepEqual(B.writesOf(after.log.slice(before.log.length)), [], 'nenhuma escrita na central');
    assert.deepEqual(after.acoes.slice(before.acoes.length), []);
    assert.deepEqual(app.problems.page, []);
  } finally { await browser.close(); }
});

test('Carro: tensão >= 14,1 V mostra aviso escrito', { skip: why || false }, async () => {
  const browser = await B.launch();
  try {
    const app = await B.openApp(browser, { viewport: SIZES[0], usb: true });
    await B.route(app, 'carro');
    await app.page.evaluate(() => { window.__bench.deliver(); window.__bench.live.unknown_raw_19 = 232; window.__bench.seq += 1; }); // 232 / 16 = 14,5 V
    await B.pump(app.page, 4);
    const g = await gauge(app.page, 'voltage');
    assert.equal(g.v, '14,5');
    assert.match(g.s, /Atenção.*GNV desliga/);
    const diag = await text(app.page, '#carroHost .carro-diag');
    assert.match(diag, /acima de 14,1 V o GNV desliga/);
    assert.match(diag, /alternador/);
  } finally { await browser.close(); }
});

test('Carro: dado velho vira —, nunca o último valor', { skip: why || false }, async () => {
  const browser = await B.launch();
  try {
    const app = await B.openApp(browser, { viewport: SIZES[0], usb: true });
    await B.route(app, 'carro');
    await B.pump(app.page, 4, { data: true });
    assert.notEqual((await gauge(app.page, 'rpm')).v, '—');
    await app.page.evaluate(() => window.__bench.unplugUsb());
    await B.pump(app.page, 6);
    assert.equal((await gauge(app.page, 'rpm')).v, '—');
  } finally { await browser.close(); }
});

for (const viewport of SIZES) {
  test(`Carro: sem rolagem nem corte em ${viewport.width}x${viewport.height}`, { skip: why || false }, async () => {
    const browser = await B.launch();
    try {
      for (const usb of [false, true]) {
        const app = await B.openApp(browser, { viewport, usb, scale: 1 });
        await B.route(app, 'carro');
        if (usb) { await app.page.evaluate(() => { window.__bench.deliver(); window.__bench.live.unknown_raw_19 = 232; window.__bench.seq += 1; }); await B.pump(app.page, 4); }
        const bad = await app.page.evaluate(() => {
          const out = [];
          const host = document.getElementById('carroHost');
          if (document.documentElement.scrollHeight > innerHeight + 1 || document.documentElement.scrollWidth > innerWidth + 1) out.push('página rola');
          if (host.scrollHeight > host.clientHeight + 1) out.push(`host rola ${host.scrollHeight}>${host.clientHeight}`);
          host.querySelectorAll('*').forEach(el => {
            const b = el.getBoundingClientRect();
            if (b.width && (b.right > innerWidth + 1 || b.bottom > innerHeight + 1)) out.push('fora da tela: ' + (el.className || el.tagName));
            if (el.scrollWidth > el.clientWidth + 1 && getComputedStyle(el).overflow !== 'visible' && el.textContent.trim()) out.push('texto cortado: ' + (el.className || el.tagName));
            if (el.scrollHeight > el.clientHeight + 1 && getComputedStyle(el).overflow !== 'visible') out.push('corte vertical: ' + (el.className || el.tagName));
          });
          return [...new Set(out)];
        });
        assert.deepEqual(bad, [], `${viewport.width}x${viewport.height} usb=${usb}`);
        assert.deepEqual(app.problems.page, []);
        await app.context.close();
      }
    } finally { await browser.close(); }
  });
}
