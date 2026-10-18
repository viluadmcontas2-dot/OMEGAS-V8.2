'use strict';
// Orçamento de leveza da interface (2026-10-18): o APK tem que ser leve e rápido SEM mudar comportamento.
// Só CONTAGENS (scripts, bytes, nós, ouvintes, timers, escritas no DOM), nunca milissegundos: um teste de tempo
// seria instável na CI. Medição completa antes/depois: tools/ci/medir_leveza.cjs e docs/evidence/2026-10-18-leveza.md.
// Sem Chromium/Playwright os testes de navegador PULAM (como os e2e de toque); o de CSS morto roda sempre.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const T = require('./helpers/browser.cjs');
const { fakeBridge } = require('../../tools/ci/_ponte_falsa.cjs');

const ROOT = path.resolve(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const skip = T.skipReason() || false;
let browser;
test.before(async () => { if (!skip) browser = await T.launch(); });
test.after(async () => { if (browser) await browser.close(); });

/** Telas pesadas: só entram quando a aba é aberta (evento), nunca no arranque. */
const POR_ABA = {
  map: ['screens/map.js', 'map-editor.js', 'core/curve-to-map.js', 'core/balance-runner.js'],
  curve: ['screens/curve.js', 'core/curve-smoothing.js'],
  tools: ['components/drawers.js', 'components/advanced.js', 'core/advanced-flags.js', 'core/advanced-flags-data.js'],
  refino: ['screens/refino.js', 'core/gasoline-reference.js', 'core/stall-region.js', 'core/map-fix.js'],
};
const ORCAMENTO = { scriptsNoArranque: 12, bytesJsNoArranque: 230_000 };

async function abrir(viewport = { width: 1280, height: 720 }) {
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  const erros = [];
  page.on('pageerror', e => erros.push(String(e.message || e)));
  page.on('console', m => { if (m.type() === 'error') erros.push(m.text()); });
  await page.addInitScript(fakeBridge);
  await page.addInitScript(() => {
    const vivos = { interval: new Set(), timeout: new Set() };
    window.__vivos = vivos;
    const si = window.setInterval, ci = window.clearInterval, st = window.setTimeout, ct = window.clearTimeout;
    window.setInterval = function (f, ms, ...a) { const id = si.call(window, f, ms, ...a); vivos.interval.add(id); return id; };
    window.clearInterval = function (id) { vivos.interval.delete(id); return ci.call(window, id); };
    window.setTimeout = function (f, ms, ...a) { const id = st.call(window, function () { vivos.timeout.delete(id); return typeof f === 'function' ? f.apply(this, arguments) : undefined; }, ms, ...a); vivos.timeout.add(id); return id; };
    window.clearTimeout = function (id) { vivos.timeout.delete(id); return ct.call(window, id); };
  });
  await page.goto('file://' + path.join(UI, 'index.html'), { waitUntil: 'load' });
  await page.waitForFunction(() => !!window.OmegasApp && !!window.OmegasApp.autoCalCockpit, null, { timeout: 15000 });
  await page.waitForTimeout(800);
  return { context, page, erros };
}

const scriptsCarregados = page => page.evaluate(() => [...document.scripts].map(s => s.getAttribute('src')).filter(Boolean));

async function irPara(page, rota) {
  await page.evaluate(r => window.OmegasApp.router.navigate(r), rota);
}
async function esperarAba(page, rota) {
  await page.waitForFunction(r => {
    const a = window.OmegasApp;
    if (r === 'map') return !!a.screens.map && !!document.getElementById('mapLiveLabel');
    if (r === 'curve') return !!a.screens.curve;
    if (r === 'refino') return !!a.refino && !!document.getElementById('refinoChart');
    if (r === 'tools') return !!document.querySelector('#toolDiagnosticsWorkspace *') && !!window.OmegasUi.AdvancedPanel;
    return true;
  }, rota, { timeout: 8000 });
}

test('arranque na AutoCal: só o que a primeira tela precisa (scripts e bytes de JS)', { skip }, async () => {
  const { context, page, erros } = await abrir();
  try {
    const srcs = await scriptsCarregados(page);
    const bytes = srcs.reduce((s, f) => s + fs.statSync(path.join(UI, f)).size, 0);
    for (const [aba, arquivos] of Object.entries(POR_ABA)) {
      for (const f of arquivos) assert.ok(!srcs.includes(f), `${f} (aba ${aba}) não pode carregar no arranque da AutoCal`);
    }
    assert.ok(srcs.length <= ORCAMENTO.scriptsNoArranque, `scripts no arranque: ${srcs.length} > ${ORCAMENTO.scriptsNoArranque}`);
    assert.ok(bytes <= ORCAMENTO.bytesJsNoArranque, `bytes de JS no arranque: ${bytes} > ${ORCAMENTO.bytesJsNoArranque}`);
    assert.deepEqual(erros, []);
  } finally { await context.close(); }
});

test('abrir cada aba carrega a tela dela (evento) e ela funciona; trocar antes de terminar de carregar não quebra', { skip }, async () => {
  const { context, page, erros } = await abrir();
  try {
    for (const rota of ['map', 'curve', 'tools', 'refino']) {
      const antes = await scriptsCarregados(page);
      for (const f of POR_ABA[rota]) assert.ok(!antes.includes(f), `${f} ainda não deveria estar carregado`);
      await irPara(page, rota);
      await esperarAba(page, rota);
      const depois = await scriptsCarregados(page);
      for (const f of POR_ABA[rota]) assert.ok(depois.includes(f), `${f} deveria carregar ao abrir a aba ${rota}`);
      assert.equal(await page.evaluate(() => window.OmegasApp.store.get().route), rota);
      assert.equal(await page.evaluate(() => document.querySelectorAll('.screen.active').length), 1, 'uma tela ativa só');
    }
    // abre e sai na hora: nenhuma tela de aba que já saiu pode "puxar" a rota de volta nem estourar erro
    const p2 = await abrir();
    try {
      await p2.page.evaluate(() => { window.OmegasApp.router.navigate('tools'); window.OmegasApp.router.navigate('autocal'); });
      await p2.page.waitForTimeout(600);
      assert.equal(await p2.page.evaluate(() => window.OmegasApp.store.get().route), 'autocal');
      assert.deepEqual(p2.erros, []);
    } finally { await p2.context.close(); }
    assert.deepEqual(erros, []);
  } finally { await context.close(); }
});

/** Roda ciclos do agendador; cada ciclo traz UM quadro novo (dado novo) ou o mesmo quadro (sem dado novo). */
async function ciclos(page, { comDado, n = 40 }) {
  return page.evaluate(async ({ comDado, n }) => {
    const app = window.OmegasApp; let seq = 9000;
    const quadro = () => ({ valid: true, ageMs: 40, telemetryAgeMs: 40, sequence: seq, live: { rpm: 1500 + (seq % 40) * 25, petrol_ms: +(4 + (seq % 7) * 0.1).toFixed(3), load_bar: +(0.45 + (seq % 5) * 0.01).toFixed(3), fuel: 'GNV', unknown_raw_19: 221 } });
    app.api.presentSnapshot = () => { if (comDado) seq += 1; return { ok: true, revision: seq, data: quadro() }; };
    const agora = Date.now; if (!comDado) { const t0 = agora.call(Date); Date.now = () => t0; } // o vigia de 2,5 s é de relógio: tem teste próprio acima
    const escondida = {}; let identicas = 0; let total = 0; const amostra = [];
    const mo = new MutationObserver(list => {
      for (const m of list) {
        total += 1;
        const alvo0 = m.target.nodeType === 1 ? m.target : m.target.parentElement;
        if (amostra.length < 6) amostra.push(m.type + ' ' + (alvo0 ? alvo0.tagName + '#' + alvo0.id + '.' + (alvo0.getAttribute('class') || '') : '?') + ' ' + (m.attributeName || ''));
        const alvo = m.target.nodeType === 1 ? m.target : m.target.parentElement;
        const tela = alvo && alvo.closest && alvo.closest('[data-screen]');
        if (tela && !tela.classList.contains('active')) escondida[tela.dataset.screen] = (escondida[tela.dataset.screen] || 0) + 1;
        const igual = m.type === 'attributes' ? m.oldValue === m.target.getAttribute(m.attributeName)
          : m.type === 'characterData' ? m.oldValue === m.target.data
            : m.removedNodes.length === 1 && m.addedNodes.length === 1 && m.removedNodes[0].nodeType === 3 && m.addedNodes[0].nodeType === 3 && m.removedNodes[0].data === m.addedNodes[0].data;
        if (igual) { identicas += 1; if (amostra.length < 12) amostra.push('IGUAL:' + (m.removedNodes[0] ? (m.removedNodes[0].data || m.removedNodes[0].outerHTML || '').slice(0, 60) : m.attributeName)); }
      }
    });
    for (let i = 0; i < 4; i++) { app.scheduler.run(); await new Promise(r => requestAnimationFrame(() => setTimeout(r, 0))); } // assenta o primeiro quadro
    await new Promise(r => setTimeout(r, 150));
    mo.observe(document.body, { subtree: true, childList: true, attributes: true, attributeOldValue: true, characterData: true, characterDataOldValue: true });
    for (let i = 0; i < n; i++) { app.scheduler.run(); await new Promise(r => requestAnimationFrame(() => setTimeout(r, 0))); }
    mo.takeRecords(); mo.disconnect();
    Date.now = agora;
    return { identicas, total, escondida, amostra };
  }, { comDado, n });
}

test('nenhum ciclo de dados reescreve o DOM com o mesmo valor, e tela escondida não renderiza', { skip }, async () => {
  const { context, page, erros } = await abrir();
  try {
    for (const rota of ['autocal', 'refino', 'map', 'curve', 'tools']) {
      await irPara(page, rota);
      await esperarAba(page, rota);
      await page.waitForTimeout(700);
      const r = await ciclos(page, { comDado: true });
      assert.equal(r.identicas, 0, `${rota}: ${r.identicas} escritas com o mesmo valor em 40 ciclos ${r.amostra.join(' ; ')}`);
      assert.deepEqual(r.escondida, {}, `${rota}: tela escondida mexeu no DOM`);
    }
    assert.deepEqual(erros, []);
  } finally { await context.close(); }
});

test('releitura completa SEM mudança (vigia de 2,5 s, abrir a aba) não reescreve o DOM com o mesmo valor nem refaz a faixa de bandas', { skip }, async () => {
  const { context, page, erros } = await abrir();
  try {
    for (const [rota, chamada] of [['autocal', () => window.OmegasApp.autoCalCockpit.refresh()], ['refino', () => window.OmegasApp.refino.refresh(true)]]) {
      await irPara(page, rota);
      await esperarAba(page, rota);
      await page.waitForTimeout(1200);
      const r = await page.evaluate(async fn => {
        const chamar = new Function('return (' + fn + ')()');
        // dado parado: sem a ponte nativa o app mostra telemetria de demonstração que anda com o relógio
        window.OmegasApp.api.presentSnapshot = () => ({ ok: true, revision: 1, data: { valid: true, ageMs: 40, telemetryAgeMs: 40, sequence: 1, live: { rpm: 1500, petrol_ms: 4, load_bar: 0.5, fuel: 'GNV', unknown_raw_19: 221 } } });
        for (let i = 0; i < 3; i++) { window.OmegasApp.scheduler.run(); await new Promise(res => requestAnimationFrame(() => setTimeout(res, 30))); }
        chamar(); await new Promise(res => requestAnimationFrame(() => setTimeout(res, 60))); // a primeira releitura assenta o quadro parado
        const amostra = []; let identicas = 0; let nosRecriados = 0;
        const mo = new MutationObserver(list => {
          for (const m of list) {
            const igual = m.type === 'attributes' ? m.oldValue === m.target.getAttribute(m.attributeName)
              : m.type === 'characterData' ? m.oldValue === m.target.data
                : m.removedNodes.length === 1 && m.addedNodes.length === 1 && ((m.removedNodes[0].nodeType === 3 && m.addedNodes[0].nodeType === 3 && m.removedNodes[0].data === m.addedNodes[0].data)
                  || (m.removedNodes[0].nodeType === 1 && m.addedNodes[0].nodeType === 1 && m.removedNodes[0].outerHTML === m.addedNodes[0].outerHTML));
            if (m.type === 'childList' && m.target.id === 'autocalBands') nosRecriados += m.addedNodes.length;
            if (igual) { identicas += 1; const n = m.target.nodeType === 1 ? m.target : m.target.parentElement; if (amostra.length < 8) amostra.push(m.type + ' ' + n.tagName + '#' + n.id + ' ' + (m.attributeName || '')); }
          }
        });
        mo.observe(document.body, { subtree: true, childList: true, attributes: true, attributeOldValue: true, characterData: true, characterDataOldValue: true });
        for (let i = 0; i < 5; i++) { chamar(); await new Promise(res => requestAnimationFrame(() => setTimeout(res, 30))); }
        mo.takeRecords(); mo.disconnect();
        return { identicas, nosRecriados, amostra };
      }, chamada.toString());
      assert.equal(r.identicas, 0, `${rota}: ${r.identicas} escritas iguais em 5 releituras: ${r.amostra.join(' ; ')}`);
      assert.equal(r.nosRecriados, 0, `${rota}: a faixa de bandas foi refeita ${r.nosRecriados} nós com a mesma leitura`);
    }
    assert.deepEqual(erros, []);
  } finally { await context.close(); }
});

test('sem dado novo, nenhuma aba mexe no DOM (gráficos não redesenham sem mudança de dado)', { skip }, async () => {
  const { context, page } = await abrir();
  try {
    for (const rota of ['autocal', 'refino', 'map', 'curve', 'tools']) {
      await irPara(page, rota);
      await esperarAba(page, rota);
      await page.waitForTimeout(700);
      const r = await ciclos(page, { comDado: false });
      // O status/contexto (1 s / 2 s) pode repetir a leitura, mas não escreve nada diferente: tolerância 0 mutação.
      assert.equal(r.total, 0, `${rota}: ${r.total} mutações em 40 ciclos sem dado novo: ${r.amostra.join(' ; ')}`);
    }
  } finally { await context.close(); }
});

test('200 trocas de aba: nós, ouvintes e timers voltam ao mesmo lugar (sem vazamento)', { skip }, async () => {
  const { context, page } = await abrir();
  try {
    const cdp = await context.newCDPSession(page);
    await cdp.send('Performance.enable');
    const medir = async () => {
      await cdp.send('HeapProfiler.collectGarbage');
      const g = Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map(x => [x.name, x.value]));
      const vivos = await page.evaluate(() => ({ interval: window.__vivos.interval.size, timeout: window.__vivos.timeout.size }));
      return { nos: g.Nodes, ouvintes: g.JSEventListeners, ...vivos };
    };
    const rotas = ['autocal', 'refino', 'map', 'curve', 'tools'];
    const trocar = async () => {
      for (let i = 0; i < 200; i++) { await page.evaluate(r => window.OmegasApp.router.navigate(r), rotas[i % 5]); if (i % 20 === 0) await page.waitForTimeout(40); }
      await page.waitForTimeout(1200);
    };
    for (const r of rotas) { await irPara(page, r); await esperarAba(page, r); }
    await trocar();                       // aquece: cada tela construída e estabilizada
    const antes = await medir();
    await trocar();
    const depois = await medir();
    assert.equal(depois.nos, antes.nos, 'nós do DOM');
    assert.equal(depois.ouvintes, antes.ouvintes, 'ouvintes');
    assert.ok(depois.interval <= 1, `setInterval vivo: ${depois.interval} (só o agendador)`);
    assert.ok(depois.timeout <= 3, `setTimeout pendente: ${depois.timeout}`);
  } finally { await context.close(); }
});

test('celular pequeno 360x740: mesmo orçamento de arranque', { skip }, async () => {
  const { context, page, erros } = await abrir({ width: 360, height: 740 });
  try {
    const srcs = await scriptsCarregados(page);
    assert.ok(srcs.length <= ORCAMENTO.scriptsNoArranque, `scripts: ${srcs.length}`);
    assert.deepEqual(erros, []);
  } finally { await context.close(); }
});

test('CSS morto: nenhum seletor de .classe/#id sem uso em HTML/JS (podar_css_morto.py não acha nada)', () => {
  const r = spawnSync('python3', ['-B', path.join(ROOT, 'tools/ci/podar_css_morto.py')], { encoding: 'utf8' });
  assert.equal(r.status, 0, r.stderr);
  const total = /total (\d+)/.exec(r.stdout);
  assert.ok(total, r.stdout);
  assert.equal(Number(total[1]), 0, r.stdout);
});
