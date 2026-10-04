'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const {freshContext,UI}=require('./_support.cjs');
const path=require('node:path');
function chart(){const ctx=freshContext({console});vm.runInContext(fs.readFileSync(path.join(UI,'screens/autocal-cockpit.js'),'utf8'),ctx);return ctx.OmegasUi.CurveChart;}
test('escala elástica: 12 ms não reserva 22; referência válida alta e >22 nunca são cortadas',()=>{
 const c=chart();
 const short=[{petrolMs:2,petrolMapBar:.3,gasMapBar:.32},{petrolMs:12,petrolMapBar:.8,gasMapBar:.82},{petrolMs:22,petrolMapBar:0,gasMapBar:0}];
 const d=c.focusDomain(short,[],[]);assert.ok(d.xMax>12&&d.xMax<14);assert.ok(d.yMin<.3&&d.yMax>.82&&d.yMax<1);
 const high=c.focusDomain([...short,{petrolMs:26,petrolMapBar:1.4}],[],[]);assert.ok(high.xMax>26&&high.yMax>1.4);
 const full=c.focusDomain(short,[],[],{fullRange:true});assert.ok(full.xMax>=22&&full.yMax>=1.15);
 const old=c.focusDomain([...short,{petrolMs:30,gasMapBar:1.5}],[],[]);assert.ok(old.xMax>30&&old.yMax>1.5);
 const eq=c.focusDomain([{petrolMs:10,petrolMapBar:.8,gasEquivalentMs:28}],[],[],{equivalent:true});assert.ok(eq.xMax>28);
});
test('Refino: betweenPoints físicos participam da escala; esconder GNV remove seu domínio',()=>{
 const c=chart();
 const axis=[2,8];const snapshot={available:true,fields:[
 {key:'PETR_INJ_TBP',status:'VALID',physicalValues:axis},
 {key:'PETR_MNFLD_PRESS_RV',status:'VALID',physicalValues:[.3,.7]},
 {key:'GAS_MNFLD_PRESS_RV',status:'VALID',physicalValues:[.35,1.3]}]};
 const eq={betweenPoints:[{index:0,state:'coletado',gas:{ms:27,mapBar:1.6,n:8},petrol:{ms:7,mapBar:.65,n:8}}]};
 const model=c.buildModel({snapshot,projection:{snapshot},eq,mode:'between'});
 assert.ok(model.domain.xMax>27&&model.domain.yMax>1.6);
 const onlyPetrol=c.buildModel({snapshot,projection:{snapshot},eq,mode:'between',view:{gas:false}});
 assert.ok(onlyPetrol.domain.xMax<10&&onlyPetrol.domain.yMax<1);
 const svg=c.buildSvg(onlyPetrol,{mode:'between'}).svg;
 assert.doesNotMatch(svg,/chart-between gas collected/);
});
const L=require('./wiring/lib.cjs');
const {mapApp}=require('./wiring/scenarios.cjs');
function touch(app,row,column){
 const el=app.$('.map-k-cell[data-row="'+row+'"][data-column="'+column+'"]');
 el.dispatchEvent(new app.win.Event('pointerdown',{bubbles:true,pointerId:1}));
 el.dispatchEvent(new app.win.Event('pointerup',{bubbles:true,pointerId:1}));app.flush();
}
function adjust(app,value){
 app.byId('mapAdjustmentMode').value='target';
 app.byId('mapAdjustmentValue').value=value;
 app.byId('mapAdjustmentValue').dispatchEvent(new app.win.Event('input',{bubbles:true}));app.flush();
}
test('Mapa K: campo apagado invalida prévia anterior e impede escrita',()=>{
 const app=mapApp();touch(app,2,3);adjust(app,'150');
 assert.equal(app.byId('mapReviewButton').disabled,false);
 adjust(app,'');assert.equal(app.byId('mapReviewButton').disabled,true);
 const before=app.world.mark();app.byId('mapReviewButton').click();app.settle(2);
 assert.equal(L.actionCalls(app,before).length,0);L.assertClean(app,'campo vazio');app.destroy();
});
test('Mapa K: trocar e desmarcar célula atualiza contexto, K e prévia',()=>{
 const app=mapApp();touch(app,2,3);adjust(app,'150');
 assert.match(app.byId('mapActiveCell').textContent,/K .*→ 150/);
 touch(app,4,6);const current=app.byId('mapActiveCell').textContent;
 touch(app,4,6);assert.notEqual(app.byId('mapActiveCell').textContent,current);
 app.byId('mapClearSelection').click();app.flush();
 assert.equal(app.byId('mapActiveCell').textContent,'Toque em uma célula');
 L.assertClean(app,'seleção');app.destroy();
});
test('AutoCal: leitura anterior precede pausa; estado integrado e ações secundárias agrupadas',()=>{
 const app=L.boot();app.go('autocal');app.settle(3);
 const pause=app.$('[data-autocal-toggle]'),history=app.$('[data-autocal-history]');
 assert.ok(pause.parentNode===history.parentNode,'histórico e pausa no mesmo grupo');
 assert.equal(pause.parentNode.children.indexOf(history)+1,pause.parentNode.children.indexOf(pause));
 assert.ok(app.byId('autocalHumanAction').closest('.ar-status'));
 assert.ok(app.$('[data-autocal-action="RESET_GAS"]').closest('.ar-buttons').querySelector('summary'));
 L.assertClean(app,'autocal');app.destroy();
});

