'use strict';
// Fatia F6 (R14): refinamento de UI. Classe de prova 1 (contrato) e 2 (sintético, fixture do cérebro).

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.join(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const read = rel => fs.readFileSync(path.join(UI, rel), 'utf8');
const walk = (dir, out = []) => {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out); else out.push(full);
  }
  return out;
};
const uiFiles = walk(UI);
const jsFiles = uiFiles.filter(file => file.endsWith('.js'));
const cssFiles = uiFiles.filter(file => file.endsWith('.css'));
const html = read('index.html');

function loadInto(context, files) {
  vm.createContext(context);
  context.window = context;
  context.globalThis = context;
  for (const file of files) vm.runInContext(read(file), context, { filename: file });
  return context;
}

// ---------------------------------------------------------------- 1. navegação
test('trilho: 7 abas numeradas, Sugestões saiu e Sessões entrou', () => {
  const buttons = [...html.matchAll(/<button type="button" data-route="([^"]+)"[^>]*><i>(\d\d)<\/i><span>([^<]+)<\/span>/g)]
    .map(m => [m[1], m[2], m[3]]);
  assert.deepEqual(buttons, [
    ['dashboard', '01', 'Agora'], ['map', '02', 'Mapa K'], ['curve', '03', 'Curva K'], ['autocal', '04', 'AutoCal'],
    ['refino', '05', 'Refino'], ['sessions', '06', 'Sessões'], ['tools', '07', 'Ferramentas'],
  ]);
  const ctx = loadInto({ console, localStorage: { getItem() { return null; }, setItem() {} } }, ['core/store.js', 'core/router.js']);
  assert.deepEqual(Array.from(ctx.OmegasUi.ROUTES), ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools']);
  for (const route of ctx.OmegasUi.ROUTES) assert.match(html, new RegExp(`data-screen="${route}"`));
});

test('Sugestões removida por inteiro (rota, botão, contador, render, CSS)', () => {
  const dead = [/data-route="suggestions"/, /suggestionCount/, /renderSuggestions/, /suggestionsButton/, /toolsButton/,
    /data-screen="suggestions"/, /suggestionDrawer/, /renderPersistentSuggestions/, /'suggestions'/];
  for (const file of [...jsFiles, path.join(UI, 'index.html')]) {
    const source = fs.readFileSync(file, 'utf8');
    for (const pattern of dead) assert.doesNotMatch(source, pattern, `${path.basename(file)} ${pattern}`);
  }
  for (const file of cssFiles) assert.doesNotMatch(fs.readFileSync(file, 'utf8'), /\.suggestion-(list|item|group|row|queue|scope|main)/, path.basename(file));
});

test('bugs de navegação do §3.1: rota learning, routeMeta.predictor e data-omegas-route', () => {
  const app = read('app.js');
  assert.doesNotMatch(read('screens/map.js'), /navigate\('learning'\)/);
  assert.match(read('screens/map.js'), /router\?\.open\('curve', 'learning'\)/);
  assert.doesNotMatch(app, /predictor/i);
  assert.match(app, /document\.body\.dataset\.omegasRoute = state\.route/);
  const ctx = loadInto({ console, localStorage: { getItem() { return null; }, setItem() {} } }, ['core/store.js', 'core/router.js']);
  const store = new ctx.OmegasUi.Store(ctx.OmegasUi.createInitialState());
  const router = new ctx.OmegasUi.Router(store);
  assert.equal(router.navigate('learning'), false, 'rota inexistente não navega');
  assert.equal(router.open('curve', 'editor'), true);
  assert.equal(store.get().route, 'curve');
  assert.equal(store.get().routeContext.subpage, 'editor');
  router.open('map');
  assert.equal(router.open('curve', 'inexistente'), true);
  assert.equal(store.get().routeContext, null);
});

test('Sessões: tela própria com duração, apagões, índice início → fim e Exportar ZIP', () => {
  assert.match(html, /<script src="screens\/sessions\.js" defer>/);
  const ctx = loadInto({ console }, ['core/display-rules.js', 'screens/sessions.js']);
  const row = ctx.OmegasUi.SessionsModel.sessionRow({
    id: 'session_2026-10-01_16-10-00', reason: 'USB', durationMs: 3600000, bytes: 1000, cngTicks: 30, petrolTicks: 10,
    semanticSummary: { blackouts: 2, index: { start: 40, end: 71 } },
  });
  assert.equal(row.blackouts, 2);
  assert.deepEqual({ ...row.index }, { start: 40, end: 71 });
  assert.equal(row.gnvPercent, 75);
  const bare = ctx.OmegasUi.SessionsModel.sessionRow({ id: 'x', durationMs: 5 });
  assert.equal(bare.blackouts, null, 'sem dado não vira 0');
  assert.equal(bare.index, null);
  const source = read('screens/sessions.js');
  assert.match(source, /Exportar ZIP/);
  assert.match(source, /api\.exportSession\(/);
  assert.doesNotMatch(read('components/drawers.js'), /data-export-session|recorded-session-item/, 'a lista saiu de Ferramentas');
});

// ---------------------------------------------------------------- 2. Agora + cérebro
const EQUIVALENCE_FIXTURE = {
  index: { value: 0.62, coverage: 9, provisional: true },
  nextAction: { kind: 'COLLECT', text: 'Rode no GNV em plano para eu medir', route: 'refino', subpage: 'pontos', pointIndexes: [3, 4] },
  points: [{ index: 0, axisMs: 2, state: 'EQUIVALENTE', mixture: 0.01 }],
  reference: { frozen: false, canFreeze: true },
};

test('NativeApi.equivalence(): null até o Kotlin expor getEquivalence; fixture válida passa', () => {
  const absent = loadInto({ console, OmegasNative: {} }, ['core/native-api.js']);
  assert.equal(new absent.OmegasUi.NativeApi().equivalence(), null);
  const demo = loadInto({ console }, ['core/native-api.js']);
  assert.equal(new demo.OmegasUi.NativeApi().equivalence(), null);

  const nested = loadInto({ console, OmegasNative: { getEquivalence: () => JSON.stringify(EQUIVALENCE_FIXTURE) } }, ['core/native-api.js']);
  const eq = new nested.OmegasUi.NativeApi().equivalence();
  assert.equal(eq.index.value, 0.62);
  assert.equal(eq.index.provisional, true);
  assert.equal(eq.nextAction.route, 'refino');
  assert.equal(eq.nextAction.subpage, 'pontos');
  assert.deepEqual(Array.from(eq.nextAction.pointIndexes), [3, 4]);

  const flat = loadInto({ console, OmegasNative: { getEquivalence: () => JSON.stringify({ ok: true, available: true, index: 0.5, coverage: 4, provisional: false, nextAction: { kind: 'WAIT', text: 'ok', route: null, subpage: null, pointIndexes: [] }, points: [], reference: null }) } }, ['core/native-api.js']);
  const eqFlat = new flat.OmegasUi.NativeApi().equivalence();
  assert.equal(eqFlat.index.value, 0.5);
  assert.equal(eqFlat.index.coverage, 4);
  assert.equal(eqFlat.nextAction.route, '');

  const none = loadInto({ console, OmegasNative: { getEquivalence: () => JSON.stringify({ ok: true, available: false, reason: 'CURVA_K_NAO_LIDA' }) } }, ['core/native-api.js']);
  assert.equal(new none.OmegasUi.NativeApi().equivalence(), null);
});

function fakeDashboardContext() {
  const nodes = new Map();
  const shell = { classes: new Set(), classList: { add(c) { shell.classes.add(c); }, remove(c) { shell.classes.delete(c); } } };
  const node = id => {
    if (!nodes.has(id)) nodes.set(id, { id, textContent: '', hidden: true, dataset: {}, parentElement: shell });
    return nodes.get(id);
  };
  const document = {
    getElementById: node,
    querySelector: () => null,
    createElement: () => ({ dataset: {} }),
    head: { appendChild() {} },
  };
  const navigations = [];
  const context = {
    console, document,
    OmegasApp: { router: { open(route, subpage) { navigations.push([route, subpage]); return true; } } },
  };
  loadInto(context, ['core/router.js']);
  return { context, node, shell, navigations };
}

test('Agora sem cérebro: bloco oculto e layout atual intacto; com fixture: índice grande + UMA ação com UM botão', () => {
  const { context, node, shell, navigations } = fakeDashboardContext();
  vm.runInContext(read('screens/dashboard.js'), context, { filename: 'screens/dashboard.js' });
  const screen = Object.create(context.OmegasUi.DashboardScreen.prototype);
  screen.renderEquivalence(null);
  assert.equal(node('dashEquivalence').hidden, true);
  assert.equal(shell.classes.has('has-equivalence'), false);

  node('dashEquivalence');
  screen.renderEquivalence(EQUIVALENCE_FIXTURE);
  assert.equal(node('dashEquivalence').hidden, false);
  assert.equal(node('dashIndex').textContent, '62%');
  assert.equal(node('dashIndexNote').hidden, false, 'provisório aparece');
  assert.equal(node('dashNextText').textContent, 'Rode no GNV em plano para eu medir');
  const button = node('dashNextButton');
  assert.equal(button.hidden, false);
  assert.equal(button.dataset.route, 'refino');
  assert.equal(button.dataset.subpage, 'pontos');
  assert.equal(button.textContent, 'Ir para Refino');
  assert.equal(shell.classes.has('has-equivalence'), true);

  // sem route válida: sem botão (não inventa destino)
  screen.renderEquivalence({ ...EQUIVALENCE_FIXTURE, nextAction: { kind: 'WAIT', text: 'Tudo certo', route: '', subpage: '', pointIndexes: [] } });
  assert.equal(button.hidden, true);
  assert.equal(navigations.length, 0, 'renderizar nunca navega nem executa');

  const source = read('screens/dashboard.js');
  assert.match(source, /app\.router\.open\(next\.dataset\.route/);
  assert.doesNotMatch(source, /startCurve|writeCurve|writeMap|startKBatchWrite|api\.(?:write|start|reset)/, 'Agora nunca executa');
});

// ---------------------------------------------------------------- 3. Detalhes técnicos
test('"Detalhes técnicos" é o único nome técnico e fica por último no bloco da sua tela', () => {
  const sources = [html, ...jsFiles.map(file => fs.readFileSync(file, 'utf8'))].join('\n');
  assert.doesNotMatch(sources, /Evidência técnica|Detalhes técnicos da ação|Detalhe técnico<|Dados técnicos<|Diagnóstico técnico</);
  const summaries = [...sources.matchAll(/<summary>([^<]*técnic[^<]*)<\/summary>/g)].map(m => m[1]);
  assert.ok(summaries.length >= 3);
  for (const text of summaries) assert.match(text, /^Detalhes técnicos/);
  const autocal = read('screens/autocal-cockpit.js');
  assert.ok(autocal.indexOf('id="autocalTechnicalDetails"') > autocal.indexOf('id="autocalSessionDrawer"'), 'AutoCal: Detalhes técnicos depois do histórico');
  const drawers = read('components/drawers.js');
  assert.ok(drawers.lastIndexOf('Detalhes técnicos') > drawers.indexOf('diagnostic-settings'), 'Ferramentas: Detalhes técnicos por último');
});

// ---------------------------------------------------------------- 4. tokens
test('tokens.css carrega primeiro e nenhum outro CSS define cor', () => {
  const links = [...html.matchAll(/<link rel="stylesheet" href="([^"]+)">/g)].map(m => m[1]);
  assert.equal(links[0], 'tokens.css');
  const tokens = read('tokens.css');
  for (const name of ['--bg', '--surface', '--text', '--accent', '--ok', '--warn', '--danger', '--state-ok', '--state-warn', '--state-error', '--state-neutral', '--state-proof', '--radius', '--space-1', '--space-6']) {
    assert.match(tokens, new RegExp(`${name}:`), name);
  }
  for (const file of cssFiles) {
    if (path.basename(file) === 'tokens.css') continue;
    const css = fs.readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
    assert.doesNotMatch(css, /#[0-9a-fA-F]{3,8}\b(?![^{}]*\{)/, `${path.basename(file)}: cor hexadecimal fora de tokens.css`);
    assert.doesNotMatch(css, /rgba?\(\s*\d/, `${path.basename(file)}: rgb() literal fora de tokens.css`);
    assert.doesNotMatch(css, /[:\s]white\b(?!-)/, `${path.basename(file)}: nome de cor fora de tokens.css`);
    assert.doesNotMatch(css, /backdrop-filter/, path.basename(file));
  }
});

test('styles-autocal-cockpit.css sem regras mortas: todo seletor de classe/id existe no JS ou no HTML', () => {
  const corpus = [html, ...jsFiles.map(file => fs.readFileSync(file, 'utf8'))].join('\n');
  const prefixes = [...corpus.matchAll(/([\w-]{4,}-)\$\{/g)].map(m => m[1]);
  const css = read('styles-autocal-cockpit.css').replace(/\/\*[\s\S]*?\*\//g, '');
  const dead = new Set();
  for (const match of css.matchAll(/([^{}@]+)\{[^{}]*\}/g)) {
    const selector = match[1].replace(/:(?:not|is|where|has)\((?:[^()]|\([^()]*\))*\)/g, '').replace(/\[[^\]]*\]/g, '');
    for (const token of selector.matchAll(/(?<![\w-])[.#](-?[_a-zA-Z][\w-]*)/g)) {
      const name = token[1];
      const alive = new RegExp(`(?<![\\w-])${name.replace(/[-\\^$*+?.()|[\]{}]/g, '\\$&')}(?![\\w-])`).test(corpus) || prefixes.some(prefix => name.startsWith(prefix));
      if (!alive) dead.add(name);
    }
  }
  // Uma regra só morre se TODOS os seus seletores morrem; aqui basta nenhum token morto em seletor de regra simples.
  const lone = [...css.matchAll(/(?:^|\})\s*([^{},@]+)\{/g)].map(m => m[1].trim()).filter(selector => /^[.#][\w-]+$/.test(selector) && dead.has(selector.slice(1)));
  assert.deepEqual(lone, []);
});

// ---------------------------------------------------------------- 5. pisos
function cssRules(css) {
  const rules = [];
  for (const match of css.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/([^{}@]+)\{([^{}]*)\}/g)) {
    rules.push({ selector: match[1].trim(), body: match[2] });
  }
  return rules;
}

test('pisos: toque >= 76 px e texto crítico >= 24 px nos controles principais; exceção só na grade do Mapa K', () => {
  const tokens = read('tokens.css');
  const floors = read('styles-floors.css');
  assert.match(tokens, /--touch-min:\s*76px/);
  assert.match(tokens, /--touch-grid:\s*44px/);
  assert.match(tokens, /--text-critical:\s*24px/);
  assert.ok(links().includes('styles-floors.css'), 'floors carregado no index.html');

  const rules = cssRules(floors);
  const control = rules.find(rule => /^html body button:not\(\.map-k-cell\):not\(\.map-axis-header\)/.test(rule.selector));
  assert.ok(control, 'regra do piso para button');
  for (const needle of ['body [role="tab"]', 'body summary', 'body select', 'body input:not(']) assert.ok(control.selector.includes(needle), needle);
  assert.match(control.body, /min-height:\s*var\(--touch-min\)\s*!important/);
  assert.match(control.body, /font-size:\s*var\(--text-critical\)\s*!important/);
  const exception = rules.find(rule => /^html body \.map-k-cell/.test(rule.selector));
  assert.equal(exception.selector.replace(/\s+/g, ' '), 'html body .map-k-cell, html body .map-axis-header');
  assert.match(exception.body, /min-height:\s*var\(--touch-grid\)/);

  // Itens do trilho: texto e número em 24 px.
  const rail = rules.find(rule => /\.side-nav button i/.test(rule.selector));
  assert.match(rail.body, /font-size:\s*var\(--text-critical\)\s*!important/);

  // Nenhuma regra de tela pode baixar o piso com !important nem limitar a altura dos controles principais.
  const mainControls = /(^|[\s,>])(button|\.primary|\.secondary|\.quiet-button|\.danger-primary|\.icon-close|\.side-nav button|\[role="tab"\]|\.view-switch button|select)(?![\w-])/;
  const violations = [];
  for (const file of cssFiles) {
    const name = path.basename(file);
    if (name === 'styles-floors.css' || name === 'tokens.css') continue;
    for (const rule of cssRules(fs.readFileSync(file, 'utf8'))) {
      const grid = /map-k-cell|map-axis-header|map-rpm-header|map-ms-header|map-k-grid/.test(rule.selector);
      if (grid || !rule.selector.split(',').some(part => mainControls.test(part.trim()))) continue;
      for (const [, property, value] of rule.body.matchAll(/(min-height|max-height|height|font-size)\s*:\s*([\d.]+)px\s*!important/g)) {
        const limit = property === 'font-size' ? 24 : 76;
        if (property === 'max-height' || Number(value) < limit) violations.push(`${name} ${rule.selector} ${property}:${value}px`);
      }
      const max = rule.body.match(/(?<![\w-])max-height\s*:\s*([\d.]+)px/);
      if (max && Number(max[1]) < 76) violations.push(`${name} ${rule.selector} max-height:${max[1]}px`);
    }
  }
  assert.deepEqual(violations, []);
  function links() { return [...html.matchAll(/<link rel="stylesheet" href="([^"]+)">/g)].map(m => m[1]); }
});

// ---------------------------------------------------------------- 6. sem timers de UI
test('sem timers de UI: só o scheduler tem um setInterval; nenhum setTimeout nas telas', () => {
  for (const file of jsFiles) {
    const rel = path.relative(UI, file);
    const source = fs.readFileSync(file, 'utf8');
    const intervals = (source.match(/setInterval\(/g) || []).length;
    const timeouts = (source.match(/setTimeout\(/g) || []).length;
    if (rel === path.join('core', 'scheduler.js')) {
      assert.equal(intervals, 1, 'um único laço no scheduler');
      assert.equal(timeouts, 0);
    } else {
      assert.equal(intervals, 0, `${rel}: setInterval`);
      assert.equal(timeouts, 0, `${rel}: setTimeout`);
    }
  }
});

// ---------------------------------------------------------------- 7. overlay de operação
test('operação na ECU: mesma fala (etapa → resultado → Desfazer/Voltar) e nenhum diálogo de confirmação', () => {
  for (const file of jsFiles) {
    assert.doesNotMatch(fs.readFileSync(file, 'utf8'), /window\.(?:confirm|alert|prompt)\(|(?<![\w.$])(?:confirm|prompt)\(/, path.relative(UI, file));
  }
  assert.doesNotMatch(html, /onclick=|confirm\(/);
  assert.match(html, /<span>Foto antes<\/span><span>Escrita<\/span><span>ACK<\/span><span>Conferindo na ECU<\/span>/);
  assert.match(html, /id="curveUndoButton"[^>]*>Desfazer</);
  assert.equal([...html.matchAll(/id="(?:map|curve)DismissResult"[^>]*>Voltar</g)].length, 2);
  for (const file of ['screens/map.js', 'screens/curve.js']) {
    const source = read(file);
    assert.match(source, /wording\(\)\.doneTitle/);
    assert.match(source, /wording\(\)\.failedTitle/);
  }
  assert.match(read('screens/refino.js'), /commitReview\(\)/);
});

test('reset da Curva K salva a foto antes e só zera depois dela', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const src = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/screens/curve.js'), 'utf8');
  const reset = src.slice(src.indexOf('    resetCurve() {'), src.indexOf('    startResetWrite() {'));
  assert.ok(reset.includes("startCurveBackup('Antes do reset')"), 'foto antes do reset');
  assert.ok(!reset.includes('api.resetCurve()'), 'o primeiro toque não zera direto');
  const write = src.slice(src.indexOf('    startResetWrite() {'), src.indexOf('    prepareRestore('));
  assert.ok(write.includes('api.resetCurve()'), 'a segunda etapa zera');
  assert.ok(src.includes("task === 'reset-photo'") && src.includes('this.startResetWrite()'), 'zera só após a foto concluir');
  assert.ok(!src.includes('confirm('), 'sem diálogo de confirmação');
});
