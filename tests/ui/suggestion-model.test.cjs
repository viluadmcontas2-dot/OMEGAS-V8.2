'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const model = require('../../app/src/main/assets/ui/suggestion-model.js');

test('separa tendência global de erro residual local', () => {
  const result = model.split({
    kFactorSuggestions: [{ id: 'c1', actionable: true, confidence: 0.9, suggestedDeltaPercent: 4, index: 7, petrolMs: 5 }],
    mapResidualSuggestions: [{ id: 'm1', actionable: true, confidence: 0.7, suggestedDeltaPercent: -3, row: 2, column: 4 }],
  });
  assert.equal(result.curve[0].scope, 'global');
  assert.equal(result.curve[0].destination, 'Curva K');
  assert.equal(result.curve[0].index, 7);
  assert.equal(result.curve[0].petrolMs, 5);
  assert.equal(result.map[0].scope, 'local');
  assert.equal(result.map[0].destination, 'Mapa K');
});

test('sugestão global nunca vira correção de célula local', () => {
  const item = model.classify({ type: 'curve', actionable: true, confidence: 0.88, suggestedDeltaPercent: 5, index: 3, row: 3, column: 3 });
  assert.equal(item.scope, 'global');
  assert.equal(item.index, 3);
  assert.match(item.explanation, /não transformar em correção de célula/);
  assert.equal(model.reviewAction(item).action, 'open-curve-editor');
});

test('sugestão residual abre apenas o editor do Mapa K', () => {
  const item = model.classify({ type: 'map', actionable: true, confidence: 0.66, suggestedDeltaPercent: -2, row: 1, column: 8 });
  const action = model.reviewAction(item);
  assert.equal(action.action, 'open-map-editor');
  assert.equal(action.writesEcu, false);
});

test('abrir uma sugestão para revisão nunca escreve na ECU', () => {
  for (const type of ['curve', 'map']) {
    const action = model.reviewAction({ type, actionable: true, confidence: 0.9, suggestedDeltaPercent: 1 });
    assert.equal(action.allowed, true);
    assert.equal(action.writesEcu, false);
  }
});

test('dados inválidos ou insuficientes não viram ação', () => {
  const invalid = model.reviewAction({ type: 'curve', actionable: true, confidence: 0.9, suggestedDeltaPercent: 'não-numérico' });
  assert.equal(invalid.allowed, false);
  assert.equal(invalid.action, 'none');

  const insufficient = model.reviewAction({ type: 'map', actionable: false, confidence: 0.2, suggestedDeltaPercent: 2, reason: 'Faltam visitas estáveis.' });
  assert.equal(insufficient.allowed, false);
  assert.match(insufficient.reason, /Faltam visitas/);
});

test('confiança é explicada sem autorizar aplicação', () => {
  assert.equal(model.classify({ type: 'curve', confidence: 0.81, suggestedDeltaPercent: 1 }).confidenceLabel, 'alta');
  assert.equal(model.classify({ type: 'curve', confidence: 0.6, suggestedDeltaPercent: 1 }).confidenceLabel, 'média');
  assert.equal(model.classify({ type: 'curve', confidence: 0.3, suggestedDeltaPercent: 1 }).confidenceLabel, 'baixa');
});

test('ordem final depende da confiança, não da ordem recebida', () => {
  const result = model.split({
    kFactorSuggestions: [
      { id: 'low', actionable: true, confidence: 0.4, suggestedDeltaPercent: 1 },
      { id: 'high', actionable: true, confidence: 0.95, suggestedDeltaPercent: 1 },
    ],
  });
  assert.deepEqual(result.actionable.map(item => item.id), ['high', 'low']);
});
