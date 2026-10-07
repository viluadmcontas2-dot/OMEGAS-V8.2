'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const {freshContext,UI}=require('./_support.cjs');
const ctx=freshContext({console});vm.runInContext(fs.readFileSync(UI+'/screens/autocal-cockpit.js','utf8'),ctx);
const model=ctx.OmegasUi.AutoCalUxModel,proto=ctx.OmegasUi.AutoCalCockpit.prototype;
const status={state:'ACQUIRING',autoCalEnabled:1};
const projection=(petrol,gas)=>({ok:true,acquisitionZones:{petrol,gas}});
test('zonas não recebidas não viram quatro zonas faltantes',()=>{
 for(const values of [[],[true],[true,false,true],['true',true,true,true]]){
  const h=model.humanState({},status,projection(values,values));
  assert.equal(h.petrolZones,null);assert.equal(h.gasZones,null);
  assert.equal(h.petrolMissingZones.length,0);assert.equal(h.gasMissingZones.length,0);
 }
});
test('gasolina em aquisição é descrita antes de GNV pendente',()=>{
 const h=model.humanState({},status,projection([false,true,false,true],[false,false,false,false]));
 const sentence=proto.sentenceFor.call({projection:{},actionState:{}},h,'ACQUIRING','petrol');
 assert.match(sentence.text,/aprendendo a gasolina/i);assert.match(sentence.text,/zonas 1 e 3/);
 assert.doesNotMatch(sentence.text,/dirija.*GNV|GNV completo/i);
 assert.match(h.progress,/Gasolina 2\/4/);assert.match(h.progress,/GNV 0\/4/);
});
test('GNV em aquisição não esconde a cobertura da gasolina',()=>{
 const h=model.humanState({},status,projection([false,false,false,false],[true,true,false,false]));
 const sentence=proto.sentenceFor.call({projection:{},actionState:{}},h,'ACQUIRING','gas');
 assert.match(sentence.text,/aprendendo o GNV/i);assert.match(sentence.text,/zonas 3 e 4/);
 assert.equal(h.petrolMissingZones.length,4);
});
test('releitura de gasolina enquanto ECU confere não anuncia aquisição concluída',()=>{
 const h=model.humanState({},status,projection([true,true,true,true],[false,false,false,false]));
 const sentence=proto.sentenceFor.call({projection:{},actionState:{action:'RESET_PETROL',state:'READING_AFTER',busy:true}},h,'ACQUIRING','gas');
 assert.match(sentence.text,/gasolina/i);assert.match(sentence.text,/conferindo/i);
 assert.doesNotMatch(sentence.text,/aprendidos|completo/i);
});
test('curva gasolina pendente após contadores novos nunca vira narrativa de aquisição GNV',()=>{
 const text=model.epochNarrative({petrolPending:false,gasPending:false,petrolReferencePending:true,gasReferencePending:false,referencePending:true});
 assert.match(text,/gasolina/i);assert.doesNotMatch(text,/GNV atual sendo adquirido/i);
});
test('desconexão e zonas desconhecidas não instruem dirigir para zonas inventadas',()=>{
 const h=model.humanState({}, {state:'DISCONNECTED'},projection([],[]));
 const disconnected=proto.sentenceFor.call({projection:{},actionState:{}},h,'DISCONNECTED','gas');
 assert.match(disconnected.text,/cabo|conexão/i);
 const pending=proto.sentenceFor.call({projection:{},actionState:{}},model.humanState({},status,projection([],[])),'ACQUIRING','petrol');
 assert.match(pending.text,/Aguardando.*zonas/i);assert.doesNotMatch(pending.text,/Falta Z1/);
});
const L=require('./wiring/lib.cjs');
// Revisto (W2): o AutoCal não tem mais faixa de zonas nem menu de opções; o gráfico ocupa a área e a barra é única.
test('AGORA se move durante reaquisição sem atualizar snapshot ou referência',()=>{
 const app=L.boot();try{app.go('autocal');app.settle(3);const screen=app.win.OmegasApp.autoCalCockpit;
  const point={index:0,petrolMs:4,mapBar:.4,counter:2,progress:.2,acquisitionState:'COLLECTING'};
  const epoch={comparisonAllowed:false,petrolPending:false,petrolReferencePending:true,gasReferencePending:true};
  screen.renderAcquisitionEpochChart([point],[],epoch,app.byId('autocalReferenceChart'));
  const snapshot=screen.snapshot;assert.ok(screen.chartScale);const layer=app.$('.autocal-live-layer');assert.ok(layer);
  const svg=app.$('#autocalReferenceChart svg');
  screen.renderAcquisitionEpochChart([{...point}],[],{...epoch},app.byId('autocalReferenceChart'));
  assert.equal(app.$('#autocalReferenceChart svg'),svg,'dados iguais preservam o SVG durante reaquisição');
  assert.equal(app.$('.autocal-live-layer'),layer,'a camada viva não é recriada');
  const setLive=ms=>{const t=screen.store.get().telemetry;screen.store.patch({telemetry:{...t,valid:true,ageMs:0,telemetryAgeMs:0,live:{...t.live,petrol_ms:ms,load_bar:.4,rpm:1200,fuel:'GASOLINA'}}});screen.renderLiveCursor();for(let i=0;i<25;i++)screen.animateCursor(i*20);};
  setLive(4);const before=layer.style.transform;setLive(5);assert.notEqual(layer.style.transform,before);
  assert.equal(screen.snapshot,snapshot,'cursor não depende de nova leitura de curva');L.assertClean(app,'cursor aquisição');
 }finally{app.destroy();}
});
test('normal → gasolina reiniciada → curva nova restaura o gráfico sem referência antiga',()=>{
 const app=L.boot();try{app.go('autocal');app.settle(3);const screen=app.win.OmegasApp.autoCalCockpit;
  const original=screen.api.projection();const reset=JSON.parse(JSON.stringify(original));
  reset.referenceUsable=false;reset.acquisitionZones={petrol:[],gas:original.acquisitionZones.gas};
  reset.liveAcquisitionEpoch={comparisonAllowed:false,petrolPending:true,petrolReferencePending:true,gasPending:false,gasReferencePending:false,petrolGeneration:1,gasGeneration:0};
  for(const snap of [reset.snapshot,reset.nativeSnapshot]) for(const f of snap.fields||[]) if(['PETR_INJ_TBP','PETR_MNFLD_PRESS_RV','PETR_INJ_TBUF','MNFLD_PRESS_BUF','NUM_BUF_UPD_PETR','ACQUIRED_ZONES_PETROL'].includes(f.key)){f.status='STALE_EPOCH';f.rawValues=[];f.physicalValues=[];}
  let current=reset;screen.api={...screen.api,projection:()=>current,actionStatus:()=>({action:'RESET_PETROL',state:'CONFIRMED',busy:false})};screen.refresh();
  assert.equal(app.$('#autocalReferenceChart .autocal-reference-line.petrol'),null);
  assert.match(app.byId('autocalChartInspector').textContent,/gasolina/i);
  current=original;screen.refresh();assert.ok(app.$('#autocalReferenceChart [data-chart-live]'));
  assert.equal(screen.referenceUsable,true);L.assertClean(app,'transição gasolina');
 }finally{app.destroy();}
});
test('curva pendente não afirma aquisição ativa com leitura pausada ou desconhecida',()=>{
 for(const enabled of [0,null]){
  const h=model.humanState({}, {state:'ACQUIRING',autoCalEnabled:enabled},projection([],[]));
  const sentence=proto.sentenceFor.call({projection:{},actionState:{}},h,'ACQUIRING','petrol');
  if(enabled===0)assert.match(sentence.text,/pausado/i);
  for(const epoch of [{petrolReferencePending:true},{gasReferencePending:true}])assert.doesNotMatch(model.epochNarrative(epoch),/em aquisição|adquirindo/i);
 }
});

