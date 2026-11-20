'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const html = fs.readFileSync(path.join(ui, 'index.html'), 'utf8');
const css = fs.readFileSync(path.join(ui, 'styles-adaptive.css'), 'utf8');

test('preserva as cinco rotas e carrega a camada adaptativa depois do cockpit', () => {
  for (const route of ['autocal','refino','map','curve','tools']) {
    assert.match(html, new RegExp('data-route="' + route + '"'));
  }
  assert.ok(html.indexOf('styles-autocal-cockpit.css') < html.indexOf('styles-adaptive.css'));
  assert.match(html, /data-autocal-cockpit-style="true"/);
  assert.match(css, /orientation:\s*portrait/);
  assert.match(css, /min-width:\s*1600px/);
  assert.match(css, /prefers-reduced-motion:\s*reduce/);
});

test('não altera comandos da central e mantém ações de gravação explícitas', () => {
  for (const id of ['mapReviewButton','curveReviewButton','curveBackupSave','curveBackupRestore']) {
    assert.match(html, new RegExp('id="' + id + '"'));
  }
  assert.doesNotMatch(css, /writeMap|writeCurve|protocolTransaction|RESET_ALL/);
});

test('matriz de evidências mantém origem, código, testes e bloqueios explícitos', () => {
  const root = path.resolve(__dirname, '../..');
  const data = JSON.parse(fs.readFileSync(path.join(root, 'docs/qualidade/matriz-funcional.json'), 'utf8'));
  const catalog = JSON.parse(fs.readFileSync(path.join(root, 'docs/progbase/evidencias/INDEX.json'), 'utf8'));
  const byId = new Map(catalog.map(e => [e.id, e]));
  assert.equal(data.scope, 'PARTIAL_SEED_NOT_COMPLETE');
  assert.ok(data.entries.length >= 10);
  const seen = new Set();
  const routes = new Set(['autocal', 'refino', 'map', 'curve', 'tools', 'global']);
  const classes = new Set(['ECU_READ', 'ECU_MANUAL_WRITE', 'APP_ONLY']);
  for (const e of data.entries) {
    assert.ok(e.id && !seen.has(e.id), 'id repetido ou ausente: '+e.id);
    seen.add(e.id);
    assert.ok(routes.has(e.route) && classes.has(e.kind), 'rota/tipo inválido: '+e.id);
    assert.ok(e.gap && e.gap.trim(), 'sem limite ou lacuna descrita: '+e.id);
    assert.ok(e.sourceFiles.length && e.testPaths.length, 'sem implementação ou teste: '+e.id);
    for (const rel of [...e.sourceFiles, ...e.testPaths]) {
      assert.ok(fs.existsSync(path.join(root, rel)), e.id+' aponta para arquivo inexistente: '+rel);
    }
    for (const id of e.sourceEvidence) assert.ok(byId.has(id), e.id+' usa evidência inexistente: '+id);
    assert.equal(e.physicalConfirmed, false, e.id+' não tem homologação física nesta versão');
    if (e.kind === 'APP_ONLY') assert.equal(e.capability, 'NOT_APPLICABLE_ECU');
    else assert.equal(e.capability, 'NOT_REVALIDATED_ON_PHYSICAL_ECU_WITH_THIS_BUILD');
    if (e.kind === 'ECU_MANUAL_WRITE') {
      assert.ok(e.sourceEvidence.length > 0, e.id+' writer sem rastreio do original');
      assert.ok(e.access === 'WRITE_UI_EXISTING', e.id+' função nova não autorizada');
      if (e.sourceEvidence.some(id => byId.get(id).marca === 'DESCONHECIDO')) {
        assert.match(e.gap, /desconhecid|não.*provad|não.*demonstrad|não.*comprovad/i, e.id+' ignora evidência inconclusiva');
      }
    }
  }
  const pressure = data.entries.find(x => x.id === 'tools.petrol-return-pressure');
  assert.ok(pressure && pressure.physicalConfirmed === false);
  const pressureCore = fs.readFileSync(
    path.join(root, 'app/src/main/java/com/omegas/prohub/autocal/VoltaGasolinaCore.kt'), 'utf8');
  const pressureWriterEnabled = /const val PRESSURE_WRITE_ENABLED\s*=\s*true/.test(pressureCore);
  assert.equal(pressure.kind, pressureWriterEnabled ? 'ECU_MANUAL_WRITE' : 'ECU_READ',
    'matriz deve refletir a permissão efetiva de pressão do código, não uma hipótese');
  assert.equal(pressure.access, pressureWriterEnabled ? 'WRITE_UI_EXISTING' : 'READ_UI_EXISTING');
  assert.ok(pressure.sourceEvidence.includes('EV-VOLTAGASOLINA-015'));
  assert.equal(data.blockingDecisions.find(x => x.id === 'PRESSURE_WRITE_REAL_ECU_ACK_NOT_PROVEN')?.requiresPhysicalEvidence, true);
  const blocked = data.blockingDecisions.find(x => x.id === 'MAP_ACK_READBACK_OWNER_CONFLICT');
  assert.equal(blocked?.requiresOwnerDecision, true);
});

