'use strict';

// Trava do dono (2026-10-11): "todo o aplicativo funciona sem rolagem nem sub-rolagem".
// Aprender e Ferramentas: nada rola; o que não cabe vira aba interna (sem "Detalhes" — dono, 2026-10-11)
// no mesmo lugar, ações embaixo e à esquerda, toque ≥ 48 px, texto ≥ 12 px. A medição real no Chromium
// fica em tools/ci/sem_rolagem_8_abas.cjs; aqui travamos as decisões de marcação/CSS sem navegador.

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(UI, file), 'utf8');
const css = read('styles.css');
const html = read('index.html');
const MARK = 'Aprender e Ferramentas sem rolagem (dono, 2026-10-11)';

function block() {
  const start = css.indexOf(MARK);
  assert.notEqual(start, -1, 'bloco CSS "' + MARK + '" no fim de styles.css');
  return css.slice(css.lastIndexOf('/*', start)).replace(/\/\*[\s\S]*?\*\//g, '');
}
function rule(selector) {
  const body = [];
  for (const match of block().matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    if (match[1].split(',').map(item => item.trim()).includes(selector)) body.push(match[2]);
  }
  assert.ok(body.length, 'regra ausente: ' + selector);
  return body.join(';');
}
function px(body, property) {
  const match = body.match(new RegExp('(?:^|;|\\s)' + property + '\\s*:\\s*(\\d+)px'));
  return match ? Number(match[1]) : null;
}
function section(screen) {
  const start = html.indexOf(`data-screen="${screen}"`);
  assert.notEqual(start, -1, screen);
  const next = html.indexOf('data-screen="', start + 20);
  return html.slice(start, next === -1 ? html.indexOf('</main>', start) : next);
}
function load(file) {
  const context = { console, Date, Math, JSON, Intl, Set, Map, Number, String, Array, Object };
  context.window = context;
  context.globalThis = context;
  context.OmegasUi = {};
  vm.createContext(context);
  vm.runInContext(read(file), context, { filename: file });
  return context;
}

test('as duas telas e o painel do Aprender não rolam (overflow escondido, nunca auto/scroll)', () => {
  assert.doesNotMatch(block(), /overflow(?:-[xy])?\s*:\s*(?:auto|scroll)/, 'o bloco novo não pode criar rolagem');
  assert.match(rule('.screen[data-screen="learning"].active'), /overflow:\s*hidden/);
  assert.match(rule('.screen[data-screen="learning"] #learningCellDetail'), /overflow:\s*hidden/, 'painel Célula/Coleta/Decisões/Tolerâncias sem sub-rolagem');
  assert.match(rule('#learningCellDetail > .learning-inspector-pane.active'), /overflow:\s*hidden/);
  assert.match(rule('#toolsDrawer.screen.active'), /overflow:\s*hidden/, 'Ferramentas deixou de ser .utility-screen que rola');
  assert.match(rule('#toolDiagnosticsWorkspace'), /overflow:\s*hidden/);
  for (const list of ['#toolDiagnosticsWorkspace .recorded-session-list', '#toolDiagnosticsWorkspace .log-lines']) {
    assert.match(rule(list), /max-height:\s*none/, list);
    assert.match(rule(list), /overflow:\s*hidden/, list);
  }
});

test('ações embaixo, à esquerda, com 56 px; toque ≥ 48 px', () => {
  const learning = rule('.screen[data-screen="learning"].active');
  assert.match(learning, /grid-template-areas:\s*"intro panel"\s*"map panel"\s*"foot panel"/, 'camadas no rodapé, painel em altura inteira');
  assert.match(rule('.screen[data-screen="learning"]'), /--foot-h:\s*56px/);
  assert.match(rule('.screen[data-screen="learning"] > .learning-controls'), /grid-area:\s*foot[\s\S]*justify-content:\s*flex-start/);
  const learningHtml = section('learning');
  assert.ok(learningHtml.indexOf('class="learning-workspace"') < learningHtml.indexOf('class="learning-controls"'), 'camadas depois do mapa (embaixo)');
  assert.ok(px(rule('#learningCellPane .learning-cell-actions button'), 'min-height') >= 56);
  assert.match(rule('#learningCellPane .learning-cell-actions'), /margin-top:\s*auto/, 'ações da célula embaixo do painel');
  assert.ok(px(rule('#learningTolerancePane .tolerance-actions button'), 'min-height') >= 56);
  assert.ok(px(rule('#learningCellDetail > .learning-inspector-tabs button'), 'min-height') >= 48);

  assert.match(rule('#toolsDrawer'), /--foot-h:\s*60px/);
  assert.match(rule('#toolsDrawer > .tool-actions'), /grid-row:\s*3[\s\S]*justify-content:\s*flex-start/, 'backup no rodapé, à esquerda');
  assert.ok(px(rule('#toolsDrawer > .tool-actions > button'), 'min-height') >= 56);
  assert.ok(px(rule('#toolDiagnosticsWorkspace details:not([open]) > summary'), 'min-height') >= 56);
  assert.ok(px(rule('#toolDiagnosticsWorkspace .recorder-actions button'), 'min-height') >= 56);
  for (const selector of ['#toolDiagnosticsWorkspace .recorded-session-item button', '#toolDiagnosticsWorkspace .tool-log-pager button', '#toolDiagnosticsWorkspace .log-filters select']) {
    assert.ok(px(rule(selector), 'min-height') >= 48, selector);
  }
  assert.doesNotMatch(html + read('screens/learning.js') + read('components/drawers.js'), />\s*Abas\s*</, 'sem botão "Abas"');
});

test('texto do bloco novo nunca abaixo de 12 px', () => {
  const sizes = [...block().matchAll(/font-size\s*:\s*(\d+(?:\.\d+)?)px/g)].map(match => Number(match[1]));
  assert.ok(sizes.length > 20);
  assert.deepEqual(sizes.filter(size => size < 12), []);
});

test('Ferramentas: painel aberto ocupa o lugar dos cartões (CSS)', () => {
  assert.doesNotMatch(block(), /data-cell-view|data-tolerance-view/, 'sem vistas de Detalhes (dono, 2026-10-11)');
  assert.match(rule('#toolDiagnosticsWorkspace details[open]'), /position:\s*absolute[\s\S]*inset:\s*0/, 'disclosure aberto ocupa o lugar dos cartões');
});

test('Aprender: Decisões em aba própria; sem Detalhes, sem Diagnóstico técnico e sem código cru (dono, 2026-10-11)', () => {
  const context = load('screens/learning.js');
  const proto = context.OmegasUi.LearningScreen.prototype;
  const source = read('screens/learning.js');
  assert.match(source, /data-learning-inspector="history">Decisões</);
  assert.doesNotMatch(source, /Diagnóstico técnico|data-learning-view-toggle|toggleView|reason-code|LIMITES CONFIGURADOS/);

  const screen = Object.create(proto);
  screen.collectionPane = { innerHTML: '', querySelector: () => null };
  screen.historyPane = { innerHTML: '' };
  screen.decisionHistory = [{ time: '10:00:00', level: 'accepted', label: 'Evidência aceita', reason: 'Amostra forte', code: 'SAMPLE_ACCEPTED', fuel: 'Gasolina', cell: '2×2' }];
  screen.renderCollection({ learningDecision: { state: 'FORMING_SAMPLE', reason: 'Formando', frame_count: 7, desired_frames: 10 }, telemetry: { live: { rpm: 2500 } } });
  assert.match(screen.collectionPane.innerHTML, /DECISÃO DO NÚCLEO[\s\S]*CONDIÇÃO AGORA/);
  assert.doesNotMatch(screen.collectionPane.innerHTML, /ÚLTIMAS DECISÕES|FORMING_SAMPLE|OBSERVING_ENGINE/, 'Coleta sem histórico e sem código cru');
  assert.match(screen.historyPane.innerHTML, /ÚLTIMAS DECISÕES OBSERVADAS[\s\S]*Amostra forte/);
  assert.doesNotMatch(screen.historyPane.innerHTML, /SAMPLE_ACCEPTED/, 'Decisões sem código cru');

  const tolerance = Object.create(proto);
  tolerance.tolerancePane = { innerHTML: '', dataset: {}, querySelectorAll: () => [], querySelector: () => null };
  tolerance.toleranceSignature = '';
  tolerance.renderTolerances({ learningTolerance: { policy: { mapOscillationBar: 0.035 }, controlModel: { minimumWaterC: 60, levels: ['A', 'B'], controls: [{ id: 'rpm', title: 'Rotação', description: 'Quanto varia', selected: 1 }] } } });
  const pane = tolerance.tolerancePane.innerHTML;
  assert.doesNotMatch(pane, />Detalhes<|LIMITES CONFIGURADOS/, 'Tolerâncias só com os ajustes');
  assert.match(pane, /class="tolerance-actions"><button type="button" class="primary" data-apply-tolerances>Aplicar tolerâncias<\/button><button type="button" class="quiet-button" data-reset-tolerances>Restaurar padrão<\/button><\/div>/, 'botões que gravam intactos');
  assert.match(pane, /Nada aqui escreve na ECU\./);

  const cell = Object.create(proto);
  cell.router = { navigate: () => {} };
  cell.cellPane = { innerHTML: '', dataset: {}, querySelector: () => ({ addEventListener: () => {} }) };
  cell.renderDetail({ learning: {} }, 0, 0);
  assert.doesNotMatch(cell.cellPane.innerHTML, /Diagnóstico técnico|<details/);
  assert.match(cell.cellPane.innerHTML, /Abrir o editor não escreve na ECU\. Revisão, confirmação, ACK e readback continuam obrigatórios\./);
});

function toolsHarness(open) {
  const host = {
    innerHTML: '',
    childElementCount: 0,
    querySelector: selector => (open[selector] ? { open: true } : null),
  };
  const context = load('components/drawers.js');
  context.document = { getElementById: id => (id === 'toolDiagnosticsWorkspace' ? host : null), activeElement: null };
  const drawers = Object.create(context.OmegasUi.Drawers.prototype);
  drawers.api = { batteryOptimizationStatus: () => ({ ignoringOptimizations: true }), overlayStatus: () => ({ permissionGranted: true }) };
  drawers.logLevel = 'ALL';
  drawers.logCategory = 'ALL';
  drawers.logPage = 0;
  drawers.sessionSettingsFeedback = '';
  const state = {
    sessionStatus: { settings: {} },
    sessions: Array.from({ length: 9 }, (_, i) => ({ id: 'session_2026-10-11_0' + i + '-00-00', reason: 'Sessão', durationMs: 60000, bytes: 1024 })),
    logs: Array.from({ length: 30 }, (_, i) => ({ time: 't' + String(i).padStart(2, '0'), level: 'INFO', category: 'USB', message: 'm' + i })),
  };
  drawers.store = { get: () => state };
  return { host, drawers, state };
}

test('Ferramentas: cartões + disclosures que abrem no lugar; log em páginas de 8 (as mesmas 24 linhas)', () => {
  const { host, drawers, state } = toolsHarness({ '.tool-sessions': true });
  drawers.renderTools(state);
  const out = host.innerHTML;
  for (const card of ['background-health-card', 'diagnostic-recorder-card', 'learning-portability-card']) assert.match(out, new RegExp('<section class="' + card + '"'));
  assert.match(out, /<details class="tool-sessions" open>[\s\S]*Sessões gravadas \(9\)[\s\S]*class="recorded-session-list"/, 'lista de sessões dentro do disclosure, estado aberto preservado');
  assert.equal((out.match(/data-export-session=/g) || []).length, 8, 'até 8 sessões, como antes');
  assert.match(out, /<details class="diagnostic-settings" >/);
  assert.match(out, /<details class="tool-logs live-log-console" >/);
  assert.match(out, /Importar aprendizado<\/b> abaixo\./, 'os botões de backup estão no rodapé (abaixo)');
  assert.equal((out.match(/<time>/g) || []).length, 8, '8 linhas por página');
  assert.match(out, /<time>t29<\/time>/, 'página 1 começa pela mais nova');
  assert.match(out, /Página 1 de 3/);

  drawers.handleToolClick({ target: { closest: () => ({ dataset: { logPage: 'next' }, matches: selector => selector === '[data-log-page]' }) } });
  assert.match(host.innerHTML, /Página 2 de 3/);
  assert.match(host.innerHTML, /<time>t21<\/time>/);
  assert.doesNotMatch(host.innerHTML, /<time>t29<\/time>/);
  drawers.handleToolClick({ target: { closest: () => ({ dataset: { logPage: 'next' }, matches: selector => selector === '[data-log-page]' }) } });
  drawers.handleToolClick({ target: { closest: () => ({ dataset: { logPage: 'next' }, matches: selector => selector === '[data-log-page]' }) } });
  assert.match(host.innerHTML, /Página 3 de 3/, 'não passa da última página');
  assert.match(host.innerHTML, /<time>t06<\/time>/, 'a 24ª linha mais nova continua alcançável');
});

test('index.html: rodapé de backup de Ferramentas intacto e o cartão entra antes dele', () => {
  const tools = section('tools');
  for (const id of ['toolExportData', 'toolExportLearning', 'toolImportLearning']) assert.match(tools, new RegExp('id="' + id + '"'));
  assert.match(tools, /Confere o arquivo antes; nunca grava na ECU/);
  assert.match(read('components/drawers.js'), /this\.tools\.insertBefore\(host, footer\)/);
});
