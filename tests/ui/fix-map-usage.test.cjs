'use strict';
// Fix UI · Mapa K (achado 15). Teste de USO: resumo no plural, mensagem parcial com ponto, seletor segmentado, botão sem contradição.
const test = require('node:test');
const L = require('./wiring/lib.cjs');
const { assert } = L;
const { mapApp } = require('./wiring/scenarios.cjs');

const cell = (app, r, c) => app.$(`.map-k-cell[data-row="${r}"][data-column="${c}"]`);
function touch(app, el) {
  el.dispatchEvent(new app.win.Event('pointerdown', { bubbles: true, pointerId: 1 }));
  el.dispatchEvent(new app.win.Event('pointerup', { bubbles: true, pointerId: 1 }));
  el.click(); app.flush();
}
function setAdjust(app, mode, value) {
  const select = app.byId('mapAdjustmentMode');
  select.value = mode; select.dispatchEvent(new app.win.Event('change', { bubbles: true }));
  const input = app.byId('mapAdjustmentValue');
  input.value = String(value); input.dispatchEvent(new app.win.Event('input', { bubbles: true }));
  app.flush();
}
const review = app => app.byId('mapReviewButton');
const resultOf = app => ({ title: app.byId('mapOperationResult').querySelector('b').textContent, detail: app.byId('mapOperationResult').querySelector('span').textContent });
function write(app, cells, value = 150) {
  cells.forEach(([r, c]) => touch(app, cell(app, r, c)));
  setAdjust(app, 'target', value);
  review(app).click(); app.flush();
}

test('15. "Gravado" no plural e no singular, com a palavra certa (células conferidas / célula conferida)', () => {
  let app = mapApp();
  write(app, [[2, 3], [2, 4], [5, 5]]); app.settle(4);
  assert.equal(resultOf(app).title, 'Gravado · 3 células conferidas na ECU');
  app.destroy();
  app = mapApp();
  write(app, [[2, 3]]); app.settle(4);
  assert.equal(resultOf(app).title, 'Gravado · 1 célula conferida na ECU');
  app.destroy();
});

test('15. falha parcial: ponto antes de "Leia a ECU de novo" (sem "Releia") e plural certo', () => {
  const app = mapApp({ outcome: { mapWrite: 'partial' } });
  write(app, [[2, 3], [2, 4], [5, 5]]); app.settle(4);
  const detail = resultOf(app).detail;
  assert.match(detail, /\.\s+Leia a ECU de novo para ver o estado real\.$/, detail);
  assert.doesNotMatch(detail, /Releia/);
  assert.match(detail, /^2 células já receberam o novo valor antes da falha\./);
  app.destroy();
});

test('15. o botão nunca diz "Gravar 144 células" quando o ajuste não muda nenhuma', () => {
  const app = mapApp();
  app.byId('mapSelectAll').click(); app.flush();
  assert.equal(app.byId('mapSelectionCount').textContent, '144 selecionadas');
  assert.doesNotMatch(review(app).textContent, /Gravar 144/);
  assert.equal(review(app).textContent, 'Digite o ajuste para mudar o K');
  assert.equal(review(app).hasAttribute('disabled'), true);
  setAdjust(app, 'delta', 3);
  assert.match(review(app).textContent, /^Gravar 144 células$/);
  assert.equal(review(app).hasAttribute('disabled'), false);
  app.destroy();
});

test('15. seletor segmentado Somar · Definir · %: um toque troca o modo, a unidade acompanha, o tema usa tokens', () => {
  const app = mapApp();
  const mode = () => app.byId('mapAdjustmentMode').value;
  const unit = () => app.byId('mapAdjustmentUnit').textContent;
  assert.equal(app.$$('[data-map-mode]').length, 3);
  assert.equal(unit(), '% sobre o K');
  app.$('[data-map-mode="delta"]').click(); app.flush();
  assert.equal(mode(), 'delta');
  assert.equal(unit(), 'K a somar');
  assert.equal(app.$('[data-map-mode="delta"]').attrs.get('aria-checked'), 'true');
  assert.equal(app.$('[data-map-mode="percent"]').attrs.get('aria-checked'), 'false');
  app.$('[data-map-mode="target"]').click(); app.flush();
  assert.equal(mode(), 'target');
  assert.equal(unit(), 'K final');
  const fs = require('node:fs');
  const path = require('node:path');
  const css = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/ui/styles-lote-f.css'), 'utf8');
  const block = css.slice(css.indexOf('Mapa K: seletor segmentado'));
  assert.match(block, /\.segmented \{[^}]*var\(--line\)/);
  assert.doesNotMatch(block.split('/* ---')[0], /#[0-9a-fA-F]{3,6}\b|rgba?\(/, 'só tokens, sem cor solta');
  app.destroy();
});

test('15. sem "AGORA" duplicado: a célula atual aparece uma vez, com a legenda "CÉLULA AGORA"', () => {
  const app = mapApp();
  app.advance(2000);
  const panel = app.$('.map-live-condition').textContent;
  assert.match(panel, /CÉLULA AGORA/);
  assert.doesNotMatch(panel, /RPM|ms/, 'RPM e injeção ficam só na faixa de status');
  app.destroy();
});
