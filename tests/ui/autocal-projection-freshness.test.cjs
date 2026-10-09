'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const context = { console, setTimeout:()=>0, clearTimeout:()=>{} };
context.globalThis = context;
vm.createContext(context);
require('./_support.cjs').preload(context);
vm.runInContext(fs.readFileSync(path.join(root, 'app/src/main/assets/ui/screens/autocal-cockpit.js'), 'utf8'),context);
const model=context.OmegasUi.AutoCalUxModel;
test('revisão de tabelas posterior à projeção obriga releitura até o cache alcançar a ECU',()=>{
  assert.equal(model.projectionBehindRevisions({ok:true,transportTablesRevision:12},{tables:13}),true);
  assert.equal(model.projectionBehindRevisions({ok:true,transportTablesRevision:13},{tables:13}),false);
  assert.equal(model.projectionBehindRevisions({ok:true,transportTablesRevision:14},{tables:13}),false);
});
test('uma nova sessão também impede mostrar uma projeção antiga do veículo',()=>{
  assert.equal(model.projectionBehindRevisions(
    { ok:true,transportTablesRevision:4,transportSessionRevision:2 },
    { tables:4,session:3 },
  ),true);
  assert.equal(model.projectionBehindRevisions(
    { ok:true,transportTablesRevision:4,transportSessionRevision:3 },
    { tables:4,session:3 },
  ),false);
});
test('modo legado, ausência de revision e revisão inicial não produzem polling infinito',()=>{
  assert.equal(model.projectionBehindRevisions({ok:true,revision:'hash'},{tables:55}),false);
  assert.equal(model.projectionBehindRevisions({ok:true,transportTablesRevision:0},{tables:-1}),false);
  assert.equal(model.projectionBehindRevisions({ok:true,transportTablesRevision:null},{tables:3}),false);
});
test('a cadência rápida respeita ponte antiga mas aguarda estado novo da nativa',()=>{
  const code=fs.readFileSync(path.join(root,'app/src/main/assets/ui/screens/autocal-cockpit.js'),'utf8');
  assert.match(code,/this\.projectionWarming\s*=\s*!authoritative\s*\|\|\s*AutoCalUxModel\.projectionBehindRevisions/);
  assert.match(code,/else if \(this\.projectionWarming\)/);
});
test('bridge invalida memorando por revisão, nunca diminui o timer global nem muda bytes ECU',()=>{
  const bridge=fs.readFileSync(path.join(root,'app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt'),'utf8');
  assert.match(bridge,/transportTablesRevision/);
  assert.match(bridge,/projectionMemo\.invalidate\(\)/);
  assert.match(bridge,/projectionMemo\.getNonBlocking/);
  assert.match(bridge,/refreshMs = 1_000L/);
});
