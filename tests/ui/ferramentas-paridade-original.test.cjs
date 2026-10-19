'use strict';
// Contrato: comportamento do ProgBase extraído do exe real, NÃO dos rótulos do próprio OMEGAS.
// Fonte OBS-COD: docs/evidence/flags-do-original.json (gerado por flags_do_original.py).
// Nenhuma ECU ou gravador é chamado por este teste.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ROOT = path.resolve(__dirname, '../..');
const UI = path.join(ROOT, 'app/src/main/assets/ui');
const flags = JSON.parse(fs.readFileSync(path.join(ROOT, 'docs/evidence/flags-do-original.json'), 'utf8')).flags;

function evidence(vector, word, mask) {
  const list = flags.filter(f => f.vetor === vector && f.palavra === word &&
    parseInt(f.mascara, 16) === mask && f.coerente === true);
  assert.equal(list.length, 1, 'origem ambígua: ' + vector + ' ' + word + ':' + mask);
  return list[0];
}
function build(params) {
  const context = { console, Number, String, Object, Array, JSON, Math, Intl, Date, Set, Map };
  context.window = context;
  context.globalThis = context;
  vm.createContext(context);
  for (const src of ['core/advanced-flags-data.js', 'core/advanced-flags.js',
    'core/anti-apagao-valores.js', 'core/idle-mode.js', 'core/cartoes-motor.js'])
    vm.runInContext(fs.readFileSync(path.join(UI, src), 'utf8'), context, { filename: src });
  return context.OmegasUi.CartoesMotor.build(params);
}
const ok = values => ({ state: 'OK', values });
const state = (word3 = 0) => ({
  FLAG_CONF1: ok([0, 0]), FLAG_CONF2: ok([0, 0]), FLAG_CONF3: ok([word3, 0]),
  NUMERO_INJ_BENZINA_CUTOFF: ok([3]),
  ANTI_STALLO: ok([1600, 100, 1500, 2, 20]),
});

test('fonte binária original distingue anti-apagão, progressões e troca sequencial', () => {
  const anti = evidence('FLAG_CONF3', 0, 0x0020);
  const progress = evidence('FLAG_CONF3', 0, 0x0010);
  const sequence = evidence('FLAG_CONF1', 1, 0x1000);
  assert.ok(anti.controles.some(c => c.nome === 'CheckAntiStallo'));
  assert.ok(progress.controles.some(c => c.nome === 'CheckCambioConSovrapp'));
  assert.ok(sequence.controles.some(c => c.nome === 'CheckIstantaneo'));
});

test('RED: ligar a sobreposição da troca não pode falsificar a saída do corte', () => {
  const cards = build(state(0x0010));
  const corte = cards.find(c => c.id === 'corte');
  const troca = cards.find(c => c.id === 'troca');
  assert.ok(corte && troca);
  assert.notEqual(corte.on, true, 'FLAG_CONF3/0x0010 NÃO é o interruptor da saída do corte');
  assert.ok(!corte.rows.some(r => /Saída suave do corte/i.test(r.label)),
    'o nome do controle não pode ser inventado a partir da flag da sobreposição');
  assert.ok(troca.rows.some(r => /sobreposiç/i.test(r.label) && r.on === true),
    'a sobreposição deve aparecer no assunto correto: troca gasolina/GNV');
  assert.match(corte.rows.map(r => r.label + ' ' + r.text).join(' | '), /3 injeções/,
    'saída do corte usa seu parâmetro nativo independente');
});

test('o anti-apagão lê seu próprio bit e não altera os outros assuntos', () => {
  const before = build(state(0)).find(c => c.id === 'anti');
  const after = build(state(0x0020)).find(c => c.id === 'anti');
  assert.equal(before.stateText, 'Desligado');
  assert.equal(after.stateText, 'Ligado');
  const unrelated = build(state(0x0020)).find(c => c.id === 'troca');
  assert.notEqual(unrelated.on, true);
});
