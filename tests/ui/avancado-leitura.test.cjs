'use strict';

// Ferramentas > Avançado, LEITURA (dono, 2026-10-18): tabela de bits FLAG_CONF1/2/3, GIRI_PER_BENZINA e
// NUMERO_INJ_BENZINA_CUTOFF em português simples. Bits só pela tabela PROVADA de
// docs/evidence/flags-do-original.json; o resto é 'desconhecido' (nunca 0); valor não lido = '—'.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
// Objetos criados no contexto vm têm outro Array: compara por valor (JSON).
const deepEqual = (a, b, m) => assert.deepEqual(JSON.parse(JSON.stringify(a)), b, m);
const vm = require('node:vm');

const ROOT = path.join(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const read = file => fs.readFileSync(path.join(UI, file), 'utf8');

function load() {
  const context = { console, Math, JSON, Number, String, Array, Object, Set, Map };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  for (const file of ['core/advanced-flags-data.js', 'core/advanced-flags.js']) vm.runInContext(read(file), context, { filename: file });
  return context.OmegasUi.AdvancedFlags;
}
const A = load();

// Derivação independente da regra "bit PROVADO" direto do JSON do original (não usa o gerador nem a tabela do app).
function provadosDoOriginal() {
  const original = JSON.parse(fs.readFileSync(path.join(ROOT, 'docs/evidence/flags-do-original.json'), 'utf8'));
  const grupos = new Map();
  for (const f of original.flags) {
    if (!['FLAG_CONF1', 'FLAG_CONF2', 'FLAG_CONF3'].includes(f.vetor)) continue;
    const chave = `${f.vetor}|${f.palavra}|${parseInt(f.mascara, 16)}`;
    if (!grupos.has(chave)) grupos.set(chave, []);
    grupos.get(chave).push(f);
  }
  const saida = new Set();
  for (const [chave, itens] of grupos) {
    const comCaixa = itens.filter(f => f.controles.length > 0);
    if (comCaixa.length === 1 && comCaixa[0].condicao === 'sempre') saida.add(chave);
  }
  return saida;
}

test('a tabela de bits do app é exatamente o conjunto PROVADO do JSON do original', () => {
  const doApp = new Set();
  for (const nome of ['FLAG_CONF1', 'FLAG_CONF2', 'FLAG_CONF3']) {
    for (const bit of A.table[nome].bits) doApp.add(`${nome}|${bit.word}|${bit.mask}`);
  }
  deepEqual([...doApp].sort(), [...provadosDoOriginal()].sort());
  assert.ok(doApp.size >= 40, 'tabela não pode estar vazia');
  assert.equal(A.table.FLAG_CONF3.code, 250);
  assert.equal(A.table.FLAG_CONF2.code, 192);
  assert.equal(A.table.FLAG_CONF1.code, 3);
});

test('bits ambíguos (outro sentido conforme o modelo da ECU) NÃO são decodificados', () => {
  const tem = (v, w, m) => A.table[v].bits.some(b => b.word === w && b.mask === m);
  assert.equal(tem('FLAG_CONF1', 0, 0x8000), false, 'caso 7 desconhecido + caso 28 condicional');
  assert.equal(tem('FLAG_CONF1', 1, 0x0800), false, 'caso 26 + caso 35 condicional');
  assert.equal(tem('FLAG_CONF3', 0, 0x0020), true, 'anti-stallo é provado');
});

test('todo rótulo é português (sem código cru do original) e tem texto', () => {
  for (const nome of ['FLAG_CONF1', 'FLAG_CONF2', 'FLAG_CONF3']) {
    for (const bit of A.table[nome].bits) {
      assert.ok(bit.label && bit.label.length > 3, `${nome} ${bit.mask} sem rótulo`);
      assert.doesNotMatch(bit.label, /^(Check|Combo|chk)/, `${nome}: ${bit.label}`);
    }
  }
});

// decodeFlags/antiStallo/progressioni saíram (2026-10-20): nenhuma tela os usava; os cartões leem por wordsOf/simpleParams
// (cartoes-motor.js) e os 5 números por AntiApagaoValores. A tabela provada continua conferida acima.

test('GIRI_PER_BENZINA e NUMERO_INJ_BENZINA_CUTOFF: 0 lido é 0; não lido é "—"', () => {
  const lido = A.simpleParams({
    GIRI_PER_BENZINA: { state: 'OK', values: [0, 8500] },
    NUMERO_INJ_BENZINA_CUTOFF: { state: 'OK', values: [0] },
  });
  const giri = lido.find(p => p.name === 'GIRI_PER_BENZINA');
  deepEqual(giri.cells, ['0', '8.500']);
  assert.equal(lido.find(p => p.name === 'NUMERO_INJ_BENZINA_CUTOFF').cells[0], '0');
  const vazio = A.simpleParams({});
  deepEqual(vazio.find(p => p.name === 'GIRI_PER_BENZINA').cells, ['—', '—']);
  deepEqual(vazio.find(p => p.name === 'NUMERO_INJ_BENZINA_CUTOFF').cells, ['—']);
});

test('gerador está em sincronia com o JSON do original (idempotente)', () => {
  const { spawnSync } = require('node:child_process');
  const r = spawnSync('python3', ['-B', 'tools/ci/gerar_flags_avancado.py', '--check'], { cwd: ROOT, encoding: 'utf8' });
  assert.equal(r.status, 0, r.stderr + r.stdout);
});
