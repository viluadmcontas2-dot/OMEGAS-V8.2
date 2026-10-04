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
  const PRE = ['core/display-rules.js', 'core/live-store.js', 'components/curve-chart.js'];
  for (const file of [...PRE, ...files.filter(file => !PRE.includes(file))]) vm.runInContext(read(file), context, { filename: file });
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
  assert.doesNotMatch(read('screens/map.js'), /learning/, 'o Mapa K não volta mais para a Aprendizado global');
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
    semanticSummary: { blackouts: 2, index: { start: 0.4, end: 0.71 } },
  });
  assert.equal(row.blackouts, 2);
  assert.deepEqual({ ...row.index }, { start: 0.4, end: 0.71 });
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

test('a UI não deriva ação da fase: o cérebro (nextAction) é a única fonte', () => {
  const api = read('core/native-api.js');
  for (const dead of ['PHASE_NEXT_ACTION', 'nextActionFromPhase', 'normalizeEquivalence', 'equivalence()']) assert.ok(!api.includes(dead), dead);
  assert.ok(!api.includes("generation: 'V7'"));
});

test('Agora é para dirigir (D1): sem cartão de equivalência, só 4 valores grandes + faixa quieta', () => {
  const source = read('screens/dashboard.js');
  for (const dead of ['dashEquivalence', 'dashIndex', 'dashNextText', 'dashNextButton', 'renderEquivalence', 'Ir para Refino', 'PRÓXIMA AÇÃO', 'próxima ação']) {
    assert.ok(!source.includes(dead), `Agora não tem mais ${dead}`);
  }
  for (const id of ['dashHeroPetrol', 'dashRpm', 'dashMap', 'dashFuel']) assert.ok(source.includes(`id="${id}"`), id);
  assert.doesNotMatch(read('app.js'), /refreshEquivalence/, 'o Agora não consulta a equivalência');
  assert.doesNotMatch(source, /startCurve|writeCurve|writeMap|startKBatchWrite|api\.(?:write|start|reset)/, 'Agora nunca executa');
});

test('Refino: faixa discreta "GNV ≈ gasolina em N %" + UMA ação + botão de um toque (índice é fração 0..1)', () => {
  const ctx = loadInto({ console }, ['core/display-rules.js', 'core/autocal-api.js', 'screens/refino.js']);
  const strip = ctx.OmegasUi.RefinoModel.equivalenceStrip;
  const routes = ['dashboard', 'map', 'curve', 'autocal', 'refino', 'sessions', 'tools'];
  const empty = strip(null, routes);
  assert.equal(empty.nextText, 'Aguardando dados da ECU', 'sem cérebro: aviso neutro, nunca ação derivada da fase');
  assert.equal(empty.route, '');
  assert.equal(empty.hasAction, false);
  const eq = { ...EQUIVALENCE_FIXTURE, nextAction: { kind: 'COLLECT', text: 'Rode no GNV em plano para eu medir', route: 'curve', subpage: 'editor', pointIndexes: [3, 4] } };
  const shown = strip(eq, routes);
  assert.equal(shown.indexText, '62% da condução já equivale à gasolina · provisório');
  assert.equal(shown.nextText, 'Rode no GNV em plano para eu medir');
  assert.equal(shown.route, 'curve');
  assert.equal(shown.subpage, 'editor');
  assert.equal(shown.routeLabel, 'Ir para Curva K');
  assert.equal(strip({ ...eq, index: { value: 0.01 } }, routes).indexText, '1% da condução já equivale à gasolina', 'fração 0,01 = 1 %, nunca 0 %');
  assert.equal(strip({ ...eq, index: { value: 1 } }, routes).indexText, '100% da condução já equivale à gasolina');
  assert.equal(strip({ ...eq, index: { value: null } }, routes).indexText, '— da condução já equivale à gasolina');
  assert.equal(strip(EQUIVALENCE_FIXTURE, routes).route, '', 'aponta para o próprio Refino: sem botão');
  assert.equal(strip({ ...eq, nextAction: { text: 'Tudo certo', route: '' } }, routes).route, '');
  const source = read('screens/refino.js');
  assert.match(source, /router\?\.open\(go\.dataset\.route/);
  assert.match(source, /id="refinoEq"/);
});

test('Sessões: índice é fração 0..1 e aparece em % (0,01 = 1 %, nunca "0%")', () => {
  const ctx = loadInto({ console }, ['core/display-rules.js', 'screens/sessions.js']);
  const pt = ctx.OmegasUi.SessionsModel.percentText;
  assert.equal(pt(0.01), '1%');
  assert.equal(pt(0.41), '41%');
  assert.equal(pt(1), '100%');
  assert.equal(pt(null), '—');
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
  assert.match(html, /<span>Foto antes<\/span><span>Gravando<\/span><span>Conferindo na ECU<\/span>/);
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

test('reset: a foto precisa ser confirmada, o poll acompanha a foto e voltar à aba cancela o reset pendente', () => {
  const root = path.join(__dirname, '../../app/src/main/assets/ui');
  const curve = fs.readFileSync(path.join(root, 'screens/curve.js'), 'utf8');
  const app = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
  assert.ok(app.includes('instances.curve.backupTask'), 'o poll roda enquanto a foto está pendente');
  assert.ok(curve.includes('!operation.hash || !operation.publicPath'), 'só a operação de foto autoriza o reset');
  const enter = curve.slice(curve.indexOf('    onEnter(context) {'), curve.indexOf('    refreshBackups() {'));
  assert.ok(enter.includes("this.backupTask === 'reset-photo'") && !enter.slice(0, 400).includes('startResetWrite'), 'voltar à aba não zera');
  const read = curve.slice(curve.indexOf('    startRead() {'), curve.indexOf('    startRead() {') + 120);
  assert.ok(read.includes('if (this.backupTask) return;'), 'leitura não rouba a foto');
});
