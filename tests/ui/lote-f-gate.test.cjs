'use strict';
// Lote F · portão de coerência: vocabulário, formatos, utilitários únicos, pisos e ordem das folhas de estilo.
// Classe de prova 1 (contrato estático).

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { UI, freshContext } = require('./_support.cjs');

const read = rel => fs.readFileSync(path.join(UI, rel), 'utf8');
const walk = (dir, out = []) => {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out); else out.push(full);
  }
  return out;
};
const files = walk(UI);
const js = files.filter(f => f.endsWith('.js'));
const css = files.filter(f => f.endsWith('.css'));
const rel = f => path.relative(UI, f);
const stripComments = source => source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:'"`\\])\/\/[^\n]*/g, '$1');

/** Texto que o motorista lê: literais de texto (com espaço) dos .js e o HTML visível do index. */
function visibleText(file) {
  const source = stripComments(fs.readFileSync(file, 'utf8'));
  const literals = [...source.matchAll(/'((?:[^'\\\n]|\\.)*)'|"((?:[^"\\\n]|\\.)*)"|`((?:[^`\\]|\\.)*)`/g)].map(m => m[1] ?? m[2] ?? m[3] ?? '');
  return literals.filter(text => /\s/.test(text) || /[À-ÿ]/.test(text)).join('\n');
}

test('vocabulário do motorista: sem ACK, readback, MUL_ACT, bracket, "Aprendizado global", "Ajuste global", "Fator desejado", "Restaurar backup", "Nossos pontos"', () => {
  const banned = /\bACK\b|\breadback\b|\bMUL_ACT\b|\bbracket\b|Aprendizado global|Ajuste global|Fator desejado|Restaurar backup|[Nn]ossos pontos|empobrece|enriqueça|\bK factor\b/;
  const targets = [...js.filter(f => /screens|components|core|app\.js/.test(rel(f))), path.join(UI, 'index.html')];
  for (const file of targets) {
    const text = file.endsWith('.html') ? fs.readFileSync(file, 'utf8').replace(/<script[\s\S]*?<\/script>/g, '') : visibleText(file);
    // "Detalhes técnicos" é o único lugar onde o jargão é permitido.
    const without = text.replace(/Detalhes técnicos[\s\S]{0,2500}/g, '');
    const hit = without.match(banned);
    assert.equal(hit, null, `${rel(file)}: "${hit && hit[0]}" aparece fora de Detalhes técnicos`);
  }
});

test('formatos: nenhum número visível sai de toFixed solto (só coordenadas de SVG, input numérico e arredondamento interno)', () => {
  const allow = { 'components/curve-chart.js': 4, 'screens/curve.js': 1, 'screens/autocal-cockpit.js': 8 };
  for (const file of js.filter(f => /screens|components|app\.js/.test(rel(f)))) {
    const source = stripComments(fs.readFileSync(file, 'utf8'));
    const odd = [...source.matchAll(/\.toFixed\((\d)\)/g)].filter(m => m[1] !== '1').length;
    assert.ok(odd <= (allow[rel(file)] ?? 0), `${rel(file)}: ${odd} toFixed(n) fora da lista (use DisplayRules)`);
  }
});

