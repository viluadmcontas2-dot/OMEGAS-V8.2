'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const model = require('../../app/src/main/assets/ui/suggestion-model.js');

const curve = (id='c1', confidence=.7, extra={}) => ({
  id, type:'curve', confidence, actionable:true,
  suggestedDeltaPercent:5, evidenceCount:3, index:1, ...extra,
});
const map = (id='m1', confidence=.7, extra={}) => ({
  id, type:'map', confidence, actionable:true,
  suggestedDeltaPercent:-4, evidenceCount:4, row:2, column:3, ...extra,
});

test('01 curve is global only', () => assert.equal(model.normalize(curve()).scope, 'global'));
test('02 map is local only', () => assert.equal(model.normalize(map()).scope, 'local'));
test('03 unknown type is never actionable', () => assert.equal(model.normalize({actionable:true,suggestedDeltaPercent:2}).actionable, false));
test('04 NaN delta is never actionable', () => assert.equal(model.normalize({type:'map',actionable:true,suggestedDeltaPercent:NaN}).actionable, false));
test('05 actionable must be explicitly true', () => assert.equal(model.normalize({type:'map',suggestedDeltaPercent:2}).actionable, false));
test('06 confidence above one clamps to one', () => assert.equal(model.normalize(map('x',9)).confidence, 1));
test('07 negative confidence clamps to zero', () => assert.equal(model.normalize(map('x',-3)).confidence, 0));
test('08 fractional evidence is truncated', () => assert.equal(model.normalize(map('x',.7,{evidenceCount:7.9})).evidence, 7));
test('09 negative evidence never becomes positive mass', () => assert.equal(model.normalize(map('x',.7,{evidenceCount:-9})).evidence, 0));
test('10 actionable fallback reason requires human review', () => assert.match(model.normalize(map()).reason, /revis/i));
test('11 high confidence starts at 0.80', () => assert.equal(model.classify(map('x',.80)).confidenceLabel, 'alta'));
test('12 medium confidence starts at 0.55', () => assert.equal(model.classify(map('x',.55)).confidenceLabel, 'média'));
test('13 below 0.55 remains low', () => assert.equal(model.classify(map('x',.549)).confidenceLabel, 'baixa'));
test('14 curve destination is Curva K', () => assert.equal(model.classify(curve()).destination, 'Curva K'));
test('15 map destination is Mapa K', () => assert.equal(model.classify(map()).destination, 'Mapa K'));
test('16 predictor list takes precedence over residual suggestion fallback', () => {
  const x = model.split({mapResidualPredictions:[{...map('p'),supportType:'DIRECT'}],mapResidualSuggestions:[map('fallback')]});
  assert.deepEqual(x.map.map(v=>v.id), ['p']);
});
test('17 GLOBAL_ONLY prediction cannot fabricate local row', () => {
  const x = model.split({mapResidualPredictions:[{...map('g'),supportType:'GLOBAL_ONLY'}]});
  assert.equal(x.map.length, 0);
});
test('18 DIRECT prediction may project local row', () => {
  const x = model.split({mapResidualPredictions:[{...map('d'),supportType:'DIRECT'}]});
  assert.deepEqual(x.map.map(v=>v.id), ['d']);
});
test('19 NEAR prediction may project local row', () => {
  const x = model.split({mapResidualPredictions:[{...map('n'),supportType:'NEAR'}]});
  assert.deepEqual(x.map.map(v=>v.id), ['n']);
});
test('20 residual fallback works only when prediction list is absent', () => {
  const x = model.split({mapResidualSuggestions:[map('r')]});
  assert.deepEqual(x.map.map(v=>v.id), ['r']);
});
test('21 actionable output sorts by confidence descending', () => {
  const x = model.split({kFactorSuggestions:[curve('low',.2),curve('high',.9)]});
  assert.deepEqual(x.actionable.map(v=>v.id), ['high','low']);
});
test('22 insufficient items never enter actionable output', () => {
  const x = model.split({kFactorSuggestions:[curve('bad',.9,{actionable:false})]});
  assert.equal(x.actionable.length, 0); assert.equal(x.insufficient.length, 1);
});
test('23 reviewing curve opens editor without ECU write', () => {
  const x = model.reviewAction(curve()); assert.equal(x.allowed,true); assert.equal(x.action,'open-curve-editor'); assert.equal(x.writesEcu,false);
});
test('24 reviewing map opens editor without ECU write', () => {
  const x = model.reviewAction(map()); assert.equal(x.allowed,true); assert.equal(x.action,'open-map-editor'); assert.equal(x.writesEcu,false);
});
test('25 invalid item cannot open editor', () => {
  const x = model.reviewAction({type:'map',actionable:false}); assert.equal(x.allowed,false); assert.equal(x.action,'none');
});
test('26 identical advice is deterministic over 1000 evaluations', () => {
  const advice={kFactorSuggestions:[curve('c')],mapResidualPredictions:[{...map('m'),supportType:'NEAR'}]};
  const expected=JSON.stringify(model.split(advice));
  for(let i=0;i<1000;i++) assert.equal(JSON.stringify(model.split(advice)), expected);
});
test('27 split does not mutate its input', () => {
  const advice={kFactorSuggestions:[curve('c')],mapResidualSuggestions:[map('m')]};
  const before=JSON.stringify(advice); model.split(advice); assert.equal(JSON.stringify(advice), before);
});
test('28 absent numeric coordinates stay null instead of invented zeroes', () => {
  const x=model.normalize({type:'map',actionable:false});
  assert.equal(x.row,null); assert.equal(x.column,null); assert.equal(x.petrolMs,null); assert.equal(x.deltaPercent,null);
});
test('29 confidence percent rounds after clamping', () => {
  assert.equal(model.normalize(map('a',.554)).confidencePercent,55);
  assert.equal(model.normalize(map('b',5)).confidencePercent,100);
  assert.equal(model.normalize(map('c',-5)).confidencePercent,0);
});
test('30 fuzzed invalid inputs never become actionable accidentally', () => {
  for(let i=0;i<2000;i++){
    const raw={type:i%3===0?'weird':null,actionable:i%2===0,suggestedDeltaPercent:i%5===0?NaN:undefined,confidence:(i%17)-8,evidenceCount:-i};
    assert.equal(model.normalize(raw).actionable,false);
  }
});
