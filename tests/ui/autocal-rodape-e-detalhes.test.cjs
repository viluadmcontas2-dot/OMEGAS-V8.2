// AutoCal e Refino (dono, 2026-10-11): gráfico grande, botões num rodapé embaixo à esquerda, estado em frase sob
// o título, "Detalhes" no lugar do gráfico; a navegação inferior nunca some e não existe botão "Abas".
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ui = path.resolve(__dirname, '../../app/src/main/assets/ui');
const cockpit = fs.readFileSync(path.join(ui, 'screens/autocal-cockpit.js'), 'utf8');
const css = fs.readFileSync(path.join(ui, 'styles-autocal-cockpit.css'), 'utf8');

// Rodapé fora do cabeçalho, depois do gráfico.
const header = cockpit.match(/<header class="autocal-focus-toolbar"[\s\S]*?<\/header>/)?.[0] || '';
assert.ok(header, 'cabeçalho do AutoCal existe');
assert.equal(header.includes('autocal-focus-actions'), false, 'botões não ficam no cabeçalho');
assert.ok(cockpit.indexOf('class="autocal-focus-actions"') > cockpit.indexOf('id="autocalReferenceChart"'), 'botões ficam depois do gráfico');
assert.match(cockpit, /<p id="autocalHeadline" class="autocal-headline">/, 'frase de estado sob o título');
assert.match(cockpit, /this\.text\('autocalHeadline', human\.title \+ ' · ' \+ human\.progress\);/, 'a frase acompanha o estado da ECU');
assert.match(cockpit, /data-autocal-details aria-expanded="false">Detalhes<\/button>/, 'Detalhes é um botão do rodapé');

// CSS: sem rolagem, botões à esquerda, Detalhes no lugar do gráfico.
assert.match(css, /\.app-shell\.autocal-focus \.screen\.autocal-route-screen \{ overflow: hidden;/);
assert.match(css, /\.app-shell\.autocal-focus \.refino-action-bar \{[\s\S]*?justify-content: flex-start;/);
assert.match(css, /\[data-autocal-view="details"\] \.autocal-chart-host \{ display: none; \}/);
assert.match(css, /\.autocal-reset-popover \{ top: auto; bottom: calc\(100% \+ 6px\);/, 'Ações avançadas abre para cima, sobre o gráfico');

// Comportamento do botão Detalhes.
const context = { console, document: { querySelector: () => null, getElementById: () => null }, setTimeout: () => 0 };
context.globalThis = context;
context.window = context;
vm.runInNewContext(cockpit, context);
const Cockpit = context.OmegasUi.AutoCalCockpit;
const section = { dataset: { autocalView: 'chart' } };
const attrs = {};
const button = { textContent: 'Detalhes', setAttribute: (k, v) => { attrs[k] = v; } };
const fake = { panel: { querySelector: sel => (sel === '.autocal-cockpit' ? section : sel === '[data-autocal-details]' ? button : null) } };
Cockpit.prototype.toggleDetails.call(fake);
assert.equal(section.dataset.autocalView, 'details');
assert.equal(button.textContent, 'Gráfico');
assert.equal(attrs['aria-expanded'], 'true');
Cockpit.prototype.toggleDetails.call(fake);
assert.equal(section.dataset.autocalView, 'chart');
assert.equal(button.textContent, 'Detalhes');

// Nenhuma tela esconde a navegação nem tem botão "Abas".
const sources = ['index.html', 'app.js', ...fs.readdirSync(path.join(ui, 'screens')).map(f => 'screens/' + f)];
for (const file of sources) {
  const text = fs.readFileSync(path.join(ui, file), 'utf8');
  assert.doesNotMatch(text, />\s*(☰\s*)?Abas\s*</, `${file}: sem botão "Abas" (dono, 2026-10-11)`);
  assert.doesNotMatch(text, /toggleNav/, `${file}: a navegação inferior nunca some`);
}
for (const file of fs.readdirSync(ui).filter(f => f.endsWith('.css'))) {
  const text = fs.readFileSync(path.join(ui, file), 'utf8');
  assert.doesNotMatch(text, /\.side-rail\s*\{[^}]*display:\s*none/, `${file}: a navegação inferior nunca some`);
}
console.log('AUTOCAL_RODAPE_E_DETALHES=PASS');