test('Refino: a régua completa não espreme aquisição curta; faixa inteira continua disponível',()=>{
 const c=chart();const axis=Array.from({length:30},(_,i)=>.5+i*.75);
 const snapshot={available:true,fields:[
 {key:'PETR_INJ_TBP',status:'VALID',physicalValues:axis},
 {key:'PETR_MNFLD_PRESS_RV',status:'VALID',physicalValues:axis.map(x=>.1+x*.05)},
 {key:'GAS_MNFLD_PRESS_RV',status:'VALID',physicalValues:axis.map(x=>.12+x*.05)}]};
 const eq={betweenPoints:[{index:0,kind:'gap',state:'coletado',gas:{ms:8,mapBar:.5,n:100},petrol:{ms:8.1,mapBar:.5,n:100}}]};
 const focused=c.buildModel({snapshot,projection:{snapshot},eq,mode:'between'});
 assert.ok(focused.domain.xMax<10);assert.ok(focused.reference.length<30);
 const full=c.buildModel({snapshot,projection:{snapshot},eq,mode:'between',view:{fullRange:true}});
 assert.equal(full.reference.length,30);assert.ok(full.domain.xMax>=22);
 const ends=c.buildModel({snapshot,projection:{snapshot},eq:{betweenPoints:[...eq.betweenPoints,{index:-1,kind:'open-low',gas:{ms:.2,mapBar:.1,n:100}}]},mode:'between'});
 assert.equal(ends.betweenPoints.length,1,'somente pontos entre bandas');
});
test('Refino: reinício local de GNV é explícito, não escreve a ECU e respeita operação em curso',()=>{
 const app=L.boot();app.go('refino');app.settle(4);
 const screen=app.win.OmegasApp.refino;let resets=0;
 screen.api={...screen.api,resetGasEvidence:()=>{resets++;return {ok:true};}};
 const before=app.world.mark();screen.resetGasEvidence();assert.equal(resets,1);
 assert.equal(L.actionCalls(app,before).length,0,'nenhum escritor nativo acionado');
 screen.operation={phase:'writing'};screen.resetGasEvidence();assert.equal(resets,1);
 L.assertClean(app,'reset local');app.destroy();
});

test('Refino: reiniciar evidência local preserva Desfazer da curva; mudança externa continua bloqueada',()=>{
 const ctx=freshContext({console});vm.runInContext(fs.readFileSync(path.join(UI,'screens/refino.js'),'utf8'),ctx);
 const source=ctx.OmegasUi.RefinoModel.undoSource;
 const photo={status:'INTERROMPIDO',photoFile:'antes.json',interruptReason:'REINICIO_GNV_PELO_DONO'};
 assert.equal(source(photo).available,true);assert.equal(source(photo).changedByEcu,false);
 assert.equal(source({...photo,interruptReason:'AUTOMATCH_NATIVO'}).available,false);
});

test('Refino: contrato sem ação bloqueia proposta antiga; salvar referência mantém sua intenção',()=>{
 const app=L.boot();app.go('refino');app.settle(3);const screen=app.win.OmegasApp.refino;
 screen.analysis={available:true,points:[{index:0,currentRaw:16384,calculatedRaw:17000,origin:'MEASURED'}]};
 screen.eq={autopilot:{phase:'PROPOSTA_PRONTA'},nextAction:{kind:'APPLY'},refinoState:{canAct:false,nextAction:'Seguir dirigindo'}};
 assert.equal(screen.actionModel().kind,'none');
 screen.eq={...screen.eq,nextAction:{kind:'FREEZE_REFERENCE'},refinoState:{canAct:true,nextAction:'Salvar a gasolina da ECU como referência'}};
 assert.equal(screen.actionModel().kind,'freeze');assert.match(screen.actionModel().label,/referência/);
 app.destroy();
});

test('Refino: reiniciar GNV não reapresenta a proposta guardada no cache da ponte',()=>{
 const app=L.boot();app.go('refino');app.settle(3);const screen=app.win.OmegasApp.refino;
 let eq={...screen.eq,gasObservations:30,autopilot:{phase:'PROPOSTA_PRONTA'},refinoState:{canAct:true,nextAction:'Gravar 1 ponto'},nextAction:{kind:'APPLY'}};
 screen.api={...screen.api,resetGasEvidence:()=>({ok:true}),equivalenceFresh:()=>eq,equivalence:()=>eq};
 screen.resetGasEvidence();assert.equal(screen.gasResetPending,true);assert.equal(screen.actionModel().kind,'none');
 eq={...eq,gasObservations:2,gasEpochReason:'REINICIO_GNV_PELO_DONO',refinoState:{canAct:false,nextAction:'Seguir dirigindo'},nextAction:{kind:'COLLECT'}};
 screen.refresh(true);assert.equal(screen.gasResetPending,false);assert.equal(screen.actionModel().kind,'none');
 app.destroy();
});