test('botão azul de Ferramentas tem texto acessível para tamanho normal', () => {
  const original = fs.readFileSync(path.join(ui, 'styles.css'), 'utf8');
  const color = key => {
    const m = original.match(new RegExp('--' + key + ':\\s*(#[0-9a-fA-F]{6})'));
    assert.ok(m, 'token ' + key + ' ausente');
    return m[1].slice(1).match(/../g).map(h => parseInt(h, 16) / 255);
  };
  const luminance = v => v.map(x => x <= .04045 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4)
    .reduce((n, x, i) => n + x * [.2126, .7152, .0722][i], 0);
  const a = luminance(color('bg')), b = luminance(color('accent'));
  const ratio = (Math.max(a,b) + .05) / (Math.min(a,b) + .05);
  assert.ok(ratio >= 4.5, 'contraste do botão de Ferramentas ' + ratio.toFixed(2) + ':1');
  assert.match(css, /#toolsDrawer \.primary\s*\{\s*color:\s*var\(--bg\);/);
});

const browser = require('./helpers/browser.cjs');
const skip = browser.skipReason() || false;
let chromium;
test.before(async () => { if (!skip) chromium = await browser.launch(); });
test.after(async () => { if (chromium) await chromium.close(); });

const viewports = [
  { width: 360, height: 740 }, // regressão visual documentada no PR #195
  { width: 360, height: 800 },
  { width: 390, height: 844 },
  { width: 412, height: 915 },
  // Full HD portrait, both literal CSS pixels (panel) and ~3x density (Android WebView).
  { width: 360, height: 640, scale: 3 },
  { width: 768, height: 1024 },
  { width: 1080, height: 1920 },
  { width: 800, height: 480 },
  { width: 1024, height: 600 },
  { width: 1280, height: 720 },
  { width: 1920, height: 1080 },
  { width: 2560, height: 1080 },
];
test('documenta os dois cenários Full HD e separa prova de navegador da física', () => {
  const root = path.resolve(__dirname, '../..');
  const design = fs.readFileSync(path.join(root, 'DESIGN.md'), 'utf8');
  const contract = fs.readFileSync(path.join(root, 'UX-CONTRACT.md'), 'utf8');
  assert.match(design, /360×640 com DPR 3 \(1080×1920 físicos\)/);
  assert.match(design, /1080×1920 CSS/);
  assert.match(contract, /CI #1957/);
  assert.match(contract, /não.*homologação de WebView\/ECU em hardware real/i);
});

test('cobre Full HD 1080x1920 e Android 1080x1920 com DPR 3', () => {
  const specs = [
    { width: 1080, height: 1920 },
    { width: 360, height: 640, scale: 3 }
  ];
  for (const s of specs) {
    assert.ok(viewports.some(v => v.width === s.width && v.height === s.height && (v.scale || 1) === (s.scale || 1)));
  }
});

for (const viewport of viewports) {
  test('navegação e barra ao vivo sem overflow no visor '+viewport.width+'x'+viewport.height, { skip }, async () => {
    const app = await browser.openApp(chromium, { viewport, usb: true, scale: viewport.scale || 1 });
    try {
      const shot = async (route) => {
        fs.mkdirSync(browser.IMG_DIR, { recursive: true });
        await app.page.screenshot({
          path: path.join(browser.IMG_DIR, 'ux-adaptive-' + viewport.width + 'x' + viewport.height + '-' + route + '.png'),
          animations: 'disabled', fullPage: false
        });
      };
      await shot('autocal');
      const actualDpr = await app.page.evaluate(() => window.devicePixelRatio);
      assert.equal(actualDpr, viewport.scale || 1, 'DPR efetivo do navegador deve corresponder ao caso testado');
      const state = await app.page.evaluate(() => {
        const rect = selector => {
          const e = document.querySelector(selector);
          const r = e.getBoundingClientRect();
          return { left:r.left,right:r.right,top:r.top,bottom:r.bottom,visible:r.width>0&&r.height>0 };
        };
        return { w: innerWidth, documentWidth: document.documentElement.scrollWidth,
          nav: rect('.side-rail'), live: rect('.live-bar'),
          routes: [...document.querySelectorAll('.side-nav button')].map(e=>({
            route:e.dataset.route, width:e.getBoundingClientRect().width,
            labelFont: parseFloat(getComputedStyle(e.querySelector('span')).fontSize),
            labelFits: (() => { const t=e.querySelector('span'); return t.scrollHeight <= t.clientHeight + 2 && t.scrollWidth <= t.clientWidth + 2; })()
          })) };
      });
      assert.ok(state.documentWidth <= state.w + 2, JSON.stringify(state));
      assert.equal(state.routes.length,5);
      assert.ok(state.routes.every(v=>v.width>=44), JSON.stringify(state));
      if (viewport.width <= 390) {
        assert.ok(state.routes.every(v=>v.labelFont >= 12 && v.labelFits), 'rótulos de navegação devem ser legíveis e inteiros: '+JSON.stringify(state));
      }
      assert.ok(state.live.right <= state.w+2, JSON.stringify(state));
      assert.ok(state.nav.right <= state.w+2, JSON.stringify(state));
      for (const route of ['refino','map','curve','tools']) {
        await app.page.evaluate(r => window.OmegasApp.router.navigate(r), route);
        await browser.pump(app.page,2);
        await shot(route);
        if (route === 'tools' && viewport.width <= 390) {
          const fitLabel = await app.page.evaluate(() => {
            const nav = document.querySelector('.side-nav button[data-route="tools"] span');
            const range = document.createRange();
            range.selectNodeContents(nav);
            return {
              lineRects: range.getClientRects().length,
              fontSize: parseFloat(getComputedStyle(nav).fontSize),
              nowrap: getComputedStyle(nav).whiteSpace === 'nowrap',
              withinViewport: nav.getBoundingClientRect().right <= innerWidth + 2,
              toolsOnly: document.getElementById('app').classList.contains('tools-focus')
            };
          });
          assert.equal(fitLabel.lineRects, 1, 'Ferramentas não pode quebrar no meio da palavra: ' + JSON.stringify(fitLabel));
          assert.ok(fitLabel.fontSize >= 12 && fitLabel.nowrap && fitLabel.withinViewport && fitLabel.toolsOnly,
            JSON.stringify(fitLabel));
        }
        if (route === 'tools' && viewport.width >= 1920) {
          const density = await app.page.evaluate(() => {
            const grid = document.querySelector('#toolAdvanced');
            const cards = [...grid.querySelectorAll('.tool-tile')].filter(e => e.getBoundingClientRect().height > 0);
            return {
              cards: cards.length,
              maxCardHeight: Math.max(...cards.map(e => e.getBoundingClientRect().height)),
              gridWidth: grid.getBoundingClientRect().width,
              gridHeight: grid.getBoundingClientRect().height
            };
          });
          assert.ok(density.cards >= 7 && density.maxCardHeight <= 260 &&
            density.gridWidth <= 1682, 'Ultrawide: densidade de leitura sem cartões gigantes: ' + JSON.stringify(density));
        }
        const fit = await app.page.evaluate(() => ({
          doc: document.documentElement.scrollWidth,
          body: document.body.scrollWidth,
          w:innerWidth,
          visible:!!document.querySelector('.screen.active')
        }));
        assert.ok(fit.visible && fit.doc <= fit.w + 2,route+' '+JSON.stringify(fit));
      }
    } finally { await app.context.close(); }
  });
}

for (const viewport of [{ width: 360, height: 800 }, { width: 390, height: 844 }, { width: 360, height: 640, scale: 3 }]) {
  test('grade K preserva as 13 colunas, eixos e 144 valores no retrato '+viewport.width+'x'+viewport.height+' DPR '+(viewport.scale || 1), { skip }, async () => {
    const app = await browser.openApp(chromium, { viewport, usb: true, scale: viewport.scale || 1 });
    try {
      await browser.route(app, 'map');
      const actualDpr = await app.page.evaluate(() => window.devicePixelRatio);
      assert.equal(actualDpr, viewport.scale || 1, 'DPR deve ser realmente aplicado no teste do Mapa K');
      const grid = await app.page.evaluate(() => {
        const table = document.querySelector('#mapGrid .map-k-grid-with-axes');
        if (!table) return { absent: true };
        const rect = el => {
          const r = el.getBoundingClientRect();
          return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width) };
        };
        const cols = getComputedStyle(table).gridTemplateColumns.trim().split(/\s+/);
        const rpm = [...table.querySelectorAll('.map-rpm-header')];
        const ms = [...table.querySelectorAll('.map-ms-header')];
        const cells = [...table.querySelectorAll('.map-k-cell')];
        return {
          columns: cols.length, rpm: rpm.length, ms: ms.length, cells: cells.length,
          corner: rect(table.querySelector('.map-axis-corner')),
          rpmFirst: rect(rpm[0]), rpmLast: rect(rpm[11]),
          msFirst: rect(ms[0]), cell00: rect(table.querySelector('[data-row="0"][data-column="0"]')),
          cell011: rect(table.querySelector('[data-row="0"][data-column="11"]')),
          host: rect(document.querySelector('#mapGrid'))
        };
      });
      assert.equal(grid.columns, 13, JSON.stringify(grid));
      assert.deepEqual([grid.rpm, grid.ms, grid.cells], [12, 12, 144]);
      assert.ok(Math.abs(grid.corner.y - grid.rpmFirst.y) <= 2, JSON.stringify(grid));
      assert.ok(Math.abs(grid.corner.x - grid.msFirst.x) <= 2, JSON.stringify(grid));
      assert.ok(Math.abs(grid.rpmFirst.x - grid.cell00.x) <= 2, JSON.stringify(grid));
      assert.ok(Math.abs(grid.msFirst.y - grid.cell00.y) <= 2, JSON.stringify(grid));
      assert.ok(grid.rpmLast.x > grid.rpmFirst.x && grid.cell011.x > grid.cell00.x, JSON.stringify(grid));
      assert.ok(grid.host.w < grid.cell011.x + grid.cell011.w, 'a grade larga deve rolar apenas dentro do painel');
      assert.equal(app.problems.requests.length, 0, JSON.stringify(app.problems));
    } finally { await app.context.close(); }
  });
}
