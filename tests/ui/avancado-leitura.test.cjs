'use strict';

// Ferramentas > Avançado, LEITURA (dono, 2026-10-18): FLAG_CONF1/2/3, PARAM_PROGRESSIONI, GIRI_PER_BENZINA,
// NUMERO_INJ_BENZINA_CUTOFF e ANTI_STALLO em português simples. Bits só pela tabela PROVADA de
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

test('FLAG_CONF3 da ECU do dono (0000 0000): anti-stallo desligado, os demais provados desligados', () => {
  const v = A.decodeFlags('FLAG_CONF3', { state: 'OK', values: [0, 0] });
  assert.equal(v.read, true);
  deepEqual(v.hex, ['0x0000', '0x0000']);
  const anti = v.rows.find(r => r.mask === 0x20 && r.word === 0);
  assert.equal(anti.state, 'off');
  assert.equal(anti.stateText, 'Desligado');
  assert.ok(v.rows.every(r => r.state === 'off'));
  assert.equal(v.rows.length, 16);
  assert.equal(v.unknownBits, 32 - 16, 'bits sem caixa conhecida: desconhecido');
});

test('FLAG_CONF1 da ECU do dono (2486 1051): bit ligado fora da tabela fica desconhecido, não vira 0', () => {
  const v = A.decodeFlags('FLAG_CONF1', { state: 'OK', values: [0x2486, 0x1051] });
  deepEqual(v.hex, ['0x2486', '0x1051']);
  // 0x0004 está ligado na palavra 0 e não existe na tabela provada: não pode aparecer como desligado.
  assert.equal(v.rows.some(r => r.word === 0 && r.mask === 0x0004), false);
  const ligados = v.rows.filter(r => r.state === 'on').map(r => `${r.word}:${r.mask.toString(16)}`);
  deepEqual(ligados.sort(), ['0:2', '0:2000', '0:80', '1:1', '1:10', '1:1000'].sort());
  assert.equal(v.unknownBits, 32 - v.rows.length);
  assert.ok(v.unknownBits > 0);
});

test('lista (combo) mostra "bit ligado/desligado": o significado do valor da lista é desconhecido', () => {
  const v = A.decodeFlags('FLAG_CONF1', { state: 'OK', values: [0x0002, 0] });
  const combo = v.rows.find(r => r.word === 0 && r.mask === 0x0002);
  assert.equal(combo.kind, 'lista');
  assert.equal(combo.stateText, 'Bit ligado');
  assert.equal(v.rows.find(r => r.word === 0 && r.mask === 0x0010).stateText, 'Bit desligado');
});

test('valor ainda não lido: tudo "—" (nenhum estado, nenhum 0)', () => {
  for (const entrada of [undefined, null, {}, { state: 'NACK', values: null }, { state: 'OK', values: [1] }, { state: 'OK', values: [1, 70000] },
    { state: 'OK', values: [-1, 0] }, { state: 'ERRO_TRANSPORTE', values: [0, 0] }]) {
    const v = A.decodeFlags('FLAG_CONF3', entrada);
    assert.equal(v.read, false, JSON.stringify(entrada));
    deepEqual(v.hex, ['—', '—']);
    assert.ok(v.rows.every(r => r.state === 'unknown' && r.stateText === '—'));
    assert.equal(v.unknownBits, 32);
  }
});

test('ANTI_STALLO: 5 valores na ordem INFERIDA, com unidade e a marca "ordem inferida"', () => {
  const a = A.antiStallo({ state: 'OK', values: [1600, 100, 1500, 2, 20] });
  assert.equal(a.orderInferred, true);
  assert.equal(a.fields.length, 5);
  deepEqual(a.fields.map(f => f.valueText), ['1.600', '100', '1.500', '2', '20']);
  deepEqual(a.fields.map(f => f.unit), ['rpm', 'rpm por ciclo', 'ms', 'rpm por ciclo', 'rpm por ciclo']);
  const rotulos = a.fields.map(f => f.label).join('|');
  assert.match(rotulos, /Rotação máxima para ativar/);
  assert.match(rotulos, /Queda de rotação para ativar/);
  assert.match(rotulos, /Atraso para sair do gás/);
  assert.match(rotulos, /Histerese/);
  assert.match(rotulos, /Queda de rotação para desativar/);
});

test('ANTI_STALLO não lido ou com tamanho errado: "—"', () => {
  for (const entrada of [undefined, { state: 'NACK', values: null }, { state: 'OK', values: [1600, 100] }]) {
    const a = A.antiStallo(entrada);
    deepEqual(a.fields.map(f => f.valueText), ['—', '—', '—', '—', '—']);
    assert.equal(a.read, false);
  }
});

test('PARAM_PROGRESSIONI: 4 linhas de 3 valores (leitura 2A por linha); linha ausente = "—"', () => {
  const params = {
    PARAM_PROGRESSIONI_L0: { state: 'OK', values: [0, 4, 500] },
    PARAM_PROGRESSIONI_L1: { state: 'OK', values: [20, 4, 500] },
    PARAM_PROGRESSIONI_L2: { state: 'NACK', values: null },
  };
  const p = A.progressioni(params);
  assert.equal(p.rows.length, 4);
  deepEqual(p.rows[0].cells, ['0', '4', '500']);
  deepEqual(p.rows[1].cells, ['20', '4', '500']);
  deepEqual(p.rows[2].cells, ['—', '—', '—']);
  deepEqual(p.rows[3].cells, ['—', '—', '—']);
  assert.equal(p.meaningKnown, false, 'o significado de cada coluna é desconhecido');
});

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
