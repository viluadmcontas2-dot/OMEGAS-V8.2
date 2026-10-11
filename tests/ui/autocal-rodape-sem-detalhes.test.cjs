// AutoCal e Refino (dono, 2026-10-11): gráfico grande, botões num rodapé embaixo à esquerda, estado em frase sob
// o título; sem "Detalhes" (o leigo só usa e valida); a navegação inferior é fixa e não existe botão "Abas".
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
assert.match(cockpit, /this\.text\('autocalHeadline', human\.title \+ ' · ' \+ human\.progress \+ autoMatchShort\);/, 'a frase acompanha o estado da ECU (zonas e AutoMatch)');
assert.doesNotMatch(cockpit, /data-autocal-details|toggleDetails/, 'sem botão Detalhes (dono, 2026-10-11)');

// CSS: sem rolagem, botões à esquerda, Detalhes no lugar do gráfico.
assert.match(css, /\.app-shell\.autocal-focus \.screen\.autocal-route-screen \{ overflow: hidden;/);
assert.match(css, /\.app-shell\.autocal-focus \.refino-action-bar \{[\s\S]*?justify-content: flex-start;/);
assert.doesNotMatch(css, /data-autocal-view|data-refino-view|autocal-details-action/, 'sem vistas de Detalhes');
assert.match(css, /\.app-shell\.autocal-focus \.autocal-secondary-details \{ display: none; \}/, 'informação secundária fora da tela');
assert.match(css, /\.autocal-reset-popover \{ top: auto; bottom: calc\(100% \+ 6px\);/, 'Ações avançadas abre para cima, sobre o gráfico');

// A frase de estado traz o AutoMatch (antes escondido em Detalhes).
const context = { console, document: { querySelector: () => null, getElementById: () => null }, setTimeout: () => 0 };
context.globalThis = context;
context.window = context;
vm.runInNewContext(cockpit, context);
assert.equal(typeof context.OmegasUi.AutoCalCockpit.prototype.toggleDetails, 'undefined');

// Refino sem Detalhes: só os botões de ação.
const refino = fs.readFileSync(path.join(ui, 'screens/refino.js'), 'utf8');
assert.doesNotMatch(refino, /data-refino-details|data-refino-panel="details"|toggleDetails/, 'Refino sem Detalhes (dono, 2026-10-11)');

// Gráfico ocupa toda a largura (dono, 2026-10-11): o desenho segue a proporção da área, sem faixas vazias dos lados.
assert.match(cockpit, /function chartWidthFor\(host, height\)[\s\S]*?Math\.round\(height \* box\.width \/ box\.height\)/);
assert.match(cockpit, /const width = chartWidthFor\(host, height\);/, 'gráfico principal usa a largura da tela');
assert.match(cockpit, /width = chartWidthFor\(host, height\), left = 64/, 'gráfico da reaquisição usa a largura da tela');
assert.doesNotMatch(cockpit, /viewBox="0 0 1000 400"/, 'nada de proporção fixa que deixa faixas vazias');
assert.match(refino, /Math\.round\(height \* box\.width \/ box\.height\)/, 'Refino também ocupa a largura toda');

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
console.log('AUTOCAL_RODAPE_SEM_DETALHES=PASS');
