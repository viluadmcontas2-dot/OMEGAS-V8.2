'use strict';
// Copy das páginas de grupo de Ferramentas (dono, 2026-10-20): "Você não tem que esconder o que o leigo não entende, você tem que FAZER
// o leigo entender. E o que ainda não está provado, você tem que botar lá." Todo cartão de valor tem 3 linhas: o que é; o EFEITO de
// mudar (só com evidência) OU a etiqueta "não comprovado"; a faixa/fábrica. Sem termo proibido (ECU, readback, ciclo, snapshot).
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
const UNPROVEN = 'Ainda não sei exatamente o efeito. Não mude sem necessidade.';
const EFEITO = /Se aumentar:|Se diminuir:|Ligado:|Desligado:|Ligado,|ligado,|desligado,|Segundo o manual|indica/;

const w2 = (a, b) => ({ state: 'OK', values: [a, b] });
const PARAMS = {
  FLAG_CONF1: w2(0x0080, 0x1013), FLAG_CONF2: w2(0x0043, 0x0400), FLAG_CONF3: w2(0x8030, 0),
  GIRI_MIN_CAMBIO: { state: 'OK', values: [1600] }, RITARDO_CAMBIO: { state: 'OK', values: [30] },
  NUMERO_INJ_BENZINA_CUTOFF: { state: 'OK', values: [4] }, GIRI_PER_BENZINA: { state: 'OK', values: [0, 0] },
};

function checkCard(where, what, effect, range) {
  assert.ok(typeof what === 'string' && what.trim().length > 5, `${where}: falta a linha 1 (o que é)`);
  assert.ok(effect === null || (typeof effect === 'string' && EFEITO.test(effect)), `${where}: linha 2 sem efeito nem "não comprovado": ${effect}`);
  assert.ok(typeof range === 'string' && range.trim(), `${where}: falta a linha 3 (faixa/fábrica)`);
  for (const t of [what, effect || '', range]) assert.doesNotMatch(t, PROIBIDO, `${where}: termo proibido em "${t}"`);
}

test('anti-apagão: os 5 números têm o que é, efeito ou "não comprovado", e faixa', () => {
  assert.equal(V.UNPROVEN, UNPROVEN);
  V.FIELDS.forEach(f => checkCard(f.label, f.what, f.effect, `${V.range(f)} · ${f.factory}`));
  // Só a Rotação máxima tem efeito sustentado (rótulo do original + logs); os 4 extras: DESCONHECIDO no firmware.
  assert.deepEqual(V.FIELDS.map(f => f.effect !== null), [true, false, false, false, false]);
  assert.match(V.FIELDS[0].effect, /Se aumentar:.*Se diminuir:/);
});

test('volta para gasolina: ligar, pressão e espera explicados com o efeito', () => {
  checkCard('pressão', VG.TEXT.pressao.what, VG.TEXT.pressao.effect, 'De 0,2 a 2,0 bar.');
  checkCard('espera', VG.TEXT.espera.what, VG.TEXT.espera.effect, 'De "na hora" a 3 s.');
  assert.match(VG.TEXT.liga.effect, /desligado/i);
});

test('grupos só de leitura (troca, marcha lenta, volta, outras): cada cartão tem efeito ou "não comprovado"', () => {
  let total = 0, nao = 0;
  for (const group of ['troca', 'lenta', 'volta', 'outras']) {
    const cards = G.readCards(group, PARAMS);
    assert.ok(cards.length > 0, group);
    for (const c of cards) {
      assert.ok('effect' in c, `${group}/${c.id}: sem campo de efeito`);
      checkCard(`${group}/${c.label}`, c.hint, c.effect, c.range);
      total++; if (c.effect === null) nao++;
    }
  }
  assert.ok(total >= 40, 'cartões: ' + total);
  assert.ok(nao > 0 && nao < total, `não comprovados: ${nao} de ${total}`);
});

test('"Outras": nomes do original (OBD, Valvetronic, Landi Connect) ficam e são explicados', () => {
  const cards = G.readCards('outras', PARAMS);
  const by = l => cards.find(c => c.label.includes(l));
  for (const nome of ['OBD', 'Valvetronic', 'Landi Connect', 'Start & Stop']) {
    const c = by(nome);
    assert.ok(c, 'cartão some: ' + nome);
    assert.doesNotMatch(c.hint, /Configuração (lida )?da central/, 'linha genérica em ' + nome);
  }
  assert.match(by('Adaptatividade OBD').hint, /OBD é a tomada de diagnóstico/);
  cards.forEach(c => assert.notEqual(c.hint, 'Configuração da central com este nome no original.', 'sem tradução: ' + c.label));
});

test('a tela desenha a linha 2 em todos os cartões de valor, com a etiqueta "não comprovado"', () => {
  const src = fs.readFileSync(path.join(UI, 'components', 'advanced.js'), 'utf8');
  assert.match(src, /<span class="grp-tag">não comprovado<\/span>/);
  for (const fn of ['readCardHtml', 'cardHtml', 'stepperHtml']) {
    const body = src.slice(src.indexOf(`    ${fn}(`), src.indexOf('\n    }\n', src.indexOf(`    ${fn}(`)));
    assert.match(body, /this\.effectHtml\(/, `${fn} sem a linha de efeito`);
  }
  const how = src.slice(src.indexOf('class="vv-how"'), src.indexOf('</ol>', src.indexOf('class="vv-how"')));
  assert.match(how, /giros por ciclo/, 'explicação da unidade da queda (única exceção para "ciclo")');
  assert.match(how, /rapidez da queda/);
});
