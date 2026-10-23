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
  // Única exceção documentada: a tela de Valores do anti-apagão com os "Ajustes extras (experimental)" abertos (não cabe sem rolar).
  // Segunda exceção (dono, 2026-10-20): a grade da PÁGINA DE GRUPO de Ferramentas rola só na vertical ("pode ter rolagem vertical").
  const grade = /#toolAdvanced \.grp-grid\s*\{\s*overflow-y:\s*auto;\s*overflow-x:\s*hidden;\s*\}/;
  assert.match(css, grade, 'a grade do grupo rola só na vertical (nunca na horizontal)');
  const semExcecao = css.replace(/\.values-view\s*\{\s*overflow-y:\s*auto;\s*\}/, '').replace(grade, '');
  assert.doesNotMatch(semExcecao, /overflow(?:-[xy])?\s*:\s*(?:auto|scroll)/, 'o CSS novo não pode criar rolagem');
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
  const sizes = [...css.slice(0, css.indexOf('/* Legenda do gráfico do AutoCal')).matchAll(/font-size\s*:\s*(\d+(?:\.\d+)?)px/g)].map(m => Number(m[1]));
  assert.deepEqual(sizes.filter(s => s < 15), []);
});

test('Sessões acessível por Ferramentas e fora da navegação principal', () => {
  const nav = html.slice(html.indexOf('<nav'), html.indexOf('</nav>'));
  assert.doesNotMatch(nav, /data-route="sessions"/, 'Sessões não ocupa uma aba principal');
  assert.match(html, /data-screen="sessions"/);
  assert.match(html, /data-session-back/);
  assert.match(read('components/drawers.js'), /data-tool-open-sessions/);
  assert.match(read('core/router.js'), /ROUTES = \\['autocal', 'refino', 'map', 'curve', 'sessions', 'tools'\\]/);
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
  assert.match(out, /Começa ao conectar a central\. Você também pode iniciar agora\./);
  state.sessionStatus.settings.autoStartOnUsb = false;
  drawers.sessionsSignature = '';
  drawers.renderSessions(state);
  assert.match(hosts.sessionsHost.innerHTML, /Não começa sozinha\. Toque para iniciar uma gravação\./);
  assert.doesNotMatch(hosts.sessionsHost.innerHTML, /Começa ao conectar a central/);
  assert.match(out, /1 de 3/);
  assert.doesNotMatch(out, /ECU|USB bruto|Telemetria salva|ZIP/);
});

test('Ferramentas: um cartão por função; só "Cópia de segurança" (sem aprendizado de outro arquivo)', () => {
  const { hosts, drawers, state } = harness();
  drawers.view = 'home';
  drawers.renderTools(state);
  const out = hosts.toolDiagnosticsWorkspace.innerHTML;
  for (const id of ['toolExportData', 'toolAutoRefine']) assert.match(out, new RegExp('id="' + id + '"'));
  assert.doesNotMatch(out, /toolBufferProbe|Teste de gravação/, 'a sonda (dois toques, escrevia na central) saiu do que o leigo vê (2026-10-20)');
  for (const id of ['toolExportLearning', 'toolImportLearning']) assert.doesNotMatch(out, new RegExp('id="' + id + '"'), 'removido a pedido do dono (2026-10-19): o app usa a telemetria atual');
  assert.match(out, /data-tool-open-sessions/, 'atalho de Sessões dentro de Ferramentas');
  assert.equal((out.match(/class="tool-tile/g) || []).length, 6);
});