test('utilitários únicos: finite, escapeHtml, fmt e clamp são definidos só em core/display-rules.js', () => {
  const definition = /(?:function\s+(finite|escapeHtml|fmt|clamp)\s*\(|(?:const|let|var)\s+(finite|escapeHtml|fmt|clamp)\s*=\s*(?:\(|[a-zA-Z_]+\s*=>|function))/;
  for (const file of js) {
    if (rel(file) === path.join('core', 'display-rules.js')) continue;
    const hit = stripComments(fs.readFileSync(file, 'utf8')).match(definition);
    assert.equal(hit, null, `${rel(file)} redefine ${hit && (hit[1] || hit[2])}`);
  }
  const rules = read('core/display-rules.js');
  for (const name of ['finite', 'escapeHtml', 'clamp']) assert.equal((rules.match(new RegExp(`function ${name}\\(`, 'g')) || []).length, 1, name);
});

test('plural e fração: "12 alterações", nunca "alteraçãoões"; índice 0,01 = 1%', () => {
  const ctx = freshContext({ console });
  const D = ctx.OmegasUi.DisplayRules;
  assert.equal(D.plural(12, 'alteração', 'alterações'), '12 alterações');
  assert.equal(D.plural(1, 'célula', 'células'), '1 célula');
  assert.equal(D.percentFraction(0.01), '1%');
  for (const file of js.filter(f => /screens|components/.test(rel(f)))) {
    assert.doesNotMatch(stripComments(fs.readFileSync(file, 'utf8')), /'ões'|"ões"|\$\{[^}]*\}\s*ponto\$\{[^}]*\? ''/, `${rel(file)}: plural montado na mão`);
  }
});

test('nomes antigos fora da UI: learning/Aprendizado, suggestion, V7 e comentários de gate', () => {
  for (const file of js) {
    const source = stripComments(fs.readFileSync(file, 'utf8'));
    if (rel(file) !== path.join('core', 'native-api.js')) assert.doesNotMatch(source, /[Ll]earning(?!_)|Suggestion|suggestion/, rel(file));
    assert.doesNotMatch(source, /generation:\s*'V7'/, rel(file));
    assert.doesNotMatch(fs.readFileSync(file, 'utf8'), /FINAL_PRE_APK|CURVE_K_RETENTION_FINAL_GATE/, rel(file));
  }
  const html = read('index.html');
  assert.doesNotMatch(html, /learning|Aprendizado|suggestion/i);
  assert.match(read('core/router.js'), /curve: \['overview', 'editor'\]/);
  for (const file of css) assert.doesNotMatch(fs.readFileSync(file, 'utf8'), /\.learning-|\.global-learning|\.suggestion-|mapBackToLearning/, rel(file));
});

test('folhas de estilo estáticas e em ordem: tokens primeiro; pisos e acabamento do Lote F por último; cor só em tokens.css', () => {
  const links = [...read('index.html').matchAll(/<link rel="stylesheet" href="([^"]+)"/g)].map(m => m[1]);
  assert.equal(links[0], 'tokens.css');
  assert.deepEqual(links.slice(-5), ['styles-floors.css', 'styles-lote-f.css', 'styles-tela-agora-mapa-curva.css', 'styles-autocal-refino.css', 'styles-diamante.css']);
  for (const sheet of ['styles-autocal-cockpit.css', 'styles-dashboard-now.css', 'styles-shell-status.css', 'styles-refine.css', 'styles-split-layout.css']) {
    assert.ok(links.indexOf(sheet) > 0 && links.indexOf(sheet) < links.indexOf('styles-floors.css'), `${sheet} antes dos pisos`);
  }
  const lote = stripComments(read('styles-lote-f.css'));
  assert.doesNotMatch(lote, /#[0-9a-fA-F]{3,8}\b(?![^{}]*\{)|rgba?\(\s*\d|backdrop-filter|drop-shadow/);
});

test('pisos de texto e toque do Lote F: 16 px para microtexto, 22 px para valores e ações, 58 px de alvo, 44 px no gráfico e na grade', () => {
  const lote = read('styles-lote-f.css');
  const chart = read('components/curve-chart.js');
  assert.match(lote, /\.curve-chart-shared text \{ font-size: 16px/);
  assert.match(lote, /\.utility-screen :is\(small, span, p, dd, dt, li, label, em, i\) \{ font-size: 16px/);
  assert.match(lote, /\.check-setting input\[type="checkbox"\][\s\S]*?inset: 0/, 'a caixa de marcar cobre o rótulo (>= 58 px)');
  assert.match(chart, /class="autocal-acquired-hit"[^`]*r="22"/);
  assert.match(read('screens/curve.js'), /class="curve-point-hit"[^`]*r="24"/, 'ponto da Curva K: círculo invisível de 48 px');
  assert.match(lote, /grid-template-rows:\s*44px repeat\(12, minmax\(44px, 1fr\)\)/);
  // O pedido Diamante substitui cartões redundantes por um cabeçalho global de 7 fatos.
  const diamante = read('styles-diamante.css');
  assert.match(diamante, /vehicle-status-strip b[^}]*font-size:22px/);
  assert.match(diamante, /vehicle-status-strip small[^}]*font-size:14px/);

});

test('uma ação primária só: botão primário usa o accent; vermelho/âmbar só em perigo e atenção', () => {
  const lote = read('styles-lote-f.css');
  assert.match(lote, /\.primary,[\s\S]*?\.autocal-primary-action[\s\S]*?background:\s*var\(--accent\)/);
  assert.doesNotMatch(read('screens/refino.js'), /class="primary">Gravar na ECU|data-refino-confirm/, 'o Refino grava em um toque, sem modal de revisão');
});
