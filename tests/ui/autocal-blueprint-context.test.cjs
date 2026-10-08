const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/ui/screens/autocal-cockpit.js', 'utf8');
const ctx = { console, setTimeout: () => 0, clearTimeout: () => {} };
ctx.globalThis = ctx;
vm.createContext(ctx);
require('./_support.cjs').preload(ctx);
vm.runInContext(source, ctx);
const { AutoCalUxModel: model, AutoCalCockpit: Cockpit } = ctx.OmegasUi;
const snapshot = { available: true, snapshotHash: 'first', fields: [
  {key:'PETR_INJ_TBP',status:'VALID',physicalValues:[2,5]},
  {key:'PETR_MNFLD_PRESS_RV',status:'VALID',physicalValues:[.3,.6]},
  {key:'GAS_MNFLD_PRESS_RV',status:'VALID',physicalValues:[.32,.65]}
]};
const projection = { ok:true, sessionId:1, referenceUsable:true, snapshot, analysis:{} };
test('metadata-only update preserves selection and comparison', () => {
  const transition = model.referenceTransition(projection, projection, snapshot, {...snapshot,snapshotHash:'counter-changed'}, {});
  assert.equal(transition.referenceChanged, false);
  assert.equal(transition.resetSelection, false);
});
test('troca de sessão USB limpa a seleção; interrupção da referência não', () => {
  assert.equal(model.referenceTransition(projection,{...projection,referenceUsable:false},snapshot,{},{}).sessionChanged,false);
  assert.equal(model.referenceTransition(projection,{...projection,sessionId:2},snapshot,snapshot,{}).sessionChanged,true);
});
test('malformed execution response does not dismiss confirmation or report success', () => {
  const alerts=[];
  const c=Object.create(Cockpit.prototype);
  c.prepared={preparationId:'prepared'};
  c.api={execute:()=>({})};
  c.store={patch:a=>alerts.push(a)};
  c.refresh=()=>{};
  ctx.document={getElementById:()=>null};
  c.confirmPrepared();
  assert.equal(c.prepared?.preparationId,'prepared');
  assert.equal(alerts[0].alert.level,'warning');
});
test('depois do reset a leitura anterior não é guardada em lugar nenhum', () => {
  const c=Object.create(Cockpit.prototype);
  Object.assign(c,{prepared:{preparationId:'p',action:'RESET_GAS'},snapshot,analysis:{},projection,referenceUsable:true});
  c.api={execute:()=>({ok:true,started:true}),projection:()=>projection,actionStatus:()=>({}),sessionStatus:()=>({}),available:()=>true};
  c.store={patch:()=>{}};
  c.render=()=>{};
  ctx.document={getElementById:()=>null};
  c.confirmPrepared();
  assert.equal('previousReferencePoints' in c,false);
  assert.equal('chartHistoryVisible' in c,false);
});

test('unchanged native geometry does not replace the chart DOM', () => {
  let replacements=0;
  const host={set innerHTML(value){replacements++},querySelector:()=>null};
  const nodes=new Map();
  ctx.document={getElementById:id=>id==='autocalReferenceChart'?host:(nodes.has(id)?nodes.get(id):(nodes.set(id,{}),nodes.get(id))),querySelectorAll:()=>[]};
  const c=Object.create(Cockpit.prototype);
  Object.assign(c,{snapshot,analysis:{},projection,referenceUsable:true,store:{get:()=>({telemetry:{}})},state:{}});
  c.renderLiveCursor=()=>{};
  c.renderReferenceChart(snapshot);
  c.renderReferenceChart({...snapshot,snapshotHash:'another-counter'});
  assert.equal(replacements,1);
});
