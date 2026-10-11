// Auditoria de fluxo (2026-10-11): "Aplicar tolerâncias" dizia "atualizadas" e o Kotlin ignorava tudo (política fixa).
// Agora a aba só oferece o que muda de verdade: a água mínima, pelo ajuste real (setLearningMinimumWaterC).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SOURCE = fs.readFileSync(path.resolve(__dirname, '../../app/src/main/assets/ui/screens/learning.js'), 'utf8');

function screenWith(api, waterInput) {
  const context = { console, window: null, document: { getElementById: id => (id === 'learningMinimumWaterInput' ? { value: waterInput } : null) } };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(SOURCE, context);
  const screen = Object.create(context.OmegasUi.LearningScreen.prototype);
  let state = { learningTolerance: { ok: true, controlModel: { minimumWaterC: 60 } } };
  const alerts = [];
  screen.store = { get: () => state, patch: p => { if (p.alert) alerts.push(p.alert); state = { ...state, ...p }; } };
  screen.api = api;
  screen.tolerancePane = { innerHTML: '', querySelector: () => null, querySelectorAll: () => [] };
  screen.toleranceSignature = '';
  return { screen, alerts, state: () => state };
}

test('a aba mostra só a água mínima, com o valor do Kotlin', () => {
  const { screen, state } = screenWith({}, '60');
  screen.renderTolerances(state());
  assert.match(screen.tolerancePane.innerHTML, /Temperatura mínima da água[\s\S]*value="60"/);
  assert.match(screen.tolerancePane.innerHTML, /data-apply-water/);
  assert.doesNotMatch(screen.tolerancePane.innerHTML, /data-apply-tolerances|Rigoroso|Flexível/);
});

test('Aplicar temperatura chama o ajuste real e mostra o valor aplicado', () => {
  const calls = [];
  const { screen, alerts, state } = screenWith({ setLearningMinimumWaterC: v => { calls.push(v); return { ok: true, applied: 70, minimumWaterC: 70 }; } }, '70');
  screen.applyMinimumWater();
  assert.deepEqual(calls, [70]);
  assert.equal(state().learningTolerance.controlModel.minimumWaterC, 70);
  assert.match(alerts.at(-1).message, /70 °C/);
});

test('falha ao aplicar aparece como falha, nunca como "atualizada"', () => {
  const { screen, alerts } = screenWith({ setLearningMinimumWaterC: () => ({ ok: false, error: 'Ponte indisponível' }) }, '70');
  screen.applyMinimumWater();
  assert.equal(alerts.at(-1).level, 'warning');
  assert.match(alerts.at(-1).message, /Ponte indisponível/);
});
