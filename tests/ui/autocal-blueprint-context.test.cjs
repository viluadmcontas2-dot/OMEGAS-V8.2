const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/ui/screens/autocal-cockpit.js', 'utf8');
const ctx = { console, setTimeout: () => 0, clearTimeout: () => {} };
ctx.globalThis = ctx;
vm.createContext(ctx);
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
test('reference transition keeps no previous reading (dono, 2026-10-11: sem leitura anterior)', () => {
  for (const next of [{...projection,referenceUsable:false},{...projection,sessionId:2}]) {
    const t=model.referenceTransition(projection,next,snapshot,snapshot,{});
    assert.equal('clearHistory' in t,false);
    assert.equal('previousPoints' in t,false);
    assert.equal(t.resetSelection,true);
  }
});
test('malformed execution response never reports success and frees the next tap', () => {
  const alerts=[]; let cancelled=0;
  const c=Object.create(Cockpit.prototype);
  c.prepared={preparationId:'prepared'};
  c.api={execute:()=>({}),cancelPreparation:()=>{cancelled++;}};
  c.store={patch:a=>alerts.push(a)};
  c.refresh=()=>{};
  ctx.document={getElementById:()=>null};
  c.confirmPrepared();
  assert.equal(c.prepared,null);
  assert.equal(cancelled,1);
  assert.equal(alerts.length,1);
  assert.equal(alerts[0].alert.level,'warning');
});
test('reset keeps no previous reading (dono, 2026-10-11: sem leitura anterior)', () => {
  const c=Object.create(Cockpit.prototype);
  Object.assign(c,{prepared:{preparationId:'p',action:'RESET_GAS'},snapshot,analysis:{},projection,referenceUsable:true});
  c.api={execute:()=>({ok:true,started:true}),projection:()=>({...projection,referenceUsable:false,snapshot:{fields:[]}}),actionStatus:()=>({}),sessionStatus:()=>({}),available:()=>true};
  c.store={patch:()=>{}};
  c.render=()=>{};
  ctx.document={getElementById:()=>null};
  c.confirmPrepared();
  assert.equal(c.prepared,null);
  assert.equal('previousReferencePoints' in c,false);
  c.api.projection=()=>projection;
  c.refresh();
  assert.equal('previousReferencePoints' in c,false);
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
