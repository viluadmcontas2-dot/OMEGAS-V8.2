'use strict';
// A ECU ORIGINAL fornece as faixas/buffers/contadores, não a nossa simulação.
// Evidência de modelo JS; não substitui o teste do monitor Kotlin ou hardware.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const fixture = JSON.parse(fs.readFileSync(path.join(root,'tests/fixtures/portmon-lognovo-autocal-epochs-v1.json'),'utf8'));
const cockpit = fs.readFileSync(path.join(root,'app/src/main/assets/ui/screens/autocal-cockpit.js'),'utf8');
const ctx = { console, setTimeout:()=>0, clearTimeout:()=>{} };
ctx.globalThis = ctx;
vm.createContext(ctx);
require('./_support.cjs').preload(ctx);
vm.runInContext(cockpit,ctx,{filename:'autocal-cockpit.js'});
const model = ctx.OmegasUi.AutoCalUxModel;

function payload(row) {
  const r=Buffer.from(row.request.replaceAll(' ',''),'hex');
  const v=Buffer.from(row.response.replaceAll(' ',''),'hex');
  assert.ok(v.subarray(0,r.length).equals(r));
  const suffix=v.subarray(r.length);
  assert.equal(suffix[0],0x53);
  assert.equal(suffix[1]+3,suffix.length);
  assert.equal(suffix.subarray(0,-1).reduce((sum,b)=>(sum+b)&255,0),suffix.at(-1));
  return suffix.subarray(2,-1);
}
function vector(row,scale) {
  const bytes=payload(row), result=[];
  assert.equal(bytes.length%2,0);
  for(let i=0;i<bytes.length;i+=2)result.push(bytes.readUInt16LE(i)/scale);
  return result;
}
function counts(row) {return vector(row,1);}
function snapshot(e,stage) {
  const petrol=e.petrolContext;
  const gas=stage==='before'?e.gasCurrentBefore:e.gasCurrentAfterRollover;
  const fields=[];
  const insert=(key,raw,phys)=>fields.push({key,status:'VALID',rawValues:raw,physicalValues:phys||raw});
  const iRaw=row=>vector(row,1);
  for(const [key,v,scale] of [
    ['PETR_INJ_TBUF',petrol.time,512],
    ['MNFLD_PRESS_BUF',petrol.map,1024],
    ['PETR_INJ_TBUF_GAS',gas.time,512],
    ['MNFLD_PRESS_BUF_GAS',gas.map,1024],
  ])insert(key,iRaw(v),vector(v,scale));
  insert('NUM_BUF_UPD_PETR',counts(petrol.counter));
  insert('NUM_BUF_UPD_GAS',counts(gas.counter));
  insert('CALIBRATION_VAL_1',Array.from(payload(fixture.staticConfigurationBeforeEpochs.CAL)));
  insert('VECT_AUTOCAL_U8_1',[3]);
  insert('NUM_AUTOMATCH_EXECUTED',[stage==='before'?e.autoMatchCountBefore:e.autoMatchCountAfter]);
  insert('MUL_ACT',vector(stage==='before'?e.mulBefore:e.mulAfter,1));
  return {fields};
}

test('três AutoMatch ORIGINAIS: consumidor JS acompanha contadores/buffers de cada época, sem misturar combustível',()=>{
  assert.equal(fixture.epochs.length,3);
  for(const e of fixture.epochs){
    const b=snapshot(e,'before'),a=snapshot(e,'after');
    const gasBefore=model.acquiredPoints(b,'gas');
    const gasAfter=model.acquiredPoints(a,'gas');
    const petrolBefore=model.acquiredPoints(b,'petrol');
    const petrolAfter=model.acquiredPoints(a,'petrol');
    const positive=(snap,fuel)=> {
      const key=fuel==='gas'?'NUM_BUF_UPD_GAS':'NUM_BUF_UPD_PETR';
      const field=snap.fields.find(f=>f.key===key);
      const time=snap.fields.find(f=>f.key===(fuel==='gas'?'PETR_INJ_TBUF_GAS':'PETR_INJ_TBUF'));
      const pressure=snap.fields.find(f=>f.key===(fuel==='gas'?'MNFLD_PRESS_BUF_GAS':'MNFLD_PRESS_BUF'));
      return field.rawValues.filter((count,i)=>count>0&&time.physicalValues[i]>0&&Number.isFinite(pressure.physicalValues[i])).length;
    };
    assert.equal(gasBefore.length,positive(b,'gas'), 'GNV antes do AutoMatch '+e.epoch);
    assert.equal(gasAfter.length,positive(a,'gas'), 'GNV depois do AutoMatch '+e.epoch);
    assert.equal(petrolBefore.length,positive(b,'petrol'));
    assert.equal(petrolAfter.length,positive(a,'petrol'));
    assert.ok(gasBefore.every(p=>p.fuel==='GAS'));
    assert.ok(petrolAfter.every(p=>p.fuel==='PETROL'));
    assert.equal(a.fields.find(f=>f.key==='NUM_AUTOMATCH_EXECUTED').rawValues[0],e.epoch);
    assert.equal(b.fields.find(f=>f.key==='NUM_AUTOMATCH_EXECUTED').rawValues[0],e.epoch-1);
    assert.notDeepEqual(b.fields.find(f=>f.key==='MUL_ACT').rawValues,a.fields.find(f=>f.key==='MUL_ACT').rawValues);
  }
});