test('comandos operacionais ficam fora dos painéis de detalhes',()=>{
 const app=L.boot();try{app.go('autocal');app.settle(3);
  for(const selector of ['[data-autocal-action="RESET_GAS"]','[data-autocal-action="RESET_PETROL"]']) assert.equal(app.$(selector).closest('details'),null,selector);
  // Revisto (W2): sem atalho "Ver sessões" no AutoCal (há a aba Sessões).
  assert.equal(app.$('[data-autocal-sessions]'),null);
  app.go('refino');app.settle(3);
  for(const selector of ['[data-refino-reset-gas]','[data-refino-acquisition]']) assert.equal(app.$(selector).closest('details'),null,selector);
  app.$('[data-refino-acquisition]').click();assert.equal(app.$('.screen.active').dataset.screen,'autocal');
  L.assertClean(app,'comandos diretos');
 }finally{app.destroy();}
});

// Revisto (P2 seleção): tocar no ponto marca/desmarca; a barra só tem "Reaprender N pontos" e "Cancelar".
test('pontos: tocar marca/desmarca, dois botões na barra e conferência antes de limpar',()=>{
 const app=L.boot();try{app.go('autocal');app.settle(3);const screen=app.win.OmegasApp.autoCalCockpit;
  const points=screen.currentAcquiredPoints.slice(0,2);assert.equal(points.length,2);
  let status={};let targets=[];
  screen.api={...screen.api,actionStatus:()=>status,preparePointDeleteBatch:t=>{targets=t;return {ok:true,prepared:true,preparationId:'batch'};},execute:()=>{status={action:'DELETE_POINT',state:'READING_AFTER',busy:true};return {ok:true,started:true};}};
  const svg=app.$('#autocalReferenceChart svg');
  for(const p of points) screen.tapAcquiredPoint(p.fuel,p.index);
  assert.equal(screen.selectedAcquiredPoints.size,2);
  const bar=app.$('.autocal-point-actions');assert.equal(bar.hidden,false);
  assert.deepEqual(bar.querySelectorAll('button').map(b=>b.textContent),['Reaprender 2 pontos','Cancelar']);
  screen.tapAcquiredPoint(points[1].fuel,points[1].index);assert.equal(screen.selectedAcquiredPoints.size,1,'tocar de novo desmarca');
  screen.tapAcquiredPoint(points[1].fuel,points[1].index);
  assert.match(app.$('#autocalChartInspector').textContent,/A ECU já passou aqui/);
  const button=app.$('[data-autocal-reacquire-selected]');assert.equal(button.closest('details'),null);assert.equal(button.closest('.ar-readout'),null);
  assert.equal(app.$('#autocalChartInspector button'),null);
  assert.equal(app.$('#autocalReferenceChart svg'),svg,'selecionar não refaz o gráfico');
  button.click();assert.equal(targets.length,2);assert.equal(screen.selectedAcquiredPoints.size,2);assert.equal(button.disabled,true);
  status={action:'DELETE_POINT',state:'FAILED',busy:false};screen.refresh();assert.equal(screen.selectedAcquiredPoints.size,2,'falha sem mexer na ECU: a intenção fica');assert.equal(button.disabled,false);
  button.click();
  status={action:'DELETE_POINT',state:'CONFIRMED',busy:false};screen.refresh();assert.equal(screen.selectedAcquiredPoints.size,0);assert.equal(app.$('.autocal-point-actions').hidden,true);
  // Ponto já apagado: cinza e intocável até a ECU mandar leitura mais nova (nada de apagar de novo).
  const key=points[0].fuel+':'+points[0].index;
  assert.equal(screen.recentlyDeleted.has(key),true);
  screen.tapAcquiredPoint(points[0].fuel,points[0].index);
  assert.equal(screen.selectedAcquiredPoints.size,0,'ponto recém-apagado não pode ser marcado');
  assert.ok(app.$(`#autocalReferenceChart [data-autocal-point-key="${key}"]`).classList.contains('recently-deleted'),'aparece cinza');
  // Leitura da ECU mais nova que a confirmação: o ponto deixa de ser "recém-apagado".
  const next=JSON.parse(JSON.stringify(app.world.projection));
  const bump=f=>{if(f&&f.capturedAtMs)f.capturedAtMs+=10*60000;};
  [next.snapshot,next.nativeSnapshot].filter(Boolean).forEach(snap=>{bump(snap);(snap.fields||[]).forEach(bump);});
  app.world.projection=next;screen.refresh();
  assert.equal(screen.recentlyDeleted.has(key),false,'leitura nova libera o ponto');
  // Cancelar desmarca tudo.
  const other=screen.currentAcquiredPoints.find(p=>!screen.recentlyDeleted.has(p.fuel+':'+p.index));
  if(other){screen.tapAcquiredPoint(other.fuel,other.index);app.$('[data-autocal-clear-point-selection]').click();assert.equal(screen.selectedAcquiredPoints.size,0);}
  L.assertClean(app,'seleção e conferência');
 }finally{app.destroy();}
});

test('pontos: falha com a ECU possivelmente alterada limpa a seleção; seleção só guarda pontos que existem',()=>{
 const app=L.boot();try{app.go('autocal');app.settle(3);const screen=app.win.OmegasApp.autoCalCockpit;
  const points=screen.currentAcquiredPoints.slice(0,2);
  let status={};
  screen.api={...screen.api,actionStatus:()=>status,preparePointDeleteBatch:()=>({ok:true,prepared:true,preparationId:'b'}),execute:()=>{status={state:'READING_AFTER',busy:true};return {ok:true};}};
  for(const p of points) screen.tapAcquiredPoint(p.fuel,p.index);
  app.$('[data-autocal-reacquire-selected]').click();
  status={state:'FAILED',busy:false,mutationMayHaveStarted:true};screen.refresh();
  assert.equal(screen.selectedAcquiredPoints.size,0,'estado incerto: a seleção velha não vale');
  screen.selectedAcquiredPoints.add('GAS:99');screen.render();
  assert.equal(screen.selectedAcquiredPoints.has('GAS:99'),false,'ponto que não existe sai da seleção antes do render');
 }finally{app.destroy();}
});
