// Refino no app real (modo demonstração), contrato de fluxo: botão principal FIXO "Gravar correção" (#refino-principal), bloco
// "Última gravação" com Desfazer sempre presente, UM toque grava e confere (sem cartão/prévia), gravar de novo NÃO exige desfazer.
// Cobre as duas gravações do Refino: Curva K (proposta do piloto) e Mapa K (engasgos). Nada grava antes do toque.
const { chromium } = require('playwright');
const path = require('path');
const assert = require('node:assert/strict');
const { fakeBridge } = require('./_ponte_falsa.cjs');
const INDEX = path.resolve('app/src/main/assets/ui/index.html');

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errors = [];
    page.on('pageerror', e => errors.push(String(e.message || e)));
    await page.addInitScript(fakeBridge);
    await page.goto('file://' + INDEX, { waitUntil: 'load' });
    await page.waitForTimeout(1200);
    await page.locator('.side-nav button[data-route="refino"]').click();
    await page.waitForSelector('#refinoChart .ao-svg', { timeout: 8000 });

    // Foto dos controles: o que o contrato exige que NUNCA mude de lugar nem suma.
    const shot = () => page.evaluate(() => {
      const box = sel => { const n = document.querySelector(sel); if (!n) return null; const r = n.getBoundingClientRect(); const s = getComputedStyle(n);
        return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), shown: !n.hidden && s.display !== 'none' && s.visibility !== 'hidden',
          aria: n.getAttribute('aria-disabled'), disabled: n.disabled === true, text: n.textContent.trim() }; };
      return { principal: box('#refino-principal'), desfazer: box('#refino-ultima [data-fluxo="desfazer"]'), ultima: box('#refino-ultima'),
        manual: box('[data-refino-manual]'), ordem: [...document.querySelectorAll('#refinoActions > [data-fluxo]')].map(n => n.getAttribute('data-fluxo')) };
    });
    const sempreIgual = (a, b, nome) => {
      assert.deepEqual(b.ordem, a.ordem, nome + ': mesma ordem dos controles');
      for (const k of ['principal', 'desfazer', 'ultima', 'manual']) {
        assert.ok(b[k] && b[k].shown, `${nome}: ${k} sempre visível`);
        assert.equal(b[k].disabled, false, `${nome}: ${k} nunca usa disabled (usa aria-disabled)`);
        assert.deepEqual([b[k].x, b[k].y, b[k].w, b[k].h], [a[k].x, a[k].y, a[k].w, a[k].h], `${nome}: ${k} no mesmo lugar e tamanho`);
      }
    };

    // Antes de qualquer proposta/gravação: tudo presente, principal cinza com motivo, Desfazer cinza, nada para desfazer.
    const inicial = await shot();
    assert.deepEqual(inicial.ordem, ['conta', 'ultima', 'principal'], 'ordem fixa dos controles');
    assert.ok(inicial.principal.shown && inicial.desfazer.shown && inicial.manual.shown);
    assert.equal(await page.evaluate(() => document.querySelector('#refino-principal').getAttribute('data-fluxo')), 'principal');
    assert.match(inicial.principal.text, /^Gravar correção/);
    assert.equal(await page.evaluate(() => document.querySelector('[data-refino-preview],[data-refino-map-confirm],[data-refino-undo],.refino-card')), null, 'sem cartão, prévia nem botões antigos');

    // Ponte falsa do Kotlin: curva de 30 pontos e mapa que respondem à gravação como a ECU (ACK + leitura de volta igual).
    await page.evaluate(() => {
      const refino = window.OmegasApp.refino;
      const api = window.OmegasApp.api;
      const curve = Array.from({ length: 30 }, () => 16384);
      window.__curve = curve; window.__curveWrites = []; window.__writes = []; let op = {};
      api.startCurveRead = () => { op = { state: 'COMPLETED', busy: false, points: curve.map((factorRaw, index) => ({ index, factorRaw })) }; return { ok: true, started: true }; };
      api.curveOperation = () => op;
      api.writeCurve = (points, reason) => { window.__curveWrites.push({ points, reason }); points.forEach(p => { curve[p.index] = p.targetRaw; });
        op = { state: 'BATCH_CONFIRMED', readbackValid: true, busy: false }; return { ok: true, started: true }; };
      api.writeMap = (items, reason) => { window.__writes.push({ items, reason }); return { ok: true, started: true, startedAt: 1 }; };
      api.mapWriteOperation = () => ({ ok: true, state: 'BATCH_CONFIRMED', readbackValid: true, busy: false, startedAt: 1 });
      const eq = JSON.parse(JSON.stringify(refino.eq || {}));
      eq.stalls = { count: 1, events: [{ kind: 'STALL', petrolMs: 2.2, mapBar: 0.3, rpmBefore: 1500 }],
        regions: [{ fromMs: 2.0, toMs: 2.5, count: 1, mapBar: 0.3, rpmBefore: 1500, rpmLow: 1500, rpmHigh: 1500 }],
        nearCount: 4, nearEvents: [{ kind: 'NEAR_STALL', petrolMs: 2.1, mapBar: 0.25 }],
        nearRegions: [{ fromMs: 2.0, toMs: 2.5, count: 4, mapBar: 0.25, rpmBefore: 1450, rpmLow: 1350, rpmHigh: 1700 }] };
      const pts = list => list.map(([index, targetRaw]) => ({ index, currentRaw: curve[index], targetRaw, referenceTimeMs: 3 }));
      window.__setProposal = list => {
        eq.autopilot = { ...(eq.autopilot || {}), ecuDone: true, ecuOnline: true, phase: list.length ? 'PROPOSTA_PRONTA' : 'VERIFICANDO',
          action: list.length ? { kind: 'review', label: 'Revisar e gravar ' + list.length + ' pontos', enabled: true } : { kind: 'none' }, secondary: [],
          proposal: { known: true, count: list.length, signature: 'e2e' + list.map(x => x.join('>')).join(','), points: pts(list) } };
        refino.api = Object.assign(Object.create(Object.getPrototypeOf(refino.api)), refino.api, { equivalence: () => eq });
        refino.refresh(true);
      };
      window.__setProposal([[6, 16900], [9, 17100], [14, 16300]]);
    });
    await page.waitForFunction(() => document.querySelector('#refino-principal').getAttribute('aria-disabled') === 'false', null, { timeout: 4000 });
    const pronto = await shot();
    sempreIgual(inicial, pronto, 'com proposta');
    assert.equal(pronto.principal.text, 'Gravar correção', 'o botão diz a mesma palavra do aviso');
    assert.match(await page.textContent('#refinoNext'), /O carro engasgou 5 vezes no GNV/);
    assert.match(await page.textContent('#refinoNext'), /Ao gravar:/, 'o antes → depois fica escrito no painel, sem cartão');
    assert.equal(await page.evaluate(() => window.__curveWrites.length), 0, 'nada gravado antes do toque');
    assert.equal(pronto.desfazer.aria, 'true', 'sem gravação ainda: Desfazer cinza, mas presente');

    // UM toque grava e confere.
    await page.locator('#refino-principal').click();
    await page.waitForFunction(() => /Curva K/.test(document.querySelector('#refino-ultima p').textContent), null, { timeout: 15000 });
    assert.equal(await page.evaluate(() => document.querySelector('.screen.active').dataset.screen), 'refino', 'não sai da aba');
    let w = await page.evaluate(() => window.__curveWrites);
    assert.equal(w.length, 1, 'um toque = uma gravação');
    assert.deepEqual(w[0].points.map(p => [p.index, p.currentRaw, p.targetRaw]), [[6, 16384, 16900], [9, 16384, 17100], [14, 16384, 16300]]);
    const aposGravar = await shot();
    sempreIgual(inicial, aposGravar, 'depois de gravar');
    assert.equal(aposGravar.desfazer.aria, 'false', 'Desfazer liga logo após gravar');
    assert.match(await page.textContent('#refino-ultima p'), /Curva K · 3 pontos · Refino/);

    // Gravar 2x seguidas SEM desfazer: nova proposta, mesmo botão, mesmo lugar; o Desfazer passa a apontar para a gravação mais nova.
    await page.evaluate(() => window.__setProposal([[3, 16700]]));
    await page.waitForFunction(() => document.querySelector('#refino-principal').getAttribute('aria-disabled') === 'false', null, { timeout: 4000 });
    sempreIgual(inicial, await shot(), 'segunda proposta');
    await page.locator('#refino-principal').click();
    await page.waitForFunction(() => /Curva K · 1 ponto/.test(document.querySelector('#refino-ultima p').textContent), null, { timeout: 15000 });
    w = await page.evaluate(() => window.__curveWrites);
    assert.equal(w.length, 2, 'segunda gravação sem desfazer a primeira');
    assert.deepEqual(w[1].points.map(p => [p.index, p.currentRaw, p.targetRaw]), [[3, 16384, 16700]]);

    // Sem proposta de Curva K, mas com engasgo: o MESMO botão grava o Mapa K (lê, confere e grava no mesmo toque).
    await page.evaluate(() => window.__setProposal([]));
    await page.waitForFunction(() => document.querySelector('#refino-principal').getAttribute('aria-disabled') === 'false', null, { timeout: 8000 });
    const mapa = await shot();
    sempreIgual(inicial, mapa, 'proposta de Mapa K');
    assert.equal(mapa.principal.text, 'Gravar correção');
    await page.locator('#refino-principal').click();
    await page.waitForFunction(() => /Mapa K/.test(document.querySelector('#refino-ultima p').textContent), null, { timeout: 15000 });
    w = await page.evaluate(() => window.__writes);
    assert.equal(w.length, 1, 'uma gravação no Mapa K');
    assert.ok(w[0].items.length >= 1 && w[0].items.length <= 6, 'poucas células: ' + w[0].items.length);
    assert.ok(w[0].items.every(i => i.target > i.current));
    sempreIgual(inicial, await shot(), 'depois do Mapa K');

    // Desfazer (1 toque): grava os valores de antes do Mapa K; o botão continua no lugar.
    await page.locator('#refino-ultima [data-fluxo="desfazer"]').click();
    await page.waitForFunction(() => window.__writes.length === 2, null, { timeout: 15000 });
    w = await page.evaluate(() => window.__writes);
    assert.deepEqual(w[1].items.map(i => [i.row, i.column, i.target]), w[0].items.map(i => [i.row, i.column, i.current]));
    await page.waitForFunction(() => document.querySelector('#refino-ultima [data-fluxo="desfazer"]').getAttribute('aria-disabled') === 'true', null, { timeout: 4000 });
    sempreIgual(inicial, await shot(), 'depois de desfazer');
    assert.equal((await shot()).desfazer.shown, true, 'Desfazer segue presente (cinza) depois de usado');
    await page.screenshot({ path: 'build/ui-evidence/refino-fluxo.png' });
    assert.deepEqual(errors, []);
    console.log('REFINO_MAPA=PASS', JSON.stringify({ curva: await page.evaluate(() => window.__curveWrites.length), mapa: w[0].items.length }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
