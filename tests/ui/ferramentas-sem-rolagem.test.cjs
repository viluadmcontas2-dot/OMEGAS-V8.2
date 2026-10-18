'use strict';

// Trava do dono: nada rola, alvos grandes, texto legível (reescrito em 2026-10-18 para os cartões por assunto e a aba Sessões).
// A medição real no Chromium (3 tamanhos de multimídia, paisagem) fica em e2e-navegacao e tools/ci/sem_rolagem_abas.cjs; aqui travamos
// as decisões de CSS/marcação e a paginação (log e sessões) sem navegador.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const UI = path.join(__dirname, '../../app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(UI, file), 'utf8');
const css = read('styles-ux-leigo.css');
const html = read('index.html');
function rules(selector) {
  const out = [];
  for (const m of css.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/([^{}]+)\{([^{}]*)\}/g)) if (m[1].split(',').map(x => x.trim()).includes(selector)) out.push(m[2]);
  assert.ok(out.length, 'regra ausente: ' + selector);
  return out.join(';');
}
const px = (body, prop) => { const m = body.match(new RegExp('(?:^|;|\\s)' + prop + '\\s*:\\s*(\\d+)px')); return m ? Number(m[1]) : null; };
function load(file) {
  const context = { console, Date, Math, JSON, Intl, Set, Map, Number, String, Array, Object };
  context.window = context; context.globalThis = context; context.OmegasUi = {};
  vm.createContext(context); vm.runInContext(read(file), context, { filename: file });
  return context;
}

test('Ferramentas e Sessões não rolam (overflow escondido, nunca auto/scroll)', () => {
  assert.doesNotMatch(css, /overflow(?:-[xy])?\s*:\s*(?:auto|scroll)/, 'o CSS novo não pode criar rolagem');
  assert.match(rules('#toolsDrawer.screen.active'), /overflow:\s*hidden/);
  assert.match(rules('.sessions-screen.active'), /overflow:\s*hidden/);
  assert.match(rules('.log-lines'), /overflow:\s*hidden/);
});

test('alvos de toque ≥ 56 px nos cartões, botões, interruptor e campos', () => {
  for (const sel of ['.tool-foot-btn', '.tool-actions button', '.tool-back', '.tile-action', '.tool-switch', '.log-tools button', '.tool-log-pager button', '.recorded-session-item button', '.tool-tech > summary']) {
    assert.ok(px(rules(sel), 'min-height') >= 56, sel);
  }
  assert.ok(px(rules('.num-box'), 'height') >= 56);
  assert.ok(px(rules('.sess-main'), 'min-height') >= 56);
  assert.doesNotMatch(html + read('components/drawers.js') + read('components/advanced.js'), />\s*Abas\s*</, 'sem botão "Abas"');
});

test('texto de corpo ≥ 18 px nos cartões e nunca abaixo de 15 px em apoio', () => {
  for (const sel of ['.tool-tile .tile-line', '.tool-layer-card p', '.num-help', '.read-list li', '.log-lines > div', '.empty-copy']) assert.ok(px(rules(sel), 'font-size') >= 18, sel);
  const sizes = [...css.matchAll(/font-size\s*:\s*(\d+(?:\.\d+)?)px/g)].map(m => Number(m[1]));
  assert.deepEqual(sizes.filter(s => s < 15), []);
});

test('Sessões é aba própria e saiu de Ferramentas', () => {
  assert.match(html, /<button type="button" data-route="sessions"><span>Sessões<\/span><\/button>/);
  assert.match(html, /data-screen="sessions"/);
  const tools = html.slice(html.indexOf('id="toolsDrawer"'), html.indexOf('</main>'));
  assert.doesNotMatch(tools, /session|Sessões/i, 'nada de sessões dentro de Ferramentas');
  assert.match(read('core/router.js'), /ROUTES = \['autocal', 'refino', 'map', 'curve', 'sessions', 'tools'\]/);
});

function harness() {
  const hosts = {};
  const mk = id => (hosts[id] = { innerHTML: '', childElementCount: 0, querySelector: () => null, dataset: {} });
  const context = load('components/drawers.js');
  context.document = { getElementById: id => hosts[id] || mk(id), activeElement: null };
  mk('toolDiagnosticsWorkspace'); mk('sessionsHost');
  const drawers = Object.create(context.OmegasUi.Drawers.prototype);
  drawers.api = { batteryOptimizationStatus: () => ({ ignoringOptimizations: true }), overlayStatus: () => ({ permissionGranted: true }) };
  Object.assign(drawers, { logLevel: 'ALL', logCategory: 'ALL', logPage: 0, sessionPage: 0, view: 'log', page: 2, sessionSettingsFeedback: '', probeNote: '' });
  const state = {
    route: 'tools', sessionStatus: { settings: {} },
    sessions: Array.from({ length: 7 }, (_, i) => ({ id: 'session_2026-10-11_0' + i + '-00-00', reason: 'Sessão', durationMs: 60000, bytes: 1024 })),
    logs: Array.from({ length: 30 }, (_, i) => ({ time: 't' + String(i).padStart(2, '0'), level: 'INFO', category: 'USB', message: 'm' + i })),
  };
  drawers.store = { get: () => state, patch() {} };
  return { hosts, drawers, state };
}

test('registro do app: as mesmas 24 linhas em páginas de 5, sem passar da última', () => {
  const { hosts, drawers, state } = harness();
  drawers.renderTools(state);
  const h = hosts.toolDiagnosticsWorkspace;
  assert.equal((h.innerHTML.match(/<time>/g) || []).length, 5);
  assert.match(h.innerHTML, /<time>t29<\/time>/, 'começa pela mais nova');
  assert.match(h.innerHTML, /1 de 5/);
  const next = { target: { closest: () => ({ id: '', dataset: { logPage: 'next' }, matches: s => s === '[data-log-page]' }) } };
  for (let i = 0; i < 9; i += 1) drawers.handleToolClick(next);
  assert.match(h.innerHTML, /5 de 5/);
  assert.match(h.innerHTML, /<time>t06<\/time>/, 'a 24ª linha mais nova continua alcançável');
});

test('sessões: 3 por página, "Salvar arquivo" por sessão, configuração dentro de "Tamanho das gravações"', () => {
  const { hosts, drawers, state } = harness();
  drawers.renderSessions(state);
  const out = hosts.sessionsHost.innerHTML;
  assert.equal((out.match(/data-export-session=/g) || []).length, 3);
  assert.match(out, /Gravações salvas <span class="count">\(7\)<\/span>/);
  assert.match(out, /<details class="diagnostic-settings" >/);
  assert.match(out, /Começar a gravar/);
  assert.match(out, /1 de 3/);
  assert.doesNotMatch(out, /ECU|USB bruto|Telemetria salva|ZIP/);
});

test('Ferramentas: um cartão por função e os botões de backup mantêm os ids', () => {
  const { hosts, drawers, state } = harness();
  drawers.view = 'home';
  drawers.renderTools(state);
  const out = hosts.toolDiagnosticsWorkspace.innerHTML;
  for (const id of ['toolExportData', 'toolExportLearning', 'toolImportLearning', 'toolAutoRefine', 'toolBufferProbe']) assert.match(out, new RegExp('id="' + id + '"'));
  assert.equal((out.match(/class="tool-tile/g) || []).length, 8);
});
