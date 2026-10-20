'use strict';
// Copy das páginas de grupo de Ferramentas (dono, 2026-10-20): "O que não for comprovado, não for útil, você nem coloca." Todo cartão
// mostrado tem 3 linhas: o que é; o EFEITO de mudar (de fonte: manual ou ProgBase); a faixa. Sem selo "não comprovado": item sem efeito não existe.
// Teste sem navegador: carrega os módulos reais e confere os textos que a tela desenha.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const UI = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'ui');
for (const f of ['core/advanced-flags-data.js', 'core/anti-apagao-valores.js', 'core/volta-gasolina.js', 'core/ferramentas-grupos.js']) {
  require(path.join(UI, f));
}
const ns = globalThis.OmegasUi;
const V = ns.AntiApagaoValores;
const VG = ns.VoltaGasolina;
const G = ns.FerramentasGrupos;

const PROIBIDO = /\bECU\b|readback|\bciclos?\b|snapshot/i;
const EFEITO = /Se aumentar:|Se diminuir:|Ligado:|Desligado:|Ligado,|ligado,|desligado,|Segundo o manual|indica/;

const w2 = (a, b) => ({ state: 'OK', values: [a, b] });
const PARAMS = {
  FLAG_CONF1: w2(0x0080, 0x1013), FLAG_CONF2: w2(0x0043, 0x0400), FLAG_CONF3: w2(0x8030, 0),
  GIRI_MIN_CAMBIO: { state: 'OK', values: [1600] }, RITARDO_CAMBIO: { state: 'OK', values: [30] },
  NUMERO_INJ_BENZINA_CUTOFF: { state: 'OK', values: [4] }, GIRI_PER_BENZINA: { state: 'OK', values: [0, 0] },
};

function checkCard(where, what, effect, range) {
  assert.ok(typeof what === 'string' && what.trim().length > 5, `${where}: falta a linha 1 (o que é)`);
  assert.ok(typeof effect === 'string' && EFEITO.test(effect), `${where}: linha 2 sem efeito: ${effect}`);
  assert.ok(typeof range === 'string' && range.trim(), `${where}: falta a linha 3 (faixa/fábrica)`);
  for (const t of [what, effect, range]) assert.doesNotMatch(t, PROIBIDO, `${where}: termo proibido em "${t}"`);
}

test('anti-apagão: só a Rotação máxima aparece, com o que é, efeito e faixa; os 5 números seguem lidos e gravados', () => {
  assert.equal(V.UNPROVEN, undefined);
  assert.equal(V.FIELDS.length, 1);
  V.FIELDS.forEach(f => checkCard(f.label, f.what, f.effect, `${V.range(f)} · ${f.factory}`));
  assert.match(V.FIELDS[0].effect, /Se aumentar:.*Se diminuir:/);
  assert.equal(V.COUNT, 5);
  assert.deepEqual(V.FACTORY, [1600, 100, 1500, 2, 20]);
  const p = V.plan([1600, 100, 1500, 2, 20], { 0: 1700 });
  assert.deepEqual(p.after, [1700, 100, 1500, 2, 20]);
});

test('volta para gasolina: ligar, pressão e espera explicados com o efeito', () => {
  checkCard('pressão', VG.TEXT.pressao.what, VG.TEXT.pressao.effect, 'De 0,2 a 2,0 bar.');
  checkCard('espera', VG.TEXT.espera.what, VG.TEXT.espera.effect, 'De "na hora" a 3 s.');
  assert.match(VG.TEXT.liga.effect, /desligado/i);
});

test('grupos só de leitura: todo cartão mostrado tem efeito de fonte; grupo sem prova não tem cartão', () => {
  let total = 0;
  for (const group of ['troca', 'outras']) {
    const cards = G.readCards(group, PARAMS);
    assert.ok(cards.length > 0, group);
    for (const c of cards) { checkCard(`${group}/${c.label}`, c.hint, c.effect, c.range); total++; }
  }
  assert.ok(total >= 9, 'cartões: ' + total);
  assert.equal(G.readCards('outras', PARAMS).length, 4);
  assert.deepEqual(G.readCards('volta', PARAMS), []);
  assert.deepEqual(G.readCards('lenta', PARAMS), []);
});

test('a tela desenha a linha de efeito nos cartões e não tem selo "não comprovado"', () => {
  const src = fs.readFileSync(path.join(UI, 'components', 'advanced.js'), 'utf8');
  const css = fs.readFileSync(path.join(UI, 'styles-ux-leigo.css'), 'utf8');
  for (const t of [src, css]) assert.doesNotMatch(t, /não comprovado|grp-tag|data-proof|UNPROVEN|experimental/i);
  for (const fn of ['readCardHtml', 'cardHtml', 'stepperHtml']) {
    const body = src.slice(src.indexOf(`    ${fn}(`), src.indexOf('\n    }\n', src.indexOf(`    ${fn}(`)));
    assert.match(body, /this\.effectHtml\(/, `${fn} sem a linha de efeito`);
  }
});
